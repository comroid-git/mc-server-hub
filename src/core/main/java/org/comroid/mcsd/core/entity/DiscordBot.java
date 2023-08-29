package org.comroid.mcsd.core.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Basic;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.jetbrains.annotations.Nullable;

@Getter
@Setter
@Entity
public class DiscordBot extends AbstractEntity {
    @Basic @ToString.Exclude @Getter(onMethod = @__(@JsonIgnore))
    private String token;
    private int shardCount = 1;
}
