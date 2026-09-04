package com.bebefish.erp.authorization.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bebefish.erp.authorization.domain.DataScope;
import com.bebefish.erp.authorization.domain.PermissionDefinition;
import com.bebefish.erp.authorization.domain.Role;
import com.bebefish.erp.authorization.domain.RoleRepository;
import com.bebefish.erp.authorization.infrastructure.InMemoryRoleRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class AuthorizationResolverTest {
    private final FakeRoleRepository repository = new FakeRoleRepository(List.of(
            new PermissionDefinition("product:view", "product", "view", "查看商品"),
            new PermissionDefinition("product:edit", "product", "edit", "编辑商品"),
            new PermissionDefinition("sales:view", "sales", "view", "查看销售"),
            new PermissionDefinition("finance:view", "finance", "view", "查看财务")
    ));
    private final AuthorizationResolver resolver = new AuthorizationResolver(repository);

    @Test
    void combinesEnabledRolesAndUsesTheWidestScope() {
        repository.save(role("PRODUCT_OPERATOR", true, DataScope.DEPARTMENT,
                Set.of("product:view", "product:edit"), Set.of("13800138000")));
        repository.save(role("SALES_VIEWER", true, DataScope.COMPANY,
                Set.of("sales:view"), Set.of("13800138000")));

        var result = resolver.resolve("13800138000");

        assertThat(result.roles()).containsExactly("PRODUCT_OPERATOR", "SALES_VIEWER");
        assertThat(result.permissions()).containsExactly("product:edit", "product:view", "sales:view");
        assertThat(result.dataScope()).contains(DataScope.COMPANY);
    }

    @Test
    void ignoresDisabledRolesAndReturnsEmptyAuthorizationForUnknownMember() {
        repository.save(role("DISABLED", false, DataScope.COMPANY,
                Set.of("finance:view"), Set.of("13800138000")));

        assertThat(resolver.resolve("13800138000").permissions()).isEmpty();
        assertThat(resolver.resolve("unknown").dataScope()).isEmpty();
    }

    @Test
    void superAdministratorReceivesTheCompleteCatalog() {
        repository.save(superAdmin(Set.of("13800138000")));

        assertThat(resolver.resolve("13800138000").permissions())
                .containsExactlyElementsOf(repository.findAllPermissions().stream()
                        .map(PermissionDefinition::code)
                        .sorted()
                        .toList());
    }

    @Test
    void productionCatalogMatchesTheCompleteApprovedDefinitionsExactly() {
        assertThat(new InMemoryRoleRepository().findAllPermissions()).containsExactly(
                new PermissionDefinition("system:user:view", "system:user", "view", "查看用户"),
                new PermissionDefinition("system:role:view", "system:role", "view", "查看角色"),
                new PermissionDefinition("system:role:manage", "system:role", "manage", "管理角色"),
                new PermissionDefinition("dashboard:view", "dashboard", "view", "查看工作台"),
                new PermissionDefinition("masterdata:view", "masterdata", "view", "查看基础资料"),
                new PermissionDefinition("masterdata:edit", "masterdata", "edit", "编辑基础资料"),
                new PermissionDefinition("product:view", "product", "view", "查看商品"),
                new PermissionDefinition("product:edit", "product", "edit", "编辑商品"),
                new PermissionDefinition("organization:view", "organization", "view", "查看组织架构"),
                new PermissionDefinition("organization:manage", "organization", "manage", "管理组织架构"),
                new PermissionDefinition("inventory:view", "inventory", "view", "查看库存"),
                new PermissionDefinition("inventory:adjust", "inventory", "adjust", "调整库存"),
                new PermissionDefinition("inventory:edit", "inventory", "edit", "编辑库存"),
                new PermissionDefinition("sales:view", "sales", "view", "查看销售"),
                new PermissionDefinition("sales:create", "sales", "create", "创建销售单"),
                new PermissionDefinition("sales:confirm", "sales", "confirm", "确认销售单"),
                new PermissionDefinition("sales:void", "sales", "void", "作废销售单"),
                new PermissionDefinition("sales:print", "sales", "print", "打印销售单"),
                new PermissionDefinition("finance:view", "finance", "view", "查看财务"),
                new PermissionDefinition("finance:receipt", "finance", "receipt", "登记收款")
        );
    }

    @Test
    void superAdministratorMustBeSystemEnabledAndCompanyScoped() {
        assertThatThrownBy(() -> new Role("SUPER_ADMIN", "超级管理员", false, true, true,
                DataScope.COMPANY, Set.of(), Set.of("13800138000")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Role("SUPER_ADMIN", "超级管理员", true, true, false,
                DataScope.COMPANY, Set.of(), Set.of("13800138000")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Role("SUPER_ADMIN", "超级管理员", true, true, true,
                DataScope.DEPARTMENT, Set.of(), Set.of("13800138000")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void superAdministratorFlagMustMatchTheReservedRoleCode() {
        assertThatThrownBy(() -> new Role("ARBITRARY_ADMIN", "任意管理员", true, true, true,
                DataScope.COMPANY, Set.of(), Set.of("13800138000")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Role("SUPER_ADMIN", "超级管理员", true, false, true,
                DataScope.COMPANY, Set.of(), Set.of("13800138000")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void resolvedAuthorizationRejectsNullDataScopeOptional() {
        assertThatThrownBy(() -> new ResolvedAuthorization(List.of(), List.of(), null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("dataScope");
    }

    private static Role role(
            String code,
            boolean enabled,
            DataScope scope,
            Set<String> permissions,
            Set<String> members
    ) {
        return new Role(code, code, false, false, enabled, scope, permissions, members);
    }

    private Role superAdmin(Set<String> members) {
        return new Role("SUPER_ADMIN", "超级管理员", true, true, true,
                DataScope.COMPANY, Set.of(), members);
    }

    private static final class FakeRoleRepository implements RoleRepository {
        private final List<Role> roles = new ArrayList<>();
        private final List<PermissionDefinition> catalog;

        private FakeRoleRepository(List<PermissionDefinition> catalog) {
            this.catalog = List.copyOf(catalog);
        }

        void save(Role role) {
            roles.add(role);
        }

        @Override
        public List<Role> findEnabledByMemberKey(String memberKey) {
            return roles.stream()
                    .filter(Role::enabled)
                    .filter(role -> role.memberKeys().contains(memberKey))
                    .toList();
        }

        @Override
        public List<PermissionDefinition> findAllPermissions() {
            return catalog;
        }
    }
}
