package org.comroid.mcsd.core.repo.system;

import org.comroid.mcsd.core.entity.system.DiscordBot;
import org.springframework.data.repository.CrudRepository;

import java.util.UUID;

public interface DiscordBotRepo extends CrudRepository<DiscordBot, UUID> {
}
