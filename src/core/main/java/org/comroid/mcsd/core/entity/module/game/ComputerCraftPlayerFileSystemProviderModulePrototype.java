package org.comroid.mcsd.core.entity.module.game;

import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.comroid.mcsd.core.entity.module.ModulePrototype;
import org.comroid.mcsd.core.entity.system.User;

import java.util.List;
import java.util.Map;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ComputerCraftPlayerFileSystemProviderModulePrototype extends ModulePrototype {
    public static final String ComputerIdSeparator = ",";

    short serverPort;
    @ElementCollection List<String> worldPaths;
    @ElementCollection Map<User, String> userPasswords;
    @ElementCollection Map<User, String> userComputers;
}
