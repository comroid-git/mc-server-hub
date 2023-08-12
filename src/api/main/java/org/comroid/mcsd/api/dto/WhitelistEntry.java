package org.comroid.mcsd.api.dto;

import lombok.Data;

import java.util.UUID;

@Data
public class WhitelistEntry {
    private UUID uuid;
    private String name;
}
