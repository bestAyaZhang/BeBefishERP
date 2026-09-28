package com.bebefish.erp.shipping.domain;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

public record ShipmentFormInput(
        @Size(max = 100) String platform,
        @NotBlank(message = "请选择店铺") @Size(max = 200) String shopName,
        @NotEmpty(message = "请至少选择一名备货人")
        @Size(max = 20, message = "备货人最多选择20人") List<@NotBlank @Size(max = 100) String> preparers,
        @NotBlank(message = "请填写收件人姓名") @Size(max = 100) String recipientName,
        @NotBlank(message = "请填写收件人电话") @Size(max = 50) String recipientPhone,
        @NotBlank(message = "请填写省") @Size(max = 30) String recipientProvince,
        @NotBlank(message = "请填写市") @Size(max = 30) String recipientCity,
        @NotBlank(message = "请填写区/县") @Size(max = 30) String recipientCounty,
        @NotBlank(message = "请填写详细地址") @Size(max = 500) String recipientDetailAddress,
        @NotBlank(message = "请填写备货内容") @Size(max = 10000) String preparationContent,
        @Size(max = 5000) String remark,
        @DecimalMin(value = "0", message = "预计运费不能小于零")
        @Digits(integer = 10, fraction = 2, message = "预计运费最多保留两位小数") BigDecimal estimatedFreight,
        @NotNull(message = "缺少安能下单资料") @Valid AneOrderDraft orderDraft
) {
    public ShipmentFormInput normalized() {
        var normalizedPreparers = new LinkedHashSet<String>();
        if (preparers != null) {
            for (String preparer : preparers) {
                String cleaned = clean(preparer);
                if (!cleaned.isEmpty()) normalizedPreparers.add(cleaned);
            }
        }
        return new ShipmentFormInput(clean(platform), clean(shopName), new ArrayList<>(normalizedPreparers),
                clean(recipientName), clean(recipientPhone), clean(recipientProvince), clean(recipientCity),
                clean(recipientCounty), clean(recipientDetailAddress), clean(preparationContent), clean(remark),
                estimatedFreight, orderDraft == null ? null : orderDraft.normalized());
    }

    private static String clean(String value) { return value == null ? "" : value.strip(); }
}
