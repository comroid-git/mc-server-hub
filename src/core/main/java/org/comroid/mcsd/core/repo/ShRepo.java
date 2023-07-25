package org.comroid.mcsd.core.repo;

import jakarta.persistence.Table;
import org.comroid.mcsd.core.entity.ShConnection;
import org.springframework.data.repository.CrudRepository;

import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

@Deprecated
public interface ShRepo extends CrudRepository<ShConnection, UUID> {
    default Map<String, UUID> toShMap() {
        return StreamSupport.stream(findAll().spliterator(), false)
                .collect(Collectors.toUnmodifiableMap(ShConnection::toString, ShConnection::getId));
    }
}
