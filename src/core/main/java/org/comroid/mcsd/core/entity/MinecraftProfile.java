package org.comroid.mcsd.core.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.SneakyThrows;
import lombok.Value;
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

    @Value
    @Converter
    public static class UuidConverter implements AttributeConverter<UUID, String> {
        @Override
        public String convertToDatabaseColumn(UUID id) {
            return id.toString();
        }

        @Override
        public UUID convertToEntityAttribute(String str) {
            if (!str.contains("-")) {
                var sb = new StringBuilder(str);
                sb.insert(8, "-").insert(13, "-").insert(18, "-").insert(23, "-");
                str = sb.toString();
            }
            return UUID.fromString(str);
        }
    }
}
