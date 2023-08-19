package org.comroid.mcsd.core.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.Delegate;
import org.comroid.api.Command;
import org.comroid.api.DelegateStream;
import org.comroid.api.Polyfill;
import org.comroid.api.info.Log;
import org.comroid.mcsd.connector.HubConnector;
import org.comroid.mcsd.connector.gateway.GatewayConnectionInfo;
import org.comroid.mcsd.core.repo.ServerRepo;
import org.comroid.mcsd.core.util.ApplicationContextProvider;
import org.comroid.util.Debug;
import org.jetbrains.annotations.NotNull;

import java.io.PrintStream;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.comroid.mcsd.core.util.ApplicationContextProvider.bean;

@Getter
@Entity
public class Agent extends AbstractEntity {
    public static final int TokenLength = 64;
    private @Setter @ManyToOne AbstractEntity target;
    private @Setter @Basic HubConnector.Role role;
    private @JsonIgnore @Basic @ToString.Exclude String token = generateToken();

    public Agent setToken(String token) {
        this.token = token;
        return this;
    }
    public static String generateToken() {
        var randomBytes = new byte[TokenLength];
        new SecureRandom().nextBytes(randomBytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
    }
}
