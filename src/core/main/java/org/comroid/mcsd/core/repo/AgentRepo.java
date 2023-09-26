package org.comroid.mcsd.core.repo;

import jakarta.persistence.Table;
import jakarta.transaction.Transactional;
import org.comroid.mcsd.core.entity.Agent;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface AgentRepo extends CrudRepository<Agent, UUID> {
    @Modifying
    @Transactional
    @Query("UPDATE Agent a SET a.hostname = :hostname WHERE a.id = :id")
    void setHostname(@Param("id") UUID id, @Param("hostname") String hostname);
}
