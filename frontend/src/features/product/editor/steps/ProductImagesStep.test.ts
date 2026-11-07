import { enableAutoUnmount, flushPromises, mount, type VueWrapper } from '@vue/test-utils';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { reactive } from 'vue';
import { createMockProductService } from '../../mockProductService';
import type { ProductService, UploadedImage } from '../../types';
import { createEditorState, type ProductEditorState } from '../productEditorState';
import ProductImagesStep from './ProductImagesStep.vue';

enableAutoUnmount(afterEach);
afterEach(() => {
  vi.restoreAllMocks();
  vi.unstubAllGlobals();
  vi.useRealTimers();
});

function mountStep(uploadImage?: ProductService['uploadImage']) {
  const state = reactive<ProductEditorState>({
    ...createEditorState(),
    imagePacks: [{ name: '商品资料包', main: [], sku: [], detail: [], long: [] }],
    materialImages: []
  });
  const service = createMockProductService();
  if (uploadImage) service.uploadImage = uploadImage;
  return {
    state,
    wrapper: mount(ProductImagesStep, {
      props: { modelValue: state, service },
      global: { stubs: { teleport: true } }
    })
  };
}

function pickImages(wrapper: VueWrapper, albumId: string, names: string[]) {
  const input = wrapper.get(`[data-testid="${albumId}-input"]`);
  Object.defineProperty(input.element, 'files', {
    configurable: true,
    value: names.map((name) => new File(['image'], name, { type: 'image/png' }))
  });
  return input.trigger('change');
}

