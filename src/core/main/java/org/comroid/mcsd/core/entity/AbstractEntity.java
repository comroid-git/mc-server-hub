package org.comroid.mcsd.core.entity;


import jakarta.persistence.*;
import lombok.Data;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.comroid.api.BitmaskAttribute;
import org.comroid.api.Named;
import org.comroid.api.Polyfill;
import org.comroid.api.Rewrapper;
import org.comroid.mcsd.core.exception.InsufficientPermissionsException;
import org.comroid.mcsd.util.Utils;
import org.comroid.util.Bitmask;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Data
@Slf4j
@Entity
@Inheritance(strategy = InheritanceType.JOINED)
public abstract class AbstractEntity implements Named {
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

    public boolean isUser() {
        return owner == null;
    }

    public final boolean hasPermission(@NotNull User user, AbstractEntity.Permission... permissions) {
        final var mask = this.permissions.getOrDefault(user, 0);
        return (owner != null && user.getId().equals(owner.getId()))
                || Arrays.stream(permissions).allMatch(flag -> Bitmask.isFlagSet(mask, flag))
                || Utils.SuperAdmins.contains(user.getId());
    }

    public final Rewrapper<AbstractEntity> verifyPermission(final @NotNull User user, final AbstractEntity.Permission... permissions) {
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
        return getClass().getSimpleName() + ' ' + getName();
    }

    public final boolean equals(Object other) {
        return other instanceof AbstractEntity && id.equals(((AbstractEntity) other).id);
    }

    public final int hashCode() {
        return id.hashCode();
    }

    // todo: ungroup permissions?
    public enum Permission implements BitmaskAttribute<Permission> {
        View(1), // Status
        Moderate(3), // view + Whitelist, Kick, Mute
        Manage(7), // moderate + Ban, Start, Stop, Backup, Update, Maintenance, Enable
        Administrate(15), // manage + Console, Execute, Enable, Files, ForceOP, TriggerCron
        Delete(16); // Wipe

        private final int value;

        Permission(int value) {
            this.value = value;
        }

        @Override
        public @NotNull Integer getValue() {
            return value;
        }
    }
}
