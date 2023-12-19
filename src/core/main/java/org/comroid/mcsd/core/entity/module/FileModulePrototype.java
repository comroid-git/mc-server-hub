package org.comroid.mcsd.core.entity.module;

import jakarta.persistence.Basic;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;
import org.jetbrains.annotations.Nullable;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public abstract class FileModulePrototype extends ModulePrototype {
    private @Nullable String directory = "~/minecraft";
    private @Nullable boolean forceCustomJar = false;
    private @Nullable String backupsDir = "$HOME/backups";
}
