package org.comroid.mcsd.core.repo;

import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.servlet.http.HttpSession;
import jakarta.transaction.Transactional;
import org.comroid.mcsd.core.entity.User;
import org.comroid.util.AlmostComplete;
import org.comroid.util.REST;
import org.comroid.util.Streams;
import org.comroid.util.Token;
import org.jetbrains.annotations.ApiStatus;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.oauth2.core.user.OAuth2User;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.stream.Stream;

public interface UserRepo extends CrudRepository<User, UUID> {
    Optional<User> findByVerification(String verification);
    Optional<User> findByName(String name);
    Optional<User> findByHubId(UUID id);
    Optional<User> findByMinecraftId(UUID id);
    Optional<User> findByDiscordId(long id);

    @Modifying
    @Transactional
    @Query("UPDATE User u SET u.verification = null, u.verificationTimeout = null WHERE u.id = :id")
    void clearVerification(@Param("id") UUID id);

    default Optional<User> findByMinecraftName(String username) {
        return findByMinecraftId(findMinecraftId(username));
    }

    default UUID findMinecraftId(String username) {
        var raw = REST.get(User.getMojangAccountUrl(username))
                .thenApply(rsp -> rsp.getBody().get("id").asString())
                .join();
        var sb = new StringBuilder(raw);
        sb.insert(8, "-").insert(13, "-").insert(18, "-").insert(23, "-");
        return UUID.fromString(sb.toString());
    }

    default AlmostComplete<User> get(String minecraftUsername) {
        return findByMinecraftName(minecraftUsername).map(AlmostComplete::of).orElseGet(() -> new AlmostComplete<>(() -> {
            var usr = new User();
            var id = findMinecraftId(minecraftUsername);
            usr.setId(id);
            usr.setName(minecraftUsername);
            usr.setDisplayName(usr.getName() + " McUser");
            usr.setMinecraftId(id);
            return usr;
        }, this::save));
    }

    default AlmostComplete<User> get(net.dv8tion.jda.api.entities.User discordUser) {
        final var id = discordUser.getIdLong();
        return findByDiscordId(id).map(AlmostComplete::of).orElseGet(() -> new AlmostComplete<>(() -> {
            var usr = new User();
            usr.setName(discordUser.getName());
            usr.setDisplayName(usr.getName() + " DiscordUser");
            usr.setDiscordId(id);
            return usr;
        }, this::save));
    }

    @ApiStatus.Internal
    default AlmostComplete<User> get(HttpSession session) {
        var oAuth2User = ((OAuth2User) ((SecurityContext) session.getAttribute("SPRING_SECURITY_CONTEXT"))
                .getAuthentication().getPrincipal());
        UUID id = UUID.fromString(Objects.requireNonNull(oAuth2User.getAttribute("id"), "User ID cannot be null"));
        return findById(id).map(AlmostComplete::of).orElseGet(() -> new AlmostComplete<>(() -> {
            var usr = new User();
            usr.setId(id);
            // todo: this will cause issues; should use username attribute value as key
            usr.setName(oAuth2User.getAttribute("login"));
            usr.setDisplayName(usr.getName()+" HubUser");
            return usr;
        }, this::save));
    }

    default String startMcDcLinkage(User user) {
        String code;
        do {
            code = Token.random(6, false);
        } while (findByVerification(code).isPresent());
        user.setVerificationTimeout(Instant.now().plus(Duration.ofMinutes(15)));
        user.setVerification(code);
        save(user);
        return code;
    }

    @Transactional
    default User merge(Optional<?>... users) {
        return merge(Stream.of(users)
                .flatMap(Optional::stream)
                .flatMap(Streams.cast(User.class))
                .toArray(User[]::new));
    }

    @Transactional
    default User merge(User... users) {
        users = Stream.of(users).distinct().toArray(User[]::new);
        if (users.length == 0)
            throw new IllegalArgumentException("users must not be empty");
        if (users.length == 1)
            return users[0];
        var base = Stream.of(users)
                .filter(usr -> usr.getHubId()!=null)
                .collect(Streams.oneOrNone(()->new IllegalStateException("cannot merge users; more than one hubUser was found")))
                .orElse(users[0]);
        for (var other : users) {
            if (base.getId() == other.getId()) continue;
            if (other.getHubId()!=null)base.setHubId(other.getHubId());
            if (other.getMinecraftId()!=null)base.setMinecraftId(other.getMinecraftId());
            if (other.getDiscordId()!=null)base.setDiscordId(other.getDiscordId());
            delete(other);
        }
        return save(base);
    }
}
