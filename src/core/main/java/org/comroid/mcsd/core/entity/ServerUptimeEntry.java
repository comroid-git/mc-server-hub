package org.comroid.mcsd.core.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.comroid.mcsd.api.model.Status;

import java.time.Instant;

@Getter
@Setter
@Entity
@Table(name = "uptime")
public class ServerUptimeEntry extends AbstractEntity {
    private @ManyToOne AbstractEntity entity;
    private Instant timestamp = Instant.now();
    private Status status;
    private String message;
}
