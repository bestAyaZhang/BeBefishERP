package com.bebefish.erp.authorization.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bebefish.erp.authorization.api.PermissionDtos;
import com.bebefish.erp.common.api.BusinessException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class PermissionManagementServiceTest {
    @Autowired
    private PermissionManagementService service;

    @Test
    void createsAndConfiguresCustomRoleWithViewDependency() {
        var created = service.createRole(new PermissionDtos.CreateRoleRequest(
                "商品运营", "product_operator", "维护商品", null
        ));
        assertThat(created.code()).isEqualTo("PRODUCT_OPERATOR");
        assertThat(created.kind()).isEqualTo("custom");

        assertThatThrownBy(() -> service.saveConfiguration(
                created.id(),
                new PermissionDtos.SaveRoleConfigurationRequest(List.of("product:edit"), "department")
        )).isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo("PERMISSION_DEPENDENCY_VIOLATION");

        var configured = service.saveConfiguration(
                created.id(),
                new PermissionDtos.SaveRoleConfigurationRequest(
                        List.of("product:view", "product:edit"), "department-and-descendants"
                )
        );
        assertThat(configured.permissionCodes()).containsExactly("product:edit", "product:view");
        assertThat(configured.dataScope()).isEqualTo("department-and-descendants");
    }

    @Test
    void protectsSystemRolesAndUniqueCodes() {
        long superAdminId = service.listRoles(null).stream()
                .filter(role -> role.code().equals("SUPER_ADMIN"))
                .findFirst().orElseThrow().id();

        assertThatThrownBy(() -> service.updateRole(
                superAdminId,
                new PermissionDtos.UpdateRoleRequest("改名", "")
        )).isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo("SYSTEM_ROLE_IMMUTABLE");
        assertThatThrownBy(() -> service.createRole(new PermissionDtos.CreateRoleRequest(
                "重复", "SUPER_ADMIN", "", null
        ))).isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo("ROLE_CODE_DUPLICATE");
    }
}
