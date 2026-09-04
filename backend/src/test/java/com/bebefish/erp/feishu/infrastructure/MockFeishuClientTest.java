package com.bebefish.erp.feishu.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bebefish.erp.feishu.FeishuClientException;
import com.bebefish.erp.feishu.FeishuProperties;
import org.junit.jupiter.api.Test;

class MockFeishuClientTest {
    @Test
    void derivesEveryDirectoryResponseFromItsOpenIdInsteadOfSharedRequestState() {
        var properties = new FeishuProperties();
        properties.setAllowedTenantKey("tenant-a");
        var client = new MockFeishuClient(properties);
        var noMobile = client.exchangeCode("mock-no-mobile");
        var degraded = client.exchangeCode("mock-role-degraded");
        client.exchangeCode("mock-user");

        assertThat(client.employeeProfile(noMobile.openId()).mobile()).isNull();
        assertThatThrownBy(() -> client.businessRoles(degraded.openId()))
                .isInstanceOf(FeishuClientException.class);
    }
}
