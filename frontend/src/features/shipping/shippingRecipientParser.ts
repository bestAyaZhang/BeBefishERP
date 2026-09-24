import { parseAddress } from '../masterdata/addressParser';
import type { RecipientFields } from './types';

export type ParsedRecipient = RecipientFields;

function labeled(text: string, labels: string[]) {
  const pattern = new RegExp(`(?:${labels.join('|')})\\s*[：:]\\s*([^\\s，,；;]+)`);
  return text.match(pattern)?.[1]?.trim() ?? '';
}

function parseGenericChineseAddress(value: string) {
  const compact = value.replace(/[\s,，、]+/g, '');
  const matched = compact.match(/^(?<province>.+?(?:省|自治区|市))(?<city>(?:.+?市|.+?自治州|.+?地区|.+?州))(?<county>.+?(?:区|县|市))(?<detail>.*)$/);
  return matched?.groups ?? { province: '', city: '', county: '', detail: '' };
}

export function parseRecipientText(raw: string): ParsedRecipient {
  const text = raw.trim();
  const phoneMatch = text.match(/1[3-9]\d{9}/);
  const recipientPhone = labeled(text, ['电话', '手机', '手机号', '联系电话']) || phoneMatch?.[0] || '';
  const addressLine = text.match(/(?:收件地址|收货地址|地址)\s*[：:]\s*([^\n]+)/)?.[1]?.trim() ?? '';
  const addressSource = addressLine || text;
  const address = parseAddress(addressSource);
  const generic = parseGenericChineseAddress(addressSource.replace(/^.*?(?=(?:[^\s]+省|北京市|上海市|天津市|重庆市))/, ''));
  const province = address.province || generic.province;
  const city = address.city || generic.city;
  const county = address.district || generic.county;
  const hasRecognizedRegion = Boolean(province || city || county);
  const usedGenericRegion = Boolean((!address.city && generic.city) || (!address.district && generic.county));
  const labeledName = labeled(text, ['收件人', '收货人', '姓名']);
  const prefix = phoneMatch ? text.slice(0, phoneMatch.index) : text.slice(0, Math.max(0,
    [province, city, county].filter(Boolean).map(value => text.indexOf(value)).filter(index => index >= 0)[0] ?? 0));
  const fallbackName = prefix
    .replace(/收件人|收货人|姓名|电话|手机|联系电话/g, '')
    .replace(/[：:，,；;|/\s]+/g, ' ')
    .trim().split(' ').filter(Boolean)[0] ?? '';

  return {
    recipientName: labeledName || fallbackName,
    recipientPhone,
    recipientProvince: province,
    recipientCity: city,
    recipientCounty: county,
    recipientDetailAddress: hasRecognizedRegion ? (usedGenericRegion ? generic.detail : address.detailAddress) : (addressLine ? address.detailAddress : '')
  };
}
