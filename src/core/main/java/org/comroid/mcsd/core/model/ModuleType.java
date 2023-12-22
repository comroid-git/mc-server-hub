package org.comroid.mcsd.core.model;

import lombok.Getter;
import org.comroid.api.Invocable;
import org.comroid.api.Named;
import org.comroid.api.SupplierX;
import org.comroid.mcsd.core.MCSD;
import org.comroid.mcsd.core.entity.module.ModulePrototype;
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
import org.comroid.mcsd.core.repo.module.ModuleRepo;

import java.util.function.Function;
import java.util.stream.Stream;

@Getter
public enum ModuleType implements Named {
    // console
    McsdCommand("MCSD Command from Console", McsdCommandModule.class, McsdCommandModulePrototype.class, MCSD::getModules_mcsd), // discord
    Discord("Discord Integration from Console", DiscordModule.class, DiscordModulePrototype.class, MCSD::getModules_discord), // local
    LocalExecution("Local Execution Module", LocalExecutionModule.class, LocalExecutionModulePrototype.class, MCSD::getModules_localExecution),
    LocalFile("Local File Module", LocalFileModule.class, LocalFileModulePrototype.class, MCSD::getModules_localFiles),
    LocalShell("Local Shell Execution Module", LocalShellModule.class, LocalShellModulePrototype.class, MCSD::getModules_localShell), // player
    ConsolePlayerEvent("Forward Console Player Events", ConsolePlayerEventModule.class, ConsolePlayerEventModulePrototype.class, MCSD::getModules_consolePlayerEvents),
    PlayerList("Cache Player List from Player Events", PlayerListModule.class, PlayerListModulePrototype.class, MCSD::getModules_playerList), // ssh
    SshFile("SSH File Module", SshFileModule.class, SshFileModulePrototype.class, MCSD::getModules_sshFile), //status
    Backup("Automated Backups", BackupModule.class, BackupModulePrototype.class, MCSD::getModules_backup),
    Update("Automated Updates", UpdateModule.class, UpdateModulePrototype.class, MCSD::getModules_update),
    Status("Internal Status Logging", StatusModule.class, StatusModulePrototype.class, MCSD::getModules_status),
    Uptime("Internal Uptime Logging", UptimeModule.class, UptimeModulePrototype.class, MCSD::getModules_uptime);

    private final String description;
    private final Class<? extends ServerModule<?>> impl;
    private final Class<? extends ModulePrototype> proto;
    private final Function<MCSD, ModuleRepo<?>> repo;
    private final Invocable<? extends ServerModule<?>> ctor;

    ModuleType(String description, Class<? extends ServerModule<?>> impl, Class<? extends ModulePrototype> proto, Function<MCSD, ModuleRepo<?>> repo) {
        this.description = description;
        this.impl = impl;
        this.proto = proto;
        this.repo = repo;
        this.ctor = Invocable.ofConstructor(impl, Server.class, proto);
    }

    @Override
    public String getAlternateName() {
        return description;
    }

    public static SupplierX<ModuleType> of(ModulePrototype proto) {
        return SupplierX.ofOptional(Stream.of(values())
                .filter(type -> type.proto.isInstance(proto))
                .findAny());
    }
}
