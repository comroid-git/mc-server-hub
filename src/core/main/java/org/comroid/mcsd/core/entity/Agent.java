package org.comroid.mcsd.core.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;
import org.comroid.util.Token;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

import static org.comroid.mcsd.core.util.ApplicationContextProvider.bean;

@Getter
@Entity
public class Agent extends AbstractEntity {
    public static final int TokenLength = 64;
    private @NotNull @Setter @Basic UUID target;
    private @Nullable @Setter @Basic String baseUrl;
    private @Nullable @Setter @Basic String hubBaseUrl;
    private @Nullable @Getter(onMethod = @__(@JsonIgnore)) @Basic @ToString.Exclude String token = Token.random(32, true);
}
