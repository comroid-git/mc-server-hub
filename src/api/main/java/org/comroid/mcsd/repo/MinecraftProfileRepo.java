package org.comroid.mcsd.repo;

import jakarta.persistence.Table;
import org.comroid.mcsd.entity.DiscordBotInfo;
import org.comroid.mcsd.entity.MinecraftProfile;
import org.springframework.data.repository.CrudRepository;
import org.springframework.web.client.RestTemplate;

import java.util.Optional;
import java.util.UUID;

@Table(name = "minecraft_users")
public interface MinecraftProfileRepo extends CrudRepository<MinecraftProfile, UUID> {
    Optional<MinecraftProfile> findByName(String name);
    Optional<MinecraftProfile> findByDiscordId(long userId);

    default MinecraftProfile get(String username) {
        return findByName(username).orElseGet(() -> {
            var profile = new RestTemplate()
                    .getForObject("https://api.mojang.com/users/profiles/minecraft/" + username,
                            MinecraftProfile.class);
            assert profile != null : "No profile found for username " + username;
            save(profile);
            return profile;
        });
    }
}
