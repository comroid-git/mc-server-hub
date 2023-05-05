package org.comroid.mcsd.web.repo;

import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import org.comroid.mcsd.web.entity.Server;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.UUID;

@Table(name = "servers", uniqueConstraints = {@UniqueConstraint(columnNames = {"port", "rConPort"})})
public interface ServerRepo extends CrudRepository<Server, UUID> {
    @Query("SELECT DISTINCT s FROM Server s JOIN s.userPermissions p WHERE KEY(p) = :userId AND VALUE(p) > 0")
    Iterable<Server> findByPermittedUser(@Param("userId") UUID userId);

    @Query("UPDATE Server s SET s.lastBackup = :time WHERE s.id = :srvId")
    void bumpLastBackup(@Param("srv") Server srv, @Param("time") Instant time);

    default void bumpLastBackup(@Param("srv") Server srv) {
        bumpLastBackup(srv, Instant.now());
    }
}
