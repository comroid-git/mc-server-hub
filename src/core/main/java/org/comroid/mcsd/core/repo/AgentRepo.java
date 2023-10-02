package org.comroid.mcsd.core.repo;

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
    @Query("UPDATE Agent a SET a.baseUrl = :baseUrl WHERE a.id = :id")
    void setBaseUrl(@Param("id") UUID id, @Param("baseUrl") String baseUrl);

    @Query("select case when a.token = :token then true else false end" +
            " from Agent a where a.id = :id")
    boolean isTokenValid(@Param("id") UUID id, @Param("token") String token);
}
