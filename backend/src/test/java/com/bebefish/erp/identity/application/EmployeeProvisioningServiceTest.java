package com.bebefish.erp.identity.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bebefish.erp.auth.application.AuthException;
import com.bebefish.erp.auth.domain.EmploymentType;
import com.bebefish.erp.feishu.FeishuEmployeeProfile;
import com.bebefish.erp.feishu.FeishuOAuthIdentity;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = "erp.feishu.allowed-tenant-key=tenant-a")
@Transactional
class EmployeeProvisioningServiceTest {
    @Autowired private EmployeeProvisioningService service;

    @Autowired private JdbcTemplate jdbc;

    @Test
    void createsFormalEmployeeWithoutMobileAndAssignsBasicRole() {
        var result =
                service.provision(
                        identity("tenant-a", "open-new", "union-new", null),
                        profile("open-new", null));

        assertThat(result.account().employmentType()).isEqualTo(EmploymentType.FORMAL);
        assertThat(result.employee().employeeNo()).matches("FS-U-[A-F0-9]{24}");
        assertThat(result.employee().profileComplete()).isFalse();
        assertThat(
                        jdbc.queryForList(
                                """
                                select r.code
                                from sys_user_role ur join sys_role r on r.id = ur.role_id
                                where ur.user_id = ? and ur.assignment_source = 'FEISHU'
                                """,
                                String.class,
                                result.account().id()))
                .containsExactly("BASIC_EMPLOYEE");
    }

    @Test
    void mergesOneFormalEmployeeByNormalizedMobileAndReusesStableIdentity() {
        jdbc.update(
                """
                insert into employee
                    (employee_no, name, mobile, employment_type, status, source,
                     profile_complete, created_at, updated_at)
                values ('E-MERGE', '待绑定员工', '13900000002', 'formal', 'active', 'manual',
                        false, now(3), now(3))
                """);
        long employeeId =
                jdbc.queryForObject(
                        "select id from employee where employee_no = 'E-MERGE'", Long.class);
        jdbc.update(
                """
                insert into sys_user (employee_id, mobile, status, created_at, updated_at)
                values (?, '13900000002', 'enabled', now(3), now(3))
                """,
                employeeId);

        var first =
                service.provision(
                        identity("tenant-a", "open-merge", "union-merge", "+86 139 0000 0002"),
                        profile("open-merge", "+86 139 0000 0002"));
        var second =
                service.provision(
                        identity("tenant-a", "open-merge", "union-merge", "+86 139 0000 0002"),
                        profile("open-merge", "+86 139 0000 0002"));

        assertThat(first.employee().id()).isEqualTo(employeeId);
        assertThat(second.account().id()).isEqualTo(first.account().id());
        assertThat(
                        jdbc.queryForObject(
                                "select count(*) from sys_feishu_identity where tenant_key ="
                                    + " 'tenant-a' and open_id = 'open-merge'",
                                Integer.class))
                .isOne();
    }

    @Test
    void rejectsTemporaryEmployeeBindingAndForeignTenantBeforeWriting() {
        assertThatThrownBy(
                        () ->
                                service.provision(
                                        identity(
                                                "tenant-a",
                                                "open-temp",
                                                "union-temp",
                                                "13800138000"),
                                        profile("open-temp", "13800138000")))
                .isInstanceOf(AuthException.class)
                .extracting("code")
                .isEqualTo("FEISHU_IDENTITY_CONFLICT");

        assertThatThrownBy(
                        () ->
                                service.provision(
                                        identity("tenant-other", "open-other", "union-other", null),
                                        profile("open-other", null)))
                .isInstanceOf(AuthException.class)
                .extracting("code")
                .isEqualTo("FEISHU_TENANT_NOT_ALLOWED");
        assertThat(
                        jdbc.queryForObject(
                                "select count(*) from sys_feishu_identity where open_id in"
                                    + " ('open-temp', 'open-other')",
                                Integer.class))
                .isZero();
    }

