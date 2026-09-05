package com.bebefish.erp.auth.domain;

import com.bebefish.erp.auth.application.LoginResult;

public interface TokenIssuer {
    LoginResult issue(AuthenticatedUser user, String loginMethod);

    LoginResult resolve(String accessToken);

    void revoke(String accessToken);
}
