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
import org.comroid.mcsd.api.dto.ChatMessage;
import org.comroid.mcsd.api.model.Status;
import org.comroid.mcsd.core.entity.MinecraftProfile;
import org.comroid.mcsd.core.repo.MinecraftProfileRepo;
import org.comroid.mcsd.core.repo.ServerRepo;
import org.comroid.mcsd.util.Tellraw;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.regex.Matcher;
import java.util.stream.Stream;

import static org.comroid.mcsd.core.util.ApplicationContextProvider.bean;
import static org.comroid.mcsd.util.McFormatCode.*;
import static org.comroid.mcsd.util.McFormatCode.Dark_Aqua;

@Log
@Data
public class DiscordConnection extends Container.Base {
    private final BiConsumer<@Nullable MinecraftProfile, @Nullable String> chatTemplate;
    private final BiConsumer<@NotNull EmbedBuilder, @Nullable MinecraftProfile> embedTemplate;
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
        this.chatTemplate = webhook
                .map(adapter::minecraftChatTemplate)
                .or(() -> publicChannel.map(adapter::minecraftChatTemplate))
                .orElseThrow();
        this.embedTemplate = webhook
                .map(adapter::embedTemplate)
                .or(() -> publicChannel.map(adapter::embedTemplate))
                .orElseThrow();

        final var consoleChannel = Optional.ofNullable(server.getConsoleChannelId());
        final var consoleStream = consoleChannel.map(id -> adapter.channelAsStream(id, srv.getServer().getConsoleMode()));

        Polyfill.stream(
                // status changes -> discord
                Stream.of(mainBus.flatMapData(Status.class)
                        .filter(e -> server.getId().toString().equals(e.getKey()))
                        .mapData(status -> new EmbedBuilder()
                                .setTitle(status.getEmoji() + '\t' + "Server is " + status.getName())
                                .setColor(status.getColor()))
                        .subscribeData(embed -> embedTemplate.accept(embed, null))),

                // public channel -> minecraft
                publicChannel.map(id -> adapter.flatMap(MessageReceivedEvent.class)
                        .filterData(e -> e.getChannel().getIdLong() == id)
                        .mapData(MessageReceivedEvent::getMessage)
                        .filterData(msg -> !msg.getAuthor().isBot())
                        .mapData(msg -> Tellraw.Command.builder()
                                .selector(Tellraw.Selector.Base.ALL_PLAYERS)
                                .component(Gray.text("<").build())
                                .component(Dark_Aqua.text(msg.getAuthor().getEffectiveName())
                                        .hoverEvent(Tellraw.Event.Action.show_text.value("Open in Discord"))
                                        .clickEvent(Tellraw.Event.Action.open_url.value(msg.getJumpUrl()))
                                        .build())
                                .component(Gray.text(">").build())
                                .component(Reset.text(" "+msg.getContentStripped()).build())
                                .build()
                                .toString())
                        .peekData(log::fine)
                        .subscribeData(srv.getIn()::println)).stream(),
                // minecraft -> public channel
                Stream.of(srv.filter(e -> DelegateStream.IO.EventKey_Output.equals(e.getKey()))
                        .mapData(str -> Stream.of(ServerProcess.ChatPattern_Vanilla, ServerProcess.PlayerEvent_Vanilla)
                                .map(rgx -> rgx.matcher(str))
                                .filter(Matcher::matches)
                                .findAny()
                                .orElse(null))
                        .subscribeData(matcher -> {
                            var username = matcher.group("username");
                            var message = matcher.group("message");
                            if (matcher.groupCount() == 2)
                                bean(Event.Bus.class,"eventBus").publish("chat", new ChatMessage(username, message));
                            else message = MarkdownUtil.quote(message);
                            var profile = bean(MinecraftProfileRepo.class).get(username);
                            chatTemplate.accept(profile, message);
                        })),

                //todo: moderation channel

                // console channel -> console
                consoleChannel.map(id -> adapter
                        .flatMap(MessageReceivedEvent.class)
                        .filterData(e -> e.getChannel().getIdLong() == id)
                        .mapData(MessageReceivedEvent::getMessage)
                        .filterData(msg -> !msg.getAuthor().isBot())
                        .mapData(msg -> {
                            var raw = msg.getContentRaw();
                            if (!msg.getAuthor().equals(adapter.getJda().getSelfUser()))
                                msg.delete().queue();
                            return raw;
                        })
                        .filterData(cmd -> cmd.startsWith(">"))
                        .peekData(cmd -> consoleStream.ifPresent(out -> out.println(cmd)))
                        .mapData(cmd -> cmd.substring(1))
                        .subscribeData(srv.getIn()::println)).stream(),
                // console -> console channel
                consoleStream.map(target -> srv.getOe()
                        .rewireOE(oe->oe.filter($->server.getLastStatus().getAsInt()>Status.Starting.getAsInt()))
                        .redirect(target, target)).stream()
        ).forEach(this::addChildren);
    }
}
