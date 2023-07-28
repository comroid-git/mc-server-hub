package org.comroid.mcsd.agent.discord;

import lombok.Data;
import lombok.SneakyThrows;
import lombok.Value;
import lombok.extern.java.Log;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.GenericEvent;
import net.dv8tion.jda.api.hooks.EventListener;
import net.dv8tion.jda.api.requests.RestAction;
import net.dv8tion.jda.api.requests.restaction.MessageCreateAction;
import net.dv8tion.jda.api.requests.restaction.MessageEditAction;
import net.dv8tion.jda.api.utils.MarkdownSanitizer;
import net.dv8tion.jda.api.utils.MarkdownUtil;
import org.comroid.api.DelegateStream;
import org.comroid.api.Event;
import org.comroid.api.Polyfill;
import org.comroid.mcsd.core.entity.DiscordBot;
import org.comroid.mcsd.core.entity.MinecraftProfile;
import org.comroid.util.Ratelimit;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.OutputStream;
import java.io.PrintStream;
import java.io.StringWriter;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import static net.dv8tion.jda.api.entities.Message.MAX_CONTENT_LENGTH;

@Log
@Data
public class DiscordAdapter extends Event.Bus<GenericEvent> implements EventListener {
    private final JDA jda;

    @SneakyThrows
    public DiscordAdapter(DiscordBot bot) {
        this.jda = JDABuilder.createDefault(bot.getToken())
                .setActivity(Activity.playing("Minecraft"))
                .addEventListeners(this)
                .build()
                .awaitReady();
    }

    @Override
    public void onEvent(@NotNull GenericEvent event) {
        publish(null, event);
    }

    @Override
    public void closeSelf() {
        jda.shutdown();
    }

    public BiConsumer<MinecraftProfile, String> minecraftChatTemplate(long id) {
        final var channel = jda.getTextChannelById(id);
        if (channel == null)
            throw new NullPointerException("channel not found: " + id);
        return (mc, txt) -> channel.sendMessageEmbeds(new EmbedBuilder()
                .setAuthor(mc.getName(), mc.getNameMcURL(), mc.getHeadURL())
                .setDescription(txt)
                .build()).queue();
    }

    public PrintStream channelAsStream(long id) {
        final var channel = jda.getTextChannelById(id);
        if (channel == null)
            throw new NullPointerException("channel not found: " + id);
        return new PrintStream(new DelegateStream.Output(new Consumer<>() {
            public static final int MaxLength = MAX_CONTENT_LENGTH - 6;

            private final AtomicReference<CompletableFuture<Message>> msg = new AtomicReference<>(newMsg(null).submit());

            @Override
            public void accept(final String txt) {
                if (txt.isBlank()) return; //todo: this shouldn't be necessary
                log.finer("accept('" + txt + "')");
                Ratelimit.run(txt, Duration.ofSeconds(2), msg, (msg, queue) -> {
                    var raw = MarkdownSanitizer.sanitize(msg.getContentRaw());
                    log.fine("length of raw = " + raw.length());
                    boolean hasSpace = false;
                    while (!queue.isEmpty() && (hasSpace = (raw.length() + queue.peek().length() < MaxLength))) {
                        var poll = queue.poll();
                        raw += poll;
                    }
                    log.finer("editing to:\n" + raw);
                    RestAction<Message> chain = msg.editMessage(wrapContent(raw));
                    if (!hasSpace) chain = chain.flatMap($ -> newMsg(queue.poll()));
                    return chain.submit();
                });
            }

            private MessageCreateAction newMsg(@Nullable String content) {
                log.finer("new message with start content:\n" + content);
                return channel.sendMessage(wrapContent(content));
            }

            private String wrapContent(@Nullable String content) {
                return MarkdownUtil.codeblock(Objects.requireNonNullElseGet(content, this::header));
            }

            private String header() {
                return "New output session started at " + Date.from(Instant.now()) + '\n';
            }
        }));
    }
}
