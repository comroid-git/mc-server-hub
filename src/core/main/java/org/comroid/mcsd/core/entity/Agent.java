package org.comroid.mcsd.core.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
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
@Table(name = "agent")
public class Agent extends AbstractEntity implements Command.Handler {
    public static final int TokenLength = 64;
    private @Setter @Basic UUID target;
    private @Setter @Basic HubConnector.Role role;
    private @JsonIgnore @Basic String token = generateToken();
    public final @JsonIgnore @Transient DelegateStream.IO oe;
    public final @JsonIgnore @Transient PrintStream out;
    public final @JsonIgnore @Transient PrintStream err;
    public final @JsonIgnore @Transient Command.Manager cmd;

    {
        oe = new DelegateStream.IO(DelegateStream.Capability.Output, DelegateStream.Capability.Error);
        out = oe.output().require(PrintStream::new);
        err = oe.error().require(PrintStream::new);
        cmd = new Command.Manager(this);

        if (Debug.isDebug())
            oe.redirectToLogger(Log.get("Agent-"+getId()));
    }

    public Agent setToken(String token) {
        this.token = token;
        return this;
    }

    @Override
    public void handleResponse(String text) {
        out.println(text);
        out.flush();
    }

    @Override
    public void handleError(Command.Error error) {
        new Exception("An internal exception occurred when executing %s %s".formatted(
                error.getCommand().getName(),
                error.getArgs() == null ? "" : Arrays.toString(error.getArgs())), error)
                .printStackTrace(err);
    }

    public static String generateToken() {
        var randomBytes = new byte[TokenLength];
        new SecureRandom().nextBytes(randomBytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
    }
}
