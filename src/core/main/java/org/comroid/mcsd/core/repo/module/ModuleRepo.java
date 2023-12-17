package org.comroid.mcsd.core.repo.module;

import org.comroid.mcsd.core.entity.module.ModulePrototype;
import org.springframework.data.repository.CrudRepository;

import java.util.Optional;
import java.util.UUID;

public interface ModuleRepo extends CrudRepository<ModulePrototype, UUID> {
    Iterable<ModulePrototype> findAllByOwnerId(UUID ownerId);
}
