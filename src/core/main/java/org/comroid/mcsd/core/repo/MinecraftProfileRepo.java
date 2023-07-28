package org.comroid.mcsd.core.repo;

import jakarta.persistence.Table;
import org.comroid.mcsd.core.entity.MinecraftProfile;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.web.client.RestTemplate;

import java.util.Base64;
import java.util.Optional;
import java.util.UUID;

public interface MinecraftProfileRepo extends CrudRepository<MinecraftProfile, UUID> {
    Optional<MinecraftProfile> findByName(String name);

    @Query("SELECT DISTINCT mc FROM MinecraftProfile mc JOIN User u WHERE u.discordId = :userId")
    Optional<MinecraftProfile> findByDiscordId(long userId);

    default MinecraftProfile get(String username) {
        return findByName(username).orElseGet(() -> {
            var profile = new RestTemplate()
                    .getForObject("https://api.mojang.com/users/profiles/minecraft/" + username,
                            MinecraftProfile.class);
            assert profile != null : "No profile found for username " + username;
            profile.setVerification(Base64.getEncoder().encodeToString(UUID.randomUUID().toString().getBytes()));
            save(profile);
            return profile;
        });
    }
}
