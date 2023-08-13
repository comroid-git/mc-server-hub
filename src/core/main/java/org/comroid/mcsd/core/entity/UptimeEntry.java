package org.comroid.mcsd.core.entity;

import jakarta.persistence.*;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import org.comroid.api.BitmaskAttribute;
import org.comroid.mcsd.api.model.IStatusMessage;
import org.comroid.mcsd.api.model.Status;
import org.comroid.util.Bitmask;

import java.time.Instant;

@Getter
@Setter
@Entity
@Table(name = "uptime")
public class UptimeEntry extends AbstractEntity {
    private @ManyToOne AbstractEntity entity;
    private Instant timestamp = Instant.now();
    private Status status;
    private String message;
}
