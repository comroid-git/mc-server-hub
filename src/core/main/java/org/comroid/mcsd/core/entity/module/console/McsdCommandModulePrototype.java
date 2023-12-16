package org.comroid.mcsd.core.entity.module.console;

import jakarta.persistence.Entity;
import jakarta.persistence.ManyToOne;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.java.Log;
import org.comroid.mcsd.core.entity.module.ModulePrototype;

@Log
@Getter
@Setter
@Entity
public class McsdCommandModulePrototype extends ModulePrototype {
    @ManyToOne ConsoleModulePrototype console;
}
