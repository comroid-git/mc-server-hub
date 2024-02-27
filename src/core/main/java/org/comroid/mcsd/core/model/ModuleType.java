package org.comroid.mcsd.core.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.persistence.AttributeConverter;
import lombok.ToString;
import lombok.Value;
import org.comroid.annotations.Ignore;
import org.comroid.api.Polyfill;
import org.comroid.api.attr.BitmaskAttribute;
import org.comroid.api.attr.Named;
import org.comroid.api.data.seri.DataStructure;
import org.comroid.api.func.ext.Wrap;
import org.comroid.api.func.util.Invocable;
import org.comroid.api.tree.Component;
import org.comroid.mcsd.core.MCSD;
import org.comroid.mcsd.core.entity.module.ModulePrototype;
import org.comroid.mcsd.core.entity.module.console.McsdCommandModulePrototype;
import org.comroid.mcsd.core.entity.module.discord.DiscordModulePrototype;
import org.comroid.mcsd.core.entity.module.local.LocalExecutionModulePrototype;
import org.comroid.mcsd.core.entity.module.local.LocalFileModulePrototype;
import org.comroid.mcsd.core.entity.module.local.LocalShellModulePrototype;
import org.comroid.mcsd.core.entity.module.player.ConsolePlayerEventModulePrototype;
import org.comroid.mcsd.core.entity.module.player.ForceOpModulePrototype;
import org.comroid.mcsd.core.entity.module.player.PlayerListModulePrototype;
import org.comroid.mcsd.core.entity.module.remote.rabbit.RabbitModulePrototype;
import org.comroid.mcsd.core.entity.module.remote.rcon.RconModulePrototype;
import org.comroid.mcsd.core.entity.module.remote.ssh.SshFileModulePrototype;
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
import org.comroid.mcsd.core.module.player.ForceOpModule;
import org.comroid.mcsd.core.module.player.PlayerListModule;
import org.comroid.mcsd.core.module.remote.rabbit.RabbitModule;
import org.comroid.mcsd.core.module.remote.rcon.RconModule;
import org.comroid.mcsd.core.module.remote.ssh.SshFileModule;
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

    // local
    /** java */
    public static final ModuleType<LocalExecutionModule, LocalExecutionModulePrototype> LocalExecution = new ModuleType<>(Side.Agent, "LocalExecution", "Local Execution Module", LocalExecutionModule.class, LocalExecutionModulePrototype.class, MCSD::getModules_localExecution);
    /** fs */
    public static final ModuleType<LocalFileModule, LocalFileModulePrototype> LocalFile = new ModuleType<>(Side.Agent, "LocalFile", "Local File Module", LocalFileModule.class, LocalFileModulePrototype.class, MCSD::getModules_localFiles);
    /** bash */
    public static final ModuleType<LocalShellModule, LocalShellModulePrototype> LocalShell = new ModuleType<>(Side.Agent, "LocalShell", "Local Shell Execution Module", LocalShellModule.class, LocalShellModulePrototype.class, MCSD::getModules_localShell);

    // remote
    /** ssh */
    public static final ModuleType<SshFileModule, SshFileModulePrototype> SshFile = new ModuleType<>(Side.Hub, "SshFile", "SSH File Module", SshFileModule.class, SshFileModulePrototype.class, MCSD::getModules_sshFile);
    /** rabbit */
    public static final ModuleType<RabbitModule, RabbitModulePrototype> Rabbit = new ModuleType<>(Side.Both, "RabbitMQ", "RabbitMQ Connection module",RabbitModule.class, RabbitModulePrototype.class, MCSD::getModules_rabbit);
    /** rcon */
    public static final ModuleType<RconModule, RconModulePrototype> Rcon = new ModuleType<>(Side.Hub, "RCon","RCon Connection Module", RconModule.class, RconModulePrototype.class, MCSD::getModules_rcon);

    // player
    /** event source: console */
    public static final ModuleType<ConsolePlayerEventModule, ConsolePlayerEventModulePrototype> ConsolePlayerEvent = new ModuleType<>(Side.Hub, "ConsolePlayerEvent", "Forward Console Player Events", ConsolePlayerEventModule.class, ConsolePlayerEventModulePrototype.class, MCSD::getModules_consolePlayerEvents);
    /** player list */
    public static final ModuleType<PlayerListModule, PlayerListModulePrototype> PlayerList = new ModuleType<>(Side.Hub, "PlayerList", "Cache Player List from Player Events", PlayerListModule.class, PlayerListModulePrototype.class, MCSD::getModules_playerList);
    /** force op */
    public static final ModuleType<ForceOpModule, ForceOpModulePrototype> ForceOP = new ModuleType<>(Side.Hub, "ForceOP", "Enforce OP for permitted players", ForceOpModule.class, ForceOpModulePrototype.class, MCSD::getModules_forceOp);

    // status
    public static final ModuleType<BackupModule, BackupModulePrototype> Backup = new ModuleType<>(Side.Agent, "Backup", "Automated Backups", BackupModule.class, BackupModulePrototype.class, MCSD::getModules_backup);
    public static final ModuleType<UpdateModule, UpdateModulePrototype> Update = new ModuleType<>(Side.Agent, "Update", "Automated Updates", UpdateModule.class, UpdateModulePrototype.class, MCSD::getModules_update);
    public static final ModuleType<StatusModule, StatusModulePrototype> Status = new ModuleType<>(Side.Both, "Status", "Status Logging", StatusModule.class, StatusModulePrototype.class, MCSD::getModules_status);
    public static final ModuleType<UptimeModule, UptimeModulePrototype> Uptime = new ModuleType<>(Side.Both, "Uptime", "Uptime Logging", UptimeModule.class, UptimeModulePrototype.class, MCSD::getModules_uptime);

    // utility
    /** mcsd command */
    public static final ModuleType<McsdCommandModule, McsdCommandModulePrototype> McsdCommand = new ModuleType<>(Side.Both, "McsdCommand", "MCSD Command from Console", McsdCommandModule.class, McsdCommandModulePrototype.class, MCSD::getModules_mcsd);
    /** discord */
    public static final ModuleType<DiscordModule, DiscordModulePrototype> Discord = new ModuleType<>(Side.Both, "Discord", "Discord Integration from Console", DiscordModule.class, DiscordModulePrototype.class, MCSD::getModules_discord);

    Side preferredSide;
    String name;
    String description;
    @ToString.Exclude DataStructure<Module> impl;
    @ToString.Exclude DataStructure<Proto> proto;
    @ToString.Exclude @JsonIgnore @Ignore Invocable<Module> ctor;
    @ToString.Exclude @JsonIgnore @Ignore Function<MCSD, ModuleRepo<Proto>> obtainRepo;

    public ModuleType(Side preferredSide,
                      String name,
                      String description,
                      Class<Module> impl,
                      Class<Proto> proto,
                      Function<MCSD, ModuleRepo<Proto>> obtainRepo
    ) {
        this.preferredSide = preferredSide;
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
        return Component.dependencies(Polyfill.uncheckedCast(impl.getType())).stream()
                .map(Component.Dependency::getType)
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

    public static Wrap<ModuleType<?, ?>> of(String name) {
        return Wrap.of(cache.getOrDefault(name, null));
    }

    public static <Module extends ServerModule<Proto>, Proto extends ModulePrototype> Wrap<ModuleType<Module, Proto>> of(Module module) {
        return of(module.getClass());
    }

    public static <Module extends ServerModule<Proto>, Proto extends ModulePrototype> Wrap<ModuleType<Module, Proto>> of(Class<?> moduleType) {
        return Wrap.ofOptional(cache.values().stream()
                .filter(type -> type.impl.getType().isAssignableFrom(moduleType))
                .map(Polyfill::<ModuleType<Module, Proto>>uncheckedCast)
                .findAny());
    }

    public static <Proto extends ModulePrototype> Wrap<ModuleType<?, Proto>> of(Proto proto) {
        return Wrap.ofOptional(cache.values().stream()
                .filter(type -> type.proto.getType().isInstance(proto))
                .map(Polyfill::<ModuleType<?, Proto>>uncheckedCast)
                .findAny());
    }

    public enum Side implements BitmaskAttribute<Side> {
        None, Agent, Hub, Both
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
