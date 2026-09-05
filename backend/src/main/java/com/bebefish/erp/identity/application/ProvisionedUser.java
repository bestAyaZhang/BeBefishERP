package com.bebefish.erp.identity.application;

import com.bebefish.erp.auth.domain.UserAccount;
import com.bebefish.erp.identity.domain.Employee;
import com.bebefish.erp.identity.domain.FeishuIdentity;

public record ProvisionedUser(
        UserAccount account, Employee employee, FeishuIdentity identity, boolean employeeCreated) {
    public ProvisionedUser(UserAccount account, Employee employee, FeishuIdentity identity) {
        this(account, employee, identity, false);
    }
}
