package com.bebefish.erp.feishu;

public record FeishuDepartment(
        String openDepartmentId,
        String parentDepartmentId,
        String name,
        String leaderOpenId,
        int order) {}
