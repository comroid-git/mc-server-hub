package org.comroid.mcsd.repo;

import jakarta.persistence.Table;
import jakarta.servlet.http.HttpSession;
import org.comroid.mcsd.entity.User;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.oauth2.core.user.OAuth2User;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Table(name = "users")
public interface UserRepo extends CrudRepository<User, UUID> {
    @Query("SELECT u FROM User u WHERE u.name = ?1")
    Optional<User> findByName(String name);

    default User findBySession(HttpSession session) {
        var oAuth2User = ((OAuth2User) ((SecurityContext) session.getAttribute("SPRING_SECURITY_CONTEXT"))
                .getAuthentication().getPrincipal());
        UUID id = UUID.fromString(Objects.requireNonNull(oAuth2User.getAttribute("id"), "User ID cannot be null"));
        return findById(id).orElseGet(() -> {
            var usr = new User();
            usr.setId(id);
            usr.setName(oAuth2User.getAttribute("login"));
            usr.setGuest(Boolean.TRUE.equals(oAuth2User.getAttribute("guest")));
            save(usr);
            return usr;
        });
    }
}
