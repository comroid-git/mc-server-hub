package org.comroid.mcsd.agent.discord;

import club.minnced.discord.webhook.WebhookClient;
import club.minnced.discord.webhook.send.WebhookEmbedBuilder;
import club.minnced.discord.webhook.send.WebhookMessageBuilder;
import lombok.Data;
import lombok.SneakyThrows;
import lombok.extern.java.Log;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.GenericEvent;
import net.dv8tion.jda.api.hooks.EventListener;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.requests.RestAction;
import net.dv8tion.jda.api.requests.restaction.MessageCreateAction;
import net.dv8tion.jda.api.utils.Compression;
import net.dv8tion.jda.api.utils.FileUpload;
import net.dv8tion.jda.api.utils.MarkdownSanitizer;
import net.dv8tion.jda.api.utils.MarkdownUtil;
import net.dv8tion.jda.internal.requests.restaction.MessageEditActionImpl;
import org.comroid.api.DelegateStream;
import org.comroid.api.Event;
import org.comroid.api.Polyfill;
import org.comroid.api.ThrowingFunction;
import org.comroid.mcsd.core.entity.DiscordBot;
import org.comroid.mcsd.core.entity.MinecraftProfile;
import org.comroid.mcsd.core.entity.Server;
import org.comroid.util.Ratelimit;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static net.dv8tion.jda.api.entities.Message.MAX_CONTENT_LENGTH;
import static org.comroid.api.Polyfill.stream;

@Log
@Data
public class DiscordAdapter extends Event.Bus<GenericEvent> implements EventListener {
    public static final int MaxBulkDelete = 100;
    private final JDA jda;

    @SneakyThrows
    public DiscordAdapter(DiscordBot bot) {
        this.jda = JDABuilder.createDefault(bot.getToken(), GatewayIntent.getIntents(GatewayIntent.ALL_INTENTS))
                .setActivity(Activity.playing("Minecraft"))
                .setCompression(Compression.ZLIB)
                .addEventListeners(this)
                .build()
                .awaitReady();
    }

    @Override
    public void onEvent(@NotNull GenericEvent event) {
        publish(null, event);
    }

    public Optional<String> createWebhook(long channelId) {
        return Optional.ofNullable(jda.getTextChannelById(channelId))
                .map(chl -> chl.createWebhook("MCSD Chat Relay")
                        .submit()
                        .thenApply(Webhook::getUrl)
                        .join());
    }

    public BiConsumer<MinecraftProfile, String> minecraftChatTemplate(final WebhookClient webhook) {
        return (mc, txt) -> webhook.send(whMessage(mc)
                .setContent(txt)
                .build());
    }
    public BiConsumer<MinecraftProfile, String> minecraftChatTemplate(long channelId) {
        final var channel = jda.getTextChannelById(channelId);
        if (channel == null)
            throw new NullPointerException("channel not found: " + channelId);
        return (mc, txt) -> channel.sendMessageEmbeds(embed(mc)
                .setDescription(txt)
                .build()).queue();
    }

    public BiConsumer<@NotNull EmbedBuilder, @Nullable MinecraftProfile> embedTemplate(final WebhookClient webhook) {
        return (embed, mc) -> {
            final var content = WebhookEmbedBuilder.fromJDA(embed(embed, mc).build()).build();
            jda.retrieveWebhookById(webhook.getId())
                    .map(wh -> wh.getChannel().asTextChannel())
                    .queue(channel -> channel.getHistory().retrievePast(1)
                            .submit()
                            .thenApply(ls -> ls.stream()
                                    .limit(1)
                                    .filter(msg -> msg.getAuthor().getIdLong() == webhook.getId())
                                    .filter(msg -> msg.getEmbeds().size() == 1)
                                    .findFirst())
                            .thenCompose(related ->
                                    related.map(ThrowingFunction.fallback(msg -> webhook.edit(msg.getIdLong(), content), () -> null))
                                            .orElseGet(() -> CompletableFuture.failedFuture(new RuntimeException("Could not find related message to edit"))))
                            .whenComplete((x, t) -> {
                                if (t == null)
                                    return;
                                webhook.send(whMessage(mc)
                                                .addEmbeds(content)
                                                .build())
                                        .join();
                            })
                            .join());
        };
    }

    public BiConsumer<@NotNull EmbedBuilder, @Nullable MinecraftProfile> embedTemplate(long channelId) {
        final var channel = jda.getTextChannelById(channelId);
        if (channel == null)
            throw new NullPointerException("channel not found: " + channelId);
        return (embed, mc) -> {
            final var content = embed(embed, mc).build();
            channel.getIterableHistory().stream()
                    .limit(1)
                    .filter(msg -> msg.getAuthor().equals(jda.getSelfUser()))
                    .filter(msg -> msg.getEmbeds().size() == 1)
                    .findFirst()
                    .<RestAction<Message>>map(msg -> msg.editMessageEmbeds(content))
                    .orElseGet(() -> channel.sendMessageEmbeds(content))
                    .queue();
        };
    }

    private WebhookMessageBuilder whMessage(@Nullable MinecraftProfile mc) {
        return new WebhookMessageBuilder()
                .setAvatarUrl(mc != null ? mc.getHeadURL() : jda.getSelfUser().getEffectiveAvatarUrl())
                .setUsername(mc != null ? mc.getName() : jda.getSelfUser().getEffectiveName());
    }

