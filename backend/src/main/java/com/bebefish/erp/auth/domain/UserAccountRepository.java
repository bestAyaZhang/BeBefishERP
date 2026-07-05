package com.bebefish.erp.auth.domain;

import java.util.Optional;

public interface UserAccountRepository {
    Optional<UserAccount> findByMobile(String mobile);

    void recordLogin(String mobile, String loginMethod);
}
