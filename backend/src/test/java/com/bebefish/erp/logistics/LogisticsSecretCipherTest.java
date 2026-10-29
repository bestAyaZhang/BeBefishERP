package com.bebefish.erp.logistics;

import static org.assertj.core.api.Assertions.*;
import java.lang.reflect.InvocationTargetException;
import java.util.Base64;
import org.junit.jupiter.api.Test;

class LogisticsSecretCipherTest {
    @Test void encryptsWithFreshAuthenticatedNoncesAndRejectsTampering() throws Exception {
        Class<?> type = Class.forName("com.bebefish.erp.logistics.LogisticsSecretCipher");
        Object cipher = type.getConstructor(String.class).newInstance(Base64.getEncoder().encodeToString(new byte[32]));
        var encrypt = type.getMethod("encrypt", String.class, String.class);
        var decrypt = type.getMethod("decrypt", String.class, String.class);
        String first = (String) encrypt.invoke(cipher, "secret-value", "account:1:test");
        String second = (String) encrypt.invoke(cipher, "secret-value", "account:1:test");
        assertThat(first).doesNotContain("secret-value").isNotEqualTo(second);
        assertThat(decrypt.invoke(cipher, first, "account:1:test")).isEqualTo("secret-value");
        assertThatThrownBy(() -> decrypt.invoke(cipher, first, "account:2:test")).isInstanceOf(InvocationTargetException.class);
        char changed = first.charAt(15) == 'A' ? 'B' : 'A';
        String tampered = first.substring(0,15) + changed + first.substring(16);
        assertThatThrownBy(() -> decrypt.invoke(cipher, tampered, "account:1:test")).isInstanceOf(InvocationTargetException.class);
    }
    @Test void missingMasterKeyRejectsWritingInsteadOfGeneratingEphemeralKeys() throws Exception {
        Class<?> type = Class.forName("com.bebefish.erp.logistics.LogisticsSecretCipher");
        Object cipher = type.getConstructor(String.class).newInstance("");
        assertThat(type.getMethod("available").invoke(cipher)).isEqualTo(false);
        assertThatThrownBy(() -> type.getMethod("encrypt", String.class,String.class).invoke(cipher,"x","a"))
            .isInstanceOf(InvocationTargetException.class).hasCauseInstanceOf(com.bebefish.erp.common.api.BusinessException.class);
    }
}
