package org.comroid.mcsd.core.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.ManyToOne;
import lombok.*;

import java.time.Duration;
import java.time.Instant;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Backup extends AbstractEntity {
    private Instant timestamp;
    private @ManyToOne Server server;
    private double sizeGB;
    private Duration duration;
    private String file;
    private boolean important;
}
