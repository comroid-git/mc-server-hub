package org.comroid.mcsd.core.repo;

import org.comroid.mcsd.core.entity.DiscordBot;
import org.springframework.data.repository.CrudRepository;

import java.util.UUID;

public interface DiscordBotRepo extends CrudRepository<DiscordBot, UUID> {
}
