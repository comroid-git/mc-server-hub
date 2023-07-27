package org.comroid.mcsd.core.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Delegate;
import org.comroid.api.Command;
import org.comroid.api.DelegateStream;
import org.comroid.mcsd.connector.HubConnector;
import org.comroid.mcsd.connector.gateway.GatewayConnectionInfo;
import org.jetbrains.annotations.NotNull;

import java.io.PrintStream;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "agent")
public class Agent extends AbstractEntity implements Command.Handler {
    public static final int TokenLength = 64;
    private @Basic UUID target;
    private @Basic HubConnector.Role role;
    private @Basic String token = generateToken();
    public final @Transient DelegateStream.IO oe;
    public final @Transient PrintStream out;
    public final @Transient PrintStream err;
    public final @Transient @Delegate Command.Manager cmd;

    {
        oe = new DelegateStream.IO(DelegateStream.Capability.Output, DelegateStream.Capability.Error);
        oe.redirectToSystem();
        out = oe.output().require(PrintStream::new);
        err = oe.error().require(PrintStream::new);
        cmd = new Command.Manager(this);
    }

    @Command
    public String help() {
        return "Commands: help status start stop shutdown";
    }

    @Override
    public void handleResponse(String text) {
        out.println(text);
    }

    @Override
    public void handleError(Throwable t, Command.Delegate cmd, String[] args) {
        new Exception("An internal exception occurred when executing %s %s".formatted(cmd.getName(), Arrays.toString(args)), t)
                .printStackTrace(err);
    }

    public static String generateToken() {
        var randomBytes = new byte[TokenLength];
        new SecureRandom().nextBytes(randomBytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
    }
}
