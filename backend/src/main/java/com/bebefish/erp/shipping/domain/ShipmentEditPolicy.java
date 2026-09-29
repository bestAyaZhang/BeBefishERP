package com.bebefish.erp.shipping.domain;

import com.bebefish.erp.common.api.BusinessException;
import java.util.Objects;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class ShipmentEditPolicy {
    public void assertAllowed(ShipmentContent previous, ShipmentFormInput next, boolean ordered) {
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
                || !Objects.equals(current.estimatedFreight(), next.estimatedFreight())
                || !Objects.equals(current.orderDraft(), next.orderDraft());
        if (immutableChanged) {
            throw new BusinessException("SHIPMENT_ALREADY_ORDERED", HttpStatus.CONFLICT,
                    "发货单已下单，平台、店铺、备货人、发货人、收件与物流资料不能修改");
        }
    }
}
