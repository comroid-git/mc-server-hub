package org.comroid.mcsd.agent.discord;

import club.minnced.discord.webhook.WebhookClient;
import club.minnced.discord.webhook.send.WebhookMessageBuilder;
import lombok.Data;
import lombok.SneakyThrows;
import lombok.extern.java.Log;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.Webhook;
import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.events.GenericEvent;
import net.dv8tion.jda.api.hooks.EventListener;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.requests.RestAction;
import net.dv8tion.jda.api.requests.restaction.MessageCreateAction;
import net.dv8tion.jda.api.utils.MarkdownSanitizer;
import net.dv8tion.jda.api.utils.MarkdownUtil;
import org.comroid.api.DelegateStream;
import org.comroid.api.Event;
import org.comroid.mcsd.core.entity.DiscordBot;
import org.comroid.mcsd.core.entity.MinecraftProfile;
import org.comroid.util.Ratelimit;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.PrintStream;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
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
        this.jda = JDABuilder.createDefault(bot.getToken(), GatewayIntent.getIntents(GatewayIntent.ALL_INTENTS))
                .setActivity(Activity.playing("Minecraft"))
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
        return (mc, txt) -> webhook.send(new WebhookMessageBuilder()
                .setAvatarUrl(mc.getHeadURL())
                .setUsername(mc.getName())
                .build());
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

    public PrintStream channelAsStream(final long id, final boolean scroll) {
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
                    if (channel.getLatestMessageIdLong() == msg.getIdLong() && !msg.isPinned()) {
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
                        .map(x -> scroll ? scroll(x) : x)
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

    @Override
    public void closeSelf() {
        jda.shutdown();
    }
}
