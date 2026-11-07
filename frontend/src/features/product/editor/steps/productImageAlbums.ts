import { PRODUCT_ALBUM_KINDS, type ProductAlbumKind, type ProductMediaImage } from '../../types';

const albumLabels: Record<ProductAlbumKind, string> = {
  main: '主图',
  sku: 'SKU 图',
  detail: '详情图',
  long: '详情长图'
};

export const productImageAlbums = PRODUCT_ALBUM_KINDS.map((kind) => ({ kind, label: albumLabels[kind] }));

export function albumImageName(image: ProductMediaImage) {
  return image.name?.trim() || `图片 ${image.fileId}`;
}

export function albumImageUrl(url: string | null) {
  const apiBase = import.meta.env.VITE_API_BASE_URL?.replace(/\/+$/, '') ?? '';
  if (url?.startsWith('/uploads/') || url?.startsWith('/api/files/content/')) {
    return apiBase + url;
  }
  return url;
}
