package org.comroid.mcsd.web.repo;

import jakarta.persistence.Table;
import org.comroid.mcsd.web.entity.Server;
import org.comroid.mcsd.web.entity.User;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;

import java.util.Iterator;
import java.util.UUID;

@Table(name = "servers")
public interface ServerRepo extends CrudRepository<Server, UUID> {
    @Query("SELECT DISTINCT s FROM Server s JOIN s.userPermissions p WHERE KEY(p) = :userId AND VALUE(p) > 0")
    Iterable<Server> findByPermittedUser(@Param("userId") UUID userId);
}
