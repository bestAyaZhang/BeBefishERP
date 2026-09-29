import { currentUser } from '../../services/authSession';
import type { Shipment, ShippingService, ShipmentStatus } from './types';
import { todayDate } from './types';
import { mockCatalogOptions } from '../platform/mockPlatformService';

const STORAGE_KEY = 'bebefish_mock_shipments_v2';

export function createMockShippingService(): ShippingService {
  const read = (): Shipment[] => JSON.parse(localStorage.getItem(STORAGE_KEY) ?? '[]');
  const write = (records: Shipment[]) => localStorage.setItem(STORAGE_KEY, JSON.stringify(records));
  const operator = () => currentUser.value?.displayName ?? currentUser.value?.mobile ?? '演示用户';
  return {
    async logisticsAvailability() { return { available: false, testEnvironment: true,
      message: '演示数据不提交真实物流订单', sender: null }; },
    async getLogisticsOrder() { return null; },
    async placeLogisticsOrder() { throw new Error('演示数据不能提交真实物流订单'); },
    async formOptions() { const catalog = mockCatalogOptions(); return { ...catalog, shopNames: catalog.shops.map(s => s.name), preparers: [] }; },
    async getFilterOptions() { return mockCatalogOptions(false); },
    async summary(date) {
      const rows = read().filter(row => row.content.shipmentDate === date);
      const count = (status: ShipmentStatus) => rows.filter(row => row.content.status === status).length;
      return { todayCount: rows.length, unfinishedCount: count('unfinished'), completedCount: count('completed'),
        outOfStockCount: count('out_of_stock'), partiallyShippedCount: count('partially_shipped') };
    },
    async list(query) {
      const keyword = query.keyword?.trim().toLocaleLowerCase() ?? '';
      const records = read().filter(row => {
        const c = row.content;
        return (!query.dateFrom || c.shipmentDate >= query.dateFrom)
          && (!query.dateTo || c.shipmentDate <= query.dateTo)
          && (!query.status || c.status === query.status)
          && (!query.incompleteOnly || c.status !== 'completed')
          && (!query.platform?.trim() || c.platform === query.platform.trim())
          && (!query.platformId || c.platformId === query.platformId)
          && (!query.shopId || c.shopId === query.shopId)
          && (!query.unlinkedOnly || c.shopId == null)
          && (!keyword || [row.shipmentNo, c.recipientName, c.recipientPhone, c.trackingNo,
            c.preparationContent, c.preparers.join('、'), c.orderer, c.shopName]
            .some(value => value.toLocaleLowerCase().includes(keyword)));
      }).sort((a, b) => b.content.shipmentDate.localeCompare(a.content.shipmentDate) || b.id - a.id);
      return { records: records.slice((query.page - 1) * query.size, query.page * query.size),
        page: query.page, pageSize: query.size, total: records.length };
    },
    async get(id) {
      const record = read().find(row => row.id === id);
      if (!record) throw new Error('发货单不存在');
      return record;
    },
    async create(form) {
      const records = read();
      const id = records.reduce((max, row) => Math.max(max, row.id), 0) + 1;
      const now = new Date().toISOString();
      const record: Shipment = {
        id, shipmentNo: `FH-DEMO-${String(id).padStart(6, '0')}`,
        logisticsOrderState: null,
        content: { ...structuredClone(form), shipmentDate: todayDate(), status: 'unfinished', orderer: operator(), logisticsCompany: '', trackingNo: '' },
        version: 0, createdAt: now, updatedAt: now, createdBy: operator(), updatedBy: operator()
      };
      write([...records, record]);
      return record;
    },
    async update(id, form, status, version) {
      const records = read();
      const previous = records.find(row => row.id === id);
      if (!previous) throw new Error('发货单不存在');
      if (previous.version !== version) throw new Error('发货单已被其他人修改，请重新打开后再保存；当前填写内容已保留');
      const updated: Shipment = { ...previous, content: { ...structuredClone(form), shipmentDate: previous.content.shipmentDate,
        status, orderer: previous.content.orderer, logisticsCompany: previous.content.logisticsCompany,
        trackingNo: previous.content.trackingNo }, version: version + 1, updatedAt: new Date().toISOString(), updatedBy: operator() };
      write(records.map(row => row.id === id ? updated : row));
      return updated;
    }
  };
}
