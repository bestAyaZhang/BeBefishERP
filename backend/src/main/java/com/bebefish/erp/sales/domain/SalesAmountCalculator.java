package com.bebefish.erp.sales.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class SalesAmountCalculator {
    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
    private static final BigDecimal ONE_HUNDRED = new BigDecimal("100");

    public SalesAmountSummary calculate(
            List<SalesLineInput> lines,
            BigDecimal shippingFee,
            BigDecimal receivedAmount
    ) {
        if (lines == null || lines.isEmpty()) {
            throw new IllegalArgumentException("销售单至少需要一条明细");
        }
        var originalAmount = ZERO;
        var discountAmount = ZERO;
        for (var line : lines) {
            validateLine(line);
            var lineOriginal = line.quantity().multiply(line.unitPrice());
            var lineDiscount = lineOriginal.multiply(line.discountRate())
                    .divide(ONE_HUNDRED, 2, RoundingMode.HALF_UP);
            originalAmount = originalAmount.add(lineOriginal);
            discountAmount = discountAmount.add(lineDiscount);
        }

        var goodsAmount = money(originalAmount.subtract(discountAmount));
        var shipping = nonNegativeMoney(shippingFee, "运费不能小于 0");
        var total = money(goodsAmount.add(shipping));
        var received = nonNegativeMoney(receivedAmount, "收款金额不能小于 0");
        if (received.compareTo(total) > 0) {
            throw new IllegalArgumentException("收款金额不能超过应收合计");
        }
        return new SalesAmountSummary(
                money(originalAmount),
                money(discountAmount),
                goodsAmount,
                shipping,
                total,
                received,
                money(total.subtract(received))
        );
    }

    private void validateLine(SalesLineInput line) {
        if (line == null || line.quantity() == null || line.quantity().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("销售数量必须大于 0");
        }
        if (line.unitPrice() == null || line.unitPrice().compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("销售单价不能小于 0");
        }
        if (line.discountRate() == null
                || line.discountRate().compareTo(BigDecimal.ZERO) < 0
                || line.discountRate().compareTo(ONE_HUNDRED) > 0) {
            throw new IllegalArgumentException("折扣率必须在 0 到 100 之间");
        }
    }

    private BigDecimal nonNegativeMoney(BigDecimal value, String message) {
        if (value == null || value.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException(message);
        }
        return money(value);
    }

    private BigDecimal money(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }
}
