package org.comroid.mcsd.core.entity;

import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.PostLoad;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.comroid.mcsd.connector.HubConnector;
import org.comroid.mcsd.connector.gateway.GatewayConnectionInfo;
import org.comroid.mcsd.util.JacksonObjectConverter;

import java.util.UUID;

@Data
@Entity
@EqualsAndHashCode(callSuper = true)
public class ServerAgent extends AbstractEntity {
    UUID target;
    HubConnector.Role role;
    @Convert(converter = JacksonObjectConverter.class)
    GatewayConnectionInfo connectionInfo;

    @PostLoad
    public void init() {
        connectionInfo = new GatewayConnectionInfo(role, target);
    }
}
