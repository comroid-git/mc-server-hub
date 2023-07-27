package org.comroid.mcsd.core.repo;

import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.transaction.Transactional;
import org.comroid.mcsd.api.model.Status;
import org.comroid.mcsd.core.entity.Agent;
import org.comroid.mcsd.core.entity.Server;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface ServerRepo extends CrudRepository<Server, UUID> {
    @Query("SELECT s FROM Server s JOIN s.userPermissions p WHERE KEY(p) = :userId AND VALUE(p) > 0")
    Iterable<Server> findByPermittedUser(@Param("userId") UUID userId);

    @Query("SELECT s FROM Server s" +
            " JOIN Agent a ON a.id = :agentId" +
            " JOIN ShConnection sh ON sh.id = a.target OR sh.id = s.shConnection")
    // todo this shit wont work in spring but in mysql its totally fine...
    Iterable<Server> findAllForAgent(@Param("agentId") UUID agentId);

    @Query("SELECT s FROM Server s" +
            " JOIN Agent a ON a.id = :agentId" +
            " JOIN ShConnection sh ON sh.id = a.target OR sh.id = s.shConnection" +
            " WHERE s.name = :name")
    Optional<Server> findByAgentAndName(@Param("agentId") UUID agentId, @Param("name") String name);

    @Modifying
    @Transactional
    @Query("UPDATE Server s SET s.enabled = :enabled WHERE s.id = :srvId")
    void setEnabled(@Param("srvId") UUID srvId, @Param("enabled") boolean enabled);

    @Modifying
    @Transactional
    @Query("UPDATE Server s SET s.maintenance = :maintenance WHERE s.id = :srvId")
    void setMaintenance(@Param("srvId") UUID srvId, @Param("maintenance") boolean maintenance);

    @Modifying
    @Transactional
    @Query("UPDATE Server s SET s.status = :status WHERE s.id = :srvId")
    void setStatus(@Param("srvId") UUID srvId, @Param("status") Status status);

    @Modifying
    @Transactional
    @Query("UPDATE Server s SET s.lastBackup = :time WHERE s.id = :srvId")
    void bumpLastBackup(@Param("srvId") UUID srvId, @Param("time") Instant time);

    @Modifying
    @Transactional
    @Query("UPDATE Server s SET s.lastUpdate = :time WHERE s.id = :srvId")
    void bumpLastUpdate(@Param("srvId") UUID srvId, @Param("time") Instant time);

    default void bumpLastBackup(Server srv) {
        bumpLastBackup(srv.getId(), Instant.now());
    }

    default void bumpLastUpdate(Server srv) {
        bumpLastUpdate(srv.getId(), Instant.now());
    }
}
