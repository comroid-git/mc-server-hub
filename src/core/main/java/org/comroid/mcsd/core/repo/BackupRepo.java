package org.comroid.mcsd.core.repo;

import org.comroid.mcsd.core.entity.Backup;
import org.springframework.data.repository.CrudRepository;

import java.util.UUID;

public interface BackupRepo extends CrudRepository<Backup, UUID> {
}
