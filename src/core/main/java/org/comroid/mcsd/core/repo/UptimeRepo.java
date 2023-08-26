package org.comroid.mcsd.core.repo;

import org.comroid.mcsd.core.entity.ServerUptimeEntry;
import org.springframework.data.repository.CrudRepository;

import java.util.UUID;

public interface UptimeRepo extends CrudRepository<ServerUptimeEntry, UUID> {
}
