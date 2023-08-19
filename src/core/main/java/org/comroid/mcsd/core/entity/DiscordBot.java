package org.comroid.mcsd.core.entity;

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
@Table(name = "discord_bot")
public class DiscordBot extends AbstractEntity {
    @Basic @ToString.Exclude
    private String token;
    private int shardCount = 1;
}
