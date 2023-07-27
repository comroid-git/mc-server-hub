package org.comroid.mcsd.core.repo;

import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import org.comroid.mcsd.core.entity.Server;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.UUID;

public interface ServerRepo extends CrudRepository<Server, UUID> {
    @Query("SELECT DISTINCT s FROM Server s JOIN s.userPermissions p WHERE KEY(p) = :userId AND VALUE(p) > 0")
    Iterable<Server> findByPermittedUser(@Param("userId") UUID userId);

    @Query("SELECT DISTINCT s FROM Server s JOIN ShConnection sh JOIN Agent a" +
            " WHERE s.shConnection = sh.id AND a.role = 0 AND a.target = sh.id AND a.id = :agentId")
    Iterable<Server> findByAgentId(@Param("agentId") UUID agentId);

    @Query("UPDATE Server s SET s.lastBackup = :time WHERE s.id = :srvId")
    void bumpLastBackup(@Param("srv") Server srv, @Param("time") Instant time);

    @Query("UPDATE Server s SET s.lastUpdate = :time WHERE s.id = :srvId")
    void bumpLastUpdate(@Param("srv") Server srv, @Param("time") Instant time);

    default void bumpLastBackup(Server srv) {
        bumpLastBackup(srv, Instant.now());
    }

    default void bumpLastUpdate(Server srv) {
        bumpLastUpdate(srv, Instant.now());
    }
}
