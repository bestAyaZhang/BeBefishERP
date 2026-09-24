import { defineComponent, ref } from 'vue';
import { mount } from '@vue/test-utils';
import { describe, expect, it } from 'vitest';
import RecipientRecognitionCard from './RecipientRecognitionCard.vue';
import type { RecipientFields } from './types';

const blankRecipient = (): RecipientFields => ({
  recipientName: '', recipientPhone: '', recipientProvince: '', recipientCity: '',
  recipientCounty: '', recipientDetailAddress: '人工保留的门牌号'
});

function render() {
  return mount(defineComponent({
    components: { RecipientRecognitionCard },
    setup() { return { recipient: ref(blankRecipient()) }; },
    template: '<RecipientRecognitionCard v-model="recipient" />'
  }));
}

describe('RecipientRecognitionCard', () => {
  it('recognizes inline and fills the fields in the same card', async () => {
    const wrapper = render();
    await wrapper.get('[data-testid="recipient-raw"]').setValue('林女士 13800006028 浙江省杭州市余杭区 示例路18号2栋101室');
    await wrapper.get('[data-testid="recipient-recognize"]').trigger('click');

    expect((wrapper.get('[data-testid="recipient-county"]').element as HTMLInputElement).value).toBe('余杭区');
    expect((wrapper.get('[data-testid="recipient-detail-address"]').element as HTMLInputElement).value).toBe('示例路18号2栋101室');
    expect(wrapper.text()).toContain('已识别，请核对');
  });

  it('does not blank manually entered values when recognition is partial', async () => {
    const wrapper = render();
    await wrapper.get('[data-testid="recipient-raw"]').setValue('收件人：王先生 电话：13900001086');
    await wrapper.get('[data-testid="recipient-recognize"]').trigger('click');

    expect((wrapper.get('[data-testid="recipient-detail-address"]').element as HTMLInputElement).value).toBe('人工保留的门牌号');
    expect(wrapper.text()).toContain('已识别部分信息');
  });
});
