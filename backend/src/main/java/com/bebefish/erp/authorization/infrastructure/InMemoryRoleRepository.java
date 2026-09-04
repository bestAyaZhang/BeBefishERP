package com.bebefish.erp.authorization.infrastructure;

import com.bebefish.erp.authorization.domain.DataScope;
import com.bebefish.erp.authorization.domain.PermissionDefinition;
import com.bebefish.erp.authorization.domain.Role;
import com.bebefish.erp.authorization.domain.RoleRepository;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Repository;

@Repository
public class InMemoryRoleRepository implements RoleRepository {
    private static final List<PermissionDefinition> PERMISSIONS = List.of(
            new PermissionDefinition("system:user:view", "查看用户"),
            new PermissionDefinition("system:role:view", "查看角色"),
            new PermissionDefinition("system:role:manage", "管理角色"),
            new PermissionDefinition("dashboard:view", "查看工作台"),
            new PermissionDefinition("masterdata:view", "查看基础资料"),
            new PermissionDefinition("masterdata:edit", "编辑基础资料"),
            new PermissionDefinition("product:view", "查看商品"),
            new PermissionDefinition("product:edit", "编辑商品"),
            new PermissionDefinition("organization:view", "查看组织架构"),
            new PermissionDefinition("organization:manage", "管理组织架构"),
            new PermissionDefinition("inventory:view", "查看库存"),
            new PermissionDefinition("inventory:adjust", "调整库存"),
            new PermissionDefinition("inventory:edit", "编辑库存"),
            new PermissionDefinition("sales:view", "查看销售"),
            new PermissionDefinition("sales:create", "创建销售单"),
            new PermissionDefinition("sales:confirm", "确认销售单"),
            new PermissionDefinition("sales:void", "作废销售单"),
            new PermissionDefinition("sales:print", "打印销售单"),
            new PermissionDefinition("finance:view", "查看财务"),
            new PermissionDefinition("finance:receipt", "登记收款")
    );
    private static final Set<String> PERMISSION_CODES = PERMISSIONS.stream()
            .map(PermissionDefinition::code)
            .collect(java.util.stream.Collectors.toUnmodifiableSet());
    private static final List<Role> ROLES = List.of(new Role(
            "SUPER_ADMIN",
            "超级管理员",
            true,
            true,
            true,
            DataScope.COMPANY,
            PERMISSION_CODES,
            Set.of("13800138000")
    ));

    @Override
    public List<Role> findEnabledByMemberKey(String memberKey) {
        return ROLES.stream()
                .filter(Role::enabled)
                .filter(role -> role.memberKeys().contains(memberKey))
                .toList();
    }

    @Override
    public Set<String> findAllPermissionCodes() {
        return PERMISSION_CODES;
    }
}
