package org.comroid.mcsd.core.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.comroid.mcsd.connector.HubConnector;
import org.comroid.mcsd.connector.gateway.GatewayConnectionInfo;
import org.jetbrains.annotations.NotNull;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

@Data
@Entity
@EqualsAndHashCode(callSuper = true)
public class Agent extends AbstractEntity {
    public static final int TokenLength = 64;
    @Basic UUID target;
    @Basic HubConnector.Role role;
    @Basic String token = generateToken();
    @ElementCollection
    List<UUID> servers;

    public static String generateToken() {
        var randomBytes = new byte[TokenLength];
        new SecureRandom().nextBytes(randomBytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
    }
}
