package org.comroid.mcsd.core.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.Delegate;
import org.comroid.api.Command;
import org.comroid.api.DelegateStream;
import org.comroid.api.Polyfill;
import org.comroid.api.info.Log;
import org.comroid.mcsd.core.repo.ServerRepo;
import org.comroid.mcsd.core.util.ApplicationContextProvider;
import org.comroid.util.Debug;
import org.comroid.util.Token;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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
    private @NotNull @Setter @Basic UUID target;
    private @Nullable @Setter @Basic String hostname;
    private @Nullable @Getter(onMethod = @__(@JsonIgnore)) @Basic @ToString.Exclude String token = Token.random(32, true);
}
