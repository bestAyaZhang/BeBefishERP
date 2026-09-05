package com.bebefish.erp.auth.domain;

public record UserAccount(
        long id,
        long employeeId,
        String mobile,
        String passwordHash,
        EmploymentType employmentType,
        UserStatus userStatus,
        EmployeeStatus employeeStatus,
        String displayName,
        String avatarUrl
) {
    public UserAccount(String mobile, String passwordHash, boolean enabled, boolean employeeActive) {
        this(
                0,
                0,
                mobile,
                passwordHash,
                EmploymentType.TEMPORARY,
                enabled ? UserStatus.ENABLED : UserStatus.DISABLED,
                employeeActive ? EmployeeStatus.ACTIVE : EmployeeStatus.DISABLED,
                mobile,
                null
        );
    }

    public boolean enabled() {
        return userStatus == UserStatus.ENABLED;
    }

    public boolean employeeActive() {
        return employeeStatus == EmployeeStatus.ACTIVE;
    }
}
