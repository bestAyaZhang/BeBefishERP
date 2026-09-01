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
  safetyStockQuantity?: number | null;
  stockQuantity?: number;
  packageLengthCm: number | null;
  packageWidthCm: number | null;
  packageHeightCm: number | null;
  packageVolumeCm3: number | null;
  innerPackageLengthCm?: number | null;
  innerPackageWidthCm?: number | null;
  innerPackageHeightCm?: number | null;
  netWeightKg: number | null;
  grossWeightKg: number | null;
  gramWeightG: number | null;
  innerPackageWeightKg?: number | null;
  packagingMethod: string;
  cartonQuantity: number | null;
  skuImageFileId: number | null;
  skuImageUrl?: string | null;
  packageImageFileId: number | null;
  packageImageUrl?: string | null;
  cartonImageFileId: number | null;
  cartonImageUrl?: string | null;
  supplierQuotes?: ProductSupplierQuoteInput[];
  defaultSku: boolean;
  status?: RecordStatus;
}

export interface ProductFormPayload {
  itemNo: string;
  productName: string;
  categoryId: number | null;
  brand: string;
  productType: ProductType;
  mainImageFileId: number | null;
  remark: string;
  specifications: ProductSpecification[];
  skus: SkuForm[];
}

export interface SupplierQuote extends ProductSupplierQuoteInput {
  id: number;
  skuId: number;
}

export interface ProductSku extends SkuForm {
  id: number;
  skuCode: string;
  specText: string;
  safetyStockQuantity: number | null;
  stockQuantity: number;
  innerPackageLengthCm: number | null;
  innerPackageWidthCm: number | null;
  innerPackageHeightCm: number | null;
  innerPackageWeightKg: number | null;
  skuImageUrl: string | null;
  packageImageUrl: string | null;
  cartonImageUrl: string | null;
  supplierQuotes: SupplierQuote[];
  status: RecordStatus;
}

export type ProductCompletenessStatus = 'complete' | 'incomplete';

export interface Product extends Omit<ProductFormPayload, 'skus'> {
  id: number;
  productCode: string;
  categoryId: number;
  status: RecordStatus;
  mainImageUrl: string | null;
  defaultSupplierName: string | null;
  totalStock: number;
  totalSafetyStock: number;
  defaultSalePrice: number | null;
  completenessPercent: number;
  completenessStatus: ProductCompletenessStatus;
  missingGroups: string[];
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
  specText: string;
  defaultSalePrice: number | null;
  standardCost: number | null;
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
  listSupplierQuotes(skuId: number): Promise<SupplierQuote[]>;
  saveSupplierQuote(skuId: number, payload: SaveSupplierQuotePayload, quoteId?: number): Promise<SupplierQuote>;
  setDefaultSupplierQuote(skuId: number, quoteId: number, syncStandardCost: boolean): Promise<SupplierQuote>;
  uploadImage(file: File): Promise<UploadedImage>;
}
