package org.comroid.mcsd.core.entity.module.status;

import jakarta.persistence.Basic;
import jakarta.persistence.Entity;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.comroid.mcsd.core.entity.module.ModulePrototype;

import java.time.Duration;
import java.time.Instant;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class UpdateModulePrototype extends ModulePrototype {
    private @Basic Duration updatePeriod = Duration.ofDays(7);
    private Instant lastUpdate = Instant.ofEpochMilli(0);
}
