package com.bebefish.erp.identity.domain;

import com.bebefish.erp.auth.domain.EmployeeStatus;
import com.bebefish.erp.auth.domain.EmploymentType;

public record Employee(
        long id,
        String employeeNo,
        String name,
        String mobile,
        String avatarUrl,
        Long departmentId,
        Long positionId,
        EmploymentType employmentType,
        EmployeeStatus status,
        String source,
        boolean profileComplete
) {
}
