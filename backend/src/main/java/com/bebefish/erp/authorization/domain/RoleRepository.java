package com.bebefish.erp.authorization.domain;

import java.util.List;

public interface RoleRepository {
    List<Role> findEnabledByMemberKey(String memberKey);

    List<PermissionDefinition> findAllPermissions();
}
