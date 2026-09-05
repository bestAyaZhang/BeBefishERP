package com.bebefish.erp.feishu;

import java.util.List;

public interface FeishuDirectoryClient {
    String currentTenantKey();

    default List<FeishuDepartment> departments() {
        throw new FeishuClientException("飞书通讯录客户端未配置");
    }

    default List<FeishuDirectoryUser> usersInDepartment(String departmentId) {
        throw new FeishuClientException("飞书通讯录客户端未配置");
    }

    FeishuEmployeeProfile employeeProfile(String openId);

    List<FeishuBusinessRole> businessRoles(String openId);

    default List<FeishuBusinessRole> allBusinessRoles() {
        return businessRoles("");
    }
}
