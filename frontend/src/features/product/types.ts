import type { PageResult, RecordStatus } from '../masterdata/types';

export type ProductType = 'simple' | 'variant';

export interface ProductSpecification {
  name: string;
  values: string[];
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
  packageLengthCm: number | null;
  packageWidthCm: number | null;
  packageHeightCm: number | null;
  packageVolumeCm3: number | null;
  netWeightKg: number | null;
  grossWeightKg: number | null;
  gramWeightG: number | null;
  packagingMethod: string;
  cartonQuantity: number | null;
  skuImageFileId: number | null;
  skuImageUrl?: string | null;
  packageImageFileId: number | null;
  packageImageUrl?: string | null;
  cartonImageFileId: number | null;
  cartonImageUrl?: string | null;
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

export interface Product extends ProductFormPayload {
  id: number;
  productCode: string;
  status: RecordStatus;
  mainImageUrl?: string | null;
  defaultSupplierName?: string | null;
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

export interface SupplierQuote {
  id: number;
  skuId: number;
  supplierId: number;
  supplierItemNo: string;
  purchasePrice: number;
  minPurchaseQuantity: number;
  defaultQuote: boolean;
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
  getProduct(id: number): Promise<Product>;
  createProduct(payload: ProductFormPayload): Promise<Product>;
  updateProduct(id: number, payload: ProductFormPayload): Promise<Product>;
  changeProductStatus(id: number, status: RecordStatus): Promise<Product>;
  listSupplierQuotes(skuId: number): Promise<SupplierQuote[]>;
  saveSupplierQuote(skuId: number, payload: SaveSupplierQuotePayload, quoteId?: number): Promise<SupplierQuote>;
  setDefaultSupplierQuote(skuId: number, quoteId: number, syncStandardCost: boolean): Promise<SupplierQuote>;
  uploadImage(file: File): Promise<UploadedImage>;
}
