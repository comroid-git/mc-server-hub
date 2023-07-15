package org.comroid.mcsd.repo;

import jakarta.persistence.Table;
import org.comroid.mcsd.entity.DiscordBotInfo;
import org.comroid.mcsd.entity.ShConnection;
import org.springframework.data.repository.CrudRepository;

import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

@Table(name = "discord_bots")
public interface DiscordBotRepo extends CrudRepository<DiscordBotInfo, UUID> {
}
