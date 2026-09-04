package com.bebefish.erp.feishu;

public record FeishuEmployeeProfile(
        String openId,
        String employeeNo,
        String mobile,
        String primaryDepartmentId,
        String displayName
) {
}
