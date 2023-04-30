package org.comroid.mcsd.web.model;

import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.Session;
import io.graversen.minecraft.rcon.service.IMinecraftRconService;
import lombok.Data;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.comroid.mcsd.web.entity.Server;
import org.comroid.mcsd.web.entity.ShConnection;
import org.comroid.mcsd.web.exception.EntityNotFoundException;
import org.comroid.mcsd.web.repo.ShRepo;
import org.springframework.core.io.ResourceLoader;

import java.io.Closeable;
import java.io.InputStream;
import java.io.OutputStream;

import static org.comroid.mcsd.web.util.ApplicationContextProvider.bean;
import static org.comroid.mcsd.web.util.ApplicationContextProvider.get;

@Data
@Slf4j
@RequiredArgsConstructor
public abstract class ServerConnection implements Closeable {
    @NonNull
    protected Server server;
    protected Session session;
    protected IMinecraftRconService rcon;

    public boolean start() {
        try {
            var con = shConnection();

            this.session = get().getBean(JSch.class)
                    .getSession(con.getUsername(), con.getHost(), con.getPort());
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
        try {
            var lfile = "mcsd.sh";
            var rfile = server.getDirectory() + '/' + lfile;

            // exec 'scp -t rfile' remotely
            rfile = "'" + rfile + "'";
            var command = "scp -t %s && chmod 755 %s".formatted(rfile,rfile);
            var channel = session.openChannel("exec");
            ((ChannelExec) channel).setCommand(command);

            // get I/O streams for remote scp
            try (OutputStream out = channel.getOutputStream(); InputStream in = channel.getInputStream()) {
                channel.connect();

                if (checkAck(in) != 0) {
                    throw new Exception("internal error");
                }

                var res = bean(ResourceLoader.class).getResource(lfile);

                // send "C0644 filesize filename", where filename should not include '/'
                long filesize = res.contentLength();
                command = "C0644 " + filesize + " ";
                command += lfile;
                command += "\n";
                out.write(command.getBytes());
                out.flush();
                if (checkAck(in) != 0) {
                    throw new Exception("internal error");
                }

                // send a content of lfile
                byte[] buf = new byte[1024];
                try (var fis = res.getInputStream()) {
                    while (true) {
                        int len = fis.read(buf, 0, buf.length);
                        if (len <= 0) break;
                        out.write(buf, 0, len);
                        out.flush();
                    }
                }

                // send '\0'
                buf[0] = 0;
                out.write(buf, 0, 1);
                out.flush();
                if (checkAck(in) != 0) {
                    throw new Exception("internal error");
                }
            }

            channel.disconnect();
            return true;
        } catch (Exception e) {
            log.error("Unable to upload runscript for Server " + server.getName(), e);
            return false;
        }
    }

    public ShConnection shConnection() {
        return get()
                .getBean(ShRepo.class)
                .findById(server.getShConnection())
                .orElseThrow(() -> new EntityNotFoundException(ShConnection.class, server.getShConnection()));
    }

    @Override
    public void close() {
        session.disconnect();
    }

    protected abstract boolean startConnection() throws Exception;

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
