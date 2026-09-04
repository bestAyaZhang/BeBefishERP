package com.bebefish.erp.authorization.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bebefish.erp.authorization.domain.DataScope;
import com.bebefish.erp.authorization.domain.Role;
import com.bebefish.erp.authorization.domain.RoleRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class AuthorizationResolverTest {
    private final FakeRoleRepository repository = new FakeRoleRepository(
            Set.of("product:view", "product:edit", "sales:view", "finance:view")
    );
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
                .containsExactlyElementsOf(repository.findAllPermissionCodes().stream().sorted().toList());
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
        private final Set<String> catalog;

        private FakeRoleRepository(Set<String> catalog) {
            this.catalog = Set.copyOf(catalog);
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
        public Set<String> findAllPermissionCodes() {
            return catalog;
        }
    }
}
