package com.bebefish.erp.product.application;

import static org.assertj.core.api.Assertions.*;
import com.bebefish.erp.common.api.BusinessException;
import com.bebefish.erp.product.application.PddCostCalculator.Line;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class PddCostCalculatorTest {
    private final PddCostCalculator calculator=new PddCostCalculator();
    private BigDecimal d(String value){return new BigDecimal(value);}
    private Line line(String price,String refund,String fee,String mode,String roi,String cpa){return new Line(null,"","透明 / 350ml",d("2.10"),d("2.05"),d("0"),d("0"),d(price),d(refund),d(fee),mode,roi==null?null:d(roi),cpa==null?null:d(cpa));}
    @Test void calculatesRefundAdjustedRoiProfitWithoutRoundingTheIntermediateChain(){
        var result=calculator.calculate(line("5.50","13","0","roi","5",null));
        assertThat(result.totalCost()).isEqualByComparingTo("4.15");
        assertThat(result.grossProfit()).isEqualByComparingTo("1.35");
        assertThat(result.grossMargin()).isEqualByComparingTo("24.5455");
        assertThat(result.breakEvenRoi()).isEqualByComparingTo("4.6828");
        assertThat(result.promotionCost()).isEqualByComparingTo("1.10");
        assertThat(result.netProfit()).isEqualByComparingTo("0.07");
        assertThat(result.netMargin()).isEqualByComparingTo("1.3545");
    }
    @Test void accountsForPlatformFeesAndCpa(){
        var result=calculator.calculate(line("10","10","2","cpa",null,"1.20"));
        assertThat(result.netProfit()).isEqualByComparingTo("3.89");
        assertThat(result.netMargin()).isEqualByComparingTo("38.8500");
    }
    @Test void preservesLossesAndDoesNotProduceInvalidBreakEvenRatios(){
        var result=calculator.calculate(line("3","0","0","none",null,null));
        assertThat(result.netProfit()).isEqualByComparingTo("-1.15");
        assertThat(result.breakEvenRoi()).isNull();
        var allRefunded=calculator.calculate(line("10","100","0","roi","5",null));
        assertThat(allRefunded.netProfit()).isEqualByComparingTo("-2");
        assertThat(allRefunded.breakEvenRoi()).isNull();
    }
    @Test void acceptsZeroCostsAndNoAdvertising(){
        var result=calculator.calculate(new Line(null,"","规格",d("0"),d("0"),d("0"),d("0"),d("10"),d("0"),d("0"),"none",null,null));
        assertThat(result.netProfit()).isEqualByComparingTo("10");
        assertThat(result.netMargin()).isEqualByComparingTo("100");
    }
    @Test void rejectsZeroSalePriceInvalidRatesAndZeroRoi(){
        assertThatThrownBy(()->calculator.calculate(line("0","0","0","none",null,null))).isInstanceOf(BusinessException.class);
        assertThatThrownBy(()->calculator.calculate(line("10","101","0","none",null,null))).isInstanceOf(BusinessException.class);
        assertThatThrownBy(()->calculator.calculate(line("10","0","-1","none",null,null))).isInstanceOf(BusinessException.class);
        assertThatThrownBy(()->calculator.calculate(line("10","0","0","roi","0",null))).isInstanceOf(BusinessException.class);
    }
    @Test void rejectsMissingCostsAndTooManyDecimalPlaces(){
        assertThatThrownBy(()->calculator.calculate(new Line(null,"","规格",null,d("0"),d("0"),d("0"),d("10"),d("0"),d("0"),"none",null,null))).isInstanceOf(BusinessException.class);
        assertThatThrownBy(()->calculator.calculate(line("10.001","0","0","none",null,null))).isInstanceOf(BusinessException.class);
        assertThatThrownBy(()->calculator.calculate(line("10","0","0","cpa",null,"-1"))).isInstanceOf(BusinessException.class);
    }
}
