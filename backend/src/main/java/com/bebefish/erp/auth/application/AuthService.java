package com.bebefish.erp.auth.application;

import com.bebefish.erp.auth.domain.AuthenticatedUser;
import com.bebefish.erp.auth.domain.EmploymentType;
import com.bebefish.erp.auth.domain.PasswordHasher;
import com.bebefish.erp.auth.domain.LoginAuditEvent;
import com.bebefish.erp.auth.domain.LoginAuditRepository;
import com.bebefish.erp.auth.domain.TokenIssuer;
import com.bebefish.erp.auth.domain.UserAccount;
import com.bebefish.erp.auth.domain.UserAccountRepository;
import com.bebefish.erp.authorization.application.AuthorizationResolver;
import java.time.Clock;
import java.time.Instant;
import org.springframework.stereotype.Service;

@Service
public class AuthService {
    private final UserAccountRepository userAccounts;
    private final PasswordHasher passwordHasher;
    private final TokenIssuer tokenIssuer;
    private final AuthorizationResolver authorizationResolver;
    private final LoginAuditRepository audits;
    private final Clock clock;

    public AuthService(
            UserAccountRepository userAccounts,
            PasswordHasher passwordHasher,
            TokenIssuer tokenIssuer,
            AuthorizationResolver authorizationResolver,
            LoginAuditRepository audits,
            Clock clock
    ) {
        this.userAccounts = userAccounts;
        this.passwordHasher = passwordHasher;
        this.tokenIssuer = tokenIssuer;
        this.authorizationResolver = authorizationResolver;
        this.audits = audits;
        this.clock = clock;
    }

    public LoginResult loginWithPassword(PasswordLoginCommand command) {
        Long userId = null;
        try {
            var user = findUser(command.normalizedMobile());
            userId = user.id();
            ensureUserCanLogin(user);
            if (user.employmentType() != EmploymentType.TEMPORARY || user.passwordHash() == null) {
                throw new AuthException("LOGIN_FAILED", "手机号或密码错误");
            }
            if (!passwordHasher.matches(command.password(), user.passwordHash())) {
                throw new AuthException("LOGIN_FAILED", "手机号或密码错误");
            }
            var result = issueLogin(user, "password");
            audits.record(audit(userId, "success", null));
            return result;
        } catch (AuthException exception) {
            audits.record(audit(userId, "failure", exception.code()));
            throw exception;
        }
    }

    public void logout(String authorization) {
        tokenIssuer.revoke(extractToken(authorization));
    }

    public LoginResult currentUser(String authorization) {
        return tokenIssuer.resolve(extractToken(authorization));
    }

    private UserAccount findUser(String mobile) {
        return userAccounts.findByMobile(mobile)
                .orElseThrow(() -> new AuthException("LOGIN_FAILED", "手机号或密码错误"));
    }

    private void ensureUserCanLogin(UserAccount user) {
        if (!user.enabled() || !user.employeeActive()) {
            throw new AuthException("USER_DISABLED", "用户或员工已禁用");
        }
    }

    private LoginResult issueLogin(UserAccount user, String method) {
        var authorization = authorizationResolver.resolve(user.id());
        var authenticatedUser = new AuthenticatedUser(
                user.id(),
                user.employeeId(),
                user.mobile(),
                user.displayName(),
                user.avatarUrl(),
                authorization.roles(),
                authorization.permissions()
        );
        userAccounts.recordLogin(user.id(), method);
        return tokenIssuer.issue(authenticatedUser, method);
    }

    private String extractToken(String authorization) {
        if (authorization == null || authorization.isBlank()) {
            throw new AuthException("UNAUTHORIZED", "未登录");
        }
        return authorization.replaceFirst("(?i)^Bearer\\s+", "").trim();
    }

    private LoginAuditEvent audit(Long userId, String result, String errorCode) {
        return new LoginAuditEvent(userId, "password", result, errorCode, null, null, null, Instant.now(clock));
    }
}
