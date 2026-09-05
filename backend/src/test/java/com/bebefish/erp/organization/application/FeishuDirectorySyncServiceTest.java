package com.bebefish.erp.organization.application;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.bebefish.erp.feishu.*;
import com.bebefish.erp.identity.application.EmployeeProvisioningService;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = "erp.feishu.allowed-tenant-key=tenant-a")
@Transactional
class FeishuDirectorySyncServiceTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired EmployeeProvisioningService provisioning;
    @Autowired PlatformTransactionManager transactionManager;

    @Test
    @Transactional(
            propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    void oneBadEmployeeDoesNotRollbackOthersAndMissingUsersAreNotDisabled() {
        var client = mock(FeishuDirectoryClient.class);
        var properties = new FeishuProperties();
        properties.setEnabled(true);
        properties.setAllowedTenantKey("tenant-a");
        when(client.currentTenantKey()).thenReturn("tenant-a");
        when(client.departments()).thenReturn(List.of());
        var goodA =
                new FeishuDirectoryUser(
                        "partial-a",
                        null,
                        null,
                        new FeishuEmployeeProfile("partial-a", null, null, null, "Good A"));
        var bad =
                new FeishuDirectoryUser(
                        "partial-bad",
                        null,
                        null,
                        new FeishuEmployeeProfile(
                                "partial-bad", null, null, null, "x".repeat(101)));
        var goodB =
                new FeishuDirectoryUser(
                        "partial-b",
                        null,
                        null,
                        new FeishuEmployeeProfile("partial-b", null, null, null, "Good B"));
        when(client.usersInDepartment("0")).thenReturn(List.of(goodA, bad, goodB));
        var service =
                new FeishuDirectorySyncService(
                        client, properties, provisioning, jdbc, transactionManager);
        try {
            var result = service.synchronize();
            assertThat(result.employeesCreated()).isEqualTo(2);
            assertThat(result.recordsFailed()).isOne();
            when(client.usersInDepartment("0")).thenReturn(List.of());
            service.synchronize();
            assertThat(
                            jdbc.queryForObject(
                                    "select count(*) from employee e join sys_user u on"
                                        + " u.employee_id=e.id join sys_feishu_identity i on"
                                        + " i.user_id=u.id where i.open_id in"
                                        + " ('partial-a','partial-b') and e.status='active'",
                                    Integer.class))
                    .isEqualTo(2);
        } finally {
            for (Long userId :
                    jdbc.queryForList(
                            "select user_id from sys_feishu_identity where open_id in"
                                + " ('partial-a','partial-b','partial-bad')",
                            Long.class)) {
                long employeeId =
                        jdbc.queryForObject(
                                "select employee_id from sys_user where id=?", Long.class, userId);
                jdbc.update("delete from sys_feishu_identity where user_id=?", userId);
                jdbc.update("delete from sys_user_role where user_id=?", userId);
                jdbc.update("delete from sys_user where id=?", userId);
                jdbc.update("delete from employee where id=?", employeeId);
            }
        }
    }

    @Test
    void importsParentFirstDeduplicatesRootUsersAndBackfillsManagerWithoutCreatingRoot() {
        var client = mock(FeishuDirectoryClient.class);
        var properties = new FeishuProperties();
        properties.setEnabled(true);
        properties.setAllowedTenantKey("tenant-a");
        when(client.currentTenantKey()).thenReturn("tenant-a");
        when(client.departments())
                .thenReturn(
                        List.of(
                                new FeishuDepartment(
                                        "sync-child", "sync-parent", "Child", "sync-person", 2),
                                new FeishuDepartment("sync-parent", "0", "Parent", null, 1)));
        var user =
                new FeishuDirectoryUser(
                        "sync-person",
                        "sync-union",
                        null,
                        new FeishuEmployeeProfile(
                                "sync-person",
                                null,
                                null,
                                "sync-child",
                                "Synced",
                                List.of("sync-parent", "sync-child"),
                                1,
                                "Manager",
                                null,
                                null,
                                false,
                                false,
                                false,
                                false));
        when(client.usersInDepartment("0")).thenReturn(List.of(user));
        when(client.usersInDepartment("sync-parent")).thenReturn(List.of(user));
        when(client.usersInDepartment("sync-child")).thenReturn(List.of(user));
        var service =
                new FeishuDirectorySyncService(
                        client, properties, provisioning, jdbc, transactionManager);
        var result = service.synchronize();
        assertThat(result.departmentsCreated()).isEqualTo(2);
        assertThat(result.employeesCreated()).isOne();
        assertThat(result.recordsSkipped()).isEqualTo(2);
        long child =
                jdbc.queryForObject(
                        "select id from department where feishu_department_id='sync-child'",
                        Long.class);
        long employee =
                jdbc.queryForObject(
                        "select employee_id from sys_user u join sys_feishu_identity i on"
                            + " i.user_id=u.id where i.open_id='sync-person'",
                        Long.class);
        assertThat(
                        jdbc.queryForObject(
                                "select department_id from employee where id=?",
                                Long.class,
                                employee))
                .isEqualTo(child);
        assertThat(
                        jdbc.queryForObject(
                                "select manager_employee_id from department where id=?",
                                Long.class,
                                child))
                .isEqualTo(employee);
        assertThat(
                        jdbc.queryForObject(
                                "select count(*) from department where feishu_department_id='0'",
                                Integer.class))
                .isZero();
        assertThat(service.synchronize().employeesUpdated()).isOne();
    }

    @Test
    void preservesTheUpstreamFailureForOperationalDiagnosis() {
        var client = mock(FeishuDirectoryClient.class);
        var properties = new FeishuProperties();
        properties.setEnabled(true);
        properties.setAllowedTenantKey("tenant-a");
        when(client.currentTenantKey()).thenReturn("tenant-a");
        var upstream = new FeishuClientException("diagnostic marker");
        when(client.departments()).thenThrow(upstream);
        var service =
                new FeishuDirectorySyncService(
                        client, properties, provisioning, jdbc, transactionManager);

        assertThatThrownBy(service::synchronize)
                .isInstanceOf(FeishuDirectorySyncService.SyncFailure.class)
                .hasCause(upstream);
    }
}
