import type { PageResult, RecordStatus } from '../masterdata/types';
import type {
  Product,
  ProductCatalogSupplierQuote,
  ProductFormPayload,
  ProductQuery,
  ProductService,
  ProductSku,
  ProductSupplierQuote,
  ProductSupplierQuoteInput,
  SaveSupplierQuotePayload,
  UploadedImage
} from './types';

export interface MockProductService extends ProductService {
  deleteSupplierQuote(skuId: number, quoteId: number): Promise<void>;
}

const defaultSeed: Product[] = [{
  id: 1,
  productCode: 'GLASS-001',
  itemNo: 'GB-001',
  productName: '高硼硅玻璃杯',
  categoryId: 1,
  categoryName: '杯具',
  brand: 'BeBefish',
  productType: 'variant',
  mainImageFileId: null,
  mainImageUrl: null,
  defaultSupplierName: null,
  totalStock: 0,
  totalSafetyStock: 0,
  defaultSalePrice: null,
  completenessPercent: 0,
  completenessStatus: 'incomplete',
  missingGroups: ['基本信息', 'SKU 信息', '采购信息', '包装重量', '图片资料'],
  status: 'enabled',
  remark: null,
  specifications: [{ name: '颜色', values: ['透明', '烟灰'] }],
  skus: [],
  createdAt: '2026-01-01T00:00:00.000Z',
  updatedAt: '2026-01-01T00:00:00.000Z'
}];

function cloneQuote<T extends ProductSupplierQuote>(quote: T): T {
  return { ...quote };
}

function cloneSku(sku: ProductSku, supplierQuotes: ProductCatalogSupplierQuote[] = sku.supplierQuotes): ProductSku {
  return {
    ...sku,
    specificationValues: [...sku.specificationValues],
    supplierQuotes: supplierQuotes.map(cloneQuote)
  };
}

function cloneProduct(product: Product): Product {
  return {
    ...product,
    missingGroups: [...product.missingGroups],
    specifications: product.specifications.map((specification) => ({
      name: specification.name,
      values: [...specification.values]
    })),
    skus: product.skus.map((sku) => cloneSku(sku))
  };
}

function maxId(values: number[], floor: number) {
  return Math.max(floor, ...values) + 1;
}

