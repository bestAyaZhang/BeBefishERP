package com.bebefish.erp.shipping.domain;

import com.bebefish.erp.common.api.BusinessException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class ShipmentEditPolicyTest {
    private final ShipmentEditPolicy policy = new ShipmentEditPolicy();

    @Test
    void orderedShipmentRejectsRecipientOrOrderFieldChanges() {
        var saved = content(form("示例路1号", "原备货", "原备注"));
        var changedRecipient = form("示例路2号", "原备货", "原备注");

        assertThatThrownBy(() -> policy.assertAllowed(saved, changedRecipient, true))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("已下单");
    }

    @Test
    void orderedShipmentAllowsPreparationRemarkAndStatusChanges() {
        var saved = content(form("示例路1号", "原备货", "原备注"));
        var progressOnly = form("示例路1号", "新的备货内容", "新的备注");

        assertThatCode(() -> policy.assertAllowed(saved, progressOnly, true)).doesNotThrowAnyException();
    }

    @Test
    void orderedShipmentRejectsSenderChanges() {
        var saved = content(form("示例路1号", "原备货", "原备注", "原发货人"));
        var changedSender = form("示例路1号", "原备货", "原备注", "新发货人");

        assertThatThrownBy(() -> policy.assertAllowed(saved, changedSender, true))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("已下单");
    }

    private ShipmentContent content(ShipmentFormInput form) {
        return ShipmentContent.from(LocalDate.of(2026, 9, 18), form, "unfinished", "陈小鱼", "", "");
    }

    private ShipmentFormInput form(String address, String preparation, String remark) {
        return form(address, preparation, remark, "测试发货人");
    }

    private ShipmentFormInput form(String address, String preparation, String remark, String senderName) {
        return new ShipmentFormInput("淘宝", "贝贝鱼淘宝旗舰店", List.of("小周", "阿杰"), "林女士", "13800006028",
                "浙江省", "杭州市", "余杭区", address, preparation, remark, new BigDecimal("36.00"),
                new AneOrderDraft("水族用品", "纸箱", new BigDecimal("18.50"), new BigDecimal("0.12"),
                        2, 524, 180, 104, "外箱加固"), senderName, "13900000000", "浙江省", "金华市", "东阳市",
                "测试发货路2号");
    }
}
