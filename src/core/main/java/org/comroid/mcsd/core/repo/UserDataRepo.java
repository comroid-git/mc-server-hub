package org.comroid.mcsd.core.repo;

import jakarta.servlet.http.HttpSession;
import net.dv8tion.jda.api.entities.User;
import org.comroid.mcsd.core.entity.UserData;
import org.comroid.util.AlmostComplete;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.oauth2.core.user.OAuth2User;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public interface UserDataRepo extends CrudRepository<UserData, UUID> {
    @Query("SELECT u FROM UserData u WHERE u.name = :name")
    Optional<UserData> findByName(@Param("name") String name);

    @Query("SELECT u FROM UserData u WHERE u.user.id = :id")
    Optional<UserData> findByUserId(@Param("id") UUID id);

    @Query("SELECT DISTINCT u FROM UserData u WHERE u.discordId = :id")
    Optional<UserData> findByDiscordId(@Param("id") long id);

    @Query("SELECT DISTINCT u FROM UserData u WHERE u.minecraft.id = :id")
    Optional<UserData> findByMinecraftId(@Param("id") UUID id);

    @Query("SELECT DISTINCT u FROM UserData u WHERE u.minecraft.name = :username")
    Optional<UserData> findByMinecraftName(@Param("username") String username);

    default AlmostComplete<UserData> modify(User discordUser) {
        final var id = discordUser.getIdLong();
        return findByDiscordId(id).map(AlmostComplete::of).orElseGet(() -> new AlmostComplete<>(() -> {
            var usr = new UserData();
            usr.setDiscordId(id);
            usr.setName(discordUser.getEffectiveName()); // todo: this will cause issues; should use username attribute value as key
            return usr;
        }, this::save));
    }

    default UserData get(HttpSession session) {
        var oAuth2User = ((OAuth2User) ((SecurityContext) session.getAttribute("SPRING_SECURITY_CONTEXT"))
                .getAuthentication().getPrincipal());
        UUID id = UUID.fromString(Objects.requireNonNull(oAuth2User.getAttribute("id"), "User ID cannot be null"));
        return findById(id).orElseGet(() -> {
            var usr = new UserData();
            //usr.setId(id);
            usr.setName(oAuth2User.getAttribute("login")); // todo: this will cause issues; should use username attribute value as key
            save(usr);
            return usr;
        });
    }
}
