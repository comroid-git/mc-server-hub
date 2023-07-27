package org.comroid.mcsd.agent.discord;

import lombok.Data;
import lombok.SneakyThrows;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.events.GenericEvent;
import net.dv8tion.jda.api.hooks.EventListener;
import org.comroid.api.DelegateStream;
import org.comroid.api.Event;
import org.comroid.mcsd.core.entity.DiscordBot;
import org.comroid.mcsd.core.entity.MinecraftProfile;
import org.jetbrains.annotations.NotNull;

import java.io.PrintStream;
import java.util.function.BiConsumer;

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
        return (mc,txt)->channel.sendMessageEmbeds(new EmbedBuilder()
                        .setAuthor(mc.getName(), mc.getNameMcURL(), mc.getHeadURL())
                        .setDescription(txt)
                .build()).queue();
    }
    public PrintStream channelAsStream(long id) {
        final var channel = jda.getTextChannelById(id);
        if (channel == null)
            throw new NullPointerException("channel not found: " + id);
        return new PrintStream(new DelegateStream.Output(txt->channel.sendMessage(txt).queue()));
    }
}
