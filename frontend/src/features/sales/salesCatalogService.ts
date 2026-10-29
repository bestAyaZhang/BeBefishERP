import { masterdataService } from '../masterdata/masterdataService';
import { productService } from '../product/productService';
import type { Product } from '../product/types';
import type { SaleProductOption, SalesCatalog, SalesCatalogService } from './types';

export function toProductOptions(products: Product[]): SaleProductOption[] {
  return products.filter(product => product.status === 'enabled').flatMap((product) => product.skus.filter(sku => sku.status === 'enabled').map((sku) => ({
    skuId: sku.id ?? 0,
    skuCode: sku.skuCode,
    itemNo: product.itemNo,
    barcode: sku.barcode ?? '',
    productName: product.productName,
    skuName: sku.skuName || '默认规格',
    specification: sku.specificationValues.join(' / ') || '默认规格',
    packagingMethod: sku.packagingMethod ?? '',
    cartonQuantity: sku.cartonQuantity,
    salesUnit: sku.salesUnit || '件',
    defaultSalePrice: Number(sku.defaultSalePrice ?? 0)
  })).filter((option) => option.skuId > 0));
}

export async function loadSaleProducts(): Promise<SaleProductOption[]> {
  const products: Product[] = [];
  for (let page = 1; ; page++) {
    const result = await productService.listProducts({ page, size: 100, status: 'enabled' });
    products.push(...result.records);
    if (products.length >= result.total || !result.records.length) return toProductOptions(products);
  }
}

export const salesCatalogService: SalesCatalogService = {
  async loadCatalog(): Promise<SalesCatalog> {
    const [customers, warehouses, products] = await Promise.all([
      masterdataService.listActiveCustomers(),
      masterdataService.listActiveWarehouses(),
      loadSaleProducts()
    ]);
    return { customers, warehouses, products };
  }
};
