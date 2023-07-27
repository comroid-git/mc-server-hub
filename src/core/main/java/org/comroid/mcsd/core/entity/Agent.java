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
import org.comroid.mcsd.connector.HubConnector;
import org.comroid.mcsd.connector.gateway.GatewayConnectionInfo;
import org.jetbrains.annotations.NotNull;

import java.io.PrintStream;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.UUID;

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
        oe.redirectToSystem();
        out = oe.output().require(PrintStream::new);
        err = oe.error().require(PrintStream::new);
        cmd = new Command.Manager(this);
    }

    public Agent setToken(String token) {
        this.token = token;
        return this;
    }

    @Command
    public String help() {
        return "Commands: help status start stop shutdown";
    }

    @Override
    public void handleResponse(String text) {
        out.println(text);
        out.flush();
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
