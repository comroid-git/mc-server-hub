package org.comroid.mcsd.api.dto;

import lombok.Value;
import org.jetbrains.annotations.Nullable;

import java.util.List;

@Value
public class McsdConfig {
    @Nullable String hubBaseUrl;
    @Nullable String discordToken;
    @Nullable String rabbitUrl;
    DBInfo database;
    List<OAuth2Info> oAuth;
    @Nullable AgentInfo agent;
}
