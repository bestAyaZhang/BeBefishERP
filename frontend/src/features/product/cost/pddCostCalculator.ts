export type AdMode = 'none' | 'roi' | 'cpa';
export interface CostLine {
  skuId: number | null; skuCode: string; specification: string;
  unitCost: number | null; shippingCost: number | null; packagingCost: number | null; otherCost: number | null;
  salePrice: number | null; refundRate: number | null; platformFeeRate: number | null;
  adMode: AdMode; roi: number | null; adCost: number | null;
}
export interface CostResult {
  totalCost: number; grossProfit: number; grossMargin: number; breakEvenRoi: number | null;
  promotionCost: number; netProfit: number; netMargin: number;
}
export function emptyCostLine(): CostLine {
  return { skuId: null, skuCode: '', specification: '', unitCost: null, shippingCost: 0, packagingCost: 0,
    otherCost: 0, salePrice: null, refundRate: 0, platformFeeRate: 0, adMode: 'none', roi: null, adCost: null };
}
export function validateCostLine(line: CostLine): string {
  if (!line.specification.trim()) return '请填写规格名称';
  if (line.specification.length > 200 || line.skuCode.length > 100) return '规格或编码过长';
  for (const [key, label] of [['unitCost','货品成本'],['shippingCost','快递费'],['packagingCost','包装费'],['otherCost','其他成本'],['salePrice','售价']] as const) {
    const value = line[key];
    if (value === null || !Number.isFinite(value) || value < 0 || value > 999999999.99 || Math.abs(value * 100 - Math.round(value * 100)) > 1e-5) return `${label}须为非负金额，最多两位小数`;
  }
  if (!line.salePrice) return '售价必须大于零';
  for (const key of ['refundRate','platformFeeRate'] as const) {
    const value = line[key];
    if (value === null || !Number.isFinite(value) || value < 0 || value > 100 || Math.abs(value * 100 - Math.round(value * 100)) > 1e-7) return '售后率和平台费率须在0到100之间，最多两位小数';
  }
  if (!['none','roi','cpa'].includes(line.adMode)) return '请选择推广计算方式';
  if (line.adMode === 'roi' && (line.roi === null || !Number.isFinite(line.roi) || line.roi <= 0 || line.roi > 1000000 || Math.abs(line.roi * 10000 - Math.round(line.roi * 10000)) > 1e-5)) return '投产比必须大于零，最多四位小数';
  if (line.adMode === 'cpa' && (line.adCost === null || !Number.isFinite(line.adCost) || line.adCost < 0 || line.adCost > 999999999.99 || Math.abs(line.adCost * 100 - Math.round(line.adCost * 100)) > 1e-5)) return '成交出价须为非负金额，最多两位小数';
  return '';
}
export function calculateCost(line: CostLine): CostResult | null {
  if (validateCostLine({ ...line, specification: line.specification || '预览' })) return null;
  const totalCost = line.unitCost! + line.shippingCost! + line.packagingCost! + line.otherCost!;
  const grossProfit = line.salePrice! - totalCost;
  const beforeAd = (grossProfit - line.salePrice! * line.platformFeeRate! / 100) * (1 - line.refundRate! / 100);
  const promotionCost = line.adMode === 'roi' ? line.salePrice! / line.roi! : line.adMode === 'cpa' ? line.adCost! : 0;
  const netProfit = beforeAd - promotionCost;
  return { totalCost, grossProfit, grossMargin: grossProfit / line.salePrice! * 100,
    breakEvenRoi: beforeAd > 0 ? line.salePrice! / beforeAd : null, promotionCost, netProfit, netMargin: netProfit / line.salePrice! * 100 };
}
export function priceForMargin(line: CostLine, margin: number): number | null {
  if (!Number.isFinite(margin) || margin < 0 || margin >= 100 || [line.unitCost,line.shippingCost,line.packagingCost,line.otherCost].some(n=>n===null || !Number.isFinite(n) || n<0)) return null;
  const cost = line.unitCost! + line.shippingCost! + line.packagingCost! + line.otherCost!;
  return cost > 0 ? Math.ceil((cost / (1 - margin / 100) - 1e-8) * 100) / 100 : null;
}
