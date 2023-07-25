package org.comroid.mcsd.core.repo;

import jakarta.persistence.Table;
import org.comroid.mcsd.core.entity.Agent;
import org.springframework.data.repository.CrudRepository;

import java.util.UUID;

public interface AgentRepo extends CrudRepository<Agent, UUID> {
}
