package org.comroid.mcsd.core.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.persistence.AttributeConverter;
import lombok.*;
import lombok.experimental.FieldDefaults;
import lombok.experimental.UtilityClass;
import org.comroid.annotations.Ignore;
import org.comroid.api.Polyfill;
import org.comroid.api.attr.Bitmask.Attribute;
import org.comroid.api.attr.Described;
import org.comroid.api.attr.Named;
import org.comroid.api.data.bind.DataStructure;
import org.comroid.api.func.ext.Wrap;
import org.comroid.api.func.util.Bitmask;
import org.comroid.api.func.util.Invocable;
import org.comroid.api.tree.Component;
import org.comroid.mcsd.core.MCSD;
import org.comroid.mcsd.core.entity.module.InternalModulePrototype;
import org.comroid.mcsd.core.entity.module.ModulePrototype;
import org.comroid.mcsd.core.entity.module.console.McsdCommandModulePrototype;
import org.comroid.mcsd.core.entity.module.discord.DiscordModulePrototype;
import org.comroid.mcsd.core.entity.module.local.LocalExecutionModulePrototype;
import org.comroid.mcsd.core.entity.module.local.LocalFileModulePrototype;
import org.comroid.mcsd.core.entity.module.local.LocalShellModulePrototype;
import org.comroid.mcsd.core.entity.module.player.ConsolePlayerEventModulePrototype;
import org.comroid.mcsd.core.entity.module.player.ForceOpModulePrototype;
import org.comroid.mcsd.core.entity.module.player.PlayerListModulePrototype;
import org.comroid.mcsd.core.entity.module.remote.rabbit.RabbitRxModulePrototype;
import org.comroid.mcsd.core.entity.module.remote.rabbit.RabbitTxModulePrototype;
import org.comroid.mcsd.core.entity.module.remote.rcon.RconModulePrototype;
import org.comroid.mcsd.core.entity.module.remote.ssh.SshFileModulePrototype;
import org.comroid.mcsd.core.entity.module.status.BackupModulePrototype;
import org.comroid.mcsd.core.entity.module.status.StatusModulePrototype;
import org.comroid.mcsd.core.entity.module.status.UpdateModulePrototype;
import org.comroid.mcsd.core.entity.module.status.UptimeModulePrototype;
import org.comroid.mcsd.core.entity.server.Server;
import org.comroid.mcsd.core.module.InternalModule;
import org.comroid.mcsd.core.module.ServerModule;
import org.comroid.mcsd.core.module.console.McsdCommandModule;
import org.comroid.mcsd.core.module.discord.DiscordModule;
import org.comroid.mcsd.core.module.local.LocalExecutionModule;
import org.comroid.mcsd.core.module.local.LocalFileModule;
import org.comroid.mcsd.core.module.local.LocalShellModule;
import org.comroid.mcsd.core.module.player.ConsolePlayerEventModule;
import org.comroid.mcsd.core.module.player.ForceOpModule;
import org.comroid.mcsd.core.module.player.PlayerListModule;
import org.comroid.mcsd.core.module.remote.rabbit.RabbitRxModule;
import org.comroid.mcsd.core.module.remote.rabbit.RabbitTxModule;
import org.comroid.mcsd.core.module.remote.rcon.RconModule;
import org.comroid.mcsd.core.module.remote.ssh.SshFileModule;
import org.comroid.mcsd.core.module.status.BackupModule;
import org.comroid.mcsd.core.module.status.StatusModule;
import org.comroid.mcsd.core.module.status.UpdateModule;
import org.comroid.mcsd.core.module.status.UptimeModule;
import org.comroid.mcsd.core.repo.module.ModuleRepo;
import org.intellij.lang.annotations.MagicConstant;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

import static org.comroid.mcsd.core.model.ModuleType.Side.*;
import static org.comroid.mcsd.core.util.ApplicationContextProvider.bean;

@Value
public class ModuleType<Module extends ServerModule<Proto>, Proto extends ModulePrototype> implements Named, Described {
    private static final Map<String, ModuleType<?, ?>> $cache = new ConcurrentHashMap<>();
    public static final Map<String, ModuleType<?, ?>> cache = Collections.unmodifiableMap($cache);

    /** internal */
    public static final ModuleType<InternalModule, @Nullable InternalModulePrototype> Internal = new ModuleType<>(Agent|Hub, "Internal", "Internal Scripting Module", InternalModule.class, InternalModulePrototype.class, null);

    // local
    /** java */
    public static final ModuleType<LocalExecutionModule, LocalExecutionModulePrototype> LocalExecution = new ModuleType<>(Agent|Exclusive, "LocalExecution", "Local Execution Module", LocalExecutionModule.class, LocalExecutionModulePrototype.class, MCSD::getModules_localExecution);
    /** fs */
    public static final ModuleType<LocalFileModule, LocalFileModulePrototype> LocalFile = new ModuleType<>(Agent|Exclusive, "LocalFile", "Local File Module", LocalFileModule.class, LocalFileModulePrototype.class, MCSD::getModules_localFiles);
    /** bash */
    public static final ModuleType<LocalShellModule, LocalShellModulePrototype> LocalShell = new ModuleType<>(Agent|Exclusive, "LocalShell", "Local Shell Execution Module", LocalShellModule.class, LocalShellModulePrototype.class, MCSD::getModules_localShell);

