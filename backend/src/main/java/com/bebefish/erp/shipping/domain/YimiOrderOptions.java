package com.bebefish.erp.shipping.domain;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

/** Explicit per-order YiMi choices; absence is valid for drafts and existing ANE snapshots. */
public record YimiOrderOptions(
        @Min(0) @Max(1) Integer visitDelivery,
        @Min(1) @Max(4) Integer deliveryFlag,
        @Size(max=10) String floorNo) {
    public YimiOrderOptions normalized() {
        return new YimiOrderOptions(visitDelivery,deliveryFlag,floorNo==null?"":floorNo.strip());
    }
}
