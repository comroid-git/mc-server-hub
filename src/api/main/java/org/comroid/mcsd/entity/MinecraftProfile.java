package org.comroid.mcsd.entity;

import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import lombok.Data;
import lombok.SneakyThrows;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.UUID;

@Data
@Entity
public class MinecraftProfile {
    @Id
    private String id;
    private String name;
    private long discordId;
    private @Nullable String verification;
    @ElementCollection
    private Map<UUID, String> serverLogins;

    public boolean isVerified() {
        return verification == null;
    }

    public UUID getUUID() {
        var sb = new StringBuilder(id);
        sb.insert(8, "-").insert(13, "-").insert(18, "-").insert(23, "-");
        return UUID.fromString(sb.toString());
    }

    @SneakyThrows
    public String getNameMcURL() {
        return "https://namemc.com/profile/" + getUUID();
    }

    @SneakyThrows
    public String getHeadURL() {
        return "https://mc-heads.net/avatar/" + getUUID();
    }

    @SneakyThrows
    public String getIsoBodyURL() {
        return "https://mc-heads.net/body/" + getUUID();
    }
}
