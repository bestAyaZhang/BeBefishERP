package com.bebefish.erp.inventory.application;

import com.bebefish.erp.common.api.BusinessException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import org.springframework.http.HttpStatus;

public final class ReplenishmentCalculator {
    private static final String AUTO = "auto";
    private static final String MANUAL = "manual";
    private static final String PENDING = "pending";
    private static final int HISTORY_DAYS = 30;
    private static final int MAX_DAYS = 36_500;
    private static final long MAX_FIXED_THRESHOLD = 99_999_999_999_999L;
    private static final BigDecimal WINDOW_LENGTH = BigDecimal.valueOf(HISTORY_DAYS);
    private static final BigDecimal MAX_SAFE_JSON_INTEGER = BigDecimal.valueOf(9_007_199_254_740_991L);
    public static final Config DEFAULT_CONFIG = new Config(AUTO, 5, 8, null, null);

    private ReplenishmentCalculator() {
    }

    public record Config(String mode, Integer bufferDays, Integer leadDays, Long manualSafety, Long manualPoint) {
    }

    public record Result(BigDecimal average, Long safety, Long point, String source, String status) {
    }

    public static Result evaluate(BigDecimal stock, int historyDays, BigDecimal demandTotal, Config config) {
        var average = historyDays >= HISTORY_DAYS ? demandTotal.divide(WINDOW_LENGTH, 6, RoundingMode.HALF_UP) : null;
        if (AUTO.equals(config.mode()) && average != null) {
            // Round each threshold from the original demand to avoid feeding rounded safety stock into the point.
            var point = threshold(demandTotal, config.bufferDays() + config.leadDays());
            if (point.compareTo(MAX_SAFE_JSON_INTEGER) > 0) {
                return new Result(average, null, null, PENDING, PENDING);
            }
            var safety = threshold(demandTotal, config.bufferDays());
            return effective(stock, average, safety.longValueExact(), point.longValueExact(), AUTO);
        }

        if (config.manualSafety() == null || config.manualPoint() == null) {
            return new Result(average, null, null, PENDING, PENDING);
        }
        var source = MANUAL.equals(config.mode()) ? MANUAL : "fallback";
        return effective(stock, average, config.manualSafety(), config.manualPoint(), source);
    }

    public static void validate(Config config) {
        if (config == null || !(AUTO.equals(config.mode()) || MANUAL.equals(config.mode()))) {
            throw invalid("请选择自动计算或手工设置");
        }
        if (!validDays(config.bufferDays()) || !validDays(config.leadDays())) {
            throw invalid("缓冲天数和采购到可售入库天数必须在 0 到 36500 之间");
        }
        if (AUTO.equals(config.mode()) && config.manualSafety() == null && config.manualPoint() == null) {
            return;
        }
        if (!validThreshold(config.manualSafety()) || !validThreshold(config.manualPoint())) {
            throw invalid("安全库存和补货点必须在 0 到 99999999999999 之间");
        }
        if (config.manualPoint() < config.manualSafety()) {
            throw invalid("补货点不能低于安全库存");
        }
    }

    private static BigDecimal threshold(BigDecimal total, int days) {
        return total.multiply(BigDecimal.valueOf(days)).divide(WINDOW_LENGTH, 0, RoundingMode.CEILING);
    }

    private static Result effective(BigDecimal stock, BigDecimal average, long safety, long point, String source) {
        if (stock.compareTo(BigDecimal.valueOf(safety)) <= 0) {
            return new Result(average, safety, point, source, "critical");
        }
        var status = stock.compareTo(BigDecimal.valueOf(point)) <= 0 ? "warning" : "normal";
        return new Result(average, safety, point, source, status);
    }

    private static boolean validDays(Integer days) {
        return days != null && days >= 0 && days <= MAX_DAYS;
    }

    private static boolean validThreshold(Long value) {
        return value != null && value >= 0 && value <= MAX_FIXED_THRESHOLD;
    }

    private static BusinessException invalid(String message) {
        return new BusinessException("VALIDATION_FAILED", HttpStatus.BAD_REQUEST, message);
    }
}
