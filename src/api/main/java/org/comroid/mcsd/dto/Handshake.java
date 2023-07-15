package org.comroid.mcsd.dto;

import lombok.Value;

import java.util.UUID;

@Value
public class Handshake {
    private UUID connectionId;
}
