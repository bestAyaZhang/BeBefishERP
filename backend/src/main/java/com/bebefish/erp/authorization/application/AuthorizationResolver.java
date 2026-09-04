package com.bebefish.erp.authorization.application;

import com.bebefish.erp.authorization.domain.DataScope;
import com.bebefish.erp.authorization.domain.Role;
import com.bebefish.erp.authorization.domain.RoleRepository;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;

@Service
public class AuthorizationResolver {
    private final RoleRepository roles;

    public AuthorizationResolver(RoleRepository roles) {
        this.roles = roles;
    }

    public ResolvedAuthorization resolve(String memberKey) {
        var assignedRoles = roles.findEnabledByMemberKey(memberKey).stream()
                .filter(Role::enabled)
                .filter(role -> role.memberKeys().contains(memberKey))
                .toList();
        var roleCodes = assignedRoles.stream()
                .map(Role::code)
                .distinct()
                .sorted()
                .toList();
        var permissionCodes = permissionCodesFor(assignedRoles);
        var dataScope = DataScope.widest(assignedRoles.stream().map(Role::dataScope).toList());

        return new ResolvedAuthorization(roleCodes, permissionCodes, dataScope);
    }

    private List<String> permissionCodesFor(List<Role> assignedRoles) {
        if (assignedRoles.stream().anyMatch(Role::superAdministrator)) {
            return roles.findAllPermissionCodes().stream().sorted().toList();
        }
        return assignedRoles.stream()
                .map(Role::permissionCodes)
                .flatMap(Set::stream)
                .distinct()
                .sorted()
                .toList();
    }
}
