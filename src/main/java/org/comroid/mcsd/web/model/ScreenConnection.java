package org.comroid.mcsd.web.model;

import com.jcraft.jsch.ChannelShell;
import com.jcraft.jsch.JSchException;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.comroid.api.DelegateStream;
import org.comroid.api.Event;
import org.comroid.mcsd.web.entity.Server;
import org.comroid.mcsd.web.util.Utils;
import org.intellij.lang.annotations.Language;

import java.io.*;
import java.util.Arrays;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.regex.Pattern;

import static org.comroid.mcsd.web.model.ServerConnection.*;

@Slf4j
public final class ScreenConnection implements Closeable {
    private final ServerConnection connection;
    public final Server server;
    public final ChannelShell channel;
    public final DelegateStream.IOE ioe;
    public final Input input;
    public final Event.Bus<String> output = new Event.Bus<>();
    public final Event.Bus<String> error = new Event.Bus<>();

    public ScreenConnection(ServerConnection con) throws JSchException {
        this.connection = con;
        this.server = con.getServer();
        this.channel = (ChannelShell) connection.getSession().openChannel("shell");

        this.ioe = new DelegateStream.IOE();
        channel.setInputStream(ioe.input());
        channel.setOutputStream(ioe.output());
        channel.setExtOutputStream(ioe.error());
        ioe.redirect.add(DelegateStream.IOE.slf4j(log));
        ioe.redirect.add(new DelegateStream.IOE(this.input = new Input(), new Output(false), new Output(true), output, error));
        //ioe.redirect.add(DelegateStream.IOE.SYSTEM);

        channel.connect();
        channel.start();

        input(con.getServer().cmdAttach());
    }

    @Override
    public void close() {
        log.warn("ScreenConnection was closed");
        channel.disconnect();
        ioe.close();
    }

    public void sendCmd(String cmd, final @Language("RegExp") String endPattern) {
        if (connection.getRcon().isConnected())
            connection.sendCmdRCon(cmd);
        else if (endPattern != null) sendCmdScreen(cmd, endPattern).join();
        else throw new RuntimeException("Cannot send command " + cmd + " because both RCon and Screen are offline");
    }

    public synchronized CompletableFuture<String> sendCmdScreen(String cmd, final @Language("RegExp") String endPattern) {
        final var pattern = Pattern.compile(endPattern);
        final var sb = new StringBuilder();
        var appender = output.listen(x -> x.ifPresent(e -> sb.append(e.getData())));
        var future = output.next(e -> pattern.matcher(e.getData()).matches()).whenComplete((e,t)->appender.close());
        input(cmd);
        return future.thenApply($->sb.toString());
    }

    public void input(String input) {
        synchronized (this.input.cmds) {
            this.input.cmds.add(input);
            this.input.cmds.notify();
        }
    }

    private static final class Input extends InputStream {
        private final Queue<String> cmds = new PriorityBlockingQueue<>();
        private String cmd;
        private int r = 0;
        private boolean endlSent = true;

        @Override
        public int read() {
            if (cmd == null) {
                if (endlSent)
                    endlSent = false;
                else {
                    endlSent = true;
                    return -1;
                }
                synchronized (cmds) {
                    while (cmds.size() == 0) {
                        try {
                            cmds.wait();
                        } catch (InterruptedException e) {
                            log.error("Could not wait for new input", e);
                        }
                    }
                    cmd = cmds.poll();
                }
            }

            int c;
            if (r < cmd.length())
                c = cmd.charAt(r++);
            else {
                cmd = null;
                c = '\n';
                r = 0;
            }
            return c;
        }
    }

    private final class Output extends OutputStream {
        private final boolean error;
        private StringWriter buf = new StringWriter();
        private boolean active;

        public Output(boolean error) {
            this.error = this.active = error;
        }

        @Override
        public void write(int b) {
            buf.write(b);
        }

        @Override
        public void flush() {
            var str = Utils.removeAnsiEscapeSequences(buf.toString());
            if (!active && str.startsWith(OutputMarker))
                active = true;
            else if (active && str.startsWith(EndMarker))
                active = false;
            else if (active) {
                for (String line : str.split("\r?\n"))
                    (error ? ScreenConnection.this.error : ScreenConnection.this.output).publish(line);
                if (Arrays.stream(new String[]{"no screen to be resumed", "command not found", "Invalid operation"})
                        .anyMatch(str::contains)) {
                    ScreenConnection.this.close();
                    return;
                }
            }
            buf = new StringWriter();
        }
    }
}
