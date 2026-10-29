import { describe,it,expect } from 'vitest';
import { calculateCost,emptyCostLine,priceForMargin,validateCostLine } from './pddCostCalculator';
const line=()=>({...emptyCostLine(),specification:'透明',unitCost:2.1,shippingCost:2.05,salePrice:5.5,refundRate:13,adMode:'roi' as const,roi:5});
describe('PDD cost calculation',()=>{
  it('matches refund-adjusted ROI calculations with full intermediate precision',()=>{
    const r=calculateCost(line())!;expect(r.totalCost).toBeCloseTo(4.15);expect(r.grossMargin).toBeCloseTo(24.5454545);
    expect(r.breakEvenRoi).toBeCloseTo(4.6828,4);expect(r.netProfit).toBeCloseTo(.0745,8);expect(r.netMargin).toBeCloseTo(1.354545,5);
  });
  it('deducts platform fees and a single CPA amount',()=>{
    const r=calculateCost({...line(),salePrice:10,refundRate:10,platformFeeRate:2,adMode:'cpa',adCost:1.2})!;
    expect(r.netProfit).toBeCloseTo(3.885);expect(r.netMargin).toBeCloseTo(38.85);
  });
  it('keeps losses and returns no break-even ROI when gross profit is exhausted',()=>{
    const r=calculateCost({...line(),salePrice:3,adMode:'none',refundRate:0})!;expect(r.netProfit).toBeCloseTo(-1.15);expect(r.breakEvenRoi).toBeNull();
    const refunded=calculateCost({...line(),refundRate:100})!;expect(refunded.netProfit).toBeCloseTo(-1.1);expect(refunded.breakEvenRoi).toBeNull();
  });
  it('does not turn missing costs, zero prices or invalid rates into zero results',()=>{
    for(const patch of [{unitCost:null},{salePrice:0},{refundRate:101},{platformFeeRate:-1},{roi:0},{salePrice:NaN},{shippingCost:.001}]) expect(calculateCost({...line(),...patch})).toBeNull();
    expect(validateCostLine({...line(),specification:''})).toContain('规格');
  });
  it('reverse-prices to the target gross margin and rounds upwards to cents',()=>{
    expect(priceForMargin(line(),30)).toBe(5.93);expect(priceForMargin(line(),100)).toBeNull();expect(priceForMargin({...line(),unitCost:null},20)).toBeNull();
  });
});
