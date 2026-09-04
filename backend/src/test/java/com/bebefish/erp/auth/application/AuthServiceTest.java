package com.bebefish.erp.auth.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bebefish.erp.auth.domain.AuthenticatedUser;
import com.bebefish.erp.auth.domain.PasswordHasher;
import com.bebefish.erp.auth.domain.SmsCodeStore;
import com.bebefish.erp.auth.domain.TokenIssuer;
import com.bebefish.erp.auth.domain.UserAccount;
import com.bebefish.erp.auth.domain.UserAccountRepository;
import com.bebefish.erp.authorization.application.AuthorizationResolver;
import com.bebefish.erp.authorization.domain.DataScope;
import com.bebefish.erp.authorization.domain.PermissionDefinition;
import com.bebefish.erp.authorization.domain.Role;
import com.bebefish.erp.authorization.domain.RoleRepository;
import com.bebefish.erp.authorization.infrastructure.InMemoryRoleRepository;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AuthServiceTest {
    private FakeUserAccountRepository users;
    private FakeRoleRepository roles;
    private FakeSmsCodeStore smsCodes;
    private AuthService authService;

    @BeforeEach
    void setUp() {
        users = new FakeUserAccountRepository();
        roles = new FakeRoleRepository();
        smsCodes = new FakeSmsCodeStore();
        PasswordHasher passwordHasher = (rawPassword, passwordHash) -> ("hash:" + rawPassword).equals(passwordHash);
        TokenIssuer tokenIssuer = new FakeTokenIssuer();
        authService = new AuthService(users, passwordHasher, smsCodes, tokenIssuer, new AuthorizationResolver(roles));
    }

    @Test
    void passwordLoginReturnsClaimsResolvedFromActiveRoles() {
        users.save(activeUser("13800138000"));
        roles.save(role("ORG_ADMIN", Set.of("organization:view", "organization:manage"), "13800138000"));

        var result = authService.loginWithPassword(new PasswordLoginCommand(" 13800138000 ", "secret"));

        assertThat(result.accessToken()).isEqualTo("token-13800138000-password");
        assertThat(result.mobile()).isEqualTo("13800138000");
        assertThat(result.roles()).containsExactly("ORG_ADMIN");
        assertThat(result.permissions()).containsExactly("organization:manage", "organization:view");
        assertThat(users.lastLoginMethod("13800138000")).isEqualTo("password");
    }

    @Test
    void developmentAdministratorResolvesSuperAdministratorPermissions() {
        var authorization = new AuthorizationResolver(new InMemoryRoleRepository()).resolve("13800138000");

        assertThat(authorization.roles()).containsExactly("SUPER_ADMIN");
        assertThat(authorization.permissions())
                .contains("system:role:view", "system:role:manage", "organization:view", "organization:manage");
    }

    @Test
    void passwordLoginRejectsWrongPassword() {
        users.save(activeUser("13800138000"));

        assertThatThrownBy(() -> authService.loginWithPassword(new PasswordLoginCommand("13800138000", "wrong")))
                .isInstanceOf(AuthException.class)
                .hasMessage("手机号、密码或验证码错误");
    }

    @Test
    void disabledUserCannotLogin() {
        users.save(new UserAccount("13800138000", "hash:secret", false, true));

        assertThatThrownBy(() -> authService.loginWithPassword(new PasswordLoginCommand("13800138000", "secret")))
                .isInstanceOf(AuthException.class)
                .hasMessage("用户或员工已禁用");
    }

    @Test
    void smsLoginConsumesCodeAndRecordsLoginMethod() {
        users.save(activeUser("13800138000"));
        smsCodes.put("13800138000", "123456");

        var result = authService.loginWithSms(new SmsLoginCommand("13800138000", "123456"));

        assertThat(result.accessToken()).isEqualTo("token-13800138000-sms");
        assertThat(smsCodes.verifyAndConsume("13800138000", "123456")).isFalse();
        assertThat(users.lastLoginMethod("13800138000")).isEqualTo("sms");
    }

    @Test
    void sendingSmsCodeRequiresActiveUser() {
        users.save(new UserAccount("13800138000", "hash:secret", true, false));

        assertThatThrownBy(() -> authService.sendSmsCode(new SendSmsCodeCommand("13800138000")))
                .isInstanceOf(AuthException.class)
                .hasMessage("用户或员工已禁用");
    }

    private UserAccount activeUser(String mobile) {
        return new UserAccount(mobile, "hash:secret", true, true);
    }

    private static Role role(String code, Set<String> permissions, String memberKey) {
        return new Role(
                code,
                code,
                false,
                false,
                true,
                DataScope.COMPANY,
                permissions,
                Set.of(memberKey)
        );
    }

    private static class FakeUserAccountRepository implements UserAccountRepository {
        private final Map<String, UserAccount> users = new HashMap<>();
        private final Map<String, String> loginMethods = new HashMap<>();

        void save(UserAccount user) {
            users.put(user.mobile(), user);
        }

        String lastLoginMethod(String mobile) {
            return loginMethods.get(mobile);
        }

        @Override
        public Optional<UserAccount> findByMobile(String mobile) {
            return Optional.ofNullable(users.get(mobile));
        }

        @Override
        public void recordLogin(String mobile, String loginMethod) {
            loginMethods.put(mobile, loginMethod);
        }
    }

    private static class FakeSmsCodeStore implements SmsCodeStore {
        private final Map<String, String> codes = new HashMap<>();

        void put(String mobile, String code) {
            codes.put(mobile, code);
        }

        @Override
        public void issueCode(String mobile) {
            codes.put(mobile, "123456");
        }

        @Override
        public boolean verifyAndConsume(String mobile, String smsCode) {
            if (!smsCode.equals(codes.get(mobile))) {
                return false;
            }
            codes.remove(mobile);
            return true;
        }
    }

    private static class FakeRoleRepository implements RoleRepository {
        private final List<Role> roles = new ArrayList<>();

        void save(Role role) {
            roles.add(role);
        }

        @Override
        public List<Role> findEnabledByMemberKey(String memberKey) {
            return roles.stream()
                    .filter(Role::enabled)
                    .filter(role -> role.memberKeys().contains(memberKey))
                    .toList();
        }

        @Override
        public List<PermissionDefinition> findAllPermissions() {
            return List.of();
        }
    }

    private static class FakeTokenIssuer implements TokenIssuer {
        @Override
        public LoginResult issue(AuthenticatedUser user, String loginMethod) {
            return new LoginResult(
                    "token-" + user.mobile() + "-" + loginMethod,
                    user.mobile(),
                    user.roles(),
                    user.permissions(),
                    loginMethod
            );
        }

        @Override
        public LoginResult resolve(String accessToken) {
            return new LoginResult(accessToken, "13800138000", List.of("ADMIN"), List.of("system:user:view"), "password");
        }

        @Override
        public void revoke(String accessToken) {
        }
    }
}
