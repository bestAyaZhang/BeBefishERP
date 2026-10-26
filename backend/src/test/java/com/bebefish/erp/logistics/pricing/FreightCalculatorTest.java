package com.bebefish.erp.logistics.pricing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bebefish.erp.common.api.BusinessException;
import com.bebefish.erp.logistics.pricing.FreightRules.*;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class FreightCalculatorTest {
    private final FreightCalculator calculator = new FreightCalculator();

    @Test
    void chargesFirstAndExactOrPartialRenewalsWithoutExtraStep() {
        var rules = weight(band("0", null, "1", "10", "0.5", "2"));
        assertQuote(rules, "1", "10", 0);
        assertQuote(rules, "1.5", "12", 1);
        assertQuote(rules, "1.50000000000000000001", "14", 2);
        assertQuote(rules, "0.1", "10", 0);
    }

    @Test
    void selectsLeftOpenRightClosedBandThenPricesTheWholeQuantity() {
        var rules = weight(band("0", "10", "1", "10", "1", "2"),
                band("10", "30", "1", "20", "1", "3"));
        assertQuote(rules, "10", "28", 9);
        assertQuote(rules, "10.00001", "50", 10);
        assertQuote(rules, "20", "77", 19);
        assertThat(quote(rules, "31").available()).isFalse();
    }

    @Test
    void zeroFeesAreAvailableButMissingFeesRemainUnpriced() {
        var zero = weight(band("0", null, "1", "0", "1", "0"));
        assertQuote(zero, "12", "0", 11);
        var incomplete = weight(band("0", null, "1", null, "1", "0"));
        calculator.validateRules(incomplete, false);
        assertInvalid(incomplete, true);
        var result = quote(incomplete, "1");
        assertThat(result.available()).isFalse();
        assertThat(result.amount()).isNull();
        assertThat(result.reason()).isNotBlank();
    }

    @Test
    void targetedProvinceWinsAndItsGapNeverFallsBackToDefault() {
        var rules = new Rules("weight", false, bd("6000"),
                List.of(band("0", null, "1", "10", "1", "2")),
                List.of(new RegionGroup("江浙沪", List.of("310000", "320000", "330000"),
                        List.of(band("0", "5", "1", "30", "1", "2")))));
        var target = calculator.quote(rules, input("浙江省", "1", null, null));
        assertThat(target.amount()).isEqualByComparingTo("30");
        assertThat(target.matchedRegion()).isEqualTo("江浙沪");
        var gap = calculator.quote(rules, input("330000", "6", null, null));
        assertThat(gap.available()).isFalse();
        assertThat(gap.amount()).isNull();
        assertThat(gap.matchedRegion()).isEqualTo("江浙沪");
        assertThat(calculator.quote(rules, input("广东", "1", null, null)).amount())
                .isEqualByComparingTo("10");
    }

    @Test
    void missingDefaultAndUnknownProvinceDoNotBecomeFreeQuotes() {
        var none = weight();
        assertThat(quote(none, "1").available()).isFalse();
        var unknown = calculator.quote(weight(standard()), input("江南", "1", null, null));
        assertThat(unknown.available()).isFalse();
        assertThat(unknown.amount()).isNull();
    }

    @Test
    void volumetricExactOneKilogramDoesNotCrossFirstOrBandBoundary() {
        var rules = volumetric("6000", band("0", "1", "1", "10", "0.1", "2"),
                band("1", null, "1", "90", "1", "10"));
        var result = calculator.quote(rules, input("广东", "0.1", "0.006", null));
        assertThat(result.amount()).isEqualByComparingTo("10");
        assertThat(result.billedQuantity()).isEqualByComparingTo("1");
        assertThat(result.renewalCount()).isZero();
    }

    @Test
    void repeatingDecimalUsesCrossMultiplicationForBandAndRenewalBoundaries() {
        // 1 / 3 kg is above 0.333333333333333333333333333333, even if display is 0.333333.
        var rules = volumetric("3",
                band("0", "0.333333333333333333333333333333", "1", "10", "1", "1"),
                band("0.333333333333333333333333333333", null,
                        "0.333333333333333333333333333333", "20", "1", "2"));
        var result = calculator.quote(rules, input("广东", "0.1", "0.000001", null));
        assertThat(result.amount()).isEqualByComparingTo("22");
        assertThat(result.renewalCount()).isEqualTo(1L);
    }

    @Test
    void displayRoundingCannotIncreaseOrDecreaseRenewalCount() {
        var rules = volumetric("3", band("0", null, "1", "10", "1", "2"));
        var below = calculator.quote(rules, input("广东", "0.1", "0.0000059999999", null));
        var above = calculator.quote(rules, input("广东", "0.1", "0.0000060000001", null));
        assertThat(below.billedQuantity()).isEqualByComparingTo(above.billedQuantity());
        assertThat(below.renewalCount()).isEqualTo(1L);
        assertThat(above.renewalCount()).isEqualTo(2L);
        assertThat(below.amount()).isEqualByComparingTo("12");
        assertThat(above.amount()).isEqualByComparingTo("14");
    }

    @Test
    void volumetricNeedsVolumeAndUsesLargerOfWeightAndVolume() {
        var rules = volumetric("6000", standard());
        assertThat(calculator.quote(rules, input("广东", "1", null, null)).available()).isFalse();
        assertThat(calculator.quote(rules, input("广东", "2", "0.006", null)).amount())
                .isEqualByComparingTo("12");
        assertThat(calculator.quote(rules, input("广东", "1", "0.018", null)).amount())
                .isEqualByComparingTo("14");
        assertThat(calculator.quote(rules, input("广东", "1", "0", null)).amount())
                .isEqualByComparingTo("10");
    }

    @Test
    void pieceModeRequiresPositiveIntegerPiecesAndIgnoresWeightAndVolume() {
        var rules = new Rules("piece", true, null, List.of(standard()), List.of());
        var result = calculator.quote(rules, input("广东", null, null, "3"));
        assertThat(result.amount()).isEqualByComparingTo("14");
        for (var pieces : new String[]{null, "0", "-1", "1.1"}) {
            assertThat(calculator.quote(rules, input("广东", "1", "1", pieces)).available()).isFalse();
        }
        assertInvalid(new Rules("piece", false, null,
                List.of(band("0", "1.1", "1", "1", "1", "1")), List.of()), true);
        assertInvalid(new Rules("piece", false, null,
                List.of(band("0", null, "1.1", "1", "1", "1")), List.of()), true);
        assertInvalid(new Rules("piece", false, null,
                List.of(band("0", null, "1", "1", "0.5", "1")), List.of()), true);
    }

    @Test
    void weightRequiresPositiveRealWeightAndDoesNotNeedSenderOrUnrelatedInputs() {
        var rules = weight(standard());
        for (var weight : new String[]{null, "0", "-1"}) {
            assertThat(calculator.quote(rules, input("广东", weight, null, null)).available()).isFalse();
        }
        assertThat(calculator.quote(rules, input("广东", "1", null, null)).available()).isTrue();
        assertThat(calculator.quote(rules, input("广东", "1", "-1", null)).available()).isTrue();
    }

    @Test
    void overlappingBandsAreRejectedButAdjacentAndGappedBandsAreAllowed() {
        assertInvalid(weight(band("0", "10", "1", "1", "1", "1"),
                band("9", null, "1", "1", "1", "1")), false);
        assertInvalid(weight(standard(), standard()), false);
        calculator.validateRules(weight(band("10", "20", "1", "1", "1", "1"),
                band("0", "10", "1", "1", "1", "1")), true);
        calculator.validateRules(weight(band("0", "1", "1", "1", "1", "1"),
                band("2", null, "1", "1", "1", "1")), true);
    }

    @Test
    void provincesCannotOverlapWithinOrAcrossGroupsAndMustUseCatalogCodes() {
        for (var groups : List.of(
                List.of(new RegionGroup("a", List.of("330000", "330000"), List.of(standard()))),
                List.of(new RegionGroup("a", List.of("330000"), List.of(standard())),
                        new RegionGroup("b", List.of("330000"), List.of(standard()))),
                List.of(new RegionGroup("a", List.of("浙江"), List.of(standard()))),
                List.of(new RegionGroup("a", List.of("999999"), List.of(standard()))))) {
            assertInvalid(new Rules("weight", false, null, List.of(), groups), false);
        }
    }

    @Test
    void invalidBandNumbersModeAndDivisorAreBusinessValidationErrors() {
        assertInvalid(new Rules("invalid", false, null, List.of(standard()), List.of()), false);
        assertInvalid(volumetric("0", standard()), false);
        assertInvalid(volumetric(null, standard()), true);
        assertInvalid(weight(band("-1", null, "1", "1", "1", "1")), false);
        assertInvalid(weight(band("1", "1", "1", "1", "1", "1")), false);
        assertInvalid(weight(band("0", null, "0", "1", "1", "1")), false);
        assertInvalid(weight(band("0", null, "1", "-1", "1", "1")), false);
        assertInvalid(weight(band("0", null, "1", "0.001", "1", "1")), false);
        assertInvalid(weight(band("0", null, "1", "1", "0", "1")), false);
        assertInvalid(weight(), true);
    }

    @Test
    void largeMoneyIsExactAndStorageOrRenewalOverflowIsUnavailable() {
        assertQuote(weight(band("0", null, "1", "90071992547409.91", "1", "0.01")),
                "2", "90071992547409.92", 1);
        assertQuote(weight(band("0", null, "1", "92233720368547758.07", "1", "0")),
                "1", "92233720368547758.07", 0);
        var overflow = quote(weight(band("0", null, "1", "92233720368547758.07", "1", "0.01")), "2");
        assertThat(overflow.available()).isFalse();
        assertThat(overflow.amount()).isNull();
        assertThat(quote(weight(standard()), "9223372036854775809").available()).isFalse();
        assertInvalid(weight(band("0", null, "1", "92233720368547758.08", "1", "1")), true);
    }

    private void assertQuote(Rules rules, String quantity, String amount, long count) {
        var result = quote(rules, quantity);
        assertThat(result.available()).as(result.reason()).isTrue();
        assertThat(result.amount()).isEqualByComparingTo(amount);
        assertThat(result.renewalCount()).isEqualTo(count);
    }

    private void assertInvalid(Rules rules, boolean publishing) {
        assertThatThrownBy(() -> calculator.validateRules(rules, publishing))
                .isInstanceOfSatisfying(BusinessException.class,
                        error -> assertThat(error.code()).isEqualTo("VALIDATION_FAILED"));
    }

    private Result quote(Rules rules, String weight) {
        return calculator.quote(rules, input("广东", weight, null, null));
    }

    private Input input(String province, String weight, String volume, String pieces) {
        return new Input(province, bd(weight), bd(volume), bd(pieces));
    }

    private static Rules weight(Band... bands) {
        return new Rules("weight", false, bd("6000"), List.of(bands), List.of());
    }

    private static Rules volumetric(String divisor, Band... bands) {
        return new Rules("weight", true, bd(divisor), List.of(bands), List.of());
    }

    private static Band standard() {
        return band("0", null, "1", "10", "1", "2");
    }

    private static Band band(String lower, String upper, String first, String fee, String additional, String renewal) {
        return new Band(bd(lower), bd(upper), bd(first), bd(fee), bd(additional), bd(renewal));
    }

    private static BigDecimal bd(String value) {
        return value == null ? null : new BigDecimal(value);
    }
}
