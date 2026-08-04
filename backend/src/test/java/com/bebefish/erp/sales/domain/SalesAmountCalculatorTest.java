package com.bebefish.erp.sales.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class SalesAmountCalculatorTest {
    private final SalesAmountCalculator calculator = new SalesAmountCalculator();

    @Test
    void calculatesDiscountShippingReceivedAndOutstanding() {
        var summary = calculator.calculate(List.of(
                line("1250", "2.20", "0"),
                line("100", "5.00", "10")
        ), new BigDecimal("100"), new BigDecimal("1000"));

        assertThat(summary.originalAmount()).isEqualByComparingTo("3250.00");
        assertThat(summary.discountAmount()).isEqualByComparingTo("50.00");
        assertThat(summary.goodsAmount()).isEqualByComparingTo("3200.00");
        assertThat(summary.shippingFee()).isEqualByComparingTo("100.00");
        assertThat(summary.totalAmount()).isEqualByComparingTo("3300.00");
        assertThat(summary.receivedAmount()).isEqualByComparingTo("1000.00");
        assertThat(summary.outstandingAmount()).isEqualByComparingTo("2300.00");
    }

    @Test
    void rejectsReceivedAmountAboveTotal() {
        assertThatThrownBy(() -> calculator.calculate(
                List.of(line("1", "10", "0")), BigDecimal.ZERO, new BigDecimal("11")
        )).hasMessage("收款金额不能超过应收合计");
    }

    @Test
    void rejectsInvalidLineValues() {
        assertThatThrownBy(() -> calculator.calculate(
                List.of(line("0", "10", "0")), BigDecimal.ZERO, BigDecimal.ZERO
        )).hasMessage("销售数量必须大于 0");
        assertThatThrownBy(() -> calculator.calculate(
                List.of(line("1", "10", "101")), BigDecimal.ZERO, BigDecimal.ZERO
        )).hasMessage("折扣率必须在 0 到 100 之间");
    }

    private SalesLineInput line(String quantity, String unitPrice, String discountRate) {
        return new SalesLineInput(new BigDecimal(quantity), new BigDecimal(unitPrice), new BigDecimal(discountRate));
    }
}
