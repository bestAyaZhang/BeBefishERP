import type { Product, ProductFormPayload } from './types';

export function toProductFormPayload(product: Product): ProductFormPayload {
  return {
    itemNo: product.itemNo,
    productName: product.productName,
    categoryId: product.categoryId,
    brand: product.brand ?? '',
    productType: product.productType,
    mainImageFileId: product.mainImageFileId,
    remark: product.remark ?? '',
    specifications: product.specifications.map((specification) => ({
      name: specification.name,
      values: [...specification.values]
    })),
    skus: product.skus.map((sku) => ({
      id: sku.id,
      skuCode: sku.skuCode,
      barcode: sku.barcode ?? '',
      skuName: sku.skuName,
      specificationValues: [...sku.specificationValues],
      salesUnit: sku.salesUnit,
      defaultSalePrice: sku.defaultSalePrice,
      standardCost: sku.standardCost,
      safetyStockQuantity: sku.safetyStockQuantity,
      packageLengthCm: sku.packageLengthCm,
      packageWidthCm: sku.packageWidthCm,
      packageHeightCm: sku.packageHeightCm,
      packageVolumeCm3: sku.packageVolumeCm3,
      innerPackageLengthCm: sku.innerPackageLengthCm,
      innerPackageWidthCm: sku.innerPackageWidthCm,
      innerPackageHeightCm: sku.innerPackageHeightCm,
      netWeightKg: sku.netWeightKg,
      grossWeightKg: sku.grossWeightKg,
      gramWeightG: sku.gramWeightG,
      innerPackageWeightKg: sku.innerPackageWeightKg,
      packagingMethod: sku.packagingMethod ?? '',
      cartonQuantity: sku.cartonQuantity,
      skuImageFileId: sku.skuImageFileId,
      packageImageFileId: sku.packageImageFileId,
      cartonImageFileId: sku.cartonImageFileId,
      supplierQuotes: sku.supplierQuotes.map((quote) => ({
        id: quote.id,
        supplierId: quote.supplierId,
        supplierItemNo: quote.supplierItemNo ?? '',
        purchasePrice: quote.purchasePrice,
        minPurchaseQuantity: quote.minPurchaseQuantity,
        defaultQuote: quote.defaultQuote,
        status: quote.status
      }))
    }))
  };
}
