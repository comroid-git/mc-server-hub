package org.comroid.mcsd.agent.discord;

import lombok.Data;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import org.comroid.api.*;
import org.comroid.mcsd.agent.ServerProcess;
import org.comroid.mcsd.core.repo.DiscordBotRepo;
import org.comroid.mcsd.core.repo.MinecraftProfileRepo;
import org.jetbrains.annotations.Nullable;

import java.io.PrintStream;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.regex.Pattern;

import static org.comroid.mcsd.core.util.ApplicationContextProvider.bean;

@Data
public class DiscordConnection extends Container.Base {
    public static final Pattern CHAT_PATTERN = Pattern.compile(""); //todo
    private final ServerProcess srv;
    private final DiscordAdapter adapter;

    public DiscordConnection(ServerProcess srv) {
        this.srv = srv;
        this.adapter = bean(DiscordBotRepo.class)
                .findById(Objects.requireNonNull(srv.getServer().getDiscordBot()))
                .map(srv.getRunner()::adapter)
                .orElseThrow();
        var server = srv.getServer();

        var consoleChannel = Optional.ofNullable(server.getConsoleChannelId());
        Optional<PrintStream> consoleStream = consoleChannel.map(id -> adapter.channelAsStream(id, srv.getServer().isLessConsoleSpam()));
        Polyfill.stream(
                Optional.ofNullable(server.getPublicChannelId())
                        .map(adapter::minecraftChatTemplate)
                        .map(target -> srv.listenForOutput("\\[CHAT]").listen().subscribeData(txt -> {
                            if (txt == null)
                                return;
                            var matcher = CHAT_PATTERN.matcher(txt);
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

    @Override
    public void closeSelf() {
    }
}
