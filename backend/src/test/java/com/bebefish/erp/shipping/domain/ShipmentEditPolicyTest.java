package com.bebefish.erp.shipping.domain;

import com.bebefish.erp.common.api.BusinessException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

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

    @ParameterizedTest
    @ValueSource(strings = {"freight", "weight", "volume"})
    void orderedShipmentAllowsUnchangedNumbersWithDifferentDecimalScales(String field) {
        var original = form("示例路1号", "原备货", "原备注");
        if (field.equals("volume")) original = withNumber(original, field, new BigDecimal("0.10"));
        var saved = content(original);
        var changedContent = original.withPreparationContent("新的备货内容", "新的备注");
        var sameNumber = field.equals("freight") ? new BigDecimal("36")
                : field.equals("weight") ? new BigDecimal("18.5") : new BigDecimal("0.1");
        var submitted = withNumber(changedContent, field, sameNumber);
        assertThatCode(() -> policy.assertAllowed(saved, submitted, true)).doesNotThrowAnyException();
    }

    @ParameterizedTest
    @ValueSource(strings = {"freight", "weight", "volume"})
    void orderedShipmentStillRejectsChangedOrRemovedNumbers(String field) {
        var original = form("示例路1号", "原备货", "原备注");
        var saved = content(original);
        for (var submitted : List.of(withNumber(original, field, BigDecimal.ONE), withNumber(original, field, null))) {
            assertThatThrownBy(() -> policy.assertAllowed(saved, submitted, true))
                    .isInstanceOfSatisfying(BusinessException.class, error -> assertThat(error.code()).isEqualTo("SHIPMENT_ALREADY_ORDERED"));
        }
    }

    private ShipmentFormInput withNumber(ShipmentFormInput source, String field, BigDecimal value) {
        var draft = source.orderDraft();
        return new ShipmentFormInput(source.platform(), source.shopName(), source.preparers(), source.recipientName(),
                source.recipientPhone(), source.recipientProvince(), source.recipientCity(), source.recipientCounty(),
                source.recipientDetailAddress(), source.preparationContent(), source.remark(),
                field.equals("freight") ? value : source.estimatedFreight(),
                new AneOrderDraft(draft.cargoName(), draft.packType(), field.equals("weight") ? value : draft.weight(),
                        field.equals("volume") ? value : draft.volume(), draft.pieceAmount(), draft.productTypeId(),
                        draft.goodsType(), draft.payType(), draft.logisticsRemark()),
                source.platformId(), source.shopId(), source.senderName(), source.senderPhone(), source.senderProvince(),
                source.senderCity(), source.senderCounty(), source.senderDetailAddress());
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

    @Test
    void completedPreparationCannotBeChangedThroughTheGeneralEditorEvenBeforeCarrierOrdering() {
        var saved = ShipmentContent.from(LocalDate.of(2026, 9, 18), form("示例路1号", "原备货", "原备注"), "completed", "下单人", "", "");
        for (boolean ordered : List.of(false, true)) {
            assertThatThrownBy(() -> policy.assertAllowed(saved, form("示例路1号", "新备货", "原备注"), ordered))
                    .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.code()).isEqualTo("SHIPMENT_PREPARATION_COMPLETED"));
            assertThatThrownBy(() -> policy.assertAllowed(saved, form("示例路1号", "原备货", "新备注"), ordered))
                    .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.code()).isEqualTo("SHIPMENT_PREPARATION_COMPLETED"));
            assertThatCode(() -> policy.assertAllowed(saved, saved.form(), ordered)).doesNotThrowAnyException();
        }
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
