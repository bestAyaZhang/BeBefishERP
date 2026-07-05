package com.bebefish.erp.auth.infrastructure;

import com.bebefish.erp.auth.application.AuthException;
import com.bebefish.erp.auth.application.LoginResult;
import com.bebefish.erp.auth.domain.TokenIssuer;
import com.bebefish.erp.auth.domain.UserAccount;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

@Component
public class InMemoryTokenIssuer implements TokenIssuer {
    private final Map<String, LoginResult> sessions = new ConcurrentHashMap<>();

    @Override
    public LoginResult issue(UserAccount user, String loginMethod) {
        var token = UUID.randomUUID().toString().replace("-", "");
        var result = new LoginResult(token, user.mobile(), user.roles(), user.permissions(), loginMethod);
        sessions.put(token, result);
        return result;
    }

    @Override
    public LoginResult resolve(String accessToken) {
        var result = sessions.get(accessToken);
        if (result == null) {
            throw new AuthException("UNAUTHORIZED", "未登录");
        }
        return result;
    }

    @Override
    public void revoke(String accessToken) {
        sessions.remove(accessToken);
    }
}
