package org.comroid.mcsd.api.dto;

import lombok.Value;
import org.jetbrains.annotations.Nullable;

@Value
public class McsdConfig {
    @Nullable String hubBaseUrl;
    @Nullable String discordToken;
    DBInfo database;
    OAuth2Info oAuth;
    @Nullable AgentInfo agent;
}
