import { masterdataService } from '../masterdata/masterdataService';
import { productService } from '../product/productService';
import type { Product } from '../product/types';
import type { SaleProductOption, SalesCatalog, SalesCatalogService } from './types';

function toProductOptions(products: Product[]): SaleProductOption[] {
  return products.flatMap((product) => product.skus.map((sku) => ({
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

export const salesCatalogService: SalesCatalogService = {
  async loadCatalog(): Promise<SalesCatalog> {
    const [customers, warehouses, products] = await Promise.all([
      masterdataService.listActiveCustomers(),
      masterdataService.listActiveWarehouses(),
      productService.listProducts({ page: 1, size: 100, status: 'enabled' })
    ]);
    return { customers, warehouses, products: toProductOptions(products.records) };
  }
};
