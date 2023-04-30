package org.comroid.mcsd.web.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import org.comroid.mcsd.web.entity.Server;

import java.util.UUID;

@Data
@AllArgsConstructor
public class StatusMessage {
    private UUID serverId;
    private Server.Status status;
    private int playerCount;
    private int playerMax;
    private String motd;
}
