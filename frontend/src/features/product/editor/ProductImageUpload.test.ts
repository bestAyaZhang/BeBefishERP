import { flushPromises, mount } from '@vue/test-utils';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import type { ProductService, UploadedImage } from '../types';
import ProductImageUpload from './ProductImageUpload.vue';

function deferred<T>() {
  let resolve!: (value: T) => void;
  let reject!: (reason?: unknown) => void;
  const promise = new Promise<T>((resolvePromise, rejectPromise) => {
    resolve = resolvePromise;
    reject = rejectPromise;
  });
  return { promise, resolve, reject };
}

function service(uploadImage: ProductService['uploadImage']): ProductService {
  return {
    listProducts: vi.fn(),
    getCategoryCounts: vi.fn(),
    getProduct: vi.fn(),
    createProduct: vi.fn(),
    updateProduct: vi.fn(),
    changeProductStatus: vi.fn(),
    listSupplierQuotes: vi.fn(),
    saveSupplierQuote: vi.fn(),
    setDefaultSupplierQuote: vi.fn(),
    uploadImage
  };
}

function mountUpload(uploadImage: ProductService['uploadImage']) {
  return mount(ProductImageUpload, {
    props: {
      service: service(uploadImage),
      fileId: null,
      preview: '',
      label: '商品主图',
      testId: 'image'
    }
  });
}

async function selectFile(wrapper: ReturnType<typeof mountUpload>, name: string) {
  const input = wrapper.get('[data-testid="image"]');
  Object.defineProperty(input.element, 'files', {
    configurable: true,
    value: [new File(['image'], name, { type: 'image/png' })]
  });
  await input.trigger('change');
}

describe('ProductImageUpload request ownership', () => {
  beforeEach(() => {
    vi.stubGlobal('URL', {
      createObjectURL: vi.fn((file: File) => `blob:${file.name}`),
      revokeObjectURL: vi.fn()
    });
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('keeps the newest selection when an older request finishes last', async () => {
    const first = deferred<UploadedImage>();
    const second = deferred<UploadedImage>();
    const uploadImage = vi.fn()
      .mockReturnValueOnce(first.promise)
      .mockReturnValueOnce(second.promise);
    const wrapper = mountUpload(uploadImage);

    await selectFile(wrapper, 'old.png');
    await selectFile(wrapper, 'new.png');
    second.resolve({ id: 22, url: '/uploads/new.png', originalFileName: 'new.png' });
    await flushPromises();
    first.resolve({ id: 11, url: '/uploads/old.png', originalFileName: 'old.png' });
    await flushPromises();

    expect(wrapper.get('[data-testid="image-preview"]').attributes('src')).toBe('/uploads/new.png');
    expect(wrapper.emitted('update:fileId')).toEqual([[22]]);
    expect(wrapper.emitted('update:preview')).toEqual([['/uploads/new.png']]);
    expect(URL.revokeObjectURL).toHaveBeenCalledWith('blob:old.png');
    expect(URL.revokeObjectURL).toHaveBeenCalledWith('blob:new.png');
  });

  it('ignores a stale failure after a newer image succeeds', async () => {
    const first = deferred<UploadedImage>();
    const second = deferred<UploadedImage>();
    const wrapper = mountUpload(vi.fn()
      .mockReturnValueOnce(first.promise)
      .mockReturnValueOnce(second.promise));

    await selectFile(wrapper, 'old.png');
    await selectFile(wrapper, 'new.png');
    second.resolve({ id: 22, url: '/uploads/new.png', originalFileName: 'new.png' });
    await flushPromises();
    first.reject(new Error('旧请求失败'));
    await flushPromises();

    expect(wrapper.get('[data-testid="image-status"]').text()).toContain('上传成功');
    expect(wrapper.text()).not.toContain('旧请求失败');
    expect(wrapper.emitted('update:fileId')).toEqual([[22]]);
  });

  it('invalidates a pending request when the image is removed', async () => {
    const pending = deferred<UploadedImage>();
    const wrapper = mountUpload(vi.fn(() => pending.promise));

    await selectFile(wrapper, 'pending.png');
    await wrapper.get('[data-testid="image-remove"]').trigger('click');
    pending.resolve({ id: 33, url: '/uploads/pending.png', originalFileName: 'pending.png' });
    await flushPromises();

    expect(wrapper.find('[data-testid="image-preview"]').exists()).toBe(false);
    expect(wrapper.get('[data-testid="image-status"]').text()).toContain('未上传');
    expect(wrapper.emitted('update:fileId')).toEqual([[null]]);
    expect(wrapper.emitted('update:preview')).toEqual([['']]);
    expect(URL.revokeObjectURL).toHaveBeenCalledTimes(1);
  });

  it('invalidates pending work on unmount without emitting late results', async () => {
    const pending = deferred<UploadedImage>();
    const wrapper = mountUpload(vi.fn(() => pending.promise));

    await selectFile(wrapper, 'unmounted.png');
    expect(wrapper.emitted('update:uploading')).toEqual([[true]]);
    wrapper.unmount();
    pending.resolve({ id: 44, url: '/uploads/unmounted.png', originalFileName: 'unmounted.png' });
    await flushPromises();

    expect(wrapper.emitted('update:fileId')).toBeUndefined();
    expect(wrapper.emitted('update:preview')).toBeUndefined();
    expect(wrapper.emitted('update:uploading')).toEqual([[false]]);
    expect(URL.revokeObjectURL).toHaveBeenCalledTimes(1);
    expect(URL.revokeObjectURL).toHaveBeenCalledWith('blob:unmounted.png');
  });

  it('shows measured upload progress before confirming success', async () => {
    const pending = deferred<UploadedImage>();
    const uploadImage = vi.fn((
      _file: File,
      onProgress?: (progress: number) => void
    ) => {
      onProgress?.(37);
      return pending.promise;
    }) as unknown as ProductService['uploadImage'];
    const wrapper = mountUpload(uploadImage);

    await selectFile(wrapper, 'progress.png');

    expect(wrapper.get('[data-testid="image-status"]').text()).toContain('上传中 37%');
    expect(wrapper.get('[data-testid="image-progress"]').attributes('aria-valuenow')).toBe('37');

    pending.resolve({ id: 45, url: '/uploads/progress.png', originalFileName: 'progress.png' });
    await flushPromises();

    expect(wrapper.get('[data-testid="image-status"]').text()).toContain('上传成功');
    expect(wrapper.find('[data-testid="image-progress"]').exists()).toBe(false);
  });
});
