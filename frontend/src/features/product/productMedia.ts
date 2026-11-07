import { PRODUCT_ALBUM_KINDS } from './types';
import type { Product, ProductImagePack, ProductImagePackInput, ProductMediaImageRef } from './types';

export function copyImagePack(pack: ProductImagePack): ProductImagePack {
  const copy: ProductImagePack = { name: pack.name, main: [], sku: [], detail: [], long: [] };
  for (const kind of PRODUCT_ALBUM_KINDS) copy[kind] = pack[kind].map((image) => ({ ...image }));
  return copy;
}

export function initialImagePacks(product?: Product): ProductImagePack[] {
  if (product?.imagePacks != null) return product.imagePacks.map(copyImagePack);
  const pack: ProductImagePack = { name: '商品资料包', main: [], sku: [], detail: [], long: [] };
  if (product?.mainImageFileId) {
    pack.main.push({ fileId: product.mainImageFileId, url: product.mainImageUrl, name: `${product.itemNo} 主图` });
  }
  for (const sku of product?.skus ?? []) {
    if (sku.skuImageFileId) pack.sku.push({ fileId: sku.skuImageFileId, url: sku.skuImageUrl, name: sku.skuCode });
  }
  return [pack];
}

export function mediaImageRef({ fileId }: ProductMediaImageRef): ProductMediaImageRef {
  return { fileId };
}

export function imagePackInput(pack: ProductImagePack): ProductImagePackInput {
  const input: ProductImagePackInput = { name: pack.name, main: [], sku: [], detail: [], long: [] };
  for (const kind of PRODUCT_ALBUM_KINDS) input[kind] = pack[kind].map(mediaImageRef);
  return input;
}
