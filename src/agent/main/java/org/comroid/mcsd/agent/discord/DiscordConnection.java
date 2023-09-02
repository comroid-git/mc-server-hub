package org.comroid.mcsd.agent.discord;

import club.minnced.discord.webhook.WebhookClientBuilder;
import lombok.Data;
import lombok.extern.java.Log;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.utils.MarkdownUtil;
import org.comroid.api.*;
import org.comroid.api.Container;
import org.comroid.api.Event;
import org.comroid.mcsd.agent.ServerProcess;
import org.comroid.mcsd.agent.util.DiscordMessageSource;
import org.comroid.mcsd.api.dto.ChatMessage;
import org.comroid.mcsd.api.model.IStatusMessage;
import org.comroid.mcsd.api.model.Status;
import org.comroid.mcsd.core.entity.MinecraftProfile;
import org.comroid.mcsd.core.entity.Server;
import org.comroid.mcsd.core.repo.MinecraftProfileRepo;
import org.comroid.mcsd.core.repo.ServerRepo;
import org.comroid.mcsd.util.McFormatCode;
import org.comroid.mcsd.util.Tellraw;
import org.comroid.util.Markdown;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.regex.Matcher;
import java.util.stream.Stream;

import static org.comroid.mcsd.core.util.ApplicationContextProvider.bean;
import static org.comroid.mcsd.util.McFormatCode.*;
import static org.comroid.mcsd.util.McFormatCode.Dark_Aqua;
import static org.comroid.mcsd.util.Tellraw.Event.Action.*;
import static org.comroid.mcsd.util.Tellraw.Event.Action.show_text;

@Log
@Data
public class DiscordConnection extends Container.Base {
    private final DiscordAdapter.MessagePublisher msgTemplate, botTemplate;
    private final DiscordAdapter adapter;
    private final ServerProcess srv;

    public DiscordConnection(ServerProcess srv) {
        final var server = srv.getServer();

        this.srv = srv;
        this.adapter = Optional.ofNullable(srv.getServer().getDiscordBot())
                .map(srv.getRunner()::adapter)
                .orElseThrow();

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
        final var consoleStream = consoleChannel.map(id -> adapter.channelAsStream(id, srv.getServer().isFancyConsole()));

        Polyfill.stream(
                // status changes -> discord
                Stream.of(mainBus.flatMapData(IStatusMessage.class)
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

                // public channel -> minecraft
                publicChannel.map(id -> adapter.flatMap(MessageReceivedEvent.class)
                        .filterData(e -> e.getChannel().getIdLong() == id)
                        .mapData(MessageReceivedEvent::getMessage)
                        .filterData(msg -> !msg.getAuthor().isBot())
                        .mapData(msg -> Tellraw.Command.builder()
                                .selector(Tellraw.Selector.Base.ALL_PLAYERS)
                                .component(Gray.text("<").build())
                                .component(Dark_Aqua.text(msg.getAuthor().getEffectiveName())
                                        .hoverEvent(show_text.value("Open in Discord"))
                                        .clickEvent(open_url.value(msg.getJumpUrl()))
                                        .build())
                                .component(Gray.text(">").build())
                                // todo convert markdown to tellraw data
                                .component(Reset.text(" " + msg.getContentStripped()).build())
                                .build()
                                .toString())
                        .peekData(log::fine)
                        .subscribeData(srv.getIn()::println)).stream(),
                // minecraft -> public channel
                Stream.of(srv.filter(e -> DelegateStream.IO.EventKey_Output.equals(e.getKey()))
                        .mapData(str -> Stream.of(ServerProcess.ChatPattern_Vanilla,
                                        ServerProcess.PlayerEventPattern_Vanilla,
                                        ServerProcess.BroadcastPattern_Vanilla,
                                        ServerProcess.CrashPattern_Vanilla)
                                .map(rgx -> rgx.matcher(str))
                                .filter(Matcher::matches)
                                .findAny()
                                .orElse(null))
                        .subscribeData(matcher -> {
                            if (matcher.groupCount() == 1) try {
                                // crash log
                                var name = matcher.group(1);
                                try (var input = new FileInputStream(server.path("crash-reports", name).toFile())) {
                                    consoleChannel.ifPresent(id -> adapter.uploadFile(id, input, name, "@here"));
                                    return;
                                }
                            } catch (IOException e) {
                                throw new RuntimeException(e);
                            }
                            var username = matcher.group("username");
                            var message = matcher.group("message");
                            var output = new DiscordMessageSource();
                            var profile = bean(MinecraftProfileRepo.class).get(username);
                            if (matcher.pattern().toString().contains("prefix")) {
                                // chat message
                                message = TextDecoration.convert(message, McFormatCode.class, Markdown.class);
                                bean(Event.Bus.class, "eventBus").publish("chat", new ChatMessage(username, message));
                                output.setData(TextDecoration.convert(message, McFormatCode.class, Markdown.class))
                                        .setPlayer(profile)
                                        .setAppend(true);
                                msgTemplate.send(output).join();
                                return;
                            } else {
                                // player event or
                                var c = message.charAt(0);
                                message = message.substring(1);
                                message = Character.toUpperCase(c) + message;
                                // broadcast command executed
                                if (matcher.pattern().toString().contains("command")
                                        && matcher.group("command").equals("broadcast")) {
                                    output.setData(new EmbedBuilder()
                                            .setAuthor("Broadcast", server.getViewURL(), server.getThumbnailURL())
                                            .setColor(Gold.getColor())
                                            .setDescription(message));
                                } else output.setData(Markdown.Quote.apply(message));
                            }
                            output.setPlayer(profile)
                                    .setAppend(true)
                                    .send(msgTemplate)
                                    .join();
                        })),

                //todo: moderation channel

                // console channel -> console
                consoleStream.map(out -> {
                    onClose().thenRunAsync(() -> out.println("Agent shutting down"));
                    return consoleChannel.map(id -> adapter
                            .flatMap(MessageReceivedEvent.class)
                            .filterData(e -> e.getChannel().getIdLong() == id)
                            .mapData(MessageReceivedEvent::getMessage)
                            .filterData(msg -> !msg.getAuthor().isBot())
                            .mapData(msg -> {
                                var raw = msg.getContentRaw();
                                if (server.isFancyConsole() && !msg.getAuthor().equals(adapter.getJda().getSelfUser()))
                                    msg.delete().queue();
                                //noinspection RedundantCast //ide error
                                return (String) raw;
                            })
                            .filterData(cmd -> server.getConsoleChannelPrefix() == null || cmd.startsWith(server.getConsoleChannelPrefix()))
                            .mapData(cmd -> server.getConsoleChannelPrefix() == null ? cmd : cmd.substring(server.getConsoleChannelPrefix().length()))
                            .peekData(out::println)
                            .subscribeData(srv.getIn()::println));
                }).stream(),
                // console -> console channel
                consoleStream.map(target -> srv.getOe()
                        .rewireOE(oe -> oe.filter($ -> server.getLastStatus().getAsInt() > Status.starting.getAsInt()))
                        .redirect(target, target)).stream()
        ).forEach(this::addChildren);
    }
}
