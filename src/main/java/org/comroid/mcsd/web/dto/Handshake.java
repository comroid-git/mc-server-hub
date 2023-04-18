package org.comroid.mcsd.web.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.UUID;

@Data
@AllArgsConstructor
public class Handshake {
    private UUID connectionId;
}
