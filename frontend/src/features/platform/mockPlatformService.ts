import type { CatalogItem, CatalogInput, CatalogQuery, CatalogUpdate, CatalogStatus, PlatformService } from './types';
import { shopOptionLabel } from './shopOptionLabel';
const KEY = 'bebefish_mock_platform_catalog_v1';
type State = { platforms: CatalogItem[]; shops: CatalogItem[] };
function read(): State { return JSON.parse(localStorage.getItem(KEY) ?? '{"platforms":[],"shops":[]}'); }
function write(state: State) { localStorage.setItem(KEY, JSON.stringify(state)); }
function fail(message: string): never { throw new Error(message); }
function displayedLabel(shop: CatalogItem, platforms: CatalogItem[]) {
  const platformName = platforms.find(p => p.id === shop.platformId)?.name ?? '';
  return shop.optionLabel || shopOptionLabel(shop.channelType ?? 'ecommerce', platformName, shop.name);
}
export function mockCatalogOptions(activeOnly = true) {
  const state = read();
  return { platforms: state.platforms.filter(p => !activeOnly || p.status === 'enabled').map(({ id, name, status }) => ({ id, name, status })),
    shops: state.shops.map(s => ({ ...s, platformStatus: state.platforms.find(p => p.id === s.platformId)?.status ?? 'disabled' as CatalogStatus }))
      .filter(s => !activeOnly || (s.status === 'enabled' && s.platformStatus === 'enabled'))
      .map(s => ({ id: s.id, platformId: s.platformId!, name: s.name, optionLabel: displayedLabel(s, state.platforms), status: s.status, platformStatus: s.platformStatus })) };
}
export function createMockPlatformService(): PlatformService {
  function get(shop: boolean, id: number) {
    const state = read(); const item = (shop ? state.shops : state.platforms).find(x => x.id === id) ?? fail('资料不存在');
    return { ...item, ...(shop ? { platformName: state.platforms.find(p => p.id === item.platformId)?.name, platformStatus: state.platforms.find(p => p.id === item.platformId)?.status,
      optionLabel: displayedLabel(item, state.platforms) } : { shopCount: state.shops.filter(s => s.platformId === id).length }) };
  }
  function list(shop: boolean, q: CatalogQuery) {
    const state = read(); const rows = (shop ? state.shops : state.platforms).filter(x => (!q.status || x.status === q.status)
      && (!q.platformId || x.platformId === q.platformId) && (!q.keyword || (x.code + x.name).toLowerCase().includes(q.keyword.toLowerCase())))
      .sort((a,b) => a.sortOrder - b.sortOrder || a.id - b.id);
    return { records: rows.slice((q.page - 1) * q.size, q.page * q.size).map(x => get(shop, x.id)), total: rows.length, page: q.page, pageSize: q.size };
  }
  function save(shop: boolean, input: CatalogInput | CatalogUpdate, id?: number) {
    const state = read(); const rows = shop ? state.shops : state.platforms;
    const previous = id ? rows.find(x => x.id === id) ?? fail('资料不存在') : undefined;
    if (previous && previous.version !== (input as CatalogUpdate).version) fail('资料已更新，请刷新核对');
    const code = previous?.code ?? ((input as CatalogInput).code?.trim().toUpperCase() || `${shop ? 'S' : 'P'}_${Math.max(0, ...rows.map(x => x.id)) + 1}`);
    const name = input.name.trim(); const platformId = previous?.platformId ?? (input as CatalogInput).platformId;
    if (!/^[A-Z0-9_-]{1,50}$/.test(code) || !name || name.length > (shop ? 200 : 100) || !Number.isInteger(input.sortOrder) || input.sortOrder < 0 || input.sortOrder > 9999 || input.remark.length > 1000) fail('字段不符合要求');
    if (shop && (!['ecommerce', 'private'].includes(input.channelType ?? '') || !input.ownerName?.trim())) fail('请选择渠道类型并填写负责人');
    if (shop && !previous && state.platforms.find(p => p.id === platformId)?.status !== 'enabled') fail('请选择有效平台');
    if (rows.some(x => x.id !== id && (x.code === code || (x.name.toLowerCase() === name.toLowerCase() && (!shop || x.platformId === platformId))))) fail('编码或名称已存在');
    const previousPlatform = state.platforms.find(p => p.id === previous?.platformId)?.name ?? '';
    const customLabel = previous?.channelType === 'private' && previous.optionLabel
      && previous.optionLabel !== shopOptionLabel('private', previousPlatform, previous.name) ? previous.optionLabel : undefined;
    const next: CatalogItem = { ...previous, id: id ?? Math.max(0, ...rows.map(x => x.id)) + 1, code, name, sortOrder: input.sortOrder, remark: input.remark.trim(), platformId,
      channelType: shop ? input.channelType : undefined, ownerName: shop ? input.ownerName?.trim() : undefined,
      optionLabel: shop && input.channelType === 'private' ? customLabel : undefined,
      status: previous?.status ?? 'enabled', version: previous ? previous.version + 1 : 0, updatedAt: new Date().toISOString() };
    if (previous) rows.splice(rows.indexOf(previous), 1, next); else rows.push(next); write(state); return get(shop, next.id);
  }
  function status(shop: boolean, id: number, status: CatalogStatus, version: number) {
    const state = read(); const row = (shop ? state.shops : state.platforms).find(x => x.id === id) ?? fail('资料不存在');
    if (row.version !== version) fail('资料已更新，请刷新核对');
    if (shop && status === 'enabled' && state.platforms.find(p => p.id === row.platformId)?.status !== 'enabled') fail('平台已停用');
    row.status = status; row.version++; row.updatedAt = new Date().toISOString(); write(state); return get(shop,id);
  }
  return { listPlatforms: async q => list(false,q), listShops: async q => list(true,q), getPlatform: async id => get(false,id), getShop: async id => get(true,id),
    createPlatform: async i => save(false,i), createShop: async i => save(true,i), updatePlatform: async (id,i) => save(false,i,id), updateShop: async (id,i) => save(true,i,id),
    changePlatformStatus: async (id,s,v) => status(false,id,s,v), changeShopStatus: async (id,s,v) => status(true,id,s,v) };
}
