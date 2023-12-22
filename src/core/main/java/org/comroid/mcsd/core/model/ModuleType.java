package org.comroid.mcsd.core.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Getter;
import lombok.Value;
import org.comroid.api.Invocable;
import org.comroid.api.Named;
import org.comroid.api.Polyfill;
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

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Stream;

@Value
public class ModuleType<Module extends ServerModule<Proto>, Proto extends ModulePrototype> implements Named {
    private static final Map<String, ModuleType<?, ?>> $cache = new ConcurrentHashMap<>();
    public static final Map<String, ModuleType<?, ?>> cache = Collections.unmodifiableMap($cache);

    // console
    public static final ModuleType<McsdCommandModule, McsdCommandModulePrototype> McsdCommand = new ModuleType<>("McsdCommand", "MCSD Command from Console", McsdCommandModule.class, McsdCommandModulePrototype.class, MCSD::getModules_mcsd);

    // discord
    public static final ModuleType<DiscordModule, DiscordModulePrototype> Discord = new ModuleType<>("Discord", "Discord Integration from Console", DiscordModule.class, DiscordModulePrototype.class, MCSD::getModules_discord);

    // local
    public static final ModuleType<LocalExecutionModule, LocalExecutionModulePrototype> LocalExecution = new ModuleType<>("LocalExecution", "Local Execution Module", LocalExecutionModule.class, LocalExecutionModulePrototype.class, MCSD::getModules_localExecution);
    public static final ModuleType<LocalFileModule, LocalFileModulePrototype> LocalFile = new ModuleType<>("LocalFile", "Local File Module", LocalFileModule.class, LocalFileModulePrototype.class, MCSD::getModules_localFiles);
    public static final ModuleType<LocalShellModule, LocalShellModulePrototype> LocalShell = new ModuleType<>("LocalShell", "Local Shell Execution Module", LocalShellModule.class, LocalShellModulePrototype.class, MCSD::getModules_localShell);

    // player
    public static final ModuleType<ConsolePlayerEventModule, ConsolePlayerEventModulePrototype> ConsolePlayerEvent = new ModuleType<>("ConsolePlayerEvent", "Forward Console Player Events", ConsolePlayerEventModule.class, ConsolePlayerEventModulePrototype.class, MCSD::getModules_consolePlayerEvents);
    public static final ModuleType<PlayerListModule, PlayerListModulePrototype> PlayerList = new ModuleType<>("PlayerList", "Cache Player List from Player Events", PlayerListModule.class, PlayerListModulePrototype.class, MCSD::getModules_playerList);

    // ssh
    public static final ModuleType<SshFileModule, SshFileModulePrototype> SshFile = new ModuleType<>("SshFile", "SSH File Module", SshFileModule.class, SshFileModulePrototype.class, MCSD::getModules_sshFile);

    //status
    public static final ModuleType<BackupModule, BackupModulePrototype> Backup = new ModuleType<>("Backup", "Automated Backups", BackupModule.class, BackupModulePrototype.class, MCSD::getModules_backup);
    public static final ModuleType<UpdateModule, UpdateModulePrototype> Update = new ModuleType<>("Update", "Automated Updates", UpdateModule.class, UpdateModulePrototype.class, MCSD::getModules_update);
    public static final ModuleType<StatusModule, StatusModulePrototype> Status = new ModuleType<>("Status", "Internal Status Logging", StatusModule.class, StatusModulePrototype.class, MCSD::getModules_status);
    public static final ModuleType<UptimeModule, UptimeModulePrototype> Uptime = new ModuleType<>("Uptime", "Internal Uptime Logging", UptimeModule.class, UptimeModulePrototype.class, MCSD::getModules_uptime);

    String name;
    String description;
    Class<Module> impl;
    Class<Proto> proto;
    @JsonIgnore Function<MCSD, ModuleRepo<Proto>> repo;
    @JsonIgnore Invocable<Module> ctor;

    ModuleType(String name,
               String description,
               Class<Module> impl,
               Class<Proto> proto,
               Function<MCSD, ModuleRepo<Proto>> repo
    ) {
        this.name = name;
        this.description = description;
        this.impl = impl;
        this.proto = proto;
        this.repo = repo;
        this.ctor = Invocable.ofConstructor(impl, Server.class, proto);

        $cache.put(name, this);
    }

    @Override
    public String getAlternateName() {
        return description;
    }

    public static SupplierX<ModuleType<?, ?>> of(String name) {
        return SupplierX.of(cache.getOrDefault(name, null));
    }

    public static <Module extends ServerModule<Proto>, Proto extends ModulePrototype> SupplierX<ModuleType<Module, ?>> of(Module module) {
        return SupplierX.ofOptional(cache.values().stream()
                .filter(type -> type.impl.isInstance(module))
                .map(Polyfill::<ModuleType<Module, Proto>>uncheckedCast)
                .findAny());
    }

    public static <Proto extends ModulePrototype> SupplierX<ModuleType<?, Proto>> of(Proto proto) {
        return SupplierX.ofOptional(cache.values().stream()
                .filter(type -> type.proto.isInstance(proto))
                .map(Polyfill::<ModuleType<?, Proto>>uncheckedCast)
                .findAny());
    }
}
