package org.comroid.mcsd.web.repo;

import jakarta.persistence.Table;
import org.comroid.mcsd.web.entity.ShConnection;
import org.springframework.data.repository.CrudRepository;

import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

@Table(name = "sh_connections")
public interface ShRepo extends CrudRepository<ShConnection, UUID> {
    default Map<String, UUID> toShMap() {
        return StreamSupport.stream(findAll().spliterator(), false)
                .collect(Collectors.toUnmodifiableMap(
                        x -> "%s@%s:%s".formatted(x.getUsername(), x.getHost(), x.getPort()),
                        ShConnection::getId));
    }
}
