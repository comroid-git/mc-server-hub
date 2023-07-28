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
import org.comroid.mcsd.core.repo.DiscordBotRepo;
import org.comroid.mcsd.core.repo.MinecraftProfileRepo;
import org.jetbrains.annotations.NotNull;

import java.io.PrintStream;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.comroid.mcsd.core.util.ApplicationContextProvider.bean;

@Data
public class DiscordConnection extends Container.Base {
    public static final Pattern ChatPattern_Vanilla = Pattern.compile("INFO]:\\s<(?<username>[\\w-_]+)>\\s(?<message>.+)\\n"); //todo
    private final DiscordAdapter adapter;
    private final ServerProcess srv;

    public DiscordConnection(ServerProcess srv) {
        this.srv = srv;
        var server = srv.getServer();
        this.adapter = bean(DiscordBotRepo.class)
                .findById(Objects.requireNonNull(srv.getServer().getDiscordBot()))
                .map(srv.getRunner()::adapter)
                .orElseThrow();
        final var publicChannelId = Optional.ofNullable(server.getPublicChannelId());
        final var chatTarget = Optional.ofNullable(server.getPublicChannelWebhook())
                .or(() -> publicChannelId.flatMap(adapter::createWebhook))
                .map(url -> new WebhookClientBuilder(url).buildJDA())
                .map(adapter::minecraftChatTemplate)
                .or(() -> publicChannelId.map(adapter::minecraftChatTemplate));
        final var consoleChannel = Optional.ofNullable(server.getConsoleChannelId());
        final var consoleStream = consoleChannel.map(id -> adapter.channelAsStream(id, srv.getServer().isLessConsoleSpam()));
        Polyfill.stream(chatTarget.map(target -> srv.filter(e -> DelegateStream.IO.EventKey_Output.equals(e.getKey()))
                                .mapData(ChatPattern_Vanilla::matcher)
                                .filterData(Matcher::matches)
                                .subscribeData(matcher -> {
                                    if (!matcher.matches())
                                        return;
                                    var username = matcher.group("username");
                                    var message = matcher.group("message");
                                    var profile = bean(MinecraftProfileRepo.class).get(username);
                                    target.accept(profile, message);
                                }))
                        .stream(),
                //todo: moderation channel
                consoleChannel.map(id -> adapter.flatMap(MessageReceivedEvent.class)
                                .filterData(e -> e.getChannel().getIdLong() == id)
                                .mapData(MessageReceivedEvent::getMessage)
                                .filterData(msg -> !msg.getAuthor().isBot())
                                .mapData(Message::getContentRaw)
                                .filterData(cmd -> cmd.startsWith(">"))
                                .peekData(cmd -> consoleStream.ifPresent(out -> out.println(cmd)))
                                .mapData(cmd -> cmd.substring(1))
                                .subscribeData(srv.getIn()::println))
                        .stream(),
                consoleStream.map(target -> srv.getOe().redirect(target, target))
                        .stream()
        ).forEach(this::addChildren);
    }
}
