package org.comroid.mcsd.core.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.persistence.AttributeConverter;
import lombok.ToString;
import lombok.Value;
import org.comroid.annotations.Ignore;
import org.comroid.api.*;
import org.comroid.mcsd.core.MCSD;
import org.comroid.mcsd.core.entity.module.ModulePrototype;
import org.comroid.mcsd.core.entity.module.console.McsdCommandModulePrototype;
import org.comroid.mcsd.core.entity.module.discord.DiscordModulePrototype;
import org.comroid.mcsd.core.entity.module.local.LocalExecutionModulePrototype;
import org.comroid.mcsd.core.entity.module.local.LocalFileModulePrototype;
import org.comroid.mcsd.core.entity.module.local.LocalShellModulePrototype;
import org.comroid.mcsd.core.entity.module.player.ConsolePlayerEventModulePrototype;
import org.comroid.mcsd.core.entity.module.player.PlayerListModulePrototype;
import org.comroid.mcsd.core.entity.module.remote.RconModulePrototype;
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
import org.comroid.mcsd.core.module.remote.RconModule;
import org.comroid.mcsd.core.module.ssh.SshFileModule;
import org.comroid.mcsd.core.module.status.BackupModule;
import org.comroid.mcsd.core.module.status.StatusModule;
import org.comroid.mcsd.core.module.status.UpdateModule;
import org.comroid.mcsd.core.module.status.UptimeModule;
import org.comroid.mcsd.core.repo.module.ModuleRepo;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

import static org.comroid.mcsd.core.util.ApplicationContextProvider.bean;

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

    // remote
    public static final ModuleType<RconModule, RconModulePrototype> Rcon = new ModuleType<>("RCon","RCon Connection Module", RconModule.class, RconModulePrototype.class, MCSD::getModules_rcon);

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
    @ToString.Exclude DataStructure<Module> impl;
    @ToString.Exclude DataStructure<Proto> proto;
    @ToString.Exclude @JsonIgnore @Ignore Invocable<Module> ctor;
    @ToString.Exclude @JsonIgnore @Ignore Function<MCSD, ModuleRepo<Proto>> obtainRepo;

    public ModuleType(String name,
               String description,
               Class<Module> impl,
               Class<Proto> proto,
               Function<MCSD, ModuleRepo<Proto>> obtainRepo
    ) {
        this.name = name;
        this.description = description;
        this.impl = DataStructure.of(impl, ServerModule.class);
        this.proto = DataStructure.of(proto, ModulePrototype.class);
        this.ctor = Invocable.ofConstructor(impl, Server.class, proto);
        this.obtainRepo = obtainRepo;

        $cache.put(name, this);
    }

    @JsonInclude
    public List<String> getDependencies() {
        return Component.requires(Polyfill.uncheckedCast(impl.getType())).stream()
                .flatMap(type -> ModuleType.of(type).stream())
                .map(Named::getName)
                .toList();
    }

    @Override
    public String getAlternateName() {
        return description;
    }

    @JsonIgnore
    public ModuleRepo<Proto> getRepo() {
        return obtainRepo.apply(bean(MCSD.class));
    }

    public static SupplierX<ModuleType<?, ?>> of(String name) {
        return SupplierX.of(cache.getOrDefault(name, null));
    }

    public static <Module extends ServerModule<Proto>, Proto extends ModulePrototype> SupplierX<ModuleType<Module, Proto>> of(Module module) {
        return of(module.getClass());
    }

    public static <Module extends ServerModule<Proto>, Proto extends ModulePrototype> SupplierX<ModuleType<Module, Proto>> of(Class<?> moduleType) {
        return SupplierX.ofOptional(cache.values().stream()
                .filter(type -> type.impl.getType().isAssignableFrom(moduleType))
                .map(Polyfill::<ModuleType<Module, Proto>>uncheckedCast)
                .findAny());
    }

    public static <Proto extends ModulePrototype> SupplierX<ModuleType<?, Proto>> of(Proto proto) {
        return SupplierX.ofOptional(cache.values().stream()
                .filter(type -> type.proto.getType().isInstance(proto))
                .map(Polyfill::<ModuleType<?, Proto>>uncheckedCast)
                .findAny());
    }

    @jakarta.persistence.Converter(autoApply = true) // autoApply doesn't work
    public static class Converter implements AttributeConverter<ModuleType<?,?>,String> {
        @Override
        public String convertToDatabaseColumn(ModuleType<?, ?> attribute) {
            return attribute.name;
        }

        @Override
        public ModuleType<?, ?> convertToEntityAttribute(String dbData) {
            return of(dbData).assertion("Unknown ModuleType: " + dbData);
        }
    }
}
