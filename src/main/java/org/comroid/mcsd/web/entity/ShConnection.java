package org.comroid.mcsd.web.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import lombok.Data;
import org.comroid.api.BitmaskAttribute;
import org.comroid.util.Bitmask;

import java.util.UUID;

@Data
@Entity
public class ShConnection {
    @Id
    private UUID id = UUID.randomUUID();
    private UUID owner;
    private String host;
    private int port = 22;
    private String username;
    private String password;
    private String backupsDir = "$HOME/backups";
    private int capabilites = Bitmask.combine(Capability.SSH);

    @Override
    public String toString() {
        return "%s@%s:%d".formatted(username, host, port);
    }

    public enum Capability implements BitmaskAttribute<Capability> { SSH, SFTP, FTP }
}
