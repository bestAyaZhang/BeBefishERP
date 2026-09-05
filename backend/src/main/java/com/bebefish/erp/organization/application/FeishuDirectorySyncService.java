package com.bebefish.erp.organization.application;

import com.bebefish.erp.feishu.*;
import com.bebefish.erp.identity.application.EmployeeProvisioningService;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.*;

@Service
public class FeishuDirectorySyncService {
    private final FeishuDirectoryClient client;
    private final FeishuProperties properties;
    private final EmployeeProvisioningService provisioning;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;

    public FeishuDirectorySyncService(
            FeishuDirectoryClient client,
            FeishuProperties properties,
            EmployeeProvisioningService provisioning,
            JdbcTemplate jdbc,
            PlatformTransactionManager transactionManager) {
        this.client = client;
        this.properties = properties;
        this.provisioning = provisioning;
        this.jdbc = jdbc;
        this.transactions = new TransactionTemplate(transactionManager);
    }

    public Result synchronize() {
        var counts = new Counts();
        try {
            if (!properties.isEnabled()) throw new FeishuClientException("飞书未启用");
            String tenant = client.currentTenantKey();
            if (tenant == null || !tenant.equals(properties.getAllowedTenantKey())) {
                throw new FeishuClientException("飞书企业不匹配");
            }
            List<FeishuDepartment> departments = client.departments();
            Map<String, Long> imported = importDepartments(departments, counts);
            Map<String, FeishuDirectoryUser> people = new LinkedHashMap<>();
            List<String> departmentIds = new ArrayList<>();
            departmentIds.add("0");
            departments.stream()
                    .map(FeishuDepartment::openDepartmentId)
                    .filter(id -> !"0".equals(id))
                    .distinct()
                    .forEach(departmentIds::add);
            for (String id : departmentIds) {
                for (FeishuDirectoryUser user : client.usersInDepartment(id)) {
                    if (people.putIfAbsent(user.openId(), user) != null) counts.skipped++;
                }
            }
            for (FeishuDirectoryUser user : people.values()) {
                try {
                    FeishuEmployeeProfile profile = user.profile();
                    var importedDepartments =
                            profile.departmentIds().stream().filter(imported::containsKey).toList();
                    var mapped =
                            new FeishuEmployeeProfile(
                                    profile.openId(),
                                    profile.employeeNo(),
                                    profile.mobile(),
                                    imported.containsKey(profile.primaryDepartmentId())
                                            ? profile.primaryDepartmentId()
                                            : null,
                                    profile.displayName(),
                                    importedDepartments,
                                    profile.employeeType(),
                                    profile.jobTitle(),
                                    profile.hireDate(),
                                    profile.activated(),
                                    profile.frozen(),
                                    profile.unjoined(),
                                    profile.resigned(),
                                    profile.exited());
                    // This call owns a short transaction; network and other users are outside it.
                    var merged = provisioning.mergeDirectory(user.identity(tenant), mapped);
                    if (merged.employeeCreated()) counts.employeesCreated++;
                    else counts.employeesUpdated++;
                } catch (RuntimeException exception) {
                    counts.failed++;
                }
            }
            for (FeishuDepartment department : departments) {
                Long id = imported.get(department.openDepartmentId());
                if (id == null) continue;
                try {
                    transactions.executeWithoutResult(
                            status -> {
                                Long manager =
                                        jdbc
                                                .queryForList(
                                                        """
select u.employee_id from sys_feishu_identity i join sys_user u on u.id=i.user_id
where i.tenant_key=? and i.open_id=?
""",
                                                        Long.class,
                                                        tenant,
                                                        department.leaderOpenId())
                                                .stream()
                                                .findFirst()
                                                .orElse(null);
                                jdbc.update(
                                        "update department set manager_employee_id=? where id=?",
                                        manager,
                                        id);
                            });
                } catch (RuntimeException exception) {
                    counts.failed++;
                }
            }
            return counts.result();
        } catch (RuntimeException exception) {
            throw new SyncFailure(counts.result(), exception);
        }
    }

    private Map<String, Long> importDepartments(List<FeishuDepartment> departments, Counts counts) {
        Map<String, Long> imported = new LinkedHashMap<>();
        Map<String, FeishuDepartment> remaining = new LinkedHashMap<>();
        for (FeishuDepartment department : departments) {
            if ("0".equals(department.openDepartmentId())) continue;
            if (remaining.putIfAbsent(department.openDepartmentId(), department) != null)
                counts.skipped++;
        }
        while (!remaining.isEmpty()) {
            boolean progress = false;
            var iterator = remaining.values().iterator();
            while (iterator.hasNext()) {
                FeishuDepartment department = iterator.next();
                String parent = department.parentDepartmentId();
                if (parent != null
                        && !parent.isBlank()
                        && !"0".equals(parent)
                        && !imported.containsKey(parent)) continue;
                iterator.remove();
                progress = true;
                try {
                    var saved =
                            transactions.execute(
                                    status -> upsertDepartment(department, imported.get(parent)));
                    imported.put(department.openDepartmentId(), saved.id());
                    if (saved.created()) counts.departmentsCreated++;
                    else counts.departmentsUpdated++;
                } catch (RuntimeException exception) {
                    counts.failed++;
                }
            }
            if (!progress) {
                counts.failed += remaining.size();
                break;
            }
        }
        return imported;
    }

    private SavedDepartment upsertDepartment(FeishuDepartment department, Long parentId) {
        var existing =
                jdbc.queryForList(
                        "select id from department where feishu_department_id=?",
                        Long.class,
                        department.openDepartmentId());
        if (!existing.isEmpty()) {
            long id = existing.getFirst();
            jdbc.update(
                    """
update department set department_name=?,parent_id=?,sort_order=?,updated_at=now(3) where id=?
""",
                    department.name(),
                    parentId,
                    department.order(),
                    id);
            return new SavedDepartment(id, false);
        }
        jdbc.update(
                """
insert into department(department_code,department_name,parent_id,feishu_department_id,
    sort_order,status,created_at,updated_at) values(?,?,?,?,?,'enabled',now(3),now(3))
""",
                FeishuStableCode.of("FS-D-", department.openDepartmentId()),
                department.name(),
                parentId,
                department.openDepartmentId(),
                department.order());
        return new SavedDepartment(
                jdbc.queryForObject(
                        "select id from department where feishu_department_id=?",
                        Long.class,
                        department.openDepartmentId()),
                true);
    }

    public record Result(
            int departmentsCreated,
            int departmentsUpdated,
            int employeesCreated,
            int employeesUpdated,
            int recordsSkipped,
            int recordsFailed) {}

    public static final class SyncFailure extends RuntimeException {
        private final Result result;

        public SyncFailure(Result result, RuntimeException cause) {
            super("飞书通讯录同步失败，请检查应用配置、通讯录权限和网络后重试", cause);
            this.result = result;
        }

        public Result result() {
            return result;
        }
    }

    private record SavedDepartment(long id, boolean created) {}

    private static final class Counts {
        int departmentsCreated;
        int departmentsUpdated;
        int employeesCreated;
        int employeesUpdated;
        int skipped;
        int failed;

        Result result() {
            return new Result(
                    departmentsCreated,
                    departmentsUpdated,
                    employeesCreated,
                    employeesUpdated,
                    skipped,
                    failed);
        }
    }
}
