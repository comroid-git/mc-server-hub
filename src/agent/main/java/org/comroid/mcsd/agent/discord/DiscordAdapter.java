package org.comroid.mcsd.agent.discord;

import lombok.Data;
import lombok.SneakyThrows;
import lombok.Synchronized;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.events.GenericEvent;
import net.dv8tion.jda.api.hooks.EventListener;
import org.comroid.api.DelegateStream;
import org.comroid.api.Event;
import org.comroid.mcsd.core.entity.DiscordBot;
import org.comroid.mcsd.core.entity.MinecraftProfile;
import org.comroid.util.Ratelimit;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.PrintStream;
import java.io.StringWriter;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

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
            private static final int MaxLength = 2000;
            private final AtomicInteger counter = new AtomicInteger(0);
            private CompletableFuture<Message> msg = null;

            @Override
            public void accept(final String txt) {
                if (txt.isBlank())
                    return;
                if (msg == null || counter.get() + txt.length() >= MaxLength) {
                    this.msg = channel.sendMessage(txt).submit();
                    counter.set(txt.length() + 1);
                } else Ratelimit.run(Duration.ofMinutes(2), () -> {
                    msg = msg.thenCompose(msg -> msg.editMessage(msg.getContentRaw() + '\n' + txt).submit());
                    msg.thenAccept(msg -> counter.set(msg.getContentRaw().length()));
                });
            }
        }));
    }
}
