import { mount } from '@vue/test-utils';
import { describe, expect, it } from 'vitest';
import { nextTick } from 'vue';
import ProductDetailImage from './components/ProductDetailImage.vue';

describe('ProductDetailImage', () => {
  it('opens the full image preview from an available detail image', async () => {
    const wrapper = mount(ProductDetailImage, {
      attachTo: document.body,
      props: {
        src: '/uploads/carton.png',
        alt: '透明款外箱图片',
        testId: 'carton-image',
        size: 'preview'
      }
    });

    await wrapper.get('[data-testid="carton-image-preview-trigger"]').trigger('click');

    expect(wrapper.get('[data-testid="carton-image-preview-dialog"]').attributes('role')).toBe('dialog');
    expect(wrapper.get('[data-testid="carton-image-preview"]').attributes()).toMatchObject({
      src: '/uploads/carton.png',
      alt: '透明款外箱图片大图预览'
    });
    wrapper.unmount();
  });

  it('closes the full image preview with Escape and restores the trigger', async () => {
    const wrapper = mount(ProductDetailImage, {
      attachTo: document.body,
      props: {
        src: '/uploads/package.png',
        alt: '透明款内盒包装图',
        testId: 'package-image'
      }
    });
    const trigger = wrapper.get('[data-testid="package-image-preview-trigger"]');
    (trigger.element as HTMLButtonElement).focus();
    await trigger.trigger('click');

    await wrapper.get('[data-testid="package-image-preview-dialog"]').trigger('keydown', { key: 'Escape' });
    await nextTick();

    expect(wrapper.find('[data-testid="package-image-preview-dialog"]').exists()).toBe(false);
    expect(document.activeElement).toBe(trigger.element);
    wrapper.unmount();
  });

  it('does not offer preview interaction when the image is unavailable', () => {
    const wrapper = mount(ProductDetailImage, {
      props: {
        src: null,
        alt: '烟灰款外箱图片',
        testId: 'missing-carton-image'
      }
    });

    expect(wrapper.find('[data-testid="missing-carton-image-preview-trigger"]').exists()).toBe(false);
    expect(wrapper.find('[data-testid="missing-carton-image-fallback"]').exists()).toBe(true);
  });

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
