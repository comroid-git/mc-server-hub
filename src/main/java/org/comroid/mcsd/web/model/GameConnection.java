package org.comroid.mcsd.web.model;

import com.jcraft.jsch.ChannelShell;
import com.jcraft.jsch.JSchException;
import lombok.extern.slf4j.Slf4j;
import org.comroid.api.DelegateStream;
import org.comroid.api.Event;
import org.comroid.mcsd.web.entity.Server;
import org.intellij.lang.annotations.Language;
import org.slf4j.event.Level;
import org.springframework.boot.logging.LogLevel;

import java.io.*;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.regex.Pattern;

@Slf4j
public final class GameConnection implements Closeable {
    private final ServerConnection connection;
    public final Server server;
    public final ChannelShell channel;
    public final Event.Bus<String> input = new Event.Bus<>();
    public final Event.Bus<String> output = new Event.Bus<>();
    public final Event.Bus<String> error = new Event.Bus<>();

    public GameConnection(ServerConnection con) throws JSchException {
        this.connection = con;
        this.server = con.getServer();
        this.channel = (ChannelShell) connection.getSession().openChannel("shell");

        channel.setInputStream(new Input(input));
        channel.setOutputStream(new DelegateStream.Output(output));
        channel.setExtOutputStream(new DelegateStream.Output(error));

        input.log(con.log(), Level.DEBUG);
        output.log(con.log(), Level.INFO);
        error.log(con.log(), Level.ERROR);

        input.accept(server.cmdAttach());
        channel.connect();
        channel.start();
    }

    @Override
    public void close() {
        log.warn("ScreenConnection was closed");
        channel.disconnect();
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
        var appender = output.listen(e -> sb.append(e.getData()));
        var future = output.next(e -> pattern.matcher(e.getData()).matches()).whenComplete((e,t)->appender.close());
        input.accept(cmd);
        return future.thenApply($->sb.toString());
    }

    private static final class Input extends InputStream {
        private final Queue<String> cmds = new PriorityBlockingQueue<>();
        private boolean eof = true;
        private String cmd;
        private int r = 0;

        public Input(Event.Bus<String> bus) {
            bus.listen(e -> {
                synchronized (cmds) {
                    cmds.add(e.getData());
                    cmds.notify();
                }
            });
        }

        @Override
        public int read() {
            if (cmd == null) {
                if (eof) {
                    eof = false;
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
                } else {
                    eof = true;
                    return -1;
                }
            }

            int c;
            if (r < cmd.length())
                c = cmd.charAt(r++);
            else {
                cmd = null;
                r = 0;
                return '\n';
            }
            return c;
        }
    }
/*
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
            if (!active && str.contains(OutputMarker))
                active = true;
            else if (active && str.contains(EndMarker))
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
 */
}
