package org.comroid.mcsd.core.entity.module.status;

import jakarta.persistence.Entity;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.comroid.mcsd.core.entity.module.ModulePrototype;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.time.Instant;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class BackupModulePrototype extends ModulePrototype {
    private @Nullable Duration backupPeriod = Duration.ofHours(12);
    private Instant lastBackup = Instant.ofEpochMilli(0);
}
