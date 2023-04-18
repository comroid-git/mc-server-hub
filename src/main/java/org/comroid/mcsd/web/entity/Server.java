package org.comroid.mcsd.web.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import lombok.Data;
import org.comroid.api.IntegerAttribute;

import java.util.UUID;

@Data
@Entity
public class Server {
    @Id
    private UUID id = UUID.randomUUID();
    private UUID shConnection;
    private String name;
    private String mcVersion = "1.19.1";
    private Mode mode = Mode.Paper;
    private int port = 25565;

    public boolean isVanilla() {
        return mode == Mode.Vanilla;
    }

    public boolean isPaper() {
        return mode == Mode.Paper;
    }

    public boolean isForge() {
        return mode == Mode.Forge;
    }

    public boolean isFabric() {
        return mode == Mode.Fabric;
    }

    public enum Mode implements IntegerAttribute {
        Vanilla, Paper, Forge, Fabric
    }
}
