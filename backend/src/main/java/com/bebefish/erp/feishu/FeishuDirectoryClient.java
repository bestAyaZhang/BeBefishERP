package com.bebefish.erp.feishu;

import java.util.List;

public interface FeishuDirectoryClient {
    String currentTenantKey();

    FeishuEmployeeProfile employeeProfile(String openId);

    List<FeishuBusinessRole> businessRoles(String openId);
}
