import { onBeforeUnmount, shallowReactive, toRaw } from 'vue';
import { message } from '../../components/feedback/message';
import { buildProductImagePackArchive } from './editor/steps/productImagePackArchive';
import { PRODUCT_ALBUM_KINDS, type ProductImagePack } from './types';

export function packImageCount(pack: ProductImagePack) {
  return PRODUCT_ALBUM_KINDS.reduce((total, kind) => total + pack[kind].length, 0);
}

export function useProductImagePackDownload() {
  const requests = shallowReactive(new Map<ProductImagePack, AbortController>());
  const errors = shallowReactive(new Map<ProductImagePack, string>());

  async function downloadPack(pack: ProductImagePack) {
    const key = toRaw(pack);
    if (requests.has(key) || !packImageCount(pack)) return;
    const controller = new AbortController();
    errors.delete(key);
    requests.set(key, controller);

    try {
      const { archive, filename } = await buildProductImagePackArchive(pack, controller.signal);
      if (controller.signal.aborted) return;
      const url = URL.createObjectURL(archive);
      const link = document.createElement('a');
      link.href = url;
      link.download = filename;
      document.body.append(link);
      try {
        link.click();
        message.success(`已开始下载 ${filename}`);
      } finally {
        link.remove();
        window.setTimeout(() => URL.revokeObjectURL(url), 30_000);
      }
    } catch (error) {
      if (!controller.signal.aborted) {
        errors.set(key, error instanceof Error ? error.message : '资料包打包失败，请重试');
      }
    } finally {
      if (requests.get(key) === controller) requests.delete(key);
    }
  }

  function cancelDownload(pack: ProductImagePack) {
    const key = toRaw(pack);
    requests.get(key)?.abort();
    requests.delete(key);
    errors.delete(key);
  }

  onBeforeUnmount(() => {
    for (const controller of requests.values()) controller.abort();
  });

  return {
    downloadPack,
    cancelDownload,
    isDownloading: (pack: ProductImagePack) => requests.has(toRaw(pack)),
    downloadError: (pack: ProductImagePack) => errors.get(toRaw(pack))
  };
}
