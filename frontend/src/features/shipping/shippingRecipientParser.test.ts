import { describe, expect, it } from 'vitest';
import { parseRecipientText } from './shippingRecipientParser';

describe('parseRecipientText', () => {
  it('parses a plain recipient line', () => {
    expect(parseRecipientText('林女士 13800006028 浙江省杭州市余杭区 示例路18号2栋101室')).toEqual({
      recipientName: '林女士', recipientPhone: '13800006028', recipientProvince: '浙江省',
      recipientCity: '杭州市', recipientCounty: '余杭区', recipientDetailAddress: '示例路18号2栋101室'
    });
  });

  it('uses labels across lines and leaves missing values empty', () => {
    expect(parseRecipientText('收件人：王先生\n电话：13900001086\n地址：江苏省苏州市吴中区')).toMatchObject({
      recipientName: '王先生', recipientPhone: '13900001086', recipientProvince: '江苏省',
      recipientCity: '苏州市', recipientCounty: '吴中区', recipientDetailAddress: ''
    });
  });
});
