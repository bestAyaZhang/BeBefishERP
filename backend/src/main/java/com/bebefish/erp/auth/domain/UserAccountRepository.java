package com.bebefish.erp.auth.domain;

import java.util.Optional;

public interface UserAccountRepository {
    Optional<UserAccount> findById(long id);

    Optional<UserAccount> findByMobile(String mobile);

    UserAccount save(UserAccount account);

    void recordLogin(long userId, String loginMethod);
}
