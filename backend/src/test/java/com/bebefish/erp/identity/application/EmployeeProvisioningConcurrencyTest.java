package com.bebefish.erp.identity.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.bebefish.erp.feishu.FeishuEmployeeProfile;
import com.bebefish.erp.feishu.FeishuOAuthIdentity;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.stream.IntStream;

@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = "erp.feishu.allowed-tenant-key=tenant-a")
class EmployeeProvisioningConcurrencyTest {
    private static final String OPEN_ID = "open-concurrent";
    private static final String UNION_ID = "union-concurrent";

    @Autowired private EmployeeProvisioningService service;

    @Autowired private JdbcTemplate jdbc;

    @Autowired
    private org.springframework.transaction.PlatformTransactionManager transactionManager;

    @org.springframework.boot.test.mock.mockito.SpyBean
    private com.bebefish.erp.identity.domain.EmployeeRepository employees;

    @BeforeEach
    @AfterEach
    void cleanFixture() {
        var userIds =
                jdbc.queryForList(
                        "select user_id from sys_feishu_identity where tenant_key = 'tenant-a' and"
                            + " open_id = ?",
                        Long.class,
                        OPEN_ID);
        for (Long userId : userIds) {
            Long employeeId =
                    jdbc.queryForObject(
                            "select employee_id from sys_user where id = ?", Long.class, userId);
            jdbc.update("delete from sys_feishu_identity where user_id = ?", userId);
            jdbc.update("delete from sys_user_role where user_id = ?", userId);
            jdbc.update("delete from sys_user where id = ?", userId);
            jdbc.update("delete from employee where id = ?", employeeId);
        }
    }

