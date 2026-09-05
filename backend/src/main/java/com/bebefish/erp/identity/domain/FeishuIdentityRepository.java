package com.bebefish.erp.identity.domain;

import java.util.Optional;

public interface FeishuIdentityRepository {
    Optional<FeishuIdentity> find(String tenantKey, String unionId, String openId);

    Optional<FeishuIdentity> findByUserId(long userId);

    FeishuIdentity save(FeishuIdentity identity);
}
