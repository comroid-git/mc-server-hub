package org.comroid.mcsd.core.repo;

import org.comroid.mcsd.core.entity.MinecraftProfile;
import org.comroid.util.Token;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.web.client.RestTemplate;

import java.util.Optional;
import java.util.UUID;

public interface MinecraftProfileRepo extends CrudRepository<MinecraftProfile, UUID> {
    Optional<MinecraftProfile> findByName(String name);
    Optional<MinecraftProfile> findByVerification(String verification);
    @Query("SELECT u.minecraft FROM User u" +
            " JOIN MinecraftProfile mc ON u.minecraft.id = mc.id" +
            " WHERE u.discordId = :discordId")
    Optional<MinecraftProfile> findByDiscordId(@Param("discordId") long discordId);

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
