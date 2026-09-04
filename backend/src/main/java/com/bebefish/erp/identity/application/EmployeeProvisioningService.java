package com.bebefish.erp.identity.application;

import com.bebefish.erp.auth.application.AuthException;
import com.bebefish.erp.auth.domain.EmployeeStatus;
import com.bebefish.erp.auth.domain.EmploymentType;
import com.bebefish.erp.auth.domain.UserAccount;
import com.bebefish.erp.auth.domain.UserAccountRepository;
import com.bebefish.erp.auth.domain.UserStatus;
import com.bebefish.erp.authorization.application.FeishuRoleSyncService;
import com.bebefish.erp.feishu.FeishuEmployeeProfile;
import com.bebefish.erp.feishu.FeishuOAuthIdentity;
import com.bebefish.erp.feishu.FeishuProperties;
import com.bebefish.erp.identity.domain.Employee;
import com.bebefish.erp.identity.domain.EmployeeRepository;
import com.bebefish.erp.identity.domain.FeishuIdentity;
import com.bebefish.erp.identity.domain.FeishuIdentityRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class EmployeeProvisioningService {
    private final EmployeeRepository employees;
    private final FeishuIdentityRepository identities;
    private final UserAccountRepository users;
    private final FeishuRoleSyncService roleSync;
    private final FeishuProperties properties;
    private final Clock clock;
    private final TransactionTemplate transactions;
    private final ConcurrentHashMap<String, Object> identityLocks = new ConcurrentHashMap<>();

    public EmployeeProvisioningService(
            EmployeeRepository employees,
            FeishuIdentityRepository identities,
            UserAccountRepository users,
            FeishuRoleSyncService roleSync,
            FeishuProperties properties,
            Clock clock,
            PlatformTransactionManager transactionManager
    ) {
        this.employees = employees;
        this.identities = identities;
        this.users = users;
        this.roleSync = roleSync;
        this.properties = properties;
        this.clock = clock;
        this.transactions = new TransactionTemplate(transactionManager);
    }

    public ProvisionedUser provision(FeishuOAuthIdentity oauth, FeishuEmployeeProfile profile) {
        requireAllowedTenant(oauth.tenantKey());
        String lockKey = oauth.tenantKey() + ":" + stableId(oauth);
        Object lock = identityLocks.computeIfAbsent(lockKey, ignored -> new Object());
        try {
            synchronized (lock) {
                try {
                    return transactions.execute(status -> provisionTransaction(oauth, profile));
                } catch (DataIntegrityViolationException exception) {
                    return transactions.execute(status -> loadAfterConcurrentInsert(oauth, profile));
                }
            }
        } finally {
            identityLocks.remove(lockKey, lock);
        }
    }

    private ProvisionedUser provisionTransaction(FeishuOAuthIdentity oauth, FeishuEmployeeProfile profile) {
        return identities.find(oauth.tenantKey(), oauth.unionId(), oauth.openId())
                .map(existing -> updateExisting(existing, oauth, profile))
                .orElseGet(() -> mergeUniqueMobileOrCreate(oauth, profile));
    }

    private ProvisionedUser loadAfterConcurrentInsert(FeishuOAuthIdentity oauth, FeishuEmployeeProfile profile) {
        var identity = identities.find(oauth.tenantKey(), oauth.unionId(), oauth.openId())
                .orElseThrow(this::identityConflict);
        return updateExisting(identity, oauth, profile);
    }

    private ProvisionedUser updateExisting(
            FeishuIdentity identity,
            FeishuOAuthIdentity oauth,
            FeishuEmployeeProfile profile
    ) {
        var account = users.findById(identity.userId()).orElseThrow(this::identityConflict);
        var employee = employees.findById(account.employeeId()).orElseThrow(this::identityConflict);
        validateLoginPolicy(account, employee);
        var updatedEmployee = refresh(employee, oauth, profile);
        var now = Instant.now(clock);
        var updatedIdentity = identities.save(new FeishuIdentity(
                identity.id(), identity.userId(), identity.tenantKey(), oauth.openId(), oauth.unionId(),
                displayName(oauth, profile), oauth.avatarUrl(), identity.boundAt(), now
        ));
        roleSync.sync(account.id(), oauth.tenantKey(), java.util.List.of());
        return new ProvisionedUser(account, updatedEmployee, updatedIdentity);
    }

    private ProvisionedUser mergeUniqueMobileOrCreate(
            FeishuOAuthIdentity oauth,
            FeishuEmployeeProfile profile
    ) {
        String mobile = normalizeMobile(firstNonBlank(profile.mobile(), oauth.mobile()));
        if (mobile != null) {
            var matches = employees.findByMobile(mobile);
            if (matches.size() > 1) {
                throw identityConflict();
            }
            if (matches.size() == 1) {
                return bindExisting(matches.get(0), oauth, profile);
            }
        }
        return createNew(oauth, profile, mobile);
    }

    private ProvisionedUser bindExisting(
            Employee employee,
            FeishuOAuthIdentity oauth,
            FeishuEmployeeProfile profile
    ) {
        var account = users.findByEmployeeId(employee.id()).orElseThrow(this::identityConflict);
        validateLoginPolicy(account, employee);
        if (identities.findByUserId(account.id()).isPresent()) {
            throw identityConflict();
        }
        var updatedEmployee = refresh(employee, oauth, profile);
        var identity = bind(account.id(), oauth, profile);
        roleSync.sync(account.id(), oauth.tenantKey(), java.util.List.of());
        return new ProvisionedUser(account, updatedEmployee, identity);
    }

    private ProvisionedUser createNew(
            FeishuOAuthIdentity oauth,
            FeishuEmployeeProfile profile,
            String mobile
    ) {
        Long departmentId = employees.findDepartmentIdByFeishuId(profile.primaryDepartmentId()).orElse(null);
        boolean complete = mobile != null
                && profile.employeeNo() != null && !profile.employeeNo().isBlank()
                && departmentId != null;
        var employee = employees.save(new Employee(
                0,
                firstNonBlank(profile.employeeNo(), generatedEmployeeNo()),
                displayName(oauth, profile),
                mobile,
                oauth.avatarUrl(),
                departmentId,
                null,
                EmploymentType.FORMAL,
                EmployeeStatus.ACTIVE,
                "feishu",
                complete
        ));
        var account = users.save(new UserAccount(
                0, employee.id(), mobile, null, EmploymentType.FORMAL, UserStatus.ENABLED,
                EmployeeStatus.ACTIVE, employee.name(), employee.avatarUrl()
        ));
        var identity = bind(account.id(), oauth, profile);
        roleSync.sync(account.id(), oauth.tenantKey(), java.util.List.of());
        return new ProvisionedUser(account, employee, identity);
    }

    private FeishuIdentity bind(long userId, FeishuOAuthIdentity oauth, FeishuEmployeeProfile profile) {
        Instant now = Instant.now(clock);
        return identities.save(new FeishuIdentity(
                0, userId, oauth.tenantKey(), oauth.openId(), oauth.unionId(),
                displayName(oauth, profile), oauth.avatarUrl(), now, now
        ));
    }

    private Employee refresh(Employee employee, FeishuOAuthIdentity oauth, FeishuEmployeeProfile profile) {
        String mobile = firstNonBlank(normalizeMobile(profile.mobile()), employee.mobile());
        var updated = new Employee(
                employee.id(),
                firstNonBlank(profile.employeeNo(), employee.employeeNo()),
                displayName(oauth, profile),
                mobile,
                firstNonBlank(oauth.avatarUrl(), employee.avatarUrl()),
                employees.findDepartmentIdByFeishuId(profile.primaryDepartmentId()).orElse(employee.departmentId()),
                employee.positionId(),
                employee.employmentType(),
                employee.status(),
                employee.source(),
                employee.profileComplete()
        );
        return employees.save(updated);
    }

    private void validateLoginPolicy(UserAccount account, Employee employee) {
        if (employee.employmentType() != EmploymentType.FORMAL) {
            throw identityConflict();
        }
        if (!account.enabled() || employee.status() != EmployeeStatus.ACTIVE) {
            throw new AuthException("USER_DISABLED", "用户或员工已禁用");
        }
    }

    private void requireAllowedTenant(String tenantKey) {
        if (properties.getAllowedTenantKey() == null
                || !properties.getAllowedTenantKey().equals(tenantKey)) {
            throw new AuthException("FEISHU_TENANT_NOT_ALLOWED", "该飞书企业不允许登录");
        }
    }

    private String displayName(FeishuOAuthIdentity oauth, FeishuEmployeeProfile profile) {
        return firstNonBlank(profile.displayName(), firstNonBlank(oauth.displayName(), "飞书员工"));
    }

    private String stableId(FeishuOAuthIdentity oauth) {
        return firstNonBlank(oauth.unionId(), oauth.openId());
    }

    private String generatedEmployeeNo() {
        return "FS-" + UUID.randomUUID().toString().replace("-", "")
                .substring(0, 8).toUpperCase(Locale.ROOT);
    }

    private String normalizeMobile(String mobile) {
        if (mobile == null || mobile.isBlank()) {
            return null;
        }
        String normalized = mobile.replaceAll("[\\s()-]", "");
        if (normalized.startsWith("+86")) {
            normalized = normalized.substring(3);
        } else if (normalized.startsWith("0086")) {
            normalized = normalized.substring(4);
        }
        return normalized;
    }

    private String firstNonBlank(String first, String fallback) {
        return first == null || first.isBlank() ? fallback : first;
    }

    private AuthException identityConflict() {
        return new AuthException("FEISHU_IDENTITY_CONFLICT", "飞书身份无法安全合并到现有员工");
    }
}
