package org.comroid.mcsd.core.entity;


import jakarta.persistence.*;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.util.UUID;

@Data
@Slf4j
@Entity
@Table(name = "entity")
public abstract class AbstractEntity {
    @Id
    @Convert(converter = MinecraftProfile.UuidConverter.class)
    private UUID id = UUID.randomUUID();
}