    @Test
    void refreshesAccountMobileAndRecomputesProfileCompleteness() {
        var oauth = identity("tenant-a", "open-refresh", "union-refresh", null);
        var first = service.provision(oauth, profile("open-refresh", null));
        jdbc.update(
                """
insert into department
    (department_code, department_name, feishu_department_id, sort_order, status, created_at, updated_at)
values ('D-REFRESH', '资料补全部门', 'od-refresh', 0, 'enabled', now(3), now(3))
""");
        long departmentId =
                jdbc.queryForObject(
                        "select id from department where department_code = 'D-REFRESH'",
                        Long.class);
        jdbc.update(
                """
insert into position
    (position_code, position_name, department_id, sort_order, status, created_at, updated_at)
values ('P-REFRESH', '资料补全岗位', ?, 0, 'enabled', now(3), now(3))
""",
                departmentId);
        long positionId =
                jdbc.queryForObject(
                        "select id from position where position_code = 'P-REFRESH'", Long.class);
        jdbc.update(
                "update employee set position_id = ? where id = ?",
                positionId,
                first.employee().id());

        var refreshed =
                service.provision(
                        identity("tenant-a", "open-refresh", "union-refresh", "13900000008"),
                        new FeishuEmployeeProfile(
                                "open-refresh", "E-REFRESH", "13900000008", "od-refresh", "已补全员工"));

        assertThat(refreshed.employee().profileComplete()).isTrue();
        assertThat(refreshed.account().mobile()).isEqualTo("13900000008");
        assertThat(
                        jdbc.queryForObject(
                                "select mobile from sys_user where id = ?",
                                String.class,
                                refreshed.account().id()))
                .isEqualTo("13900000008");
    }

    @Test
    void refusesUnionAndOpenIdsThatPointToDifferentAccounts() {
        var a =
                service.provision(
                        identity("tenant-a", "safe-a", "union-a", null), profile("safe-a", null));
        var b =
                service.provision(
                        identity("tenant-a", "safe-b", "union-b", null), profile("safe-b", null));
        assertThatThrownBy(
                        () ->
                                service.provision(
                                        identity("tenant-a", "safe-b", "union-a", null),
                                        profile("safe-b", null)))
                .isInstanceOf(AuthException.class)
                .extracting("code")
                .isEqualTo("FEISHU_IDENTITY_CONFLICT");
        assertThat(
                        jdbc.queryForObject(
                                "select open_id from sys_feishu_identity where user_id=?",
                                String.class,
                                a.account().id()))
                .isEqualTo("safe-a");
    }

    @Test
    void directoryMergePreservesLocalFieldsAndManualDisableAndUsesStableCollisionFallback() {
        var first =
                service.mergeDirectory(
                        identity("tenant-a", "directory-a", "directory-union", null),
                        fullProfile("directory-a", "COLLISION", null, 2, null, false));
        long employeeId = first.employee().id();
        long userId = first.account().id();
        jdbc.update("update sys_user set password_hash='existing-hash' where id=?", userId);
        jdbc.update(
                "update employee set status='disabled',status_source='manual' where id=?",
                employeeId);
        jdbc.update(
                "insert into sys_user_role(user_id,role_id,assignment_source,assigned_at) select"
                    + " ?,id,'LOCAL',now(3) from sys_role where code='SUPER_ADMIN'",
                userId);
        var second =
                service.mergeDirectory(
                        identity("tenant-a", "directory-a", "directory-union", null),
                        fullProfile("directory-a", "COLLISION", null, 2, true, false));
        assertThat(second.employee().id()).isEqualTo(employeeId);
        assertThat(second.employee().status().name()).isEqualTo("DISABLED");
        assertThat(second.employee().employmentType()).isEqualTo(EmploymentType.TEMPORARY);
        assertThat(second.employee().feishuJobTitle()).isEqualTo("Job title");
        assertThat(second.employee().hireDate()).isEqualTo(java.time.LocalDate.of(2025, 1, 1));
        assertThat(
                        jdbc.queryForObject(
                                "select password_hash from sys_user where id=?",
                                String.class,
                                userId))
                .isEqualTo("existing-hash");
        assertThat(
                        jdbc.queryForObject(
                                "select count(*) from sys_user_role where user_id=? and"
                                    + " assignment_source='LOCAL'",
                                Integer.class,
                                userId))
                .isOne();
        var collision =
                service.mergeDirectory(
                        identity("tenant-a", "directory-b", null, null),
                        fullProfile("directory-b", "COLLISION", null, 1, null, false));
        assertThat(collision.employee().employeeNo()).matches("FS-U-[A-F0-9]{24}");
        assertThat(
                        service.mergeDirectory(
                                        identity("tenant-a", "directory-b", null, null),
                                        fullProfile(
                                                "directory-b", "COLLISION", null, 1, null, false))
                                .employee()
                                .employeeNo())
                .isEqualTo(collision.employee().employeeNo());
    }

