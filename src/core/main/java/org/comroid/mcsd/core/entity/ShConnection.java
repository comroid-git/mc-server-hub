package org.comroid.mcsd.core.entity;

import jakarta.persistence.Basic;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.comroid.api.BitmaskAttribute;
import org.comroid.util.Bitmask;

import java.util.UUID;

@Data
@Entity
@Table(name = "sh_connection")
@EqualsAndHashCode(callSuper = true)
public class ShConnection extends AbstractEntity {
    @Basic
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
