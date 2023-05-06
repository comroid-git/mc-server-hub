package org.comroid.mcsd.web.dto;

import lombok.Value;

import java.util.UUID;

@Value
public class Handshake {
    private UUID connectionId;
}
