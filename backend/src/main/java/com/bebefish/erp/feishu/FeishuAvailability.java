package com.bebefish.erp.feishu;

public record FeishuAvailability(boolean available, String errorCode, String message) {
    public static FeishuAvailability ready() {
        return new FeishuAvailability(true, null, "飞书登录可用");
    }

    public static FeishuAvailability unavailable(String errorCode, String message) {
        return new FeishuAvailability(false, errorCode, message);
    }
}
