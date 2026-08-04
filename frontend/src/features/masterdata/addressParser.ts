import { regionTree } from './regionService';

export interface ParsedAddress {
  province: string;
  city: string;
  district: string;
  detailAddress: string;
}

export interface ParsedCustomerText extends ParsedAddress {
  customerName: string;
  contactPerson: string;
  mobile: string;
}

function findRegionStart(text: string) {
  return regionTree
    .flatMap((province) => [province, ...province.cities, ...province.cities.flatMap((city) => city.districts)])
    .map((region) => text.indexOf(region.name))
    .filter((index) => index >= 0)
    .sort((left, right) => left - right)[0] ?? -1;
}

export function parseAddress(value: string): ParsedAddress {
  const text = value.trim().replace(/[\s,，、]+/g, '');
  if (!text) return { province: '', city: '', district: '', detailAddress: '' };

  const regionStart = findRegionStart(text);
  const addressText = regionStart >= 0 ? text.slice(regionStart) : text;
  let province = regionTree.find((candidate) => addressText.includes(candidate.name));
  const cities = province?.cities ?? regionTree.flatMap((candidate) => candidate.cities);
  const city = cities.find((candidate) => addressText.includes(candidate.name));
  if (!province && city) province = regionTree.find((candidate) => candidate.cities.some((candidateCity) => candidateCity.code === city.code));
  const districts = city?.districts ?? regionTree.flatMap((candidate) => candidate.cities).flatMap((candidate) => candidate.districts);
  const district = districts.find((candidate) => addressText.includes(candidate.name));
  const matchedNames = [province?.name, city?.name, district?.name].filter((name): name is string => Boolean(name));
  const detailAddress = matchedNames.reduce((remaining, name) => remaining.replace(name, ''), addressText).replace(/^[,，、\s]+/, '');

  return {
    province: province?.name ?? '',
    city: city?.name ?? '',
    district: district?.name ?? '',
    detailAddress
  };
}

export function parseCustomerText(value: string): ParsedCustomerText {
  const rawText = value.trim().replace(/\s+/g, ' ');
  const address = parseAddress(rawText);
  const mobile = rawText.match(/1[3-9]\d{9}/)?.[0] ?? '';
  const regionStart = findRegionStart(rawText);
  const identityText = (regionStart >= 0 ? rawText.slice(0, regionStart) : rawText)
    .replace(mobile, '')
    .replace(/客户名称|客户名|联系人|手机号码|手机号|手机|地址/g, '')
    .replace(/[：:]/g, ' ')
    .trim();
  const parts = identityText.split(/[，,；;|/\s]+/).filter(Boolean);
  const labeledName = rawText.match(/(?:客户名称|客户名)\s*[：:]\s*([^，,；;\s]+)/)?.[1] ?? '';
  const labeledContact = rawText.match(/联系人\s*[：:]\s*([^，,；;\s]+)/)?.[1] ?? '';

  return {
    ...address,
    customerName: labeledName || parts[0] || '',
    contactPerson: labeledContact || parts[1] || '',
    mobile
  };
}
