package org.comroid.mcsd.core.entity;


import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.util.UUID;

@Data
@Slf4j
@Entity
public abstract class AbstractEntity {
    @Id
    private UUID id = UUID.randomUUID();
}
