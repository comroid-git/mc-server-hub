package org.comroid.mcsd.core.entity;


import jakarta.persistence.*;
import lombok.Data;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.comroid.api.BitmaskAttribute;
import org.comroid.api.Named;
import org.comroid.api.SupplierX;
import org.comroid.mcsd.core.entity.system.User;
import org.comroid.mcsd.core.exception.InsufficientPermissionsException;
import org.comroid.mcsd.util.Utils;
import org.comroid.util.Bitmask;
import org.comroid.util.Constraint;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;

import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;

@Data
@Slf4j
@Entity
@Inheritance(strategy = InheritanceType.JOINED)
public abstract class AbstractEntity implements Named {
    public static final int CurrentVersion = 2;
    @Id
    private UUID id = UUID.randomUUID();
    @Setter
    @Nullable
    private String name;
    @Setter
    @Nullable
    private String displayName;
    @Setter
    @Nullable
    @ManyToOne
    private User owner;
    @ElementCollection(fetch = FetchType.EAGER)
    private Map<User, @NotNull Integer> permissions;
    private @Nullable Integer version = CurrentVersion;

    public String getBestName() {
        return Optional.ofNullable(displayName)
                .or(() -> Optional.ofNullable(name))
                .or(() -> Optional.ofNullable(owner)
                        .map(AbstractEntity::getBestName)
                        .map(n -> n + "s " + getClass().getSimpleName()))
                .filter(Predicate.not("null"::equals))
                .orElseGet(id::toString);
    }

    public boolean isUser() {
        return owner == null;
    }

    public final boolean hasAnyPermission(@NotNull User user) {
        return hasPermission(user, Permission.Any);
    }

    public boolean hasPermission(@NotNull User user, AbstractEntity.Permission... permissions) {
        Constraint.Length.min(1, permissions, "permissions").run();
        final var mask = this.permissions.getOrDefault(user, 0);
        return (owner != null && user.getId().equals(owner.getId()))
                || Arrays.stream(permissions).allMatch(flag -> Bitmask.isFlagSet(mask, flag))
                || Utils.SuperAdmins.contains(user.getId());
    }

    public final SupplierX<AbstractEntity> verifyPermission(final @NotNull User user, final AbstractEntity.Permission... permissions) {
        return () -> hasPermission(user, permissions) ? this : null;
    }

    public final void requirePermission(final @NotNull User user, final AbstractEntity.Permission... permissions) {
        verifyPermission(user, permissions).orElseThrow(()->new InsufficientPermissionsException(user,this,permissions));
    }

    @Override
    public String getAlternateName() {
        return Optional.ofNullable(getDisplayName()).orElseGet(this::getName);
    }

    public String toString() {
        return getClass().getSimpleName() + ' ' + getBestName();
    }

    public final boolean equals(Object other) {
        return other instanceof AbstractEntity && id.equals(((AbstractEntity) other).id);
    }

    public final int hashCode() {
        return id.hashCode();
    }

    // todo: ungroup permissions?
    public enum Permission implements BitmaskAttribute<Permission> {
        None(0),
        Status,
        Whitelist,
        Kick,
        Mute,
        Ban,
        Start,
        Stop,
        Backup,
        Update,
        Maintenance,
        Enable,
        Console,
        Execute,
        Files,
        ForceOP,
        TriggerCron,

        CreateAgent,
        CreateDiscordBot,
        CreateServer,
        CreateSh,
        Modify,

        View(0x0100_0000, Status),
        Moderate(0x0200_0000, Whitelist, Kick, Mute),
        Manage(0x0400_0000, Ban, Start, Stop, Backup, Update, Maintenance, Enable),
        Administrate(0x0800_0000, Console, Execute, Files, ForceOP, TriggerCron),
        Delete(0x1000_0000),

        ManageUsers(0x2000_0000),

        Any(0xffff_ffff);

        static {
            log.info("Registered permissions up to "+Delete);
        }

        private final int value;

        Permission() {
            this(Bitmask.nextFlag());
        }

        Permission(int base, Permission... members) {this.value = base | Bitmask.combine(members);}

        @Override
        public @NotNull Integer getValue() {
            return value;
        }

        @Override
        public String toString() {
            return "%s(0x%x)".formatted(name(),value);
        }
    }

    public interface Repo<T extends AbstractEntity> extends CrudRepository<T, UUID> {
        @Query("SELECT e FROM #{#entityName} e WHERE e.version = null OR e.version <= :version")
        Iterable<T> findMigrationCandidates(@Param("version") int fromVersion);
    }
}
