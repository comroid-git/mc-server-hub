package org.comroid.mcsd.api.dto;

import lombok.*;
import lombok.experimental.FieldDefaults;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

@Data
@Builder
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PROTECTED)
public final class AgentInfo {
    @NotNull UUID target;
    @NotNull UUID agent;
    @NotNull String hubBaseUrl;
    @NotNull String token;
    @Nullable String baseUrl;
}
