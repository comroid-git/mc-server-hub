package org.comroid.mcsd.core.repo.system;

import jakarta.transaction.Transactional;
import org.comroid.mcsd.core.entity.AbstractEntity;
import org.comroid.mcsd.core.entity.system.Agent;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface AgentRepo extends AbstractEntity.Repo<Agent> {
    @Deprecated
    @Query("SELECT a FROM Agent a" +
            " JOIN ShConnection sh" +
            " JOIN Server s ON s.id = :serverId" +
            " WHERE sh.id = a.target AND sh.id = s.shConnection.id")
    Optional<Agent> findForServer(@Param("serverId") UUID serverId);

    @Modifying
    @Transactional
    @Query("UPDATE Agent a SET a.baseUrl = :baseUrl WHERE a.id = :id")
    void setBaseUrl(@Param("id") UUID id, @Param("baseUrl") String baseUrl);

    Optional<Agent> getByIdAndToken(@Param("id") UUID id, @Param("token") String token);
}
