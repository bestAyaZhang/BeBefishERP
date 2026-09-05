package com.bebefish.erp.organization.application;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.bebefish.erp.feishu.FeishuProperties;

import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.util.ArrayList;
import java.util.List;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class FeishuDirectorySyncTaskServiceTest {
    @Autowired JdbcTemplate jdbc;
    FeishuDirectorySyncService sync;
    FeishuDirectorySyncTaskService tasks;
    List<Runnable> queue;
    long userId;
    Instant now = Instant.parse("2026-09-05T08:00:00Z");

    @BeforeEach
    void setup() {
        jdbc.update("delete from sys_feishu_directory_sync");
        userId = jdbc.queryForObject("select id from sys_user order by id limit 1", Long.class);
        var properties = new FeishuProperties();
        properties.setAllowedTenantKey("tenant-task");
        sync = mock(FeishuDirectorySyncService.class);
        queue = new ArrayList<>();
        tasks =
                new FeishuDirectorySyncTaskService(
                        jdbc, sync, properties, Clock.fixed(now, ZoneOffset.UTC), queue::add);
    }

    @Test
    void coalescesStartsAndLoginReturnsWithoutRunningQueuedWork() {
        var first = tasks.startManual(userId);
        var second = tasks.startManual(userId);
        tasks.triggerAfterLogin(userId);
        assertThat(second.id()).isEqualTo(first.id());
        assertThat(first.status()).isEqualTo("pending");
        assertThat(queue).hasSize(1);
        verifyNoInteractions(sync);
        when(sync.synchronize())
                .thenReturn(new FeishuDirectorySyncService.Result(1, 2, 3, 4, 5, 1));
        queue.removeFirst().run();
        assertThat(tasks.get(first.id()).status()).isEqualTo("partial");
        assertThat(tasks.get(first.id()).employeesCreated()).isEqualTo(3);
        tasks.triggerAfterLogin(userId);
        assertThat(queue).isEmpty();
        jdbc.update(
                "update sys_feishu_directory_sync set finished_at=? where id=?",
                java.sql.Timestamp.from(now.minusSeconds(1801)),
                first.id());
        tasks.triggerAfterLogin(userId);
        assertThat(queue).hasSize(1);
    }

    @Test
    void persistsSanitizedFailureAndRecoversOnlyStaleJobs() {
        when(sync.synchronize())
                .thenThrow(new IllegalStateException("secret token phone 13800000000"));
        var first = tasks.startManual(userId);
        queue.removeFirst().run();
        assertThat(tasks.get(first.id()).status()).isEqualTo("failed");
        assertThat(tasks.get(first.id()).errorMessage()).doesNotContain("secret", "13800000000");
        var fresh = tasks.startManual(userId);
        tasks.recoverStaleTasks();
        assertThat(tasks.get(fresh.id()).status()).isEqualTo("pending");
        jdbc.update(
                "update sys_feishu_directory_sync set started_at=? where id=?",
                java.sql.Timestamp.from(now.minusSeconds(7201)),
                fresh.id());
        tasks.recoverStaleTasks();
        assertThat(tasks.get(fresh.id()).status()).isEqualTo("failed");
    }

    @Test
    @Transactional(
            propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    void simultaneousStartsPersistOnePendingTask() {
        var properties = new FeishuProperties();
        properties.setAllowedTenantKey("tenant-concurrent-tasks");
        var concurrentTasks =
                new FeishuDirectorySyncTaskService(
                        jdbc, sync, properties, Clock.fixed(now, ZoneOffset.UTC), queue::add);
        try (var callers = java.util.concurrent.Executors.newFixedThreadPool(8)) {
            var start = new java.util.concurrent.CountDownLatch(1);
            var futures =
                    java.util.stream.IntStream.range(0, 8)
                            .mapToObj(
                                    index ->
                                            callers.submit(
                                                    () -> {
                                                        start.await();
                                                        return concurrentTasks
                                                                .startManual(userId)
                                                                .id();
                                                    }))
                            .toList();
            start.countDown();
            var ids =
                    futures.stream()
                            .map(
                                    future -> {
                                        try {
                                            return future.get(
                                                    5, java.util.concurrent.TimeUnit.SECONDS);
                                        } catch (Exception exception) {
                                            throw new AssertionError(exception);
                                        }
                                    })
                            .distinct()
                            .toList();
            assertThat(ids).hasSize(1);
            assertThat(queue).hasSize(1);
            assertThat(
                            jdbc.queryForObject(
                                    "select count(*) from sys_feishu_directory_sync where"
                                        + " tenant_key='tenant-concurrent-tasks'",
                                    Integer.class))
                    .isOne();
        } finally {
            jdbc.update(
                    "delete from sys_feishu_directory_sync where"
                        + " tenant_key='tenant-concurrent-tasks'");
        }
    }

    @Test
    void executorRejectionPersistsFailureAndSuccessThrottles() {
        var properties = new FeishuProperties();
        properties.setAllowedTenantKey("tenant-task");
        var rejecting =
                new FeishuDirectorySyncTaskService(
                        jdbc,
                        sync,
                        properties,
                        Clock.fixed(now, ZoneOffset.UTC),
                        command -> {
                            throw new java.util.concurrent.RejectedExecutionException("secret");
                        });
        assertThat(rejecting.startManual(userId).status()).isEqualTo("failed");
        when(sync.synchronize())
                .thenReturn(new FeishuDirectorySyncService.Result(0, 0, 0, 0, 0, 0));
        var task = tasks.startManual(userId);
        queue.removeFirst().run();
        assertThat(tasks.get(task.id()).status()).isEqualTo("success");
        tasks.triggerAfterLogin(userId);
        assertThat(queue).isEmpty();
    }
}