    @Test
    void explicitNegativeStatusDisablesAndUnknownActivationDoesNotDisableNewUser() {
        var oauth = identity("tenant-a", "status-a", null, null);
        var first =
                service.mergeDirectory(oauth, fullProfile("status-a", null, null, 1, null, false));
        assertThat(first.account().enabled()).isTrue();
        var negative =
                service.mergeDirectory(oauth, fullProfile("status-a", null, null, 1, false, true));
        assertThat(negative.employee().status().name()).isEqualTo("RESIGNED");
        assertThat(negative.account().enabled()).isFalse();
        assertThat(
                        jdbc.queryForObject(
                                "select status from sys_user where id=?",
                                String.class,
                                first.account().id()))
                .isEqualTo("disabled");
    }

    @Test
    void ambiguousNormalizedMobileAndAlreadyBoundMobileAreRejected() {
        jdbc.update(
                "insert into employee"
                    + " (employee_no,name,mobile,employment_type,status,source,profile_complete,created_at,updated_at)"
                    + " values('AMB-A','A','+86 139 1234"
                    + " 5678','formal','active','manual',false,now(3),now(3)),('AMB-B','B','13912345678','formal','active','manual',false,now(3),now(3))");
        assertThatThrownBy(
                        () ->
                                service.mergeDirectory(
                                        identity("tenant-a", "amb-open", null, "13912345678"),
                                        profile("amb-open", "13912345678")))
                .isInstanceOf(AuthException.class)
                .extracting("code")
                .isEqualTo("FEISHU_IDENTITY_CONFLICT");
        service.mergeDirectory(
                identity("tenant-a", "bound-a", null, "13999990001"),
                profile("bound-a", "13999990001"));
        assertThatThrownBy(
                        () ->
                                service.mergeDirectory(
                                        identity("tenant-a", "bound-b", null, "13999990001"),
                                        profile("bound-b", "13999990001")))
                .isInstanceOf(AuthException.class)
                .extracting("code")
                .isEqualTo("FEISHU_IDENTITY_CONFLICT");
        assertThat(
                        jdbc.queryForObject(
                                "select count(*) from sys_feishu_identity where open_id in"
                                    + " ('amb-open','bound-b')",
                                Integer.class))
                .isZero();
    }

    private FeishuEmployeeProfile fullProfile(
            String open,
            String number,
            String mobile,
            int type,
            Boolean activated,
            boolean resigned) {
        return new FeishuEmployeeProfile(
                open,
                number,
                mobile,
                null,
                "Directory employee",
                java.util.List.of(),
                type,
                "Job title",
                java.time.LocalDate.of(2025, 1, 1),
                activated,
                false,
                false,
                resigned,
                false);
    }

    private FeishuOAuthIdentity identity(String tenant, String open, String union, String mobile) {
        return new FeishuOAuthIdentity(tenant, open, union, "飞书员工", null, mobile);
    }

    private FeishuEmployeeProfile profile(String open, String mobile) {
        return new FeishuEmployeeProfile(open, null, mobile, null, "飞书员工");
    }
}
