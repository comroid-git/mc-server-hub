package org.comroid.mcsd.api.dto;

import lombok.Value;

import java.util.UUID;

@Value
public class Handshake {
    private UUID connectionId;
}