describe('ProductImagesStep', () => {
  it('adds an ordered pack with four visible albums and confirms deletion by image count', async () => {
    const { state, wrapper } = mountStep();
    await wrapper.get('[data-testid="add-image-pack"]').trigger('click');
    await wrapper.get('[data-testid="image-pack-name"]').setValue('  1688 上架图  ');
    await wrapper.get('[data-testid="image-pack-form"]').trigger('submit');

    expect(state.imagePacks.map((pack) => pack.name)).toEqual(['商品资料包', '1688 上架图']);
    const pack = wrapper.get('[data-testid="image-pack-1"]');
    expect(pack.findAll('input[type=file]').map((input) => input.attributes('aria-label'))).toEqual([
      '为1688 上架图的主图添加图片',
      '为1688 上架图的SKU 图添加图片',
      '为1688 上架图的详情图添加图片',
      '为1688 上架图的详情长图添加图片'
    ]);

    await pack.get('[aria-label="重命名1688 上架图"]').trigger('click');
    await wrapper.get('[data-testid="image-pack-name"]').setValue('天猫上架图');
    await wrapper.get('[data-testid="image-pack-form"]').trigger('submit');
    await pack.get('[aria-label="删除天猫上架图"]').trigger('click');
    expect(wrapper.get('[role="dialog"]').text()).toContain('包含 0 张图片');
    expect(state.imagePacks).toHaveLength(2);
    await wrapper.get('[data-testid="confirm-delete-image-pack"]').trigger('click');
    expect(state.imagePacks.map((entry) => entry.name)).toEqual(['商品资料包']);
  });

  it('keeps an ordered batch in its album and seeds the empty canonical main', async () => {
    let id = 100;
    const { state, wrapper } = mountStep(async (file) => ({
      id: ++id, url: `/uploads/${file.name}`, originalFileName: file.name
    }));
    await pickImages(wrapper, 'image-album-0-main', ['front.png', 'angle.png']);
    await flushPromises();

    expect(state.imagePacks[0].main.map((image) => image.fileId)).toEqual([101, 102]);
    expect(state.imagePacks[0].sku).toEqual([]);
    expect(state.mainImageFileId).toBe(101);
    expect(state.imagePreviews.main).toBe('/uploads/front.png');

    await wrapper.get('[aria-label="查看 front.png"]').trigger('click');
    expect(wrapper.get('[role="dialog"] img').attributes('src')).toBe('/uploads/front.png');
    await wrapper.get('[aria-label="下一张"]').trigger('click');
    expect(wrapper.get('[role="dialog"] img').attributes('src')).toBe('/uploads/angle.png');
    await wrapper.get('[role="dialog"]').trigger('keydown', { key: 'Escape' });
    await wrapper.get('[aria-label="移除 front.png"]').trigger('click');
    expect(state.imagePacks[0].main.map((image) => image.fileId)).toEqual([102]);
    expect(state.mainImageFileId).toBe(101);
  });

  it('keeps uploads active across albums and reports a failed image without losing other uploads', async () => {
    let finishMain!: (value: UploadedImage) => void;
    let failDetail!: (reason: Error) => void;
    const { state, wrapper } = mountStep((file, progress) => {
      progress?.(35);
      return new Promise<UploadedImage>((resolve, reject) => {
        if (file.name === 'front.png') finishMain = resolve;
        else failDetail = reject;
      });
    });
    await pickImages(wrapper, 'image-album-0-main', ['front.png']);
    await pickImages(wrapper, 'image-album-0-detail', ['detail.png']);
    expect(wrapper.get('[data-testid="image-album-0-detail"] [role="progressbar"]').attributes('aria-valuenow')).toBe('35');

    failDetail(new Error('上传连接已中断'));
    await flushPromises();
    expect(wrapper.get('[data-testid="image-album-0-detail"] [role="alert"]').text()).toContain('detail.png');
    expect(wrapper.emitted('update:uploading')).toEqual([[true]]);
    finishMain({ id: 71, url: '/uploads/front.png', originalFileName: 'front.png' });
    await flushPromises();

    expect(wrapper.emitted('update:uploading')).toEqual([[true], [false]]);
    expect(state.imagePacks[0].detail).toEqual([]);
    expect(state.imagePacks[0].main.map((image) => image.fileId)).toEqual([71]);
  });

  it('stores dropped raw material independently of the image packs', async () => {
    const { state, wrapper } = mountStep(async (file) => ({
      id: 80, url: '/uploads/raw.png', originalFileName: file.name
    }));
    const raw = wrapper.get('[data-testid="image-album-material"]');
    const file = new File(['image'], '原始素材.png', { type: 'image/png' });
    await raw.trigger('drop', { dataTransfer: { files: [file] } });
    await flushPromises();

    expect(state.materialImages).toEqual([{ fileId: 80, url: '/uploads/raw.png', name: '原始素材.png' }]);
    expect(state.imagePacks[0].main).toEqual([]);
    expect(raw.get('[aria-label="查看 原始素材.png"] img').attributes('src')).toBe('/uploads/raw.png');
  });

  it('leaves an explicitly uploading canonical main alone', async () => {
    const { state, wrapper } = mountStep(async () => ({
      id: 81, url: '/uploads/album.png', originalFileName: 'album.png'
    }));
    await wrapper.setProps({ canonicalMainUploading: true });
    await pickImages(wrapper, 'image-album-0-main', ['album.png']);
    await flushPromises();

    expect(state.imagePacks[0].main.map((image) => image.fileId)).toEqual([81]);
    expect(state.mainImageFileId).toBeNull();
  });

  it('downloads a ZIP from the selected pack before the product is saved', async () => {
    const { state, wrapper } = mountStep(async (file) => ({
      id: 90, url: '/api/files/content/uploaded.png', originalFileName: file.name
    }));
    await pickImages(wrapper, 'image-album-0-main', ['uploaded.png']);
    await flushPromises();
    state.materialImages.push({ fileId: 91, url: '/uploads/raw.png', name: 'raw.png' });

    const bytes = Uint8Array.from(atob('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+aAxkAAAAASUVORK5CYII='), (char) => char.charCodeAt(0));
    const fetchImage = vi.fn(async () => new Response(bytes));
    vi.stubGlobal('fetch', fetchImage);
    vi.stubGlobal('URL', {
      createObjectURL: vi.fn(() => 'blob:zip'),
      revokeObjectURL: vi.fn()
    });
    let download = '';
    vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(function (this: HTMLAnchorElement) {
      download = this.download;
    });
    vi.useFakeTimers();

    await wrapper.get('[aria-label="打包下载商品资料包"]').trigger('click');
    await flushPromises();
    expect(download).toBe('商品资料包.zip');
    expect(fetchImage.mock.calls).toEqual([['/api/files/content/uploaded.png', { signal: expect.any(AbortSignal) }]]);
    expect(URL.createObjectURL).toHaveBeenCalledWith(expect.objectContaining({ type: 'application/zip' }));
    vi.advanceTimersByTime(30_000);
  });
});
