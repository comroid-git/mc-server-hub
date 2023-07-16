package org.comroid.mcsd.core.entity;


import jakarta.persistence.Basic;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.comroid.mcsd.connector.HubConnector;
import org.comroid.mcsd.connector.gateway.GatewayConnectionInfo;
import org.comroid.mcsd.util.JacksonObjectConverter;

@Data
@Entity
@EqualsAndHashCode(callSuper = true)
public class ServerAgent extends AbstractEntity {
    @Basic
    HubConnector.Role role;
    @Convert(converter = JacksonObjectConverter.class)
    GatewayConnectionInfo connectionInfo;
}
