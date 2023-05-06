package org.comroid.mcsd.web.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import lombok.*;

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

    @Override
    public String toString() {
        return "%s@%s:%d".formatted(username,host,port);
    }
}
