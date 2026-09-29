import type { PageResult } from '../masterdata/types';
export type CatalogStatus = 'enabled' | 'disabled';
export type CatalogChannelType = 'ecommerce' | 'private';
export interface CatalogItem {
  id: number; code: string; name: string; status: CatalogStatus; sortOrder: number; remark: string; version: number;
  createdBy?: string; updatedBy?: string; createdAt?: string; updatedAt: string;
  shopCount?: number; platformId?: number; platformName?: string; platformStatus?: CatalogStatus;
  channelType?: CatalogChannelType; ownerName?: string; optionLabel?: string | null;
}
export interface CatalogQuery { page: number; size: number; keyword?: string; status?: string; platformId?: number }
export interface CatalogInput { code?: string; name: string; sortOrder: number; remark: string; platformId?: number;
  channelType?: CatalogChannelType; ownerName?: string }
export type CatalogUpdate = Pick<CatalogInput, 'name' | 'sortOrder' | 'remark' | 'channelType' | 'ownerName'> & { version: number };
export interface PlatformService {
  listPlatforms(query: CatalogQuery): Promise<PageResult<CatalogItem>>;
  listShops(query: CatalogQuery): Promise<PageResult<CatalogItem>>;
  getPlatform(id: number): Promise<CatalogItem>; getShop(id: number): Promise<CatalogItem>;
  createPlatform(input: CatalogInput): Promise<CatalogItem>; createShop(input: CatalogInput): Promise<CatalogItem>;
  updatePlatform(id: number, input: CatalogUpdate): Promise<CatalogItem>; updateShop(id: number, input: CatalogUpdate): Promise<CatalogItem>;
  changePlatformStatus(id: number, status: CatalogStatus, version: number): Promise<CatalogItem>;
  changeShopStatus(id: number, status: CatalogStatus, version: number): Promise<CatalogItem>;
}
