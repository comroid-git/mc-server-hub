package org.comroid.mcsd.web.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import lombok.Data;

import java.util.UUID;

@Data
@Entity
public class ShConnection {
    @Id
    private UUID id = UUID.randomUUID();
    private String host;
    private int port = 22;
    private String username;
    private String password;

    @Override
    public String toString() {
        return "%s@%s:%d".formatted(username,host,port);
    }
}
