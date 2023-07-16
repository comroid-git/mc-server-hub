package org.comroid.mcsd.api.dto;

import lombok.*;
import org.comroid.mcsd.api.model.Status;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Data
@AllArgsConstructor
@RequiredArgsConstructor
@Builder(toBuilder = true)
public class StatusMessage {
    public final Instant timestamp = Instant.now();
    public final @NonNull UUID targetId;
    public @With Status status = Status.Offline;
    public @With @Nullable Status rcon = Status.Offline;
    public @With @Nullable Status ssh = Status.Offline;
    public @With int playerCount = 0;
    public @With int playerMax = 0;
    public @With @Nullable String motd;
    public @With @Nullable List<String> players;
    public @With @Nullable String gameMode;
    public @With @Nullable String worldName;
    public @With @Nullable UUID userId;

    public StatusMessage combine(@Nullable StatusMessage other) {
        if (other == null)
            return this;
        if (!targetId.equals(other.targetId))
            throw new IllegalArgumentException("Server IDs must be equal");
        if (other.players == null)
            other = other.withPlayers(players);
        if (other.gameMode == null)
            other = other.withGameMode(gameMode);
        if (other.worldName == null)
            other = other.withWorldName(worldName);
        return other;
    }
}
