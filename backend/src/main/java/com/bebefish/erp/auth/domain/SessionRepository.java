package com.bebefish.erp.auth.domain;

import com.bebefish.erp.auth.application.LoginResult;
import java.time.Duration;

public interface SessionRepository {
    LoginResult issue(AuthenticatedUser user, String loginMethod, Duration ttl);

    LoginResult resolve(String accessToken);

    void revoke(String accessToken);
}
