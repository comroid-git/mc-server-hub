package org.comroid.mcsd.core.entity.system;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import org.comroid.api.BitmaskAttribute;
import org.comroid.mcsd.core.entity.AbstractEntity;
import org.comroid.util.Bitmask;

import java.util.UUID;

@Getter
@Setter
@Entity
public class ShConnection extends AbstractEntity {
    private String host;
    private int port = 22;
    private String username;
    private @Getter(onMethod = @__(@JsonIgnore)) String password;
    private String backupsDir = "$HOME/backups";
    private int capabilites = Bitmask.combine(Capability.SSH);

    @Override
    public String toString() {
        return "%s@%s:%d".formatted(username, host, port);
    }

    public enum Capability implements BitmaskAttribute<Capability> { SSH, SFTP, FTP }
}
