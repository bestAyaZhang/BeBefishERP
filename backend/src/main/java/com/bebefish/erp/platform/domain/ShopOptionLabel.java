package com.bebefish.erp.platform.domain;

public final class ShopOptionLabel {
    private ShopOptionLabel() {}

    public static String generate(String channelType, String platformName, String shopName, String override) {
        if (override != null && !override.isBlank()) return override;
        boolean privateChannel = "private".equals(channelType);
        String segment = privateChannel && platformName.endsWith("代发") ? "代发" : platformName;
        return (privateChannel ? "私域" : "电商") + "_" + segment + "_" + shopName;
    }
}