export function createMockProductService(seed: Product[] = defaultSeed): MockProductService {
  let products = seed.map(cloneProduct);
  const supplierNames = new Map(products.flatMap((product) => product.skus.flatMap((sku) => (
    sku.supplierQuotes.map((quote) => [quote.supplierId, quote.supplierName] as const)
  ))));
  let quotes: ProductSupplierQuote[] = products.flatMap((product) => product.skus.flatMap((sku) => (
    sku.supplierQuotes.map(({ supplierName: _supplierName, ...quote }) => quote)
  )));
  let nextProductId = maxId(products.map((product) => product.id), 99);
  let nextSkuId = maxId(products.flatMap((product) => product.skus.map((sku) => sku.id)), 999);
  let nextQuoteId = maxId(quotes.map((quote) => quote.id), 9999);

  products = products.map((product) => ({
    ...product,
    skus: product.skus.map((sku) => ({ ...sku, supplierQuotes: [] }))
  }));

  function materializeProduct(product: Product): Product {
    return cloneProduct({
      ...product,
      skus: product.skus.map((sku) => cloneSku(
        sku,
        quotes
          .filter((quote) => quote.skuId === sku.id)
          .map((quote): ProductCatalogSupplierQuote => ({
            ...quote,
            supplierName: supplierNames.get(quote.supplierId) ?? `供应商 ${quote.supplierId}`
          }))
      ))
    });
  }

  function findSku(skuId: number) {
    return products.flatMap((product) => product.skus).find((sku) => sku.id === skuId);
  }

  function imageUrl(fileId: number | null, previousFileId: number | null | undefined, previousUrl: string | null | undefined) {
    return fileId !== null && fileId === previousFileId ? previousUrl ?? null : null;
  }

  function asProductSku(
    productId: number,
    sku: ProductFormPayload['skus'][number],
    index: number,
    current?: ProductSku
  ): ProductSku {
    const id = sku.id ?? nextSkuId++;
    const specificationValues = [...sku.specificationValues];
    return {
      id,
      skuCode: sku.skuCode || `PRD-${String(productId).padStart(6, '0')}-${String(index + 1).padStart(3, '0')}`,
      barcode: sku.barcode || null,
      skuName: sku.skuName,
      specText: specificationValues.length > 0 ? specificationValues.join(' / ') : null,
      specificationValues,
      salesUnit: sku.salesUnit,
      defaultSalePrice: sku.defaultSalePrice ?? 0,
      standardCost: sku.standardCost ?? 0,
      safetyStockQuantity: sku.safetyStockQuantity ?? 0,
      stockQuantity: current?.stockQuantity ?? 0,
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
      packagingMethod: sku.packagingMethod || null,
      cartonQuantity: sku.cartonQuantity,
      skuImageFileId: sku.skuImageFileId,
      skuImageUrl: imageUrl(sku.skuImageFileId, current?.skuImageFileId, current?.skuImageUrl),
      packageImageFileId: sku.packageImageFileId,
      packageImageUrl: imageUrl(sku.packageImageFileId, current?.packageImageFileId, current?.packageImageUrl),
      cartonImageFileId: sku.cartonImageFileId,
      cartonImageUrl: imageUrl(sku.cartonImageFileId, current?.cartonImageFileId, current?.cartonImageUrl),
      supplierQuotes: [],
      defaultSku: index === 0,
      status: current?.status ?? 'enabled'
    };
  }

  function asProduct(
    id: number,
    payload: ProductFormPayload,
    current?: Product
  ): Product {
    const skus = payload.skus.map((sku, index) => asProductSku(
      id,
      sku,
      index,
      current?.skus.find((candidate) => candidate.id === sku.id)
    ));
    const now = new Date().toISOString();
    return {
      id,
      productCode: current?.productCode ?? `PRD-${String(id).padStart(6, '0')}`,
      itemNo: payload.itemNo,
      productName: payload.productName,
      categoryId: payload.categoryId ?? 0,
      categoryName: current?.categoryName ?? null,
      brand: payload.brand || null,
      productType: payload.productType,
      mainImageFileId: payload.mainImageFileId,
      mainImageUrl: imageUrl(payload.mainImageFileId, current?.mainImageFileId, current?.mainImageUrl),
      defaultSupplierName: current?.defaultSupplierName ?? null,
      totalStock: skus.reduce((total, sku) => total + sku.stockQuantity, 0),
      totalSafetyStock: skus.reduce((total, sku) => total + sku.safetyStockQuantity, 0),
      defaultSalePrice: skus[0]?.defaultSalePrice ?? null,
      completenessPercent: current?.completenessPercent ?? 0,
      completenessStatus: current?.completenessStatus ?? 'incomplete',
      missingGroups: [...(current?.missingGroups ?? ['基本信息', 'SKU 信息', '采购信息', '包装重量', '图片资料'])],
      status: current?.status ?? 'enabled',
      remark: payload.remark || null,
      specifications: payload.specifications.map((specification) => ({
        name: specification.name,
        values: [...specification.values]
      })),
      skus,
      createdAt: current?.createdAt ?? now,
      updatedAt: now
    };
  }

  function quoteFromInput(skuId: number, input: ProductSupplierQuoteInput): ProductSupplierQuote {
    return {
      id: input.id ?? nextQuoteId++,
      skuId,
      supplierId: input.supplierId,
      supplierItemNo: input.supplierItemNo || null,
      purchasePrice: input.purchasePrice,
      minPurchaseQuantity: input.minPurchaseQuantity,
      defaultQuote: input.defaultQuote,
      status: input.status
    };
  }

  function syncPayloadQuotes(payload: ProductFormPayload, product: Product, removedSkuIds: number[]) {
    if (removedSkuIds.length > 0) {
      const removed = new Set(removedSkuIds);
      quotes = quotes.filter((quote) => !removed.has(quote.skuId));
    }
    payload.skus.forEach((sku, index) => {
      if (sku.supplierQuotes === undefined) return;
      const skuId = product.skus[index].id;
      quotes = quotes.filter((quote) => quote.skuId !== skuId);
      quotes.push(...sku.supplierQuotes.map((quote) => quoteFromInput(skuId, quote)));
    });
  }

  function page(query: ProductQuery): PageResult<Product> {
    const keyword = query.keyword?.trim().toLowerCase() ?? '';
    const filtered = products.filter((product) =>
      (!query.status || product.status === query.status)
      && (!query.categoryId || product.categoryId === query.categoryId)
      && (!keyword || [product.productCode, product.itemNo, product.productName]
        .some((value) => value.toLowerCase().includes(keyword)))
    );
    return {
      records: filtered
        .slice((query.page - 1) * query.size, query.page * query.size)
        .map(materializeProduct),
      page: query.page,
      pageSize: query.size,
      total: filtered.length
    };
  }

  function updateSkuStandardCost(skuId: number, standardCost: number) {
    products = products.map((product) => ({
      ...product,
      skus: product.skus.map((sku) => sku.id === skuId ? { ...sku, standardCost } : sku)
    }));
  }

  return {
    listProducts: async (query) => page(query),
    getCategoryCounts: async () => products.reduce<Record<number, number>>((counts, product) => {
      counts[product.categoryId] = (counts[product.categoryId] ?? 0) + 1;
      return counts;
    }, {}),
    getProduct: async (id) => {
      const product = products.find((value) => value.id === id);
      if (!product) throw new Error('产品不存在');
      return materializeProduct(product);
    },
    createProduct: async (payload) => {
      const product = asProduct(nextProductId++, payload);
      syncPayloadQuotes(payload, product, []);
      products = [...products, product];
      return materializeProduct(product);
    },
    updateProduct: async (id, payload) => {
      const current = products.find((value) => value.id === id);
      if (!current) throw new Error('产品不存在');
      const product = asProduct(id, payload, current);
      const retainedSkuIds = new Set(product.skus.map((sku) => sku.id));
      syncPayloadQuotes(payload, product, current.skus.filter((sku) => !retainedSkuIds.has(sku.id)).map((sku) => sku.id));
      products = products.map((value) => value.id === id ? product : value);
      return materializeProduct(product);
    },
    changeProductStatus: async (id, status: RecordStatus) => {
      const current = products.find((value) => value.id === id);
      if (!current) throw new Error('产品不存在');
      const product = { ...current, status, updatedAt: new Date().toISOString() };
      products = products.map((value) => value.id === id ? product : value);
      return materializeProduct(product);
    },
    listSupplierQuotes: async (skuId) => quotes
      .filter((quote) => quote.skuId === skuId)
      .map(cloneQuote),
    saveSupplierQuote: async (skuId, payload: SaveSupplierQuotePayload, quoteId?) => {
      if (!findSku(skuId)) throw new Error('SKU 不存在');
      if (quoteId !== undefined && !quotes.some((quote) => quote.id === quoteId && quote.skuId === skuId)) {
        throw new Error('报价不存在');
      }
      if (payload.defaultQuote) {
        quotes = quotes.map((quote) => quote.skuId === skuId ? { ...quote, defaultQuote: false } : quote);
      }
      const quote: ProductSupplierQuote = {
        id: quoteId ?? nextQuoteId++,
        skuId,
        supplierId: payload.supplierId,
        supplierItemNo: payload.supplierItemNo || null,
        purchasePrice: payload.purchasePrice,
        minPurchaseQuantity: payload.minPurchaseQuantity,
        defaultQuote: payload.defaultQuote,
        status: 'enabled'
      };
      quotes = quoteId === undefined
        ? [...quotes, quote]
        : quotes.map((value) => value.id === quoteId ? quote : value);
      if (payload.syncStandardCost) updateSkuStandardCost(skuId, payload.purchasePrice);
      return cloneQuote(quote);
    },
    setDefaultSupplierQuote: async (skuId, quoteId, syncStandardCost) => {
      const selected = quotes.find((quote) => quote.id === quoteId && quote.skuId === skuId);
      if (!selected) throw new Error('报价不存在');
      quotes = quotes.map((quote) => quote.skuId === skuId
        ? { ...quote, defaultQuote: quote.id === quoteId }
        : quote);
      const quote = quotes.find((value) => value.id === quoteId && value.skuId === skuId)!;
      if (syncStandardCost) updateSkuStandardCost(skuId, quote.purchasePrice);
      return cloneQuote(quote);
    },
    deleteSupplierQuote: async (skuId, quoteId) => {
      quotes = quotes.filter((quote) => quote.skuId !== skuId || quote.id !== quoteId);
    },
    uploadImage: async (file): Promise<UploadedImage> => ({
      id: Date.now(),
      url: URL.createObjectURL(file),
      originalFileName: file.name
    })
  };
}

export const mockProductService = createMockProductService();