    private EmbedBuilder embed(@Nullable MinecraftProfile mc) {
        return embed(new EmbedBuilder(), mc);
    }

    private EmbedBuilder embed(@NotNull EmbedBuilder builder, @Nullable MinecraftProfile mc) {
        if (mc != null)
            builder = builder.setAuthor(mc.getName(), mc.getNameMcURL(), mc.getHeadURL());
        return builder;
    }

    public PrintStream channelAsStream(final long id, final Server.ConsoleMode mode) {
        final var scroll = mode != Server.ConsoleMode.Append;
        final var channel = jda.getTextChannelById(id);
        if (channel == null)
            throw new NullPointerException("channel not found: " + id);
        return new PrintStream(new DelegateStream.Output(new Consumer<>() {
            public static final int MaxLength = MAX_CONTENT_LENGTH - 6;

            private final AtomicReference<CompletableFuture<Message>> msg;

            {
                // getOrCreate msg
                final var msg = Optional.of(0)
                        .filter($ -> mode == Server.ConsoleMode.ScrollClean)
                        .flatMap($ -> Polyfill.stream(channel.getIterableHistory())
                                .filter(m -> jda.getSelfUser().equals(m.getAuthor()))
                                .findFirst())
                        .map(CompletableFuture::completedFuture)
                        .orElseGet(() -> newMsg(null).submit());
                this.msg = new AtomicReference<>(msg);

                // cleanup channel
                if (mode == Server.ConsoleMode.ScrollClean)
                    msg.thenApply(ISnowflake::getIdLong).thenComposeAsync(it -> Polyfill
                            .batches(MaxBulkDelete, channel.getIterableHistory()
                                    .stream()
                                    .peek(x->log.info("filter(id): "+x))
                                    .filter(m -> m.getIdLong() != it)
                                    .peek(x->log.info("map(id): "+x))
                                    .map(ISnowflake::getId))
                            .peek(ids -> log.fine(Polyfill.batches(8, ids.stream())
                                    .map(ls -> String.join(", ", ls))
                                    .collect(Collectors.joining("\n\t\t", "Deleting message batch:\n\t\t", ""))))
                            .peek(x->log.info("bulkDelete(): "+x))
                            .map(channel::deleteMessagesByIds)
                            .peek(x->log.info("submit(): "+x))
                            .map(RestAction::submit)
                            .peek(x->log.info("collect(): "+x))
                            .collect(Collectors.collectingAndThen(
                                    Collectors.<CompletableFuture<?>>toList(),
                                    all -> CompletableFuture.allOf(all.toArray(CompletableFuture[]::new)))
                            )).exceptionally(Polyfill.exceptionLogger());
            }

            @Override
            public void accept(final String txt) {
                if (txt.isBlank()) return; //todo: this shouldn't be necessary
                log.finer("accept('" + txt + "')");
                Ratelimit.run(txt, Duration.ofSeconds(scroll ? 3 : 1), msg, (msg, queue) -> {
                    var raw = MarkdownSanitizer.sanitize(msg.getContentRaw());
                    var add = "";
                    log.fine("length of raw = " + raw.length());
                    boolean hasSpace = false;
                    while (!queue.isEmpty() && (scroll || (hasSpace = (raw + add + queue.peek()).length() < MaxLength))) {
                        var poll = queue.poll();
                        add += poll;
                    }
                    RestAction<Message> chain;
                    if (mode == Server.ConsoleMode.ScrollClean ||
                            channel.getLatestMessageIdLong() == msg.getIdLong() && msg.isPinned()) {
                        var content = raw + add;
                        chain = msg.editMessage(wrapContent(content));
                        if (!scroll && !hasSpace)
                            chain = chain.flatMap($ -> newMsg(queue.poll()));
                    } else chain = newMsg(add);
                    return chain.submit();
                });
            }

            private MessageCreateAction newMsg(@Nullable String content) {
                log.finer("new message with start content:\n" + content);
                return channel.sendMessage(wrapContent(content));
            }

            private String wrapContent(@Nullable String content) {
                return MarkdownUtil.codeblock(Optional.ofNullable(content)
                        .map(this::scroll)
                        .orElseGet(this::header));
            }

            private String scroll(String content) {
                if (!scroll) return content;
                var lines = content.split("\r?\n");
                var rev = new ArrayList<String>();
                var index = lines.length - 1;
                int size = 0;
                while (index >= 0 && (size = rev.stream().mapToInt(s -> s.length() + 1).sum() + lines[index].length()) < MaxLength)
                    rev.add(lines[index--]);
                log.fine("size after scroll = " + (index < 0 ? size : (size - lines[index].length())));
                Collections.reverse(rev);
                return String.join("\n", rev)+'\n';
            }

            private String header() {
                return "New output session started at " + Date.from(Instant.now()) + '\n';
            }
        }));
    }

    public void uploadFile(long id, InputStream source, String name, String message) {
        Objects.requireNonNull(jda.getTextChannelById(id))
                .sendFiles(FileUpload.fromData(source, name))
                .setContent(message)
                .queue();
    }

    @Override
    public void closeSelf() {
        jda.shutdown();
    }
}
