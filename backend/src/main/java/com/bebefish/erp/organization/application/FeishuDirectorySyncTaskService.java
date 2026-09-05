package com.bebefish.erp.organization.application;

import com.bebefish.erp.common.api.BusinessException;
import com.bebefish.erp.feishu.FeishuProperties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;

import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.concurrent.Executor;

@Service
public class FeishuDirectorySyncTaskService {
    private static final Logger log = LoggerFactory.getLogger(FeishuDirectorySyncTaskService.class);
    private final JdbcTemplate jdbc;
    private final FeishuDirectorySyncService sync;
    private final FeishuProperties properties;
    private final Clock clock;
    private final Executor executor;
    private final Object startLock = new Object();

    public FeishuDirectorySyncTaskService(
            JdbcTemplate jdbc,
            FeishuDirectorySyncService sync,
            FeishuProperties properties,
            Clock clock,
            @Qualifier("feishuDirectoryExecutor") Executor executor) {
        this.jdbc = jdbc;
        this.sync = sync;
        this.properties = properties;
        this.clock = clock;
        this.executor = executor;
    }

    public Task startManual(long userId) {
        return start(userId, "manual");
    }

    public void triggerAfterLogin(long userId) {
        try {
            start(userId, "login");
        } catch (RuntimeException exception) {
            log.warn("Could not schedule Feishu directory sync");
        }
    }

    private Task start(long userId, String trigger) {
        synchronized (startLock) {
            var active =
                    jdbc.query(
                            """
select * from sys_feishu_directory_sync where tenant_key=? and status in ('pending','running')
order by id desc limit 1
""",
                            this::map,
                            tenant());
            if (!active.isEmpty()) return active.getFirst();
            if ("login".equals(trigger)) {
                var recent =
                        jdbc.query(
                                """
select * from sys_feishu_directory_sync where tenant_key=? and status in ('success','partial')
and finished_at>=? order by id desc limit 1
""",
                                this::map,
                                tenant(),
                                Timestamp.from(Instant.now(clock).minusSeconds(1800)));
                if (!recent.isEmpty()) return recent.getFirst();
            }
            var keys = new GeneratedKeyHolder();
            jdbc.update(
                    connection -> {
                        var statement =
                                connection.prepareStatement(
                                        """
insert into sys_feishu_directory_sync(tenant_key,trigger_type,status,started_by_user_id,started_at)
values(?,?,'pending',?,?)
""",
                                        Statement.RETURN_GENERATED_KEYS);
                        statement.setString(1, tenant());
                        statement.setString(2, trigger);
                        statement.setLong(3, userId);
                        statement.setTimestamp(4, Timestamp.from(Instant.now(clock)));
                        return statement;
                    },
                    keys);
            long id = keys.getKey().longValue();
            Task pending = get(id);
            try {
                executor.execute(() -> execute(id));
            } catch (RuntimeException exception) {
                finish(id, "failed", emptyResult(), "后台同步任务无法启动，请重试");
                return get(id);
            }
            return pending;
        }
    }

    private void execute(long id) {
        try {
            if (jdbc.update(
                            "update sys_feishu_directory_sync set status='running' where id=? and"
                                + " status='pending'",
                            id)
                    != 1) return;
            var result = sync.synchronize();
            finish(id, result.recordsFailed() == 0 ? "success" : "partial", result, null);
        } catch (RuntimeException exception) {
            var result =
                    exception instanceof FeishuDirectorySyncService.SyncFailure failure
                            ? failure.result()
                            : emptyResult();
            finish(id, "failed", result, "飞书通讯录同步失败，请检查应用配置、通讯录权限和网络后重试");
        }
    }

    private void finish(
            long id, String status, FeishuDirectorySyncService.Result result, String error) {
        jdbc.update(
                """
update sys_feishu_directory_sync set status=?,finished_at=?,departments_created=?,departments_updated=?,
employees_created=?,employees_updated=?,records_skipped=?,records_failed=?,warning_message=?,error_message=?
where id=? and status in ('pending','running')
""",
                status,
                Timestamp.from(Instant.now(clock)),
                result.departmentsCreated(),
                result.departmentsUpdated(),
                result.employeesCreated(),
                result.employeesUpdated(),
                result.recordsSkipped(),
                result.recordsFailed(),
                result.recordsFailed() > 0 ? "部分记录因身份冲突、字段无效或部门层级问题未同步，请核对后重试" : null,
                error,
                id);
        log.info(
                "Feishu directory sync id={} status={} departments={} employees={} failed={}",
                id,
                status,
                result.departmentsCreated() + result.departmentsUpdated(),
                result.employeesCreated() + result.employeesUpdated(),
                result.recordsFailed());
    }

    @EventListener(ApplicationReadyEvent.class)
    public void recoverStaleTasks() {
        jdbc.update(
                """
                update sys_feishu_directory_sync set status='failed',finished_at=?,error_message=?
                where status in ('pending','running') and started_at<?
                """,
                Timestamp.from(Instant.now(clock)),
                "同步任务中断或超时，请重新发起",
                Timestamp.from(Instant.now(clock).minusSeconds(7200)));
    }

    public Task latest() {
        return jdbc
                .query(
                        "select * from sys_feishu_directory_sync where tenant_key=? order by id"
                            + " desc limit 1",
                        this::map,
                        tenant())
                .stream()
                .findFirst()
                .orElse(null);
    }

    public Task get(long id) {
        return jdbc
                .query(
                        "select * from sys_feishu_directory_sync where id=? and tenant_key=?",
                        this::map,
                        id,
                        tenant())
                .stream()
                .findFirst()
                .orElseThrow(
                        () -> new BusinessException("NOT_FOUND", HttpStatus.NOT_FOUND, "同步任务不存在"));
    }

    private Task map(java.sql.ResultSet rs, int row) throws java.sql.SQLException {
        Timestamp finished = rs.getTimestamp("finished_at");
        return new Task(
                rs.getLong("id"),
                rs.getString("trigger_type"),
                rs.getString("status"),
                rs.getTimestamp("started_at").toInstant(),
                finished == null ? null : finished.toInstant(),
                rs.getInt("departments_created"),
                rs.getInt("departments_updated"),
                rs.getInt("employees_created"),
                rs.getInt("employees_updated"),
                rs.getInt("records_skipped"),
                rs.getInt("records_failed"),
                rs.getString("warning_message"),
                rs.getString("error_message"));
    }

    private String tenant() {
        return properties.getAllowedTenantKey() == null ? "" : properties.getAllowedTenantKey();
    }

    private FeishuDirectorySyncService.Result emptyResult() {
        return new FeishuDirectorySyncService.Result(0, 0, 0, 0, 0, 0);
    }

    public record Task(
            long id,
            String triggerType,
            String status,
            Instant startedAt,
            Instant finishedAt,
            int departmentsCreated,
            int departmentsUpdated,
            int employeesCreated,
            int employeesUpdated,
            int recordsSkipped,
            int recordsFailed,
            String warningMessage,
            String errorMessage) {}
}
