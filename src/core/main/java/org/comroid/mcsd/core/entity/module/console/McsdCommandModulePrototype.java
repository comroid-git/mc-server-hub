package org.comroid.mcsd.core.entity.module.console;

import jakarta.persistence.Entity;
import jakarta.persistence.ManyToOne;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.extern.java.Log;
import org.comroid.mcsd.core.entity.module.ModulePrototype;

@Entity
@Getter
@Setter
@NoArgsConstructor
//@AllArgsConstructor
public class McsdCommandModulePrototype extends ModulePrototype {
}
