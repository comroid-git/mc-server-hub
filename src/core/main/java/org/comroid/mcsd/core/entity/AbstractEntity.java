package org.comroid.mcsd.core.entity;


import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import jakarta.persistence.*;
import lombok.Data;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.comroid.api.BitmaskAttribute;
import org.comroid.api.IntegerAttribute;
import org.comroid.api.Named;
import org.comroid.api.Rewrapper;
import org.comroid.util.Bitmask;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Map;
import java.util.UUID;

@Data
@Slf4j
@Entity
@Table(name = "entity")
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

    public final boolean hasPermission(@NotNull User user, AbstractEntity.Permission... permissions) {
        final var mask = this.permissions.getOrDefault(user.getId(), 0);
        return Arrays.stream(permissions).allMatch(flag -> Bitmask.isFlagSet(mask, flag));
    }

    public final Rewrapper<Object> verifyPermission(final @NotNull User user, final AbstractEntity.Permission... permissions) {
        return () -> hasPermission(user, permissions) ? new Object() : null;
    }

    public final boolean equals(Object other) {
        return other instanceof AbstractEntity && id.equals(((AbstractEntity) other).id);
    }

    public final int hashCode() {
        return id.hashCode();
    }

    public enum Permission implements BitmaskAttribute<Permission> {
        View,
        Manage,
        Administrate,
        Delete
    }
}
