package org.comroid.mcsd.core.module.player;

import org.comroid.api.Component;
import org.comroid.mcsd.api.dto.PlayerEvent;
import org.comroid.mcsd.core.entity.Server;
import org.comroid.mcsd.core.entity.User;
import org.comroid.mcsd.core.module.ServerModule;
import org.comroid.mcsd.core.repo.UserRepo;

import java.util.HashSet;
import java.util.Set;

import static org.comroid.mcsd.core.util.ApplicationContextProvider.bean;

@Component.Requires(ConsolePlayerEventModule.class)
public class PlayerListModule extends ServerModule {
    public static final Factory<PlayerListModule> Factory = new Factory<>(PlayerListModule.class) {
        @Override
        public PlayerListModule create(Server parent) {
            return new PlayerListModule(parent);
        }
    };

    public PlayerListModule(Server parent) {
        super(parent);
    }

    private final Set<User> players = new HashSet<>();
    private ConsolePlayerEventModule chat;
    private UserRepo users;

    @Override
    protected void $initialize() {
        chat = component(ConsolePlayerEventModule.class).assertion();
        users = bean(UserRepo.class);

        chat.getBus().filterData(e->e.getType().hasFlag(PlayerEvent.Type.JoinLeave))
                .subscribeData(e -> {
                    var player = users.get(e.getUsername()).get();
                    if (e.getMessage().toLowerCase().contains("join"))
                        players.add(player);
                    else players.remove(player);
                });
    }
}
