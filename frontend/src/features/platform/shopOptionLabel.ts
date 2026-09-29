import type { CatalogChannelType } from './types';

export function shopOptionLabel(channelType: CatalogChannelType, platformName: string, shopName: string): string {
  const segment = channelType === 'private' && platformName.endsWith('代发') ? '代发' : platformName;
  return `${channelType === 'private' ? '私域' : '电商'}_${segment}_${shopName}`;
}
