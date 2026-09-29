package com.bebefish.erp.shipping.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import com.bebefish.erp.shipping.application.ShipmentService;
import com.bebefish.erp.platform.api.PlatformDtos;
import com.bebefish.erp.platform.application.PlatformCatalogService;
import com.bebefish.erp.shipping.domain.AneOrderDraft;
import com.bebefish.erp.shipping.domain.ShipmentFormInput;
import com.bebefish.erp.shipping.domain.ShipmentSource;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ShipmentSenderSnapshotIntegrationTest {
    @Autowired
    private ShipmentService shipments;
    @Autowired
    private PlatformCatalogService catalog;

    @Test
    void savesAndReloadsTheEditedSenderAsPartOfTheShipment() {
        var platform = catalog.createPlatform(new PlatformDtos.Create(null, "寄件测试平台" + System.nanoTime(), 0, "", null), "test");
        var shop = catalog.createShop(new PlatformDtos.Create(null, "寄件测试店", 0, "", platform.id(),
                "ecommerce", "运营", null), "test");
        var form = new ShipmentFormInput("淘宝", "测试店铺", List.of("备货员"), "收件人", "13800138000",
                "浙江省", "杭州市", "余杭区", "收件测试路1号", "测试商品1件", "", null,
                new AneOrderDraft("测试商品", "纸箱", new BigDecimal("2.5"), new BigDecimal("0.1"),
                        1, 524, 180, 102, ""),
                "本单发货人", "13900000000", "浙江省", "金华市", "东阳市", "本单发货路2号")
                .withSource(new ShipmentSource(platform.id(), shop.id(), platform.name(), shop.name()));

        var created = shipments.create(form, "test", "测试下单员");
        var reloaded = shipments.get(created.id());

        assertThat(reloaded.content().senderName()).isEqualTo("本单发货人");
        assertThat(reloaded.content().senderPhone()).isEqualTo("13900000000");
        assertThat(reloaded.content().senderProvince()).isEqualTo("浙江省");
        assertThat(reloaded.content().senderCity()).isEqualTo("金华市");
        assertThat(reloaded.content().senderCounty()).isEqualTo("东阳市");
        assertThat(reloaded.content().senderDetailAddress()).isEqualTo("本单发货路2号");
    }
}
