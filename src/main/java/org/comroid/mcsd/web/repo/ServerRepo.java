package org.comroid.mcsd.web.repo;

import jakarta.persistence.Table;
import org.comroid.mcsd.web.entity.Server;
import org.springframework.data.repository.CrudRepository;

import java.util.UUID;

@Table(name = "servers")
public interface ServerRepo extends CrudRepository<Server, UUID> {
}
