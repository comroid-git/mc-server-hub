package org.comroid.mcsd.core.entity;

import jakarta.persistence.Basic;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.SneakyThrows;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.UUID;

@Data
@Entity
@EqualsAndHashCode(callSuper = true)
public class MinecraftProfile extends AbstractEntity {
    private String name;
    private long discordId;
    private @Nullable String verification;
    @ElementCollection
    private Map<UUID, String> serverLogins;

    public boolean isVerified() {
        return verification == null;
    }

    @Basic
    public UUID getId() {
        var sb = new StringBuilder();
        sb.insert(8, "-").insert(13, "-").insert(18, "-").insert(23, "-");
        return UUID.fromString(sb.toString());
    }

    @SneakyThrows
    public String getNameMcURL() {
        return "https://namemc.com/profile/" + getId();
    }

    @SneakyThrows
    public String getHeadURL() {
        return "https://mc-heads.net/avatar/" + getId();
    }

    @SneakyThrows
    public String getIsoBodyURL() {
        return "https://mc-heads.net/body/" + getId();
    }
}
