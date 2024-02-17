package org.comroid.mcsd.core.entity.system;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Basic;
import jakarta.persistence.Entity;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.comroid.api.func.util.Streams;
import org.comroid.api.net.Token;
import org.comroid.mcsd.core.entity.AbstractEntity;
import org.comroid.mcsd.core.repo.server.ServerRepo;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;
import java.util.stream.Stream;

import static org.comroid.mcsd.core.util.ApplicationContextProvider.bean;

@Getter
@Entity
public class Agent extends AbstractEntity {
    public static final int TokenLength = 64;
    private @Deprecated @NotNull @Setter @Basic UUID target;
    private @Nullable @Setter @Basic String baseUrl;
    //private @Nullable @Setter @Basic String hubBaseUrl;
    private @Nullable @Getter(onMethod = @__(@JsonIgnore)) @Basic @ToString.Exclude String token = Token.random(32, true);

    @Override
    public Stream<? extends AbstractEntity> managedChildren() {
        return Stream.concat(super.managedChildren(), Streams.of(bean(ServerRepo.class).findAllForAgent(getId())));
    }
}