    // remote
    /** ssh */
    public static final ModuleType<SshFileModule, SshFileModulePrototype> SshFile = new ModuleType<>(Hub, "SshFile", "SSH File Module", SshFileModule.class, SshFileModulePrototype.class, MCSD::getModules_sshFile);
    /** rcon */
    public static final ModuleType<RconModule, RconModulePrototype> Rcon = new ModuleType<>(Hub, "RCon","RCon Connection Module", RconModule.class, RconModulePrototype.class, MCSD::getModules_rcon);
    /** agent-side rabbitmq */
    public static final ModuleType<RabbitTxModule, RabbitTxModulePrototype> RabbitTx = new ModuleType<>(Agent|Exclusive, "Rabbit Tx", "RabbitMQ Agent Module", RabbitTxModule.class, RabbitTxModulePrototype.class, MCSD::getModules_rabbitTx);
    /** hub-side rabbitmq */
    public static final ModuleType<RabbitRxModule, RabbitRxModulePrototype> RabbitRx = new ModuleType<>(Hub|Exclusive, "Rabbit Rx", "RabbitMQ Agent Module", RabbitRxModule.class, RabbitRxModulePrototype.class, MCSD::getModules_rabbitRx);

    // player
    /** event source: console */
    public static final ModuleType<ConsolePlayerEventModule, ConsolePlayerEventModulePrototype> ConsolePlayerEvent = new ModuleType<>(Hub, "ConsolePlayerEvent", "Forward Console Player Events", ConsolePlayerEventModule.class, ConsolePlayerEventModulePrototype.class, MCSD::getModules_consolePlayerEvents);
    /** player list */
    public static final ModuleType<PlayerListModule, PlayerListModulePrototype> PlayerList = new ModuleType<>(Hub, "PlayerList", "Cache Player List from Player Events", PlayerListModule.class, PlayerListModulePrototype.class, MCSD::getModules_playerList);
    /** force op */
    public static final ModuleType<ForceOpModule, ForceOpModulePrototype> ForceOP = new ModuleType<>(Hub, "ForceOP", "Enforce OP for permitted players", ForceOpModule.class, ForceOpModulePrototype.class, MCSD::getModules_forceOp);

    // status
    public static final ModuleType<BackupModule, BackupModulePrototype> Backup = new ModuleType<>(Agent|Exclusive, "Backup", "Automated Backups", BackupModule.class, BackupModulePrototype.class, MCSD::getModules_backup);
    public static final ModuleType<UpdateModule, UpdateModulePrototype> Update = new ModuleType<>(Agent|Exclusive, "Update", "Automated Updates", UpdateModule.class, UpdateModulePrototype.class, MCSD::getModules_update);
    public static final ModuleType<StatusModule, StatusModulePrototype> Status = new ModuleType<>(Agent|Hub, "Status", "Status Logging", StatusModule.class, StatusModulePrototype.class, MCSD::getModules_status);
    public static final ModuleType<UptimeModule, UptimeModulePrototype> Uptime = new ModuleType<>(Agent|Hub, "Uptime", "Uptime Logging", UptimeModule.class, UptimeModulePrototype.class, MCSD::getModules_uptime);

    // utility
    /** mcsd command */
    public static final ModuleType<McsdCommandModule, McsdCommandModulePrototype> McsdCommand = new ModuleType<>(Agent|Hub, "McsdCommand", "MCSD Command from Console", McsdCommandModule.class, McsdCommandModulePrototype.class, MCSD::getModules_mcsd);
    /** discord */
    public static final ModuleType<DiscordModule, DiscordModulePrototype> Discord = new ModuleType<>(Agent|Hub, "Discord", "Discord Integration from Console", DiscordModule.class, DiscordModulePrototype.class, MCSD::getModules_discord);

    @MagicConstant(flagsFromClass = Side.class) long preferredSide;
    String name;
    String description;
    @ToString.Exclude DataStructure<Module> impl;
    @ToString.Exclude DataStructure<Proto> proto;
    @ToString.Exclude @JsonIgnore @Ignore Invocable<Module> ctor;
    @ToString.Exclude @JsonIgnore @Ignore Function<MCSD, ModuleRepo<Proto>> obtainRepo;

    public ModuleType(@MagicConstant(flagsFromClass = Side.class) long preferredSide,
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

    @Getter
    @RequiredArgsConstructor
    @FieldDefaults(makeFinal = true,level = AccessLevel.PRIVATE)
    public enum Side implements Bitmask.Attribute<Side> {
        Agent(1),
        Hub(2),
        Both(3);

        long value;
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
