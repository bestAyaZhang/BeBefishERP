package com.bebefish.erp.authorization.domain;

import java.util.List;
import java.util.Set;

public interface RoleRepository {
    List<Role> findEnabledByMemberKey(String memberKey);

    Set<String> findAllPermissionCodes();
}
