package org.comroid.mcsd.web.model;

import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.Session;
import io.graversen.minecraft.rcon.service.IMinecraftRconService;
import lombok.Data;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.comroid.mcsd.web.entity.Server;
import org.comroid.mcsd.web.entity.ShConnection;
import org.comroid.mcsd.web.exception.EntityNotFoundException;
import org.comroid.mcsd.web.repo.ShRepo;
import org.springframework.core.io.ResourceLoader;

import java.io.*;

import static org.comroid.mcsd.web.util.ApplicationContextProvider.bean;

@Data
@Slf4j
@RequiredArgsConstructor
public class ServerConnection implements Closeable {
    @NonNull
    protected Server server;
    protected Session session;
    protected IMinecraftRconService rcon;

    public static boolean send(Server srv, String command) {
        try (ServerConnection exec = new ServerConnection(srv) {

            private ChannelExec channel;

            @Override
            protected boolean startConnection() throws Exception {
                this.channel = (ChannelExec) session.openChannel("exec");

                channel.setCommand(command);
                channel.setOutputStream(System.out);
                channel.connect();

                while (channel.getExitStatus() == -1)
                    //noinspection BusyWait
                    Thread.sleep(10);
                log.info("Command %s for server %s finished with exit code %d".formatted(command, server.getName(), channel.getExitStatus()));
                return true;
            }

            @Override
            public void close() {
                if (channel != null)
                    channel.disconnect();
                super.close();
            }
        }) {
            return exec.start();
        }
    }

    public boolean start() {
        try {
            var con = shConnection();

            this.session = bean(JSch.class).getSession(con.getUsername(), con.getHost(), con.getPort());
            session.setPassword(con.getPassword());
            session.setConfig("StrictHostKeyChecking", "no"); // todo This is bad and unsafe
            session.connect();

            return uploadRunScript() && startConnection();
        } catch (Exception e) {
            log.error("Could not start connection", e);
            return false;
        }
    }

    private boolean uploadRunScript() {
        var lfile = "mcsd.sh";
        var rfile = server.getDirectory() + '/' + lfile;
        var res = bean(ResourceLoader.class).getResource(lfile);
        try (var source = res.getInputStream()) {
            return uploadFile(res.contentLength(), source, lfile, rfile);
        } catch (Exception e) {
            log.error("Unable to upload runscript for Server " + server.getName(), e);
            return false;
        }
    }

    private boolean uploadFile(long length, InputStream source, String filename, String filepath) throws Exception {
        // exec 'scp -t filepath' remotely
        filepath = "'" + filepath + "'";
        var command = "scp -t %s && chmod 754 %s".formatted(filepath, filepath);
        var channel = session.openChannel("exec");
        ((ChannelExec) channel).setCommand(command);

        // get I/O streams for remote scp
        try (OutputStream out = channel.getOutputStream(); InputStream in = channel.getInputStream()) {
            channel.connect();

            if (checkAck(in) != 0)
                log.warn("Unexpected ACK state");

            // send "C0644 filesize filename", where filename should not include '/'
            command = "C0644 %d %s\n".formatted(length, filename);
            out.write(command.getBytes());
            out.flush();
            if (checkAck(in) != 0)
                log.warn("Unexpected ACK state");

            // send a content of filename
            byte[] buf = new byte[1024];
            while (true) {
                int len = source.read(buf, 0, buf.length);
                if (len <= 0) break;
                out.write(buf, 0, len);
                out.flush();
            }

            // send '\0'
            buf[0] = 0;
            out.write(buf, 0, 1);
            out.flush();
            if (checkAck(in) != 0)
                log.warn("Unexpected ACK state");
        }

        channel.disconnect();
        return true;
    }
    private InputStream downloadFile(final String rfile) throws Exception {
        return new InputStream() {
            private final ChannelExec channel;
            private final OutputStream out;
            private final InputStream scp;
            private final String fileName;
            private long available = -1;

            {
                this.channel = (ChannelExec) session.openChannel("exec");
                channel.setCommand("scp -f '%s'".formatted(rfile));
                this.out = channel.getOutputStream();
                this.scp = channel.getInputStream();
                channel.connect();

                out.write(0);
                out.flush();

                // scp ack
                int c = checkAck(scp);
                if (c != 'C')
                    throw new IOException("Invalid scp ACK");

                // scp code
                final String code = "0644 ";
                byte[] buf = new byte[5];
                if (scp.read(buf) != code.length())
                    throw new IOException("Invalid scp code");

                // file size
                if (available == -1) {
                    buf = new byte[1];
                    while (true) {
                        if (scp.read(buf) < 0)
                            throw new RuntimeException("Invalid amount of bytes was read");
                        if (buf[0] == ' ')
                            break;
                        available = available * 10L + (long) (buf[0] - '0');
                    }
                }

                // file name
                buf = new byte[4 * 1024];
                int i;
                for(i = 0; ; i++){
                    if (scp.read(buf, i, 1) == 1 && buf[i]==(byte)0x0a)
                        break;
                }
                fileName = new String(buf, 0, i);

                // start receiving content
                out.write(0);
                out.flush();
            }

            @Override
            public int read() throws IOException {
                if (available <= 0) {
                    return -1;
                } else {
                    available -= 1;
                    return scp.read();
                }
            }

            @Override
            public int available() {
                return Math.toIntExact(available);
            }

            @Override
            @SneakyThrows
            public void close() {
                // scp ack
                if(checkAck(scp)!=0)
                    log.error("Invalid ACK when closing SCP Download Stream " + fileName);

                // send '\0'
                out.write(0);
                out.flush();

                scp.close();
                out.close();
                super.close();
                channel.disconnect();
            }
        };
    }

    public ShConnection shConnection() {
        return bean(ShRepo.class)
                .findById(server.getShConnection())
                .orElseThrow(() -> new EntityNotFoundException(ShConnection.class, server.getShConnection()));
    }

    @Override
    public void close() {
        if (session != null)
            session.disconnect();
    }

    protected boolean startConnection() throws Exception {
        return true;
    }

    private static int checkAck(InputStream in) throws Exception {
        int b = in.read();
        // b may be 0 for success,
        //          1 for error,
        //          2 for fatal error,
        //          -1
        if (b == 0) return b;
        if (b == -1) return b;

        if (b == 1 || b == 2) {
            StringBuffer sb = new StringBuffer();
            int c;
            do {
                c = in.read();
                sb.append((char) c);
            }
            while (c != '\n');

            if (b == 1) log.error("Error: " + sb);// error
            else log.error("Fatal error: " + sb);// fatal error
        }
        return b;
    }
}
