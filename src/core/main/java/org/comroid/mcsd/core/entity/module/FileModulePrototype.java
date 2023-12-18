package org.comroid.mcsd.core.entity.module;

import jakarta.persistence.Basic;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public abstract class FileModulePrototype extends ModulePrototype {
    private String directory = "~/minecraft";
    private boolean forceCustomJar = false;
    private String backupsDir = "$HOME/backups";
}
