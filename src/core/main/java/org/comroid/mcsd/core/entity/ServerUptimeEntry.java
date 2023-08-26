package org.comroid.mcsd.core.entity;

import jakarta.persistence.*;
import lombok.*;
import org.comroid.mcsd.api.model.Status;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;

@Data
@Entity
@NoArgsConstructor
public class ServerUptimeEntry {
    private static final Object lock = new Object();

    private @Id Instant timestamp;
    private @ManyToOne Server server;
    private Status status;
    private int players;
    private long ramKB;
    private @Nullable String message;

    public ServerUptimeEntry(Server server, Status status, int players, long ramKB, @Nullable String message) {
        synchronized (lock) {
            timestamp = Instant.now();
        }
        this.server = server;
        this.status = status;
        this.players = players;
        this.ramKB = ramKB;
        this.message = message;
    }
}
