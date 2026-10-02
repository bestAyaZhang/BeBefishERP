import type { Product } from './types';

export type PrototypeProductRow = {
  sku: string;
  name: string;
  spu: string;
  category: string;
  brand: string;
  channel: string;
  supplier: string;
  stock: number;
  safetyStock: number;
  price: string;
  cost: string;
  status: string;
  audit: string;
  updated: string;
  sales: string;
  imageTone: string;
  alerts: string[];
};

function money(value: number | null) {
  return value === null ? '¥0.00' : `¥${value.toFixed(2)}`;
}

export function mapProductToPrototype(product: Product, categoryName: string): PrototypeProductRow {
  const sku = product.skus.find((item) => item.defaultSku) ?? product.skus[0];
  const enabled = product.status === 'enabled';
  return {
    sku: sku?.skuCode || `${product.productCode}-DEFAULT`,
    name: product.productName,
    spu: product.productCode,
    category: categoryName || '未分类',
    brand: product.brand || '未设置品牌',
    channel: 'ERP',
    supplier: '待维护',
    stock: 0,
    safetyStock: 0,
    price: money(sku?.defaultSalePrice ?? null),
    cost: money(sku?.standardCost ?? null),
    status: product.status === 'draft' ? '草稿' : enabled ? '在售' : '已停用',
    audit: product.mainImageFileId ? '资料完整' : '待补主图',
    updated: '刚刚更新',
    sales: '0',
    imageTone: enabled ? 'bg-blue-50 text-[#536dff]' : 'bg-slate-100 text-slate-500',
    alerts: []
  };
}
