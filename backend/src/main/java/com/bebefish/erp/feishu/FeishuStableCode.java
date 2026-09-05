package com.bebefish.erp.feishu;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

public final class FeishuStableCode {
    private FeishuStableCode() {}

    public static String of(String prefix, String externalId) {
        try {
            byte[] digest =
                    MessageDigest.getInstance("SHA-256")
                            .digest(externalId.getBytes(StandardCharsets.UTF_8));
            return prefix + HexFormat.of().withUpperCase().formatHex(digest, 0, 12);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }
}
