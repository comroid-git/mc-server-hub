package org.comroid.mcsd.agent.discord;

import club.minnced.discord.webhook.WebhookClient;
import club.minnced.discord.webhook.WebhookClientBuilder;
import club.minnced.discord.webhook.external.JDAWebhookClient;
import lombok.Data;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.Webhook;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import org.comroid.api.*;
import org.comroid.mcsd.agent.ServerProcess;
import org.comroid.mcsd.core.entity.MinecraftProfile;
import org.comroid.mcsd.core.repo.DiscordBotRepo;
import org.comroid.mcsd.core.repo.MinecraftProfileRepo;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.NotNull;

import java.io.PrintStream;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.comroid.mcsd.core.util.ApplicationContextProvider.bean;

@Data
public class DiscordConnection extends Container.Base {
    public static final Pattern ChatPattern_Vanilla = Pattern.compile("INFO]: <(?<username>[\\S\\w-_]+)> (?<message>.+)\\n"); //todo
    public static final Pattern PlayerEvent_Vanilla = Pattern.compile("INFO]: (?<message>(?<username>[\\S\\w-_]+) (joined|left) the game)\\n"); //todo
    private final BiConsumer<MinecraftProfile, String> chatRelay;
    private final DiscordAdapter adapter;
    private final ServerProcess srv;

    public DiscordConnection(ServerProcess srv) {
        this.srv = srv;
        var server = srv.getServer();
        this.adapter = bean(DiscordBotRepo.class)
                .findById(Objects.requireNonNull(srv.getServer().getDiscordBot()))
                .map(srv.getRunner()::adapter)
                .orElseThrow();
        final var publicChannel = Optional.ofNullable(server.getPublicChannelId());
        this.chatRelay = Optional.ofNullable(server.getPublicChannelWebhook())
                .or(() -> publicChannel.flatMap(adapter::createWebhook))
                .map(url -> new WebhookClientBuilder(url).buildJDA())
                .map(adapter::minecraftChatTemplate)
                .or(() -> publicChannel.map(adapter::minecraftChatTemplate))
                .orElseThrow();

        final var consoleChannel = Optional.ofNullable(server.getConsoleChannelId());
        final var consoleStream = consoleChannel.map(id -> adapter.channelAsStream(id, srv.getServer().isLessConsoleSpam()));

        Polyfill.stream(
                // public channel
                publicChannel.map(id -> adapter.flatMap(MessageReceivedEvent.class)
                        .filterData(e -> e.getChannel().getIdLong() == id)
                        .mapData(MessageReceivedEvent::getMessage)
                        .filterData(msg -> !msg.getAuthor().isBot())
                        .mapData(msg -> "tell @a §6<%s>§r %s".formatted(
                                msg.getAuthor().getEffectiveName(),
                                msg.getContentStripped()))
                        .subscribeData(srv.getIn()::println)).stream(),
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
                                    chatRelay.accept(profile, message);
                                })),

                //todo: moderation channel

                // console channel
                consoleChannel.map(id -> adapter.flatMap(MessageReceivedEvent.class)
                                .filterData(e -> e.getChannel().getIdLong() == id)
                                .mapData(MessageReceivedEvent::getMessage)
                                .filterData(msg -> !msg.getAuthor().isBot())
                                .mapData(Message::getContentRaw)
                                .filterData(cmd -> cmd.startsWith(">"))
                                .peekData(cmd -> consoleStream.ifPresent(out -> out.println(cmd)))
                                .mapData(cmd -> cmd.substring(1))
                                .subscribeData(srv.getIn()::println)).stream(),
                consoleStream.map(target -> srv.getOe().redirect(target, target)).stream()
        ).forEach(this::addChildren);
    }
}
