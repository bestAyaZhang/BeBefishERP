import type { PageResult, RecordStatus } from '../masterdata/types';

export type ProductType = 'simple' | 'variant';

export interface ProductSpecification {
  name: string;
  values: string[];
}

export interface ProductSupplierQuoteInput {
  id?: number;
  supplierId: number;
  supplierItemNo: string;
  purchasePrice: number;
  minPurchaseQuantity: number;
  defaultQuote: boolean;
  status: RecordStatus;
}

export interface SkuForm {
  id?: number;
  skuCode: string;
  barcode: string;
  skuName: string;
  specificationValues: string[];
  salesUnit: string;
  defaultSalePrice: number | null;
  standardCost: number | null;
  safetyStockQuantity: number | null;
  packageLengthCm: number | null;
  packageWidthCm: number | null;
  packageHeightCm: number | null;
  packageVolumeCm3: number | null;
  innerPackageLengthCm: number | null;
  innerPackageWidthCm: number | null;
  innerPackageHeightCm: number | null;
  netWeightKg: number | null;
  grossWeightKg: number | null;
  gramWeightG: number | null;
  innerPackageWeightKg: number | null;
  packagingMethod: string;
  cartonQuantity: number | null;
  skuImageFileId: number | null;
  packageImageFileId: number | null;
  cartonImageFileId: number | null;
  supplierQuotes?: ProductSupplierQuoteInput[];
  defaultSku: boolean;
  status: RecordStatus;
}

export interface ProductFormPayload {
  itemNo: string;
  productName: string;
  categoryId: number | null;
  brand: string;
  productType: ProductType;
  status: RecordStatus;
  mainImageFileId: number | null;
  remark: string;
  specifications: ProductSpecification[];
  skus: SkuForm[];
}

export interface ProductSupplierQuote {
  id: number;
  skuId: number;
  supplierId: number;
  supplierItemNo: string | null;
  purchasePrice: number;
  minPurchaseQuantity: number;
  defaultQuote: boolean;
  status: RecordStatus;
}

export interface ProductCatalogSupplierQuote extends ProductSupplierQuote {
  supplierName: string;
}

export interface ProductSku {
  id: number;
  skuCode: string;
  barcode: string | null;
  skuName: string;
  specText: string | null;
  specificationValues: string[];
  salesUnit: string;
  defaultSalePrice: number;
  standardCost: number;
  safetyStockQuantity: number;
  stockQuantity: number;
  packageLengthCm: number | null;
  packageWidthCm: number | null;
  packageHeightCm: number | null;
  packageVolumeCm3: number | null;
  innerPackageLengthCm: number | null;
  innerPackageWidthCm: number | null;
  innerPackageHeightCm: number | null;
  netWeightKg: number | null;
  grossWeightKg: number | null;
  gramWeightG: number | null;
  innerPackageWeightKg: number | null;
  packagingMethod: string | null;
  cartonQuantity: number | null;
  skuImageFileId: number | null;
  skuImageUrl: string | null;
  packageImageFileId: number | null;
  packageImageUrl: string | null;
  cartonImageFileId: number | null;
  cartonImageUrl: string | null;
  supplierQuotes: ProductCatalogSupplierQuote[];
  defaultSku: boolean;
  status: RecordStatus;
}

export type ProductCompletenessStatus = 'complete' | 'incomplete';

export interface Product {
  id: number;
  productCode: string;
  itemNo: string;
  productName: string;
  categoryId: number;
  categoryName: string | null;
  brand: string | null;
  productType: ProductType;
  mainImageFileId: number | null;
  mainImageUrl: string | null;
  defaultSupplierName: string | null;
  totalStock: number;
  totalSafetyStock: number;
  defaultSalePrice: number | null;
  completenessPercent: number;
  completenessStatus: ProductCompletenessStatus;
  missingGroups: string[];
  status: RecordStatus;
  remark: string | null;
  specifications: ProductSpecification[];
  skus: ProductSku[];
  createdAt: string;
  updatedAt: string;
}

export interface ProductQuery {
  page: number;
  size: number;
  keyword?: string;
  categoryId?: number;
  supplierId?: number;
  status?: RecordStatus;
}

export interface SkuSummary {
  id: number;
  skuCode: string;
  skuName: string;
  specText: string | null;
  defaultSalePrice: number;
  standardCost: number;
  status: RecordStatus;
}

export interface SaveSupplierQuotePayload {
  supplierId: number;
  supplierItemNo: string;
  purchasePrice: number;
  minPurchaseQuantity: number;
  defaultQuote: boolean;
  syncStandardCost: boolean;
}

export interface UploadedImage {
  id: number;
  url: string;
  originalFileName: string;
}

export interface ProductService {
  listProducts(query: ProductQuery): Promise<PageResult<Product>>;
  getCategoryCounts(): Promise<Record<number, number>>;
  getProduct(id: number): Promise<Product>;
  createProduct(payload: ProductFormPayload): Promise<Product>;
  updateProduct(id: number, payload: ProductFormPayload): Promise<Product>;
  changeProductStatus(id: number, status: RecordStatus): Promise<Product>;
  listSupplierQuotes(skuId: number): Promise<ProductSupplierQuote[]>;
  saveSupplierQuote(skuId: number, payload: SaveSupplierQuotePayload, quoteId?: number): Promise<ProductSupplierQuote>;
  setDefaultSupplierQuote(skuId: number, quoteId: number, syncStandardCost: boolean): Promise<ProductSupplierQuote>;
  uploadImage(file: File): Promise<UploadedImage>;
}
