package com.bebefish.erp.shipping.domain;

import com.bebefish.erp.common.api.BusinessException;
import java.math.BigDecimal;
import java.util.Objects;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class ShipmentEditPolicy {
    public void assertAllowed(ShipmentContent previous, ShipmentFormInput next, boolean ordered) {
        if ("completed".equals(previous.status()) && (!Objects.equals(previous.preparationContent(), next.preparationContent())
                || !Objects.equals(previous.remark(), next.remark()))) {
            throw new BusinessException("SHIPMENT_PREPARATION_COMPLETED", HttpStatus.CONFLICT, "备货已完成，不能修改备货物品和备注");
        }
        if (!ordered) return;
        var current = previous.form();
        boolean immutableChanged = !Objects.equals(current.platformId(), next.platformId())
                || !Objects.equals(current.shopId(), next.shopId())
                || !Objects.equals(current.platform(), next.platform())
                || !Objects.equals(current.shopName(), next.shopName())
                || !Objects.equals(current.preparers(), next.preparers())
                || !Objects.equals(current.senderName(), next.senderName())
                || !Objects.equals(current.senderPhone(), next.senderPhone())
                || !Objects.equals(current.senderProvince(), next.senderProvince())
                || !Objects.equals(current.senderCity(), next.senderCity())
                || !Objects.equals(current.senderCounty(), next.senderCounty())
                || !Objects.equals(current.senderDetailAddress(), next.senderDetailAddress())
                || !Objects.equals(current.recipientName(), next.recipientName())
                || !Objects.equals(current.recipientPhone(), next.recipientPhone())
                || !Objects.equals(current.recipientProvince(), next.recipientProvince())
                || !Objects.equals(current.recipientCity(), next.recipientCity())
                || !Objects.equals(current.recipientCounty(), next.recipientCounty())
                || !Objects.equals(current.recipientDetailAddress(), next.recipientDetailAddress())
                || !sameNumber(current.estimatedFreight(), next.estimatedFreight())
                || !Objects.equals(comparableDraft(current.orderDraft()), comparableDraft(next.orderDraft()));
        if (immutableChanged) {
            throw new BusinessException("SHIPMENT_ALREADY_ORDERED", HttpStatus.CONFLICT,
                    "发货单已下单，平台、店铺、备货人、发货人、收件与物流资料不能修改");
        }
    }

    private static boolean sameNumber(BigDecimal left, BigDecimal right) {
        return left == null || right == null ? left == right : left.compareTo(right) == 0;
    }

    private static AneOrderDraft comparableDraft(AneOrderDraft draft) {
        if (draft == null) return null;
        // Browser JSON omits decimal padding returned by MySQL. Compare numeric values without changing the saved draft.
        return new AneOrderDraft(draft.cargoName(), draft.packType(),
                draft.weight() == null ? null : draft.weight().stripTrailingZeros(),
                draft.volume() == null ? null : draft.volume().stripTrailingZeros(),
                draft.pieceAmount(), draft.productTypeId(), draft.goodsType(), draft.payType(), draft.logisticsRemark(),draft.yimi());
    }
}
