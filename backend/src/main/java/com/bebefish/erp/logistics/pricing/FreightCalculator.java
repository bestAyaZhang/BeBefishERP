package com.bebefish.erp.logistics.pricing;

import com.bebefish.erp.common.api.BusinessException;
import com.bebefish.erp.logistics.pricing.FreightRules.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import org.springframework.http.HttpStatus;

/** Pure contract pricing; neither sender selection nor carrier I/O belongs here. */
public final class FreightCalculator {
    private static final BigDecimal MILLION = new BigDecimal("1000000");
    private static final BigDecimal MAX_AMOUNT = BigDecimal.valueOf(Long.MAX_VALUE, 2);

    public Result quote(Rules rules, Input input) {
        try {
            validateRules(rules, true);
        } catch (BusinessException error) {
            return unavailable("运费规则未完整配置：" + error.getMessage(), null, null, null);
        }
        if (input == null || ProvinceCatalog.normalize(input.destinationProvince()) == null) {
            return unavailable("请补全可识别的收件省份", null, null, null);
        }

        BigDecimal numerator;
        BigDecimal denominator = BigDecimal.ONE;
        boolean piece = "piece".equals(rules.mode());
        if (piece) {
            if (!positive(input.pieces()) || !integer(input.pieces())) {
                return unavailable("包裹件数必须为正整数", null, null, null);
            }
            numerator = input.pieces();
        } else {
            if (!positive(input.weight())) {
                return unavailable("请填写大于零的预计重量", null, null, null);
            }
            numerator = input.weight();
            if (rules.volumetric()) {
                if (input.volume() == null || input.volume().signum() < 0) {
                    return unavailable("请补充非负的总体积", null, null, null);
                }
                denominator = rules.volumeDivisor();
                numerator = input.weight().multiply(denominator).max(input.volume().multiply(MILLION));
            }
        }

        var quantity = piece ? numerator : numerator.divide(denominator, 6, RoundingMode.HALF_UP);
        var province = ProvinceCatalog.normalize(input.destinationProvince());
        var bands = list(rules.defaultBands());
        String region = "默认地区";
        boolean targeted = false;
        for (var group : list(rules.regionGroups())) {
            if (group.provinces().contains(province)) {
                bands = group.bands();
                region = group.name();
                targeted = true;
                break;
            }
        }
        Band matched = null;
        for (var band : bands) {
            if (numerator.compareTo(band.lowerExclusive().multiply(denominator)) > 0
                    && (band.upperInclusive() == null
                    || numerator.compareTo(band.upperInclusive().multiply(denominator)) <= 0)) {
                matched = band;
                break;
            }
        }
        if (matched == null) {
            return unavailable(targeted ? "该地区此区间暂无报价" : "该地区暂无适用区间报价", quantity, region, null);
        }

        // Do not divide the rational chargeable quantity before selecting the band or applying the ceiling.
        var remainder = numerator.subtract(matched.firstQuantity().multiply(denominator)).max(BigDecimal.ZERO);
        var renewal = remainder.divide(matched.additionalQuantity().multiply(denominator), 0, RoundingMode.CEILING);
        var amount = matched.firstFee().add(renewal.multiply(matched.additionalFee()));
        try {
            long count = renewal.longValueExact();
            amount.movePointRight(2).longValueExact();
            return new Result(true, "", amount.setScale(2, RoundingMode.UNNECESSARY), quantity, region, matched, count);
        } catch (ArithmeticException error) {
            return unavailable("运费金额或续量次数超出可保存范围", quantity, region, matched);
        }
    }

    public void validateRules(Rules rules, boolean publishing) {
        if (rules == null || !("weight".equals(rules.mode()) || "piece".equals(rules.mode()))) {
            throw invalid("请选择重量或件数计价");
        }
        boolean piece = "piece".equals(rules.mode());
        if (!piece && rules.volumetric()) {
            requirePositive(rules.volumeDivisor(), publishing, "体积系数");
        }
        validateBands(rules.defaultBands(), piece, publishing);
        var provinces = new HashSet<String>();
        boolean hasBands = !list(rules.defaultBands()).isEmpty();
        for (var group : list(rules.regionGroups())) {
            if (group == null || group.name() == null || group.name().isBlank()
                    || list(group.provinces()).isEmpty()) {
                throw invalid("指定地区组需填写名称并选择省份");
            }
            for (var province : group.provinces()) {
                if (province == null || !province.equals(ProvinceCatalog.normalize(province))) {
                    throw invalid("指定地区必须使用省份目录编码");
                }
                if (!provinces.add(province)) {
                    throw invalid("同一省份不能重复或同时属于多个地区组");
                }
            }
            validateBands(group.bands(), piece, publishing);
            if (publishing && list(group.bands()).isEmpty()) {
                throw invalid("指定地区组需配置运费区间");
            }
            hasBands |= !list(group.bands()).isEmpty();
        }
        if (publishing && !hasBands) {
            throw invalid("请配置默认运费或指定地区运费");
        }
    }

    private void validateBands(List<Band> bands, boolean piece, boolean publishing) {
        for (var band : list(bands)) {
            if (band == null || band.lowerExclusive() == null || band.lowerExclusive().signum() < 0) {
                throw invalid("区间下限必须为非负数");
            }
            if (band.upperInclusive() != null && band.upperInclusive().compareTo(band.lowerExclusive()) <= 0) {
                throw invalid("区间上限必须大于下限");
            }
            requirePositive(band.firstQuantity(), publishing, "首量");
            requirePositive(band.additionalQuantity(), publishing, "续量");
            requireMoney(band.firstFee(), publishing);
            requireMoney(band.additionalFee(), publishing);
            if (piece && (!integer(band.lowerExclusive()) || !integer(band.upperInclusive())
                    || !integer(band.firstQuantity()) || !integer(band.additionalQuantity()))) {
                throw invalid("按件计价的区间、首件和续件必须为整数");
            }
        }
        var sorted = new ArrayList<>(list(bands));
        sorted.sort(Comparator.comparing(Band::lowerExclusive));
        for (int i = 1; i < sorted.size(); i++) {
            var previous = sorted.get(i - 1);
            if (previous.upperInclusive() == null
                    || sorted.get(i).lowerExclusive().compareTo(previous.upperInclusive()) < 0) {
                throw invalid("同一地区的运费区间不能重叠");
            }
        }
    }

    private void requirePositive(BigDecimal value, boolean required, String label) {
        if ((value == null && required) || (value != null && value.signum() <= 0)) {
            throw invalid(label + "必须大于零");
        }
    }

    private void requireMoney(BigDecimal value, boolean required) {
        if (value == null) {
            if (required) throw invalid("首费和续费必须显式填写，零元请输入0");
            return;
        }
        if (value.signum() < 0 || value.compareTo(MAX_AMOUNT) > 0) {
            throw invalid("费用必须非负且不能超过可保存金额范围");
        }
        try {
            value.setScale(2, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException error) {
            throw invalid("费用最多保留两位小数");
        }
    }

    private static boolean positive(BigDecimal value) {
        return value != null && value.signum() > 0;
    }

    private static boolean integer(BigDecimal value) {
        return value == null || value.stripTrailingZeros().scale() <= 0;
    }

    private static <T> List<T> list(List<T> values) {
        return values == null ? List.of() : values;
    }

    private static Result unavailable(String reason, BigDecimal quantity, String region, Band band) {
        return new Result(false, reason, null, quantity, region, band, null);
    }

    private static BusinessException invalid(String message) {
        return new BusinessException("VALIDATION_FAILED", HttpStatus.BAD_REQUEST, message);
    }
}
