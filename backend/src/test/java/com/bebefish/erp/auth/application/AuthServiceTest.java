package com.bebefish.erp.auth.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bebefish.erp.auth.domain.PasswordHasher;
import com.bebefish.erp.auth.domain.SmsCodeStore;
import com.bebefish.erp.auth.domain.TokenIssuer;
import com.bebefish.erp.auth.domain.UserAccount;
import com.bebefish.erp.auth.domain.UserAccountRepository;
import com.bebefish.erp.auth.infrastructure.InMemoryUserAccountRepository;
import java.time.Clock;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

class AuthServiceTest {
    private FakeUserAccountRepository users;
    private FakeSmsCodeStore smsCodes;
    private AuthService authService;

    @BeforeEach
    void setUp() {
        users = new FakeUserAccountRepository();
        smsCodes = new FakeSmsCodeStore();
        PasswordHasher passwordHasher = (rawPassword, passwordHash) -> ("hash:" + rawPassword).equals(passwordHash);
        TokenIssuer tokenIssuer = new FakeTokenIssuer();
        authService = new AuthService(users, passwordHasher, smsCodes, tokenIssuer);
    }

    @Test
    void passwordLoginReturnsTokenRolesAndPermissions() {
        users.save(activeUser("13800138000"));

        var result = authService.loginWithPassword(new PasswordLoginCommand(" 13800138000 ", "secret"));

        assertThat(result.accessToken()).isEqualTo("token-13800138000-password");
        assertThat(result.mobile()).isEqualTo("13800138000");
        assertThat(result.roles()).containsExactly("ADMIN");
        assertThat(result.permissions()).containsExactly(
                "system:user:view",
                "system:role:view",
                "organization:view",
                "organization:manage"
        );
        assertThat(users.lastLoginMethod("13800138000")).isEqualTo("password");
    }

    @Test
    void demoAdministratorIncludesOrganizationPermissions() {
        PasswordEncoder passwordEncoder = new PasswordEncoder() {
            @Override
            public String encode(CharSequence rawPassword) {
                return rawPassword.toString();
            }

            @Override
            public boolean matches(CharSequence rawPassword, String encodedPassword) {
                return rawPassword.toString().equals(encodedPassword);
            }
        };
        var repository = new InMemoryUserAccountRepository(Clock.systemUTC(), passwordEncoder);

        assertThat(repository.findByMobile("13800138000").orElseThrow().permissions())
                .contains("organization:view", "organization:manage");
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
        users.save(new UserAccount("13800138000", "hash:secret", false, true, List.of("ADMIN"), List.of()));

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
        users.save(new UserAccount("13800138000", "hash:secret", true, false, List.of("ADMIN"), List.of()));

        assertThatThrownBy(() -> authService.sendSmsCode(new SendSmsCodeCommand("13800138000")))
                .isInstanceOf(AuthException.class)
                .hasMessage("用户或员工已禁用");
    }

    private UserAccount activeUser(String mobile) {
        return new UserAccount(
                mobile,
                "hash:secret",
                true,
                true,
                List.of("ADMIN"),
                List.of("system:user:view", "system:role:view", "organization:view", "organization:manage")
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

    private static class FakeTokenIssuer implements TokenIssuer {
        @Override
        public LoginResult issue(UserAccount user, String loginMethod) {
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
