package com.bebefish.erp.product.application;

import com.bebefish.erp.common.api.BusinessException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class PddCostCalculator {
    public record Line(Long skuId, String skuCode, String specification, BigDecimal unitCost,
            BigDecimal shippingCost, BigDecimal packagingCost, BigDecimal otherCost, BigDecimal salePrice,
            BigDecimal refundRate, BigDecimal platformFeeRate, String adMode, BigDecimal roi, BigDecimal adCost) {}
    public record Result(BigDecimal totalCost, BigDecimal grossProfit, BigDecimal grossMargin,
            BigDecimal breakEvenRoi, BigDecimal promotionCost, BigDecimal netProfit, BigDecimal netMargin) {}
    private static final BigDecimal HUNDRED = new BigDecimal("100");

    public Result calculate(Line line) {
        if (line == null) throw invalid("规格行不能为空");
        money(line.unitCost(), "货品成本"); money(line.shippingCost(), "快递费");
        money(line.packagingCost(), "包装费"); money(line.otherCost(), "其他成本"); money(line.salePrice(), "售价");
        if (line.salePrice().signum() == 0) throw invalid("售价必须大于零");
        rate(line.refundRate(), "售后率"); rate(line.platformFeeRate(), "平台费率");
        if (!java.util.Set.of("none", "roi", "cpa").contains(line.adMode() == null ? "" : line.adMode())) throw invalid("请选择推广计算方式");
        if ("roi".equals(line.adMode()) && (line.roi() == null || line.roi().signum() <= 0
                || line.roi().scale() > 4 || line.roi().compareTo(new BigDecimal("1000000")) > 0)) throw invalid("投产比必须大于零，最多四位小数");
        if ("cpa".equals(line.adMode())) money(line.adCost(), "成交出价");
        var total = line.unitCost().add(line.shippingCost()).add(line.packagingCost()).add(line.otherCost());
        var gross = line.salePrice().subtract(total);
        var retained = BigDecimal.ONE.subtract(line.refundRate().divide(HUNDRED));
        var feeRate = line.platformFeeRate().divide(HUNDRED);
        // 售后订单不计商品毛利，推广费仍计入；平台费按未退款成交额计提。
        var beforeAd = gross.subtract(line.salePrice().multiply(feeRate)).multiply(retained);
        var promotion = switch (line.adMode()) {
            case "roi" -> line.salePrice().divide(line.roi(), 12, RoundingMode.HALF_UP);
            case "cpa" -> line.adCost();
            default -> BigDecimal.ZERO;
        };
        var net = beforeAd.subtract(promotion);
        return new Result(round(total), round(gross), percent(gross, line.salePrice()),
                beforeAd.signum() <= 0 ? null : line.salePrice().divide(beforeAd, 4, RoundingMode.HALF_UP),
                round(promotion), round(net), percent(net, line.salePrice()));
    }

    private BigDecimal round(BigDecimal value) { return value.setScale(2, RoundingMode.HALF_UP); }
    private BigDecimal percent(BigDecimal value, BigDecimal sale) { return value.multiply(HUNDRED).divide(sale, 4, RoundingMode.HALF_UP); }
    private void money(BigDecimal value, String label) {
        if (value == null || value.signum() < 0 || value.scale() > 2 || value.compareTo(new BigDecimal("999999999.99")) > 0)
            throw invalid(label + "须为非负金额，最多两位小数且不超过999999999.99");
    }
    private void rate(BigDecimal value, String label) {
        if (value == null || value.signum() < 0 || value.compareTo(HUNDRED) > 0 || value.scale() > 2)
            throw invalid(label + "须在0到100之间，最多两位小数");
    }
    public static BusinessException invalid(String message) { return new BusinessException("VALIDATION_FAILED", HttpStatus.BAD_REQUEST, message); }
}
