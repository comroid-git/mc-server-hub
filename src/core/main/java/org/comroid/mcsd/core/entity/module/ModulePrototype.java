package org.comroid.mcsd.core.entity.module;

import jakarta.persistence.Entity;
import jakarta.persistence.Inheritance;
import jakarta.persistence.InheritanceType;
import lombok.*;
import lombok.experimental.FieldDefaults;
import org.comroid.api.Invocable;
import org.comroid.api.Named;
import org.comroid.mcsd.core.entity.AbstractEntity;
import org.comroid.mcsd.core.entity.module.console.McsdCommandModulePrototype;
import org.comroid.mcsd.core.entity.module.discord.DiscordModulePrototype;
import org.comroid.mcsd.core.entity.module.local.LocalExecutionModulePrototype;
import org.comroid.mcsd.core.entity.module.local.LocalFileModulePrototype;
import org.comroid.mcsd.core.entity.module.local.LocalShellModulePrototype;
import org.comroid.mcsd.core.entity.module.player.ConsolePlayerEventModulePrototype;
import org.comroid.mcsd.core.entity.module.player.PlayerListModulePrototype;
import org.comroid.mcsd.core.entity.module.ssh.SshFileModulePrototype;
import org.comroid.mcsd.core.entity.module.status.BackupModulePrototype;
import org.comroid.mcsd.core.entity.module.status.StatusModulePrototype;
import org.comroid.mcsd.core.entity.module.status.UpdateModulePrototype;
import org.comroid.mcsd.core.entity.module.status.UptimeModulePrototype;
import org.comroid.mcsd.core.entity.server.Server;
import org.comroid.mcsd.core.module.ServerModule;
import org.comroid.mcsd.core.module.console.McsdCommandModule;
import org.comroid.mcsd.core.module.discord.DiscordModule;
import org.comroid.mcsd.core.module.local.LocalExecutionModule;
import org.comroid.mcsd.core.module.local.LocalFileModule;
import org.comroid.mcsd.core.module.local.LocalShellModule;
import org.comroid.mcsd.core.module.player.ConsolePlayerEventModule;
import org.comroid.mcsd.core.module.player.PlayerListModule;
import org.comroid.mcsd.core.module.ssh.SshFileModule;
import org.comroid.mcsd.core.module.status.BackupModule;
import org.comroid.mcsd.core.module.status.StatusModule;
import org.comroid.mcsd.core.module.status.UpdateModule;
import org.comroid.mcsd.core.module.status.UptimeModule;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
public abstract class ModulePrototype extends AbstractEntity {
    private String dtype;

    @Getter
    @RequiredArgsConstructor
    @FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
    public enum Type implements Named {
        // console
        McsdCommand("MCSD Command from Console", McsdCommandModule.class, McsdCommandModulePrototype.class),
        // discord
        Discord("Discord Integration from Console", DiscordModule.class, DiscordModulePrototype.class),
        // local
        LocalExecution("Local Execution Module", LocalExecutionModule.class, LocalExecutionModulePrototype.class),
        LocalFile("Local File Module", LocalFileModule.class, LocalFileModulePrototype.class),
        LocalShell("Local Shell Execution Module", LocalShellModule.class, LocalShellModulePrototype.class),
        // player
        ConsolePlayerEventForwarder("Forward Console Player Events", ConsolePlayerEventModule.class, ConsolePlayerEventModulePrototype.class),
        PlayerList("Cache Player List from Player Events", PlayerListModule.class, PlayerListModulePrototype.class),
        // ssh
        SshFile("SSH File Module", SshFileModule.class, SshFileModulePrototype.class),
        //status
        Backup("Automated Backups", BackupModule.class, BackupModulePrototype.class),
        Update("Automated Updates", UpdateModule.class, UpdateModulePrototype.class),
        Status("Internal Status Logging", StatusModule.class, StatusModulePrototype.class),
        Uptime("Internal Uptime Logging", UptimeModule.class, UptimeModulePrototype.class);

        String displayName;
        Class<? extends ServerModule> impl;
        Class<? extends ModulePrototype> proto;
        Invocable<? extends ServerModule> ctor = Invocable.ofConstructor(impl, Server.class);
    }
}
