package org.comroid.mcsd.web.model;

import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.JSchException;
import lombok.SneakyThrows;
import lombok.experimental.Delegate;
import lombok.extern.slf4j.Slf4j;
import org.comroid.mcsd.web.entity.Server;
import org.comroid.mcsd.web.util.Utils;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.Nullable;

import java.io.Closeable;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.StringWriter;
import java.util.Arrays;
import java.util.Objects;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.regex.Pattern;

import static org.comroid.mcsd.web.model.ServerConnection.OutputMarker;
import static org.comroid.mcsd.web.model.ServerConnection.br;

@Slf4j
public class AttachedConnection implements Closeable {
    private final @Delegate(excludes = {Closeable.class}) ServerConnection $;
    public final CompletableFuture<Void> connected = new CompletableFuture<>();
    public final Server server;
    public final ChannelExec channel;
    public final Input input;
    public final Output output, error;
    private @Nullable Predicate<String> successMatcher;
    private @Nullable CompletableFuture<?> future;
    private @Nullable StringBuilder result;

    public AttachedConnection(Server server) throws JSchException {
        this(server, null);
    }

    public AttachedConnection(Server server, @Nullable @Language("sh") String cmd) throws JSchException {
        this.$ = server.getConnection();
        this.server = server;
        this.channel = (ChannelExec) getSsh().openChannel("exec");

        channel.setCommand(Objects.requireNonNullElseGet(cmd, server::cmdAttach));

        channel.setInputStream(this.input = new Input());
        channel.setOutputStream(this.output = new Output(false));
        channel.setExtOutputStream(this.error = new Output(true));

        channel.connect();
        connected.complete(null);
        channel.start();
    }

    @SneakyThrows
    @SuppressWarnings("BusyWait")
    public void waitForExitStatus() {
        while (channel.getExitStatus() == -1)
            Thread.sleep(50);
    }

    public synchronized String exec(String cmd, @Nullable @Language("RegExp") String successMatcher) {
        this.successMatcher = successMatcher == null ? null : Pattern.compile("^.*(" + successMatcher + ").*$").asMatchPredicate();
        this.future = new CompletableFuture<>();
        this.result = new StringBuilder();
        future.thenRun(() -> {
            this.successMatcher = null;
            this.future = null;
            this.result = null;
        });
        input(cmd + '\n');
        future.join();
        return result.toString();
    }

    @Override
    public void close() {
        channel.disconnect();
    }

    protected void handleStdOut(String txt) {
        if (result == null) {
            log.debug("[%s] %s".formatted(shConnection().getHost(), txt));
            return;
        }

        assert successMatcher != null;
        assert future != null;

        result.append(txt);
        if (successMatcher.test(txt))
            future.complete(null);
    }

    protected void handleStdErr(String txt) {
        log.error("[%s] %s".formatted(shConnection().getHost(), txt));
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
        private final Consumer<String> handler;
        private boolean active;
        private StringWriter buf = new StringWriter();

        public Output(boolean error) {
            this.handler = (error ? AttachedConnection.this::handleStdErr : AttachedConnection.this::handleStdOut);
            this.active = error;
        }

        @Override
        public void write(int b) {
            buf.write(b);
        }

        @Override
        public void flush() {
            if (!connected.isDone())
                connected.join();
            var str = Utils.removeAnsiEscapeSequences(buf.toString()).replaceAll("\r?\n", br);
            if (!active && str.equals(OutputMarker + br))
                active = true;
            else if (active) {
                handler.accept(str);
                if (Arrays.stream(new String[]{"no screen to be resumed", "command not found", "Invalid operation"})
                        .anyMatch(str::contains)) {
                    AttachedConnection.this.close();
                    return;
                }
            }
            buf = new StringWriter();
        }
    }
}
