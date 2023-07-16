package org.comroid.mcsd.connector.gateway;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldDefaults;
import org.comroid.annotations.Convert;
import org.comroid.mcsd.connector.HubConnector;
import org.comroid.util.EncryptionUtil;
import org.intellij.lang.annotations.MagicConstant;

import javax.crypto.Cipher;
import java.util.UUID;

@Data
@Builder
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PROTECTED)
public final class GatewayConnectionData {
    final UUID id = UUID.randomUUID();
    HubConnector.Role role;
    UUID target;
    @JsonIgnore
    String token;

    @Convert
    public Cipher toCipher(@MagicConstant(valuesFromClass = Cipher.class) int mode) {
        return EncryptionUtil.prepareCipher(getId(),
                EncryptionUtil.Algorithm.RSA,
                EncryptionUtil.Transformation.RSA_ECB_OAEPWithSHA_256AndMGF1Padding,
                mode,
                getToken());
    }
}
