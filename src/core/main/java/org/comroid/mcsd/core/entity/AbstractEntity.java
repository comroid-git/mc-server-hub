package org.comroid.mcsd.core.entity;


import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import jakarta.persistence.*;
import lombok.Data;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.comroid.api.BitmaskAttribute;
import org.comroid.api.Named;
import org.comroid.api.Rewrapper;
import org.comroid.mcsd.core.model.IUser;
import org.comroid.mcsd.util.Utils;
import org.comroid.util.Bitmask;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

@Data
@Slf4j
@Entity
@Inheritance(strategy = InheritanceType.JOINED)
public abstract class AbstractEntity implements Named {
    @Id
    @Convert(converter = MinecraftProfile.UuidConverter.class)
    @JsonDeserialize(converter = MinecraftProfile.UuidConverter.class)
    private UUID id = UUID.randomUUID();
    @Setter
    @Nullable
    private String name;
    @Setter
    @Nullable
    @ManyToOne
    private User owner;
    @ElementCollection(fetch = FetchType.EAGER)
    private Map<UUID, @NotNull Integer> permissions;

    public final boolean hasPermission(@NotNull IUser user, AbstractEntity.Permission... permissions) {
        if (owner != null && user.getUserId().equals(owner.getId())
                || Arrays.asList(Utils.SuperAdmins).contains(user.getUserId()))
            return true;
        final var mask = this.permissions.getOrDefault(user.getUserId(), 0);
        return Arrays.stream(permissions).allMatch(flag -> Bitmask.isFlagSet(mask, flag));
    }

    public final Rewrapper<AbstractEntity> verifyPermission(final @NotNull IUser user, final AbstractEntity.Permission... permissions) {
        return () -> hasPermission(user, permissions) ? this : null;
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
