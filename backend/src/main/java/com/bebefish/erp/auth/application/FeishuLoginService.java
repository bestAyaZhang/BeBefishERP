package com.bebefish.erp.auth.application;

import com.bebefish.erp.auth.domain.AuthenticatedUser;
import com.bebefish.erp.auth.domain.LoginAuditEvent;
import com.bebefish.erp.auth.domain.LoginAuditRepository;
import com.bebefish.erp.auth.domain.TokenIssuer;
import com.bebefish.erp.auth.domain.UserAccountRepository;
import com.bebefish.erp.authorization.application.AuthorizationResolver;
import com.bebefish.erp.authorization.application.FeishuRoleSyncService;
import com.bebefish.erp.feishu.FeishuClientException;
import com.bebefish.erp.feishu.FeishuDirectoryClient;
import com.bebefish.erp.feishu.FeishuOAuthClient;
import com.bebefish.erp.identity.application.EmployeeProvisioningService;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class FeishuLoginService {
    private final FeishuOAuthClient oauth;
    private final FeishuDirectoryClient directory;
    private final EmployeeProvisioningService provisioning;
    private final FeishuRoleSyncService roleSync;
    private final LoginTicketService tickets;
    private final UserAccountRepository users;
    private final AuthorizationResolver authorizations;
    private final TokenIssuer sessions;
    private final LoginAuditRepository audits;
    private final Clock clock;

    public FeishuLoginService(
            FeishuOAuthClient oauth,
            FeishuDirectoryClient directory,
            EmployeeProvisioningService provisioning,
            FeishuRoleSyncService roleSync,
            LoginTicketService tickets,
            UserAccountRepository users,
            AuthorizationResolver authorizations,
            TokenIssuer sessions,
            LoginAuditRepository audits,
            Clock clock
    ) {
        this.oauth = oauth;
        this.directory = directory;
        this.provisioning = provisioning;
        this.roleSync = roleSync;
        this.tickets = tickets;
        this.users = users;
        this.authorizations = authorizations;
        this.sessions = sessions;
        this.audits = audits;
        this.clock = clock;
    }

    public String login(String code, String ipAddress, String userAgent) {
        Long userId = null;
        String tenantKey = null;
        try {
            var identity = oauth.exchangeCode(code);
            tenantKey = identity.tenantKey();
            var profile = directory.employeeProfile(identity.openId());
            var provisioned = provisioning.provision(identity, profile);
            userId = provisioned.account().id();
            List<String> warnings;
            try {
                warnings = roleSync.sync(
                        userId,
                        identity.tenantKey(),
                        directory.businessRoles(identity.openId())
                );
            } catch (FeishuClientException exception) {
                warnings = roleSync.syncDegraded(userId);
            }
            users.recordLogin(userId, "feishu");
            audits.record(audit(userId, "success", null, tenantKey, ipAddress, userAgent));
            return tickets.issue(userId, warnings);
        } catch (AuthException exception) {
            audits.record(audit(userId, "failure", exception.code(), tenantKey, ipAddress, userAgent));
            throw exception;
        } catch (FeishuClientException exception) {
            var mapped = new AuthException("FEISHU_USER_UNAVAILABLE", "暂时无法读取飞书用户信息，请重试");
            audits.record(audit(userId, "failure", mapped.code(), tenantKey, ipAddress, userAgent));
            throw mapped;
        }
    }

    public LoginResult exchange(String ticket) {
        var consumed = tickets.consume(ticket);
        var account = users.findById(consumed.userId())
                .orElseThrow(() -> new AuthException("FEISHU_CALLBACK_EXPIRED", "登录结果已失效，请重新扫码"));
        if (!account.enabled() || !account.employeeActive()) {
            throw new AuthException("USER_DISABLED", "用户或员工已禁用");
        }
        var authorization = authorizations.resolve(account.id());
        var user = new AuthenticatedUser(
                account.id(), account.employeeId(), account.mobile(), account.displayName(), account.avatarUrl(),
                authorization.roles(), authorization.permissions()
        );
        var login = sessions.issue(user, "feishu");
        return new LoginResult(
                login.accessToken(), login.employeeId(), login.mobile(), login.displayName(), login.avatarUrl(),
                login.roles(), login.permissions(), login.loginMethod(), consumed.warnings()
        );
    }

    private LoginAuditEvent audit(
            Long userId,
            String result,
            String errorCode,
            String tenantKey,
            String ipAddress,
            String userAgent
    ) {
        return new LoginAuditEvent(
                userId, "feishu", result, errorCode, tenantKey, ipAddress,
                userAgent == null ? null : userAgent.substring(0, Math.min(userAgent.length(), 500)),
                Instant.now(clock)
        );
    }
}
