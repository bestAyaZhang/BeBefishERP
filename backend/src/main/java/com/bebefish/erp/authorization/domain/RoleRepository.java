package com.bebefish.erp.authorization.domain;

import java.util.List;

public interface RoleRepository {
    List<Role> findEnabledByUserId(long userId);

    List<Role> findEnabledByMemberKey(String memberKey);

    List<PermissionDefinition> findAllPermissions();
}
