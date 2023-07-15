package org.comroid.mcsd.core.dto;

import lombok.*;
import org.comroid.mcsd.core.entity.Server;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Data
@AllArgsConstructor
@RequiredArgsConstructor
public class StatusMessage {
    public final Instant timestamp = Instant.now();
    public final @NonNull UUID serverId;
    public @With Server.Status status = Server.Status.Offline;
    public @With Server.Status rcon = Server.Status.Offline;
    public @With Server.Status ssh = Server.Status.Offline;
    public @With int playerCount = 0;
    public @With int playerMax = 0;
    public @With String motd = "Server is unreachable";
    public @With @Nullable List<String> players;
    public @With @Nullable String gameMode;
    public @With @Nullable String worldName;
    public @With @Nullable UUID userId;

    public StatusMessage combine(@Nullable StatusMessage msg) {
        if (msg == null)
            return this;
        if (!serverId.equals(msg.serverId))
            throw new IllegalArgumentException("Server IDs must be equal");
        if (msg.players == null)
            msg = msg.withPlayers(players);
        if (msg.gameMode == null)
            msg = msg.withWorldName(gameMode);
        if (msg.worldName == null)
            msg = msg.withWorldName(worldName);
        return msg;
    }
}
