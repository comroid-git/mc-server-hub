package org.comroid.mcsd.core.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
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

@Data
@Entity
@Table(name = "agent")
@EqualsAndHashCode(callSuper = true)
public class Agent extends AbstractEntity implements Command.Handler {
    public static final int TokenLength = 64;
    @Basic UUID target;
    @Basic HubConnector.Role role;
    @Basic String token = generateToken();
    public final @Transient DelegateStream.IO oe = new DelegateStream.IO(DelegateStream.Capability.Output, DelegateStream.Capability.Error);
    public final @Transient PrintStream out = oe.output().require(PrintStream::new);
    public final @Transient PrintStream err = oe.error().require(PrintStream::new);
    public final @Transient @Delegate Command.Manager cmd = new Command.Manager(this);

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
