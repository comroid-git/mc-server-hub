package org.comroid.mcsd.web.dto;

import lombok.*;
import org.comroid.mcsd.web.entity.Server;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@AllArgsConstructor
@RequiredArgsConstructor
public class StatusMessage {
    private final Instant timestamp = Instant.now();
    private final UUID serverId;
    private @Builder.Default Server.Status status = Server.Status.Offline;
    private @Builder.Default Server.Status rcon = Server.Status.Offline;
    private @Builder.Default int playerCount = 0;
    private @Builder.Default int playerMax = 0;
    private @Builder.Default String motd = "Server is unreachable";
    private @Nullable List<String> players;
    private @Nullable String gameMode;
    private @Nullable String worldName;

    public StatusMessage combine(StatusMessage msg) {
        if (!serverId.equals(msg.serverId))
            throw new IllegalArgumentException("Server IDs must be equal");
        return builder()
                .serverId(serverId)
                .status(msg.status)
                .rcon(msg.rcon)
                .players(msg.players != null ? msg.players : players)
                .playerCount(msg.playerCount)
                .playerMax(msg.playerMax)
                .motd(msg.motd)
                .gameMode(msg.gameMode)
                .worldName(msg.worldName != null ? msg.worldName : worldName)
                .build();
    }
}
