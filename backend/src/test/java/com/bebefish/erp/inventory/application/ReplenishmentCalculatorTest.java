package com.bebefish.erp.inventory.application;

import static com.bebefish.erp.inventory.application.ReplenishmentCalculator.DEFAULT_CONFIG;
import static com.bebefish.erp.inventory.application.ReplenishmentCalculator.evaluate;
import static com.bebefish.erp.inventory.application.ReplenishmentCalculator.validate;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatNoException;

import com.bebefish.erp.common.api.BusinessException;
import com.bebefish.erp.inventory.application.ReplenishmentCalculator.Config;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class ReplenishmentCalculatorTest {
    @Test
    void staysPendingAt29DaysWithoutFallback() {
        var result = evaluate(BigDecimal.valueOf(50), 29, BigDecimal.valueOf(300), DEFAULT_CONFIG);

        assertThat(result.average()).isNull();
        assertThat(result.safety()).isNull();
        assertThat(result.point()).isNull();
        assertThat(result.source()).isEqualTo("pending");
        assertThat(result.status()).isEqualTo("pending");
    }

    @Test
    void replacesManualFallbackWithAutomaticThresholdsAt30Days() {
        var config = new Config("auto", 5, 8, 20L, 70L);
        var stock = BigDecimal.valueOf(50);
        var demand = BigDecimal.valueOf(300);

        var fallback = evaluate(stock, 29, demand, config);
        assertThat(fallback.average()).isNull();
        assertThat(fallback.safety()).isEqualTo(20L);
        assertThat(fallback.point()).isEqualTo(70L);
        assertThat(fallback.source()).isEqualTo("fallback");
        assertThat(fallback.status()).isEqualTo("warning");

        var automatic = evaluate(stock, 30, demand, config);
        assertThat(automatic.average()).isEqualByComparingTo("10");
        assertThat(automatic.safety()).isEqualTo(50L);
        assertThat(automatic.point()).isEqualTo(130L);
        assertThat(automatic.source()).isEqualTo("auto");
        assertThat(automatic.status()).isEqualTo("critical");
    }

    @Test
    void calculatesZeroDemandAndFlagsZeroStockAtTheThreshold() {
        var result = evaluate(BigDecimal.ZERO, 30, BigDecimal.ZERO, DEFAULT_CONFIG);

        assertThat(result.average()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(result.safety()).isZero();
        assertThat(result.point()).isZero();
        assertThat(result.source()).isEqualTo("auto");
        assertThat(result.status()).isEqualTo("critical");
        assertThat(evaluate(new BigDecimal("0.01"), 30, BigDecimal.ZERO, DEFAULT_CONFIG).status())
                .isEqualTo("normal");
    }

    @Test
    void roundsTheTwoThresholdsSeparatelyAndWarnsAtTheReplenishmentPoint() {
        var config = new Config("auto", 1, 1, null, null);
        var demand = new BigDecimal("15.01");
        var result = evaluate(new BigDecimal("2.00"), 30, demand, config);

        assertThat(result.safety()).isEqualTo(1L);
        assertThat(result.point()).isEqualTo(2L);
        assertThat(result.status()).isEqualTo("warning");
        assertThat(evaluate(new BigDecimal("2.01"), 30, demand, config).status()).isEqualTo("normal");

        var demandWhereDoubleRoundingWouldOverstatePoint = BigDecimal.TEN;
        assertThat(evaluate(BigDecimal.ONE, 30, demandWhereDoubleRoundingWouldOverstatePoint, config).point())
                .isEqualTo(1L);
    }

    @Test
    void usesFixedThresholdsEvenWithEnoughDemandHistory() {
        var config = new Config("manual", 5, 8, 10L, 40L);
        var result = evaluate(new BigDecimal("38.75"), 30, BigDecimal.valueOf(300), config);

        assertThat(result.safety()).isEqualTo(10L);
        assertThat(result.point()).isEqualTo(40L);
        assertThat(result.source()).isEqualTo("manual");
        assertThat(result.status()).isEqualTo("warning");
    }

    @Test
    void preservesSafeIntegerBoundaryAndSuppressesUnrepresentableThresholds() {
        var config = new Config("auto", 1, 0, null, null);
        var representable = evaluate(BigDecimal.ZERO, 30, new BigDecimal("270215977642229730"), config);
        assertThat(representable.point()).isEqualTo(9_007_199_254_740_991L);

        var tooLarge = evaluate(BigDecimal.ZERO, 30, new BigDecimal("270215977642229730.0001"), config);
        assertThat(tooLarge.safety()).isNull();
        assertThat(tooLarge.point()).isNull();
        assertThat(tooLarge.status()).isEqualTo("pending");
    }

    @Test
    void rejectsMissingNegativeAndReversedFixedThresholds() {
        assertInvalid(new Config("manual", 5, 8, 25L, null));
        assertInvalid(new Config("manual", 5, 8, -1L, 25L));
        assertInvalid(new Config("manual", 5, 8, 25L, 24L));
        assertInvalid(new Config("manual", 5, 8, 25L, 100_000_000_000_000L));
        assertInvalid(new Config("auto", 5, 8, null, 25L));
        assertThatNoException().isThrownBy(() -> validate(new Config("manual", 5, 8, 0L, 99_999_999_999_999L)));
    }

    @Test
    void rejectsUnknownModeAndIncompleteOrNegativeAutomaticDays() {
        assertInvalid(new Config("other", 5, 8, null, null));
        assertInvalid(new Config("auto", null, 8, null, null));
        assertInvalid(new Config("auto", 5, -1, null, null));
        assertInvalid(new Config("manual", 36_501, 8, 0L, 0L));
        assertThatNoException().isThrownBy(() -> validate(new Config("auto", 0, 36_500, null, null)));
    }

    private static void assertInvalid(Config config) {
        assertThatExceptionOfType(BusinessException.class)
                .isThrownBy(() -> validate(config))
                .satisfies(error -> {
                    assertThat(error.code()).isEqualTo("VALIDATION_FAILED");
                    assertThat(error.status()).isEqualTo(HttpStatus.BAD_REQUEST);
                });
    }
}
