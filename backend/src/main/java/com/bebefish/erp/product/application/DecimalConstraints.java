package com.bebefish.erp.product.application;

import com.bebefish.erp.common.api.BusinessException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import org.springframework.http.HttpStatus;

final class DecimalConstraints {
    private DecimalConstraints() {
    }

    static BigDecimal requireFits(
            BigDecimal value,
            int integerDigits,
            int fractionDigits,
            String fieldName
    ) {
        if (value == null) {
            return null;
        }
        try {
            value.setScale(fractionDigits, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException exception) {
            throw invalid(integerDigits, fractionDigits, fieldName);
        }
        var maximum = BigDecimal.TEN.pow(integerDigits)
                .subtract(BigDecimal.ONE.movePointLeft(fractionDigits));
        if (value.abs().compareTo(maximum) > 0) {
            throw invalid(integerDigits, fractionDigits, fieldName);
        }
        return value;
    }

    private static BusinessException invalid(int integerDigits, int fractionDigits, String fieldName) {
        return new BusinessException(
                "VALIDATION_FAILED",
                HttpStatus.BAD_REQUEST,
                fieldName + "最多允许 " + integerDigits + " 位整数和 " + fractionDigits + " 位小数"
        );
    }
}