    @Test
    void waitsForManualEditAndPreservesDisablePasswordAndPosition() throws Exception {
        var oauth =
                new FeishuOAuthIdentity(
                        "tenant-a", OPEN_ID, UNION_ID, "Concurrent employee", null, null);
        var profile = new FeishuEmployeeProfile(OPEN_ID, null, null, null, "Concurrent employee");
        var first = service.provision(oauth, profile);
        jdbc.update(
                "insert into position"
                    + " (position_code,position_name,sort_order,status,created_at,updated_at)"
                    + " values('SYNC-LOCK-P','Manual position',0,'enabled',now(3),now(3))");
        long positionId =
                jdbc.queryForObject(
                        "select id from position where position_code='SYNC-LOCK-P'", Long.class);
        var manualHasLock = new java.util.concurrent.CountDownLatch(1);
        var releaseManual = new java.util.concurrent.CountDownLatch(1);
        var syncReadsEmployee = new java.util.concurrent.CountDownLatch(1);
        org.mockito.Mockito.doAnswer(
                        invocation -> {
                            syncReadsEmployee.countDown();
                            return invocation.callRealMethod();
                        })
                .when(employees)
                .findByIdForUpdate(first.employee().id());
        try (var executor = Executors.newFixedThreadPool(2)) {
            var manual =
                    executor.submit(
                            () ->
                                    new org.springframework.transaction.support.TransactionTemplate(
                                                    transactionManager)
                                            .executeWithoutResult(
                                                    status -> {
                                                        jdbc.update(
                                                                "update employee set"
                                                                    + " status='disabled',status_source='manual',position_id=?"
                                                                    + " where id=?",
                                                                positionId,
                                                                first.employee().id());
                                                        jdbc.update(
                                                                "update sys_user set"
                                                                    + " status='disabled',password_hash='manual-password'"
                                                                    + " where id=?",
                                                                first.account().id());
                                                        manualHasLock.countDown();
                                                        try {
                                                            if (!releaseManual.await(
                                                                    5,
                                                                    java.util.concurrent.TimeUnit
                                                                            .SECONDS))
                                                                throw new AssertionError(
                                                                        "release timeout");
                                                        } catch (InterruptedException exception) {
                                                            throw new RuntimeException(exception);
                                                        }
                                                    }));
            assertThat(manualHasLock.await(5, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            var sync = executor.submit(() -> service.mergeDirectory(oauth, profile));
            assertThat(syncReadsEmployee.await(5, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            Thread.sleep(100);
            releaseManual.countDown();
            manual.get(5, java.util.concurrent.TimeUnit.SECONDS);
            var result = sync.get(5, java.util.concurrent.TimeUnit.SECONDS);
            assertThat(result.employee().status().name()).isEqualTo("DISABLED");
            assertThat(result.employee().positionId()).isEqualTo(positionId);
            assertThat(result.account().passwordHash()).isEqualTo("manual-password");
        } finally {
            releaseManual.countDown();
            jdbc.update("update employee set position_id=null where id=?", first.employee().id());
            jdbc.update("delete from position where id=?", positionId);
        }
    }

    @Test
    void changedMobileWhileWaitingForLockCannotBindTheWrongEmployee() throws Exception {
        var original =
                new FeishuOAuthIdentity(
                        "tenant-a", OPEN_ID, UNION_ID, "Employee", null, "13900007771");
        var first =
                service.provision(
                        original,
                        new FeishuEmployeeProfile(OPEN_ID, null, "13900007771", null, "Employee"));
        jdbc.update("delete from sys_feishu_identity where user_id=?", first.account().id());
        var locked = new java.util.concurrent.CountDownLatch(1);
        var release = new java.util.concurrent.CountDownLatch(1);
        var reading = new java.util.concurrent.CountDownLatch(1);
        org.mockito.Mockito.doAnswer(
                        invocation -> {
                            reading.countDown();
                            return invocation.callRealMethod();
                        })
                .when(employees)
                .findByIdForUpdate(first.employee().id());
        try (var executor = Executors.newFixedThreadPool(2)) {
            var manual =
                    executor.submit(
                            () ->
                                    new org.springframework.transaction.support.TransactionTemplate(
                                                    transactionManager)
                                            .executeWithoutResult(
                                                    status -> {
                                                        jdbc.update(
                                                                "update employee set"
                                                                    + " mobile='13900007772' where"
                                                                    + " id=?",
                                                                first.employee().id());
                                                        jdbc.update(
                                                                "update sys_user set"
                                                                    + " mobile='13900007772' where"
                                                                    + " id=?",
                                                                first.account().id());
                                                        locked.countDown();
                                                        try {
                                                            release.await(
                                                                    5,
                                                                    java.util.concurrent.TimeUnit
                                                                            .SECONDS);
                                                        } catch (InterruptedException exception) {
                                                            throw new RuntimeException(exception);
                                                        }
                                                    }));
            assertThat(locked.await(5, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            var sync =
                    executor.submit(
                            () ->
                                    service.mergeDirectory(
                                            original,
                                            new FeishuEmployeeProfile(
                                                    OPEN_ID,
                                                    null,
                                                    "13900007771",
                                                    null,
                                                    "Employee")));
            assertThat(reading.await(5, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            Thread.sleep(100);
            release.countDown();
            manual.get(5, java.util.concurrent.TimeUnit.SECONDS);
            org.assertj.core.api.Assertions.assertThatThrownBy(
                            () -> sync.get(5, java.util.concurrent.TimeUnit.SECONDS))
                    .hasCauseInstanceOf(com.bebefish.erp.auth.application.AuthException.class);
            assertThat(
                            jdbc.queryForObject(
                                    "select count(*) from sys_feishu_identity where user_id=?",
                                    Integer.class,
                                    first.account().id()))
                    .isZero();
        } finally {
            release.countDown();
            jdbc.update("delete from sys_feishu_identity where user_id=?", first.account().id());
            jdbc.update("delete from sys_user_role where user_id=?", first.account().id());
            jdbc.update("delete from sys_user where id=?", first.account().id());
            jdbc.update("delete from employee where id=?", first.employee().id());
        }
    }

    @Test
    void loginRejectionLeavesExplicitNegativeStatusCommitted() {
        var oauth = new FeishuOAuthIdentity("tenant-a", OPEN_ID, UNION_ID, "Employee", null, null);
        var first =
                service.provision(
                        oauth, new FeishuEmployeeProfile(OPEN_ID, null, null, null, "Employee"));
        var negative =
                new FeishuEmployeeProfile(
                        OPEN_ID,
                        null,
                        null,
                        null,
                        "Employee",
                        java.util.List.of(),
                        1,
                        null,
                        null,
                        false,
                        false,
                        false,
                        false,
                        false);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.provision(oauth, negative))
                .isInstanceOf(com.bebefish.erp.auth.application.AuthException.class)
                .extracting("code")
                .isEqualTo("USER_DISABLED");
        assertThat(
                        jdbc.queryForObject(
                                "select status from employee where id=?",
                                String.class,
                                first.employee().id()))
                .isEqualTo("disabled");
        assertThat(
                        jdbc.queryForObject(
                                "select status from sys_user where id=?",
                                String.class,
                                first.account().id()))
                .isEqualTo("disabled");
    }

    @Test
    void tenConcurrentFirstLoginsCreateOneEmployeeAndAccount() {
        var identity = new FeishuOAuthIdentity("tenant-a", OPEN_ID, UNION_ID, "并发员工", null, null);
        var profile = new FeishuEmployeeProfile(OPEN_ID, null, null, null, "并发员工");

        try (var executor = Executors.newFixedThreadPool(10)) {
            var futures =
                    IntStream.range(0, 10)
                            .mapToObj(
                                    index ->
                                            CompletableFuture.supplyAsync(
                                                    () -> service.provision(identity, profile),
                                                    executor))
                            .toList();
            var userIds =
                    futures.stream()
                            .map(CompletableFuture::join)
                            .map(result -> result.account().id())
                            .distinct()
                            .toList();

            assertThat(userIds).hasSize(1);
            assertThat(
                            jdbc.queryForObject(
                                    "select count(*) from sys_feishu_identity where tenant_key ="
                                        + " 'tenant-a' and open_id = ?",
                                    Integer.class,
                                    OPEN_ID))
                    .isOne();
        }
    }
}
