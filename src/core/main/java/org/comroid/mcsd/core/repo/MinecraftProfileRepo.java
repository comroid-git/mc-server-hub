package org.comroid.mcsd.core.repo;

import org.comroid.mcsd.core.entity.MinecraftProfile;
import org.comroid.util.Token;
import org.springframework.data.repository.CrudRepository;
import org.springframework.web.client.RestTemplate;

import java.util.Optional;
import java.util.UUID;

public interface MinecraftProfileRepo extends CrudRepository<MinecraftProfile, UUID> {
    Optional<MinecraftProfile> findByName(String name);
    Optional<MinecraftProfile> findByVerification(String verification);

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
