package com.bebefish.erp.auth.application;

import java.util.List;

public record LoginResult(
        String accessToken,
        String mobile,
        List<String> roles,
        List<String> permissions,
        String loginMethod
) {
}
