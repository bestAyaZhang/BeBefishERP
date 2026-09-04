package com.bebefish.erp.auth.application;

import com.bebefish.erp.auth.domain.AuthenticatedUser;
import com.bebefish.erp.auth.domain.PasswordHasher;
import com.bebefish.erp.auth.domain.SmsCodeStore;
import com.bebefish.erp.auth.domain.TokenIssuer;
import com.bebefish.erp.auth.domain.UserAccount;
import com.bebefish.erp.auth.domain.UserAccountRepository;
import com.bebefish.erp.authorization.application.AuthorizationResolver;
import org.springframework.stereotype.Service;

@Service
public class AuthService {
    private final UserAccountRepository userAccounts;
    private final PasswordHasher passwordHasher;
    private final SmsCodeStore smsCodeStore;
    private final TokenIssuer tokenIssuer;
    private final AuthorizationResolver authorizationResolver;

    public AuthService(
            UserAccountRepository userAccounts,
            PasswordHasher passwordHasher,
            SmsCodeStore smsCodeStore,
            TokenIssuer tokenIssuer,
            AuthorizationResolver authorizationResolver
    ) {
        this.userAccounts = userAccounts;
        this.passwordHasher = passwordHasher;
        this.smsCodeStore = smsCodeStore;
        this.tokenIssuer = tokenIssuer;
        this.authorizationResolver = authorizationResolver;
    }

    public void sendSmsCode(SendSmsCodeCommand command) {
        var mobile = command.normalizedMobile();
        ensureUserCanLogin(findUser(mobile));
        smsCodeStore.issueCode(mobile);
    }

    public LoginResult loginWithPassword(PasswordLoginCommand command) {
        var user = findUser(command.normalizedMobile());
        ensureUserCanLogin(user);
        if (!passwordHasher.matches(command.password(), user.passwordHash())) {
            throw new AuthException("LOGIN_FAILED", "手机号、密码或验证码错误");
        }
        return issueLogin(user, "password");
    }

    public LoginResult loginWithSms(SmsLoginCommand command) {
        var user = findUser(command.normalizedMobile());
        ensureUserCanLogin(user);
        if (!smsCodeStore.verifyAndConsume(command.normalizedMobile(), command.smsCode())) {
            throw new AuthException("SMS_CODE_EXPIRED", "短信验证码不存在或已过期");
        }
        return issueLogin(user, "sms");
    }

    public void logout(String authorization) {
        tokenIssuer.revoke(extractToken(authorization));
    }

    public LoginResult currentUser(String authorization) {
        return tokenIssuer.resolve(extractToken(authorization));
    }

    private UserAccount findUser(String mobile) {
        return userAccounts.findByMobile(mobile)
                .orElseThrow(() -> new AuthException("LOGIN_FAILED", "手机号、密码或验证码错误"));
    }

    private void ensureUserCanLogin(UserAccount user) {
        if (!user.enabled() || !user.employeeActive()) {
            throw new AuthException("USER_DISABLED", "用户或员工已禁用");
        }
    }

    private LoginResult issueLogin(UserAccount user, String method) {
        var authorization = authorizationResolver.resolve(user.id());
        var authenticatedUser = new AuthenticatedUser(
                user.mobile(),
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
}
