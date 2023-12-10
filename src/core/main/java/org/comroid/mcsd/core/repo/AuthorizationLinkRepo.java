package org.comroid.mcsd.core.repo;

import org.comroid.api.SupplierX;
import org.comroid.api.info.Log;
import org.comroid.mcsd.core.entity.AbstractEntity;
import org.comroid.mcsd.core.entity.AuthorizationLink;
import org.comroid.mcsd.core.entity.User;
import org.comroid.util.Bitmask;
import org.comroid.util.Token;
import org.springframework.data.repository.CrudRepository;

import java.util.Arrays;
import java.util.UUID;
import java.util.logging.Level;

public interface AuthorizationLinkRepo extends CrudRepository<AuthorizationLink, String> {
    default AuthorizationLink create(User user, UUID targetId, AbstractEntity.Permission... permissions) {
        return create(user,targetId, Bitmask.combine(permissions));
    }
    default AuthorizationLink create(User user, UUID targetId, int permissions) {
        String code;
        do {
            code = Token.random(16, false);
        } while (findById(code).isPresent());
        var link = new AuthorizationLink(code, user, targetId, permissions);
        return save(link);
    }

    default SupplierX<AuthorizationLink> validate(String editKey, User user, UUID target, AbstractEntity.Permission... permissions) {
        return validate(editKey, user, target, Bitmask.combine(permissions));
    }
    default SupplierX<AuthorizationLink> validate(String editKey, User user, UUID target, int permissions) {
        return SupplierX.ofSupplier(()->findById(editKey).orElse(null))
                .filter(link -> link.getCreator().equals(user))
                .filter(link -> link.getTarget().equals(target))
                .filter(link -> link.getPermissions() == permissions)
                .peek(this::delete);
    }

    default void flush(String... codes) {
        try {
            deleteAllById(Arrays.asList(codes));
        } catch (Throwable t) {
            Log.at(Level.FINE, "Error flushing Authorizations", t);
        }
    }
}
