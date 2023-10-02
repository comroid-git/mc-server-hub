package org.comroid.mcsd.api.dto;

import lombok.Data;

import java.util.UUID;

@Data
public class OpListEntry {
    private UUID uuid;
    private String name;
    private byte level;
    private boolean bypassesPlayerLimit;
}
