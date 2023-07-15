package org.comroid.mcsd.connector.gateway;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.*;
import lombok.experimental.NonFinal;
import org.comroid.api.IntegerAttribute;
import org.comroid.api.StringSerializable;
import org.comroid.api.UUIDContainer;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.UUID;

@Value
@RequiredArgsConstructor
public class GatewayPacket implements StringSerializable {
    static final ObjectMapper mapper = new ObjectMapper();
    Instant timestamp = Instant.now();
    UUID connection;
    Type type;
    @NonFinal @Setter String topic;
    @Nullable String data;
    @NonFinal @Setter boolean received = false;

    @SneakyThrows
    public <T> T parse(Class<? extends T> type) {
        return mapper.readValue(data, type);
    }

    @Override
    @SneakyThrows
    public String toSerializedString() {
        return mapper.writeValueAsString(this);
    }

    public enum Type implements IntegerAttribute {
        Heartbeat,

        Connect,
        Data
    }

    public interface Creator extends UUIDContainer {
        default GatewayPacket empty(Type type) {
            return new GatewayPacket(getUuid(), type, null);
        }

        default GatewayPacket connect() {
            return empty(Type.Connect);
        }

        @SneakyThrows
        default GatewayPacket data(Object data) {
            return new GatewayPacket(getUuid(), Type.Data, mapper.writeValueAsString(data));
        }
    }
}
