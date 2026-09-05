package com.bebefish.erp.authorization.domain;

import java.util.Set;

public interface FeishuRoleMappingRepository {
    Set<Long> findEnabledErpRoleIds(String tenantKey, Set<String> feishuRoleIds);

    long basicEmployeeRoleId();

    void replaceFeishuAssignments(long userId, Set<Long> roleIds);
}
