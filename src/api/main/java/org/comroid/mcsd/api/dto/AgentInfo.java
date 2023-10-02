package org.comroid.mcsd.api.dto;

import lombok.*;
import lombok.experimental.FieldDefaults;
import org.comroid.abstr.DataNode;
import org.comroid.annotations.Convert;
import org.comroid.util.EncryptionUtil;
import org.intellij.lang.annotations.MagicConstant;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.crypto.Cipher;
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
    @Nullable String hostname;
}
