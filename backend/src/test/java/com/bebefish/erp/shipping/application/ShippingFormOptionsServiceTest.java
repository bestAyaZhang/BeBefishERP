package com.bebefish.erp.shipping.application;

import com.bebefish.erp.shipping.api.ShippingFormOptions;
import com.bebefish.erp.shipping.infrastructure.JdbcShippingFormOptionsRepository;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ShippingFormOptionsServiceTest {
    @Test
    void normalizesShopsAndReturnsOnlyRepositoryApprovedPreparers() {
        var repository = mock(JdbcShippingFormOptionsRepository.class);
        when(repository.findActivePreparers()).thenReturn(List.of(
                new ShippingFormOptions.PreparerOption(3, "阿杰"),
                new ShippingFormOptions.PreparerOption(9, "小周")));
        var properties = new ShippingProperties(List.of(" 贝贝鱼淘宝旗舰店 ", "", "贝贝鱼淘宝旗舰店", "抖音旗舰店"));

        var options = new ShippingFormOptionsService(repository, properties).options();

        assertThat(options.shopNames()).containsExactly("贝贝鱼淘宝旗舰店", "抖音旗舰店");
        assertThat(options.preparers()).extracting(ShippingFormOptions.PreparerOption::employeeId)
                .containsExactly(3L, 9L);
    }

    @Test
    void blankShopSettingBecomesEmptyList() {
        var repository = mock(JdbcShippingFormOptionsRepository.class);
        when(repository.findActivePreparers()).thenReturn(List.of());

        var options = new ShippingFormOptionsService(repository, new ShippingProperties(List.of(" "))).options();

        assertThat(options.shopNames()).isEmpty();
    }
}
