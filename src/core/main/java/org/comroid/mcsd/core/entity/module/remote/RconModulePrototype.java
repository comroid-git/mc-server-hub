package org.comroid.mcsd.core.entity.module.remote;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.graversen.minecraft.rcon.Defaults;
import jakarta.persistence.Entity;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.comroid.mcsd.core.MCSD;
import org.comroid.mcsd.core.entity.module.console.ConsoleModulePrototype;
import org.comroid.mcsd.core.util.ApplicationContextProvider;
import org.comroid.util.Token;
import org.jetbrains.annotations.Nullable;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class RconModulePrototype extends ConsoleModulePrototype {
    public static final int DefaultPort = 25575;

    private @Nullable Integer port;
    private @Nullable @Getter(onMethod = @__(@JsonIgnore)) String password;

    public String regeneratePassword() {
        var repo = ApplicationContextProvider.bean(MCSD.class).getModules_rcon();
        password = Token.random(16, false);
        repo.save(this);
        return password;
    }
}
