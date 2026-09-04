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
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@TestPropertySource(properties = "erp.feishu.allowed-tenant-key=tenant-a")
@Transactional
class EmployeeProvisioningServiceTest {
    @Autowired
    private EmployeeProvisioningService service;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void createsFormalEmployeeWithoutMobileAndAssignsBasicRole() {
        var result = service.provision(
                identity("tenant-a", "open-new", "union-new", null),
                profile("open-new", null)
        );

        assertThat(result.account().employmentType()).isEqualTo(EmploymentType.FORMAL);
        assertThat(result.employee().employeeNo()).matches("FS-[A-Z0-9]{8}");
        assertThat(result.employee().profileComplete()).isFalse();
        assertThat(jdbc.queryForList("""
                select r.code
                from sys_user_role ur join sys_role r on r.id = ur.role_id
                where ur.user_id = ? and ur.assignment_source = 'FEISHU'
                """, String.class, result.account().id())).containsExactly("BASIC_EMPLOYEE");
    }

    @Test
    void mergesOneFormalEmployeeByNormalizedMobileAndReusesStableIdentity() {
        jdbc.update("""
                insert into employee
                    (employee_no, name, mobile, employment_type, status, source,
                     profile_complete, created_at, updated_at)
                values ('E-MERGE', '待绑定员工', '13900000002', 'formal', 'active', 'manual',
                        false, now(3), now(3))
                """);
        long employeeId = jdbc.queryForObject("select id from employee where employee_no = 'E-MERGE'", Long.class);
        jdbc.update("""
                insert into sys_user (employee_id, mobile, status, created_at, updated_at)
                values (?, '13900000002', 'enabled', now(3), now(3))
                """, employeeId);

        var first = service.provision(
                identity("tenant-a", "open-merge", "union-merge", "+86 139 0000 0002"),
                profile("open-merge", "+86 139 0000 0002")
        );
        var second = service.provision(
                identity("tenant-a", "open-merge", "union-merge", "+86 139 0000 0002"),
                profile("open-merge", "+86 139 0000 0002")
        );

        assertThat(first.employee().id()).isEqualTo(employeeId);
        assertThat(second.account().id()).isEqualTo(first.account().id());
        assertThat(jdbc.queryForObject(
                "select count(*) from sys_feishu_identity where tenant_key = 'tenant-a' and open_id = 'open-merge'",
                Integer.class
        )).isOne();
    }

    @Test
    void rejectsTemporaryEmployeeBindingAndForeignTenantBeforeWriting() {
        assertThatThrownBy(() -> service.provision(
                identity("tenant-a", "open-temp", "union-temp", "13800138000"),
                profile("open-temp", "13800138000")
        )).isInstanceOf(AuthException.class).extracting("code").isEqualTo("FEISHU_IDENTITY_CONFLICT");

        assertThatThrownBy(() -> service.provision(
                identity("tenant-other", "open-other", "union-other", null),
                profile("open-other", null)
        )).isInstanceOf(AuthException.class).extracting("code").isEqualTo("FEISHU_TENANT_NOT_ALLOWED");
        assertThat(jdbc.queryForObject(
                "select count(*) from sys_feishu_identity where open_id in ('open-temp', 'open-other')",
                Integer.class
        )).isZero();
    }

    @Test
    void refreshesAccountMobileAndRecomputesProfileCompleteness() {
        var oauth = identity("tenant-a", "open-refresh", "union-refresh", null);
        var first = service.provision(oauth, profile("open-refresh", null));
        jdbc.update("""
                insert into department
                    (department_code, department_name, feishu_department_id, sort_order, status, created_at, updated_at)
                values ('D-REFRESH', '资料补全部门', 'od-refresh', 0, 'enabled', now(3), now(3))
                """);
        long departmentId = jdbc.queryForObject(
                "select id from department where department_code = 'D-REFRESH'", Long.class);
        jdbc.update("""
                insert into position
                    (position_code, position_name, department_id, sort_order, status, created_at, updated_at)
                values ('P-REFRESH', '资料补全岗位', ?, 0, 'enabled', now(3), now(3))
                """, departmentId);
        long positionId = jdbc.queryForObject(
                "select id from position where position_code = 'P-REFRESH'", Long.class);
        jdbc.update("update employee set position_id = ? where id = ?", positionId, first.employee().id());

        var refreshed = service.provision(
                identity("tenant-a", "open-refresh", "union-refresh", "13900000008"),
                new FeishuEmployeeProfile(
                        "open-refresh", "E-REFRESH", "13900000008", "od-refresh", "已补全员工"
                )
        );

        assertThat(refreshed.employee().profileComplete()).isTrue();
        assertThat(refreshed.account().mobile()).isEqualTo("13900000008");
        assertThat(jdbc.queryForObject(
                "select mobile from sys_user where id = ?", String.class, refreshed.account().id()
        )).isEqualTo("13900000008");
    }

    private FeishuOAuthIdentity identity(String tenant, String open, String union, String mobile) {
        return new FeishuOAuthIdentity(tenant, open, union, "飞书员工", null, mobile);
    }

    private FeishuEmployeeProfile profile(String open, String mobile) {
        return new FeishuEmployeeProfile(open, null, mobile, null, "飞书员工");
    }
}
