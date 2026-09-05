package com.bebefish.erp.feishu;

import java.time.LocalDate;
import java.util.List;

public record FeishuEmployeeProfile(
        String openId,
        String employeeNo,
        String mobile,
        String primaryDepartmentId,
        String displayName,
        List<String> departmentIds,
        Integer employeeType,
        String jobTitle,
        LocalDate hireDate,
        Boolean activated,
        boolean frozen,
        boolean unjoined,
        boolean resigned,
        boolean exited) {
    public FeishuEmployeeProfile {
        departmentIds = departmentIds == null ? List.of() : List.copyOf(departmentIds);
    }

    public FeishuEmployeeProfile(
            String openId,
            String employeeNo,
            String mobile,
            String primaryDepartmentId,
            String displayName) {
        this(
                openId,
                employeeNo,
                mobile,
                primaryDepartmentId,
                displayName,
                List.of(),
                null,
                null,
                null,
                null,
                false,
                false,
                false,
                false);
    }
}
