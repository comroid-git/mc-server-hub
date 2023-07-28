package org.comroid.mcsd.agent.discord;

import club.minnced.discord.webhook.WebhookClientBuilder;
import club.minnced.discord.webhook.external.JDAWebhookClient;
import lombok.Data;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import org.comroid.api.*;
import org.comroid.api.Container;
import org.comroid.api.Event;
import org.comroid.mcsd.agent.ServerProcess;
import org.comroid.mcsd.api.model.Status;
import org.comroid.mcsd.core.entity.MinecraftProfile;
import org.comroid.mcsd.core.repo.DiscordBotRepo;
import org.comroid.mcsd.core.repo.MinecraftProfileRepo;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.comroid.mcsd.core.util.ApplicationContextProvider.bean;

@Data
public class DiscordConnection extends Container.Base {
    public static final Pattern ChatPattern_Vanilla = Pattern.compile("INFO]: <(?<username>[\\S\\w-_]+)> (?<message>.+)\\n"); //todo
    public static final Pattern PlayerEvent_Vanilla = Pattern.compile("INFO]: (?<message>(?<username>[\\S\\w-_]+) (joined|left) the game)\\n"); //todo
    private final BiConsumer<@Nullable MinecraftProfile, @Nullable String> chatTemplate;
    private final BiConsumer<@NotNull EmbedBuilder, @Nullable MinecraftProfile> embedTemplate;
    private final DiscordAdapter adapter;
    private final ServerProcess srv;

    public DiscordConnection(ServerProcess srv) {
        final var server = srv.getServer();

        this.srv = srv;
        this.adapter = bean(DiscordBotRepo.class)
                .findById(Objects.requireNonNull(srv.getServer().getDiscordBot()))
                .map(srv.getRunner()::adapter)
                .orElseThrow();

        final Event.Bus<Object> mainBus = bean(Event.Bus.class, "eventBus");
        final var publicChannel = Optional.ofNullable(server.getPublicChannelId());

        final var webhook = Optional.ofNullable(server.getPublicChannelWebhook())
                .or(() -> publicChannel.flatMap(adapter::createWebhook))
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
        final var consoleStream = consoleChannel.map(id -> adapter.channelAsStream(id, srv.getServer().isLessConsoleSpam()));

        Polyfill.stream(
                // status changes -> discord
                Stream.of(mainBus.flatMapData(Status.class)
                        .filter(e->server.getId().toString().equals(e.getKey()))
                        .mapData(status-> new EmbedBuilder()
                                .setTitle(status.getEmoji()+' '+server+" is " +status.getName())
                                .setColor(status.getColor()))
                        .subscribeData(embed -> embedTemplate.accept(embed,null))),

                // public channel -> minecraft
                publicChannel.map(id -> adapter.flatMap(MessageReceivedEvent.class)
                        .filterData(e -> e.getChannel().getIdLong() == id)
                        .mapData(MessageReceivedEvent::getMessage)
                        .filterData(msg -> !msg.getAuthor().isBot())
                        .mapData(msg -> "tell @a §6<%s>§r %s".formatted(
                                msg.getAuthor().getEffectiveName(),
                                msg.getContentStripped()))
                        .subscribeData(srv.getIn()::println)).stream(),
                // minecraft -> public channel
                Stream.of(srv.filter(e -> DelegateStream.IO.EventKey_Output.equals(e.getKey()))
                                .mapData(str -> Stream.of(ChatPattern_Vanilla, PlayerEvent_Vanilla)
                                        .map(rgx->rgx.matcher(str))
                                        .filter(Matcher::matches)
                                        .findAny()
                                        .orElse(null))
                                .subscribeData(matcher -> {
                                    var username = matcher.group("username");
                                    var message = matcher.group("message");
                                    var profile = bean(MinecraftProfileRepo.class).get(username);
                                    chatTemplate.accept(profile, message);
                                })),

                //todo: moderation channel

                // console channel -> console
                consoleChannel.map(id -> adapter.flatMap(MessageReceivedEvent.class)
                                .filterData(e -> e.getChannel().getIdLong() == id)
                                .mapData(MessageReceivedEvent::getMessage)
                                .filterData(msg -> !msg.getAuthor().isBot())
                                .mapData(Message::getContentRaw)
                                .filterData(cmd -> cmd.startsWith(">"))
                                .peekData(cmd -> consoleStream.ifPresent(out -> out.println(cmd)))
                                .mapData(cmd -> cmd.substring(1))
                                .subscribeData(srv.getIn()::println)).stream(),
                // console -> console channel
                consoleStream.map(target -> srv.getOe().redirect(target, target)).stream()
        ).forEach(this::addChildren);
    }
}
