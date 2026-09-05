package com.bebefish.erp.feishu;

public record FeishuBusinessRole(String id, String name, int memberCount) {
    public FeishuBusinessRole(String id, String name) {
        this(id, name, 0);
    }
}
