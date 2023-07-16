package org.comroid.mcsd.connector.gateway;

import lombok.*;
import lombok.experimental.FieldDefaults;
import org.comroid.annotations.Convert;
import org.comroid.mcsd.connector.HubConnector;
import org.comroid.mcsd.util.JacksonObjectConverter;
import org.comroid.util.EncryptionUtil;
import org.intellij.lang.annotations.MagicConstant;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.crypto.Cipher;
import javax.persistence.Converter;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.UUID;

@Data
@Builder
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PROTECTED)
public final class GatewayConnectionInfo {
    public static final int TokenLength = 64;
    final UUID id = UUID.randomUUID();
    @Nullable String hubBaseUrl;
    @NotNull HubConnector.Role role;
    @NotNull UUID target;
    @lombok.Builder.Default
    @NotNull String token = regenerateToken();

    public String regenerateToken() {
        var randomBytes = new byte[TokenLength];
        new SecureRandom().nextBytes(randomBytes);
        return token = Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
    }

    @Convert
    public Cipher toCipher(@MagicConstant(valuesFromClass = Cipher.class) int mode) {
        return EncryptionUtil.prepareCipher(getId(),
                EncryptionUtil.Algorithm.RSA,
                EncryptionUtil.Transformation.RSA_ECB_OAEPWithSHA_256AndMGF1Padding,
                mode,
                getToken());
    }

    @Value
    @javax.persistence.Converter
    @EqualsAndHashCode(callSuper = true)
    public static class Converter extends JacksonObjectConverter<GatewayConnectionInfo> {
        public Converter() {
            super(GatewayConnectionInfo.class);
        }
    }
}
