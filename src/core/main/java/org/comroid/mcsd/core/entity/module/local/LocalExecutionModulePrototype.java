package org.comroid.mcsd.core.entity.module.local;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.extern.java.Log;
import org.comroid.mcsd.core.entity.module.console.ConsoleModulePrototype;
import org.jetbrains.annotations.Nullable;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class LocalExecutionModulePrototype extends ConsoleModulePrototype {
    private @Nullable @Column(columnDefinition = "TEXT") String customCommand = null;
    private byte ramGB = 4;
}
