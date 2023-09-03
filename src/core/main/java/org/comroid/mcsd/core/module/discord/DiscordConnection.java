package org.comroid.mcsd.core.module.discord;

import club.minnced.discord.webhook.WebhookClientBuilder;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.java.Log;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import org.comroid.api.*;
import org.comroid.api.Event;
import org.comroid.mcsd.api.dto.ChatMessage;
import org.comroid.mcsd.api.model.IStatusMessage;
import org.comroid.mcsd.api.model.Status;
import org.comroid.mcsd.core.entity.Server;
import org.comroid.mcsd.core.repo.MinecraftProfileRepo;
import org.comroid.mcsd.core.repo.ServerRepo;
import org.comroid.mcsd.util.McFormatCode;
import org.comroid.mcsd.util.Tellraw;
import org.comroid.util.Markdown;

import java.io.*;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.stream.Stream;

import static org.comroid.mcsd.core.util.ApplicationContextProvider.bean;
import static org.comroid.mcsd.util.McFormatCode.*;
import static org.comroid.mcsd.util.McFormatCode.Dark_Aqua;
import static org.comroid.mcsd.util.Tellraw.Event.Action.*;
import static org.comroid.mcsd.util.Tellraw.Event.Action.show_text;

@Log
@Getter
@Setter
@Deprecated
public class DiscordConnection extends DiscordModule {
    private final DiscordAdapter.MessagePublisher msgTemplate, botTemplate;

    public DiscordConnection(Server server) {
        super(server);

        final Event.Bus<Object> mainBus = bean(Event.Bus.class, "eventBus");
        final var publicChannel = Optional.ofNullable(server.getPublicChannelId());

        final var webhook = Optional.ofNullable(server.getPublicChannelWebhook())
                .or(() -> publicChannel.flatMap(adapter::createWebhook)
                        .map(url -> {
                            bean(ServerRepo.class).save(server.setPublicChannelWebhook(url));
                            return url;
                        }))
                .map(url -> new WebhookClientBuilder(url).buildJDA());
        this.msgTemplate = webhook
                .map(adapter::messageTemplate)
                .or(() -> publicChannel.map(adapter::messageTemplate))
                .orElseThrow();
        this.botTemplate = publicChannel.map(adapter::messageTemplate)
                .orElseThrow();

        final var consoleChannel = Optional.ofNullable(server.getConsoleChannelId());
        final var consoleStream = consoleChannel.map(id -> adapter.channelAsStream(id, server.isFancyConsole()));

        Polyfill.stream(
                // status changes -> discord
                Stream.of(mainBus.flatMap(IStatusMessage.class)
                        .filter(e -> server.getId().toString().equals(e.getKey()))
                        .mapData(message -> {
                            EmbedBuilder builder = new EmbedBuilder();
                            if (message.getMessage() != null)
                                builder.setDescription(message.getMessage());
                            return builder
                                    .setTitle(message.toStatusMessage())
                                    .setColor(message.getStatus().getColor());
                        })
                        .mapData(DiscordMessageSource::new)
                        .subscribeData(botTemplate)),

                //todo: moderation channel

        ).forEach(this::addChildren);
    }
}
