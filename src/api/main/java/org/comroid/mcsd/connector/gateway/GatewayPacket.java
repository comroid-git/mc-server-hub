package org.comroid.mcsd.connector.gateway;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Builder;
import lombok.Data;
import lombok.SneakyThrows;
import org.comroid.api.IntegerAttribute;
import org.comroid.api.StringSerializable;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Data
@Builder(toBuilder = true, builderClassName = "Builder")
public class GatewayPacket implements StringSerializable {
    static final Duration HeartbeatTimeout = Duration.ofMinutes(1);
    static final ObjectMapper mapper = new ObjectMapper();
    @lombok.Builder.Default Instant timestamp = Instant.now();
    UUID connectionId;
    Type type;
    @Nullable String topic;
    @Nullable String data;
    @lombok.Builder.Default boolean received = false;

    public boolean isHeartbeatValid() {
        return type == Type.Heartbeat && timestamp.plus(HeartbeatTimeout).isAfter(Instant.now());
    }

    @SneakyThrows
    public <T> T parse(Class<? extends T> type) {
        return mapper.readValue(data, type);
    }

    @Override
    @SneakyThrows
    public String toSerializedString() {
        return serialize(this);
    }

    @SneakyThrows
    static String serialize(Object data) {
        return mapper.writeValueAsString(data);
    }

    @Deprecated
    public enum Type implements IntegerAttribute {
        Heartbeat,
        Close,

        Connect,
        Data
    }
}
