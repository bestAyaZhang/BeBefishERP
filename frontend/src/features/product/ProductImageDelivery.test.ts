import { mount } from '@vue/test-utils';
import { describe, expect, it } from 'vitest';
import ProductTable from './components/ProductTable.vue';
import ProductImageAlbum from './editor/steps/ProductImageAlbum.vue';
import { productFixture } from './productTestFixtures';

describe('product preview delivery wiring', () => {
  it('renders catalog thumbnails with bounded URLs and lazy loading', () => {
    const product = productFixture({ id: 1, productCode: 'P1', itemNo: 'TG1', productName: '玻璃杯',
      categoryId: 1, brand: null, productType: 'simple', mainImageFileId: 10,
      mainImageUrl: '/api/files/content/glass.png', status: 'enabled', remark: null, specifications: [], skus: [] });
    const wrapper = mount(ProductTable, { props: { products: [product] } });
    expect(wrapper.get('[data-testid="product-image-1"]').attributes()).toMatchObject({
      src: '/api/files/content/glass.png?variant=thumb', loading: 'lazy', decoding: 'async'
    });
    wrapper.unmount();
  });

  it('uses an album thumbnail without changing the original media reference', async () => {
    const image = { fileId: 10, url: '/api/files/content/material.png', name: '产品资料' };
    const wrapper = mount(ProductImageAlbum, { props: { label: '主图', title: '主图', testId: 'album', images: [image] } });
    expect(wrapper.get('img').attributes()).toMatchObject({ src: '/api/files/content/material.png?variant=thumb', loading: 'lazy' });
    await wrapper.get('button.album-preview').trigger('click');
    expect(wrapper.emitted('preview')).toEqual([[0]]);
    expect(image.url).toBe('/api/files/content/material.png');
    wrapper.unmount();
  });
});
