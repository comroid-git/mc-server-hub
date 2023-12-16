package org.comroid.mcsd.core.entity.module.local;

import jakarta.persistence.Entity;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.java.Log;
import org.comroid.mcsd.core.entity.module.console.ConsoleModulePrototype;

@Log
@Getter
@Setter
@Entity
public class LocalExecutionModulePrototype extends ConsoleModulePrototype {
}
