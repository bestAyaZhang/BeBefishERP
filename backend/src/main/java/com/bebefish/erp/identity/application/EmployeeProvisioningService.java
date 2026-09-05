package com.bebefish.erp.identity.application;

import com.bebefish.erp.auth.application.AuthException;
import com.bebefish.erp.auth.domain.*;
import com.bebefish.erp.authorization.application.FeishuRoleSyncService;
import com.bebefish.erp.feishu.*;
import com.bebefish.erp.identity.domain.*;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

@Service
public class EmployeeProvisioningService {
    private final EmployeeRepository employees;
    private final FeishuIdentityRepository identities;
    private final UserAccountRepository users;
    private final FeishuRoleSyncService roleSync;
    private final FeishuProperties properties;
    private final Clock clock;
    private final TransactionTemplate transactions;
    // Shared by login and directory imports; no network I/O is performed under this lock.
    private final Object mergeLock = new Object();

    public EmployeeProvisioningService(
            EmployeeRepository employees,
            FeishuIdentityRepository identities,
            UserAccountRepository users,
            FeishuRoleSyncService roleSync,
            FeishuProperties properties,
            Clock clock,
            PlatformTransactionManager transactionManager) {
        this.employees = employees;
        this.identities = identities;
        this.users = users;
        this.roleSync = roleSync;
        this.properties = properties;
        this.clock = clock;
        this.transactions = new TransactionTemplate(transactionManager);
    }

    public ProvisionedUser provision(FeishuOAuthIdentity oauth, FeishuEmployeeProfile profile) {
        // Reject local temporary password accounts before binding; directory imports can bind them.
        var result = merge(oauth, profile, true);
        if (result.employee().employmentType() != EmploymentType.FORMAL) throw identityConflict();
        if (!result.account().enabled() || result.employee().status() != EmployeeStatus.ACTIVE) {
            throw new AuthException("USER_DISABLED", "用户或员工已禁用");
        }
        roleSync.sync(result.account().id(), oauth.tenantKey(), List.of());
        return result;
    }

    public ProvisionedUser mergeDirectory(
            FeishuOAuthIdentity oauth, FeishuEmployeeProfile profile) {
        return merge(oauth, profile, false);
    }

    private ProvisionedUser merge(
            FeishuOAuthIdentity oauth, FeishuEmployeeProfile profile, boolean login) {
        if (properties.getAllowedTenantKey() == null
                || !properties.getAllowedTenantKey().equals(oauth.tenantKey())) {
            throw new AuthException("FEISHU_TENANT_NOT_ALLOWED", "该飞书企业不允许登录");
        }
        if (oauth.openId() == null
                || oauth.openId().isBlank()
                || (profile.openId() != null && !oauth.openId().equals(profile.openId())))
            throw identityConflict();
        synchronized (mergeLock) {
            try {
                return transactions.execute(status -> mergeTransaction(oauth, profile, login));
            } catch (DataIntegrityViolationException exception) {
                // A concurrent insert may have committed on another application instance.
                return transactions.execute(
                        status -> {
                            if (identities
                                    .find(oauth.tenantKey(), oauth.unionId(), oauth.openId())
                                    .isEmpty()) throw identityConflict();
                            return mergeTransaction(oauth, profile, login);
                        });
            }
        }
    }

