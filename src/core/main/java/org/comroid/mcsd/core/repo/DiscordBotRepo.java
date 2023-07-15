package org.comroid.mcsd.core.repo;

import jakarta.persistence.Table;
import org.comroid.mcsd.core.entity.DiscordBotInfo;
import org.springframework.data.repository.CrudRepository;

import java.util.UUID;

@Table(name = "discord_bots")
public interface DiscordBotRepo extends CrudRepository<DiscordBotInfo, UUID> {
}
