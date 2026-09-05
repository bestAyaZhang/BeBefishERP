package com.bebefish.erp.feishu;

import java.net.URI;

public interface FeishuOAuthClient {
    URI authorizationUri(String state);

    FeishuOAuthIdentity exchangeCode(String code);
}
