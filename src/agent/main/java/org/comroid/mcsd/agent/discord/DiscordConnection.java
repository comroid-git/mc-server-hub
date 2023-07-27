package org.comroid.mcsd.agent.discord;

import lombok.Data;
import org.comroid.api.Container;
import org.comroid.api.Startable;
import org.comroid.mcsd.agent.AgentRunner;
import org.comroid.mcsd.agent.ServerProcess;
import org.comroid.mcsd.core.repo.DiscordBotRepo;
import org.comroid.mcsd.core.repo.MinecraftProfileRepo;

import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

import static org.comroid.mcsd.core.util.ApplicationContextProvider.bean;

@Data
public class DiscordConnection extends Container.Base implements Startable {
    public static final Pattern CHAT_PATTERN = Pattern.compile(""); //todo
    private final ServerProcess srv;
    private final DiscordAdapter adapter;

    public DiscordConnection(ServerProcess srv) {
        this.srv = srv;
        this.adapter = bean(DiscordBotRepo.class)
                .findById(Objects.requireNonNull(srv.getServer().getDiscordBot()))
                .map(bean(AgentRunner.class)::adapter)
                .orElseThrow();
    }

    @Override
    public void start() {
        var server = srv.getServer();

        Optional.ofNullable(server.getPublicChannelId())
                .map(adapter::minecraftChatTemplate)
                .map(target->srv.listenForOutput("\\[CHAT]").listen().subscribeData(txt->{
                    var matcher = CHAT_PATTERN.matcher(txt);
                    if (!matcher.matches())
                        return;
                    var username = matcher.group("username");
                    var message = matcher.group("message");
                    var profile = bean(MinecraftProfileRepo.class).get(username);
                    target.accept(profile, message);
                }))
                .ifPresent(this::addChildren);
        //todo: moderation channel
        Optional.ofNullable(server.getConsoleChannelId())
                .map(adapter::channelAsStream)
                .ifPresent(target->srv.getOe().redirect(target,target));
    }

    @Override
    public void closeSelf() {
    }
}
