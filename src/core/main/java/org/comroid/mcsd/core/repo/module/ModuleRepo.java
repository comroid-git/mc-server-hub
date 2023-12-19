package org.comroid.mcsd.core.repo.module;

import org.comroid.mcsd.core.entity.AbstractEntity;
import org.comroid.mcsd.core.entity.module.ModulePrototype;
import org.springframework.data.repository.CrudRepository;

import java.util.Optional;
import java.util.UUID;

public interface ModuleRepo<T extends ModulePrototype> extends AbstractEntity.Repo<T> {
    Iterable<T> findAllByServerId(UUID serverId);
    Optional<T> findByServerIdAndDtype(UUID serverId, String dtype);
}
