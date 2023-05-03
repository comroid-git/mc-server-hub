package org.comroid.mcsd.web.model;

import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.ChannelSftp;
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
import org.jetbrains.annotations.Nullable;
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

    public static @Nullable OutputStream upload(Server srv, long length, String fileName, String filePath) {
        try (var con = new ServerConnection(srv)) {
            con.start();
            return con.uploadFile(filePath);
        } catch (Exception e) {
            log.error("Error opening upload stream to server %s for file %s".formatted(srv.getName(), fileName), e);
            return null;
        }
    }

    public static @Nullable InputStream download(Server srv, String filePath) {
        try (var con = new ServerConnection(srv)) {
            con.start();
            return con.downloadFile(filePath);
        } catch (Exception e) {
            log.error("Error opening download stream from server %s from file %s".formatted(srv.getName(), filePath), e);
            return null;
        }
    }

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
        try (var source = res.getInputStream();
             var target = uploadFile(rfile)) {
            source.transferTo(target);
            return true;
        } catch (Exception e) {
            log.error("Unable to upload runscript for Server " + server.getName(), e);
            return false;
        }
    }

    public OutputStream uploadFile(final String path) throws Exception {
        return new OutputStream() {
            private final ChannelSftp sftp;
            private final OutputStream delegate;

            {
                this.sftp = (ChannelSftp) session.openChannel("sftp");
                sftp.connect();
                this.delegate = sftp.put(path);
            }

            @Override
            public void write(int b) throws IOException {
                delegate.write(b);
            }

            @Override
            public void close() throws IOException {
                super.close();
                sftp.disconnect();
            }
        };
    }

    public InputStream downloadFile(final String path) throws Exception {
        return new InputStream() {
            private final ChannelSftp sftp;
            private final InputStream delegate;

            {
                this.sftp = (ChannelSftp) session.openChannel("sftp");
                sftp.connect();
                this.delegate = sftp.get(path);
            }

            @Override
            public int read() throws IOException {
                return delegate.read();
            }

            @Override
            public void close() throws IOException {
                sftp.disconnect();
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
