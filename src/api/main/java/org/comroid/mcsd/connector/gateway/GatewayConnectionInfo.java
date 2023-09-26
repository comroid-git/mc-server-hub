package org.comroid.mcsd.connector.gateway;

import com.fasterxml.jackson.annotation.JsonValue;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import lombok.*;
import lombok.experimental.FieldDefaults;
import org.comroid.annotations.Convert;
import org.comroid.util.EncryptionUtil;
import org.intellij.lang.annotations.MagicConstant;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.crypto.Cipher;
import javax.persistence.AttributeConverter;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.UUID;

@Data
@Builder
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PROTECTED)
public final class GatewayConnectionInfo {
    final UUID id = UUID.randomUUID();
    @Nullable String hubBaseUrl;
    @NotNull UUID target;
    @NotNull UUID agent;
    @Nullable String token;

    @Convert
    public Cipher toCipher(@MagicConstant(valuesFromClass = Cipher.class) int mode) {
        return EncryptionUtil.prepareCipher(getId(),
                EncryptionUtil.Algorithm.RSA,
                EncryptionUtil.Transformation.RSA_ECB_OAEPWithSHA_256AndMGF1Padding,
                mode,
                getToken());
    }
}
