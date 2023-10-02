package org.comroid.mcsd.core.repo;

import org.comroid.mcsd.core.entity.Server;
import org.comroid.mcsd.core.entity.ServerUptimeEntry;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface ServerUptimeRepo extends CrudRepository<ServerUptimeEntry, UUID> {
    @Query("SELECT e FROM ServerUptimeEntry e" +
            " WHERE e.server.id = :id AND e.timestamp < :time" +
            " ORDER BY e.timestamp")
    Iterable<ServerUptimeEntry> since(@Param("id") UUID id, @Param("time") Instant time);
}
