import { mount } from '@vue/test-utils';
import { describe, expect, it } from 'vitest';
import { nextTick } from 'vue';
import ProductDetailImage from './components/ProductDetailImage.vue';

describe('ProductDetailImage', () => {
  it('recovers from an image error when a refreshed temporary URL arrives', async () => {
    const wrapper = mount(ProductDetailImage, {
      props: {
        src: '/temporary/expired.png',
        alt: '透明款内盒包装图',
        testId: 'refreshing-package-image',
        size: 'small'
      }
    });

    await wrapper.get('[data-testid="refreshing-package-image"]').trigger('error');
    expect(wrapper.find('[data-testid="refreshing-package-image-fallback"]').exists()).toBe(true);

    await wrapper.setProps({ src: '/temporary/refreshed.png' });
    await nextTick();

    expect(wrapper.get('[data-testid="refreshing-package-image"]').attributes('src')).toBe('/temporary/refreshed.png');
    expect(wrapper.find('[data-testid="refreshing-package-image-fallback"]').exists()).toBe(false);
  });
});
