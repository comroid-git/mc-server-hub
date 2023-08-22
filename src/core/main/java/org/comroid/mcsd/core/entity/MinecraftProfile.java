package org.comroid.mcsd.core.entity;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.type.SimpleType;
import com.fasterxml.jackson.databind.type.TypeFactory;
import jakarta.persistence.*;
import lombok.*;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.UUID;

@Getter
@Setter
@Entity
public class MinecraftProfile extends AbstractEntity {
    private @Nullable String verification;
    @ElementCollection(fetch = FetchType.EAGER)
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
    public static class UuidConverter implements com.fasterxml.jackson.databind.util.Converter<String, UUID>, AttributeConverter<UUID, String> {
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

        @Override
        public UUID convert(String value) {
            return convertToEntityAttribute(value);
        }

        @Override
        public JavaType getInputType(TypeFactory typeFactory) {
            return SimpleType.constructUnsafe(String.class);
        }

        @Override
        public JavaType getOutputType(TypeFactory typeFactory) {
            return SimpleType.constructUnsafe(UUID.class);
        }
    }
}
