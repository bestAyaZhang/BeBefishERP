import { request } from '../../services/http';
import type { PageResult } from '../masterdata/types';
import type { CatalogItem, CatalogQuery, PlatformService } from './types';
function list(path: string, query: CatalogQuery) {
  const params = new URLSearchParams({ page: String(query.page), size: String(query.size) });
  if (query.keyword?.trim()) params.set('keyword', query.keyword.trim());
  if (query.status) params.set('status', query.status);
  if (query.platformId != null) params.set('platformId', String(query.platformId));
  return request<PageResult<CatalogItem>>(path + '?' + params);
}
const write = (path: string, method: string, body: unknown) => request<CatalogItem>(path, { method, body: JSON.stringify(body) });
export const httpPlatformService: PlatformService = {
  listPlatforms: q => list('/api/platforms', q), listShops: q => list('/api/platform-shops', q),
  getPlatform: id => request('/api/platforms/' + id), getShop: id => request('/api/platform-shops/' + id),
  createPlatform: input => write('/api/platforms', 'POST', input), createShop: input => write('/api/platform-shops', 'POST', input),
  updatePlatform: (id, input) => write('/api/platforms/' + id, 'PUT', input),
  updateShop: (id, input) => write('/api/platform-shops/' + id, 'PUT', input),
  changePlatformStatus: (id, status, version) => write('/api/platforms/' + id + '/status', 'POST', { status, version }),
  changeShopStatus: (id, status, version) => write('/api/platform-shops/' + id + '/status', 'POST', { status, version })
};