    private ProvisionedUser mergeTransaction(
            FeishuOAuthIdentity oauth, FeishuEmployeeProfile profile, boolean login) {
        var identity =
                identities.find(oauth.tenantKey(), oauth.unionId(), oauth.openId()).orElse(null);
        UserAccount account =
                identity == null
                        ? null
                        : users.findById(identity.userId()).orElseThrow(this::identityConflict);
        Employee employee =
                account == null
                        ? null
                        : employees
                                .findByIdForUpdate(account.employeeId())
                                .orElseThrow(this::identityConflict);
        String mobile = normalizeMobile(firstNonBlank(profile.mobile(), oauth.mobile()));
        if (employee == null && mobile != null) {
            var matches = employees.findByMobile(mobile);
            if (matches.size() > 1) throw identityConflict();
            if (matches.size() == 1) {
                employee =
                        employees
                                .findByIdForUpdate(matches.get(0).id())
                                .orElseThrow(this::identityConflict);
                // The mobile match was a snapshot read; a manual edit may have committed while we
                // waited.
                if (!mobile.equals(normalizeMobile(employee.mobile()))) throw identityConflict();
                account = users.findByEmployeeId(employee.id()).orElseThrow(this::identityConflict);
                if (identities.findByUserId(account.id()).isPresent()) throw identityConflict();
                if (login && employee.employmentType() != EmploymentType.FORMAL)
                    throw identityConflict();
            }
        }
        if (account != null)
            account = users.findByIdForUpdate(account.id()).orElseThrow(this::identityConflict);
        if (mobile != null) {
            for (Employee other : employees.findByMobile(mobile)) {
                if (employee == null || other.id() != employee.id()) throw identityConflict();
            }
        }
        boolean created = employee == null;
        Long departmentId =
                employees.findDepartmentIdByFeishuId(profile.primaryDepartmentId()).orElse(null);
        if (departmentId == null) {
            for (String department : profile.departmentIds()) {
                departmentId = employees.findDepartmentIdByFeishuId(department).orElse(null);
                if (departmentId != null) break;
            }
        }
        if (login && departmentId == null && employee != null)
            departmentId = employee.departmentId();
        Long positionId = created ? null : employee.positionId();
        EmploymentType type =
                profile.employeeType() == null
                        ? (created ? EmploymentType.FORMAL : employee.employmentType())
                        : profile.employeeType() == 1
                                ? EmploymentType.FORMAL
                                : EmploymentType.TEMPORARY;
        EmployeeStatus status = externalStatus(profile);
        String statusSource = "feishu";
        if (!created
                && "manual".equals(employee.statusSource())
                && employee.status() != EmployeeStatus.ACTIVE) {
            // Preserve manual non-active status even when Feishu later reports active.
            status = employee.status();
            statusSource = "manual";
        }
        String number = employeeNumber(profile.employeeNo(), oauth.openId(), employee);
        String name =
                firstNonBlank(profile.displayName(), firstNonBlank(oauth.displayName(), "飞书员工"));
        var saved =
                employees.save(
                        new Employee(
                                created ? 0 : employee.id(),
                                number,
                                name,
                                mobile,
                                oauth.avatarUrl(),
                                departmentId,
                                positionId,
                                type,
                                status,
                                created ? "feishu" : employee.source(),
                                mobile != null && departmentId != null && positionId != null,
                                statusSource,
                                profile.jobTitle(),
                                profile.hireDate()));
        UserStatus userStatus =
                status == EmployeeStatus.ACTIVE ? UserStatus.ENABLED : UserStatus.DISABLED;
        if (account != null && !account.enabled() && employee.status() == EmployeeStatus.ACTIVE) {
            userStatus = UserStatus.DISABLED;
        }
        var savedAccount =
                users.save(
                        new UserAccount(
                                account == null ? 0 : account.id(),
                                saved.id(),
                                mobile,
                                account == null ? null : account.passwordHash(),
                                type,
                                userStatus,
                                status,
                                name,
                                saved.avatarUrl()));
        // JDBC updated employee fields may still be cached in JPA's persistence context.
        var resultAccount =
                new UserAccount(
                        savedAccount.id(),
                        saved.id(),
                        mobile,
                        savedAccount.passwordHash(),
                        type,
                        userStatus,
                        status,
                        name,
                        saved.avatarUrl());
        Instant now = Instant.now(clock);
        var savedIdentity =
                identities.save(
                        new FeishuIdentity(
                                identity == null ? 0 : identity.id(),
                                savedAccount.id(),
                                oauth.tenantKey(),
                                oauth.openId(),
                                firstNonBlank(
                                        oauth.unionId(),
                                        identity == null ? null : identity.unionId()),
                                name,
                                oauth.avatarUrl(),
                                identity == null ? now : identity.boundAt(),
                                now));
        return new ProvisionedUser(resultAccount, saved, savedIdentity, created);
    }

    private String employeeNumber(String requested, String openId, Employee employee) {
        if (requested != null && !requested.isBlank()) {
            var owner = employees.findByEmployeeNo(requested);
            if (owner.isEmpty() || (employee != null && owner.get().id() == employee.id()))
                return requested;
        }
        return FeishuStableCode.of("FS-U-", openId);
    }

    private EmployeeStatus externalStatus(FeishuEmployeeProfile profile) {
        if (profile.resigned() || profile.exited()) return EmployeeStatus.RESIGNED;
        if (profile.frozen() || profile.unjoined() || Boolean.FALSE.equals(profile.activated()))
            return EmployeeStatus.DISABLED;
        return EmployeeStatus.ACTIVE;
    }

    private String normalizeMobile(String mobile) {
        if (mobile == null || mobile.isBlank()) return null;
        String value = mobile.replaceAll("[\\s()-]", "");
        if (value.startsWith("+86")) value = value.substring(3);
        else if (value.startsWith("0086")) value = value.substring(4);
        return value.isBlank() ? null : value;
    }

    private String firstNonBlank(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private AuthException identityConflict() {
        return new AuthException("FEISHU_IDENTITY_CONFLICT", "飞书身份无法安全合并到现有员工");
    }
}
