package org.comroid.mcsd.core.repo;

import jakarta.persistence.Table;
import org.comroid.mcsd.core.entity.DiscordBotInfo;
import org.comroid.mcsd.core.entity.ServerAgent;
import org.springframework.data.repository.CrudRepository;

import java.util.UUID;

@Table(name = "agents")
public interface AgentRepo extends CrudRepository<ServerAgent, UUID> {
}
