package org.comroid.mcsd.repo;

import jakarta.persistence.Table;
import org.comroid.mcsd.entity.ShConnection;
import org.springframework.data.repository.CrudRepository;

import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

@Table(name = "sh_connections")
public interface ShRepo extends CrudRepository<ShConnection, UUID> {
    default Map<String, UUID> toShMap() {
        return StreamSupport.stream(findAll().spliterator(), false)
                .collect(Collectors.toUnmodifiableMap(ShConnection::toString, ShConnection::getId));
    }
}
