package org.comroid.mcsd.core.repo;

import org.comroid.mcsd.core.entity.ServerUptimeEntry;
import org.springframework.data.repository.CrudRepository;

import java.util.UUID;

public interface ServerUptimeRepo extends CrudRepository<ServerUptimeEntry, UUID> {
}
