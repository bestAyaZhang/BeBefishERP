import { currentUser } from '../../services/authSession';
import type { Shipment, ShippingService, ShipmentStatus } from './types';
import { todayDate } from './types';
import { mockCatalogOptions } from '../platform/mockPlatformService';
import { canConfirmCreator, canEditPreparationContent, canUpdatePreparation, shipmentOperatorIdentifier } from './preparationAccess';

const STORAGE_KEY = 'bebefish_mock_shipments_v2';

export function createMockShippingService(): ShippingService {
  const read = (): Shipment[] => JSON.parse(localStorage.getItem(STORAGE_KEY) ?? '[]');
  const write = (records: Shipment[]) => localStorage.setItem(STORAGE_KEY, JSON.stringify(records));
  const operator = () => currentUser.value?.displayName ?? currentUser.value?.mobile ?? '演示用户';
  return {
    async logisticsAvailability() { return { available: false, testEnvironment: true,
      message: '演示数据不提交真实物流订单', sender: null }; },
    async getLogisticsOrder() { return null; },
    async getLogisticsAccountBinding() { return { companyId:null,accountId:null,accountVersionId:null,linked:false,requiresConfirmation:false }; },
    async placeLogisticsOrder() { throw new Error('演示数据不能提交真实物流订单'); },
    async cancelLogisticsOrder() { throw new Error('演示数据不能取消真实物流订单'); },
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
      if(form.logisticsChoice?.quoteId) throw new Error('演示数据不能保存真实合作报价，请使用本地后端');
      const { logisticsChoice: _choice, ...fields } = form;
      const records = read();
      const id = records.reduce((max, row) => Math.max(max, row.id), 0) + 1;
      const now = new Date().toISOString();
      const record: Shipment = {
        id, shipmentNo: `FH-DEMO-${String(id).padStart(6, '0')}`,
        logisticsOrderState: null,
        creatorEmployeeId: currentUser.value?.employeeId ?? null,
        preparerEmployeeIds: form.preparerEmployeeIds ?? [],
        content: { ...structuredClone(fields), shipmentDate: todayDate(), status: 'unfinished', orderer: operator(), logisticsCompany: '', trackingNo: '' },
        version: 0, createdAt: now, updatedAt: now, createdBy: shipmentOperatorIdentifier(currentUser.value) || 'demo', updatedBy: shipmentOperatorIdentifier(currentUser.value) || 'demo'
      };
      write([...records, record]);
      return record;
    },
    async update(id, form, status, version) {
      if(form.logisticsChoice?.quoteId) throw new Error('演示数据不能保存真实合作报价，请使用本地后端');
      const { logisticsChoice: _choice, ...fields } = form;
      const records = read();
      const previous = records.find(row => row.id === id);
      if (!previous) throw new Error('发货单不存在');
      if (previous.version !== version) throw new Error('发货单已被其他人修改，请重新打开后再保存；当前填写内容已保留');
      if (previous.content.status === 'completed' && (form.preparationContent !== previous.content.preparationContent || form.remark !== previous.content.remark))
        throw Object.assign(new Error('备货已完成，不能修改备货物品和备注'), { code: 'SHIPMENT_PREPARATION_COMPLETED' });
      const updated: Shipment = { ...previous, preparerEmployeeIds: form.preparerEmployeeIds ?? previous.preparerEmployeeIds ?? [], content: { ...structuredClone(fields), shipmentDate: previous.content.shipmentDate,
        status, orderer: previous.content.orderer, logisticsCompany: previous.content.logisticsCompany,
        trackingNo: previous.content.trackingNo }, version: version + 1, updatedAt: new Date().toISOString(), updatedBy: operator() };
      write(records.map(row => row.id === id ? updated : row));
      return updated;
    },
    async confirmCreator(id, payload) {
      const records = read();
      const previous = records.find(row => row.id === id);
      if (!previous) throw new Error('发货单不存在');
      if (!canConfirmCreator(previous, currentUser.value)) throw new Error('只有超级管理员可确认尚未关联的历史单下单账号');
      if (previous.version !== payload.version) throw Object.assign(new Error('发货单已更新，请重新加载核对'), { code: 'SHIPMENT_VERSION_CONFLICT' });
      if (!Number.isInteger(payload.employeeId) || payload.employeeId <= 0) throw new Error('请选择员工账号');
      const updated: Shipment = { ...previous, creatorEmployeeId: payload.employeeId, version: previous.version + 1,
        updatedBy: shipmentOperatorIdentifier(currentUser.value), updatedAt: new Date().toISOString() };
      write(records.map(row => row.id === id ? updated : row));
      return updated;
    },
    async updatePreparationContent(id, payload) {
      const records = read();
      const previous = records.find(row => row.id === id);
      if (!previous) throw new Error('发货单不存在');
      if (previous.content.status === 'completed') throw Object.assign(new Error('备货已完成，不能修改备货物品和备注'), { code: 'SHIPMENT_PREPARATION_COMPLETED' });
      if (!canEditPreparationContent(previous, currentUser.value)) throw Object.assign(new Error('只有本单下单人可以修改备货物品和备注'), { code: 'SHIPMENT_CONTENT_FORBIDDEN' });
      if (previous.version !== payload.version) throw Object.assign(new Error('发货单已更新，请重新加载核对；当前填写内容已保留'), { code: 'SHIPMENT_VERSION_CONFLICT' });
      if (!payload.preparationContent.trim() || payload.preparationContent.length > 10000 || payload.remark.length > 5000) throw Object.assign(new Error('备货物品和备注格式无效'), { code: 'VALIDATION_FAILED' });
      const updated: Shipment = { ...previous, content: { ...previous.content, preparationContent: payload.preparationContent.trim(), remark: payload.remark.trim() },
        version: previous.version + 1, updatedBy: shipmentOperatorIdentifier(currentUser.value), updatedAt: new Date().toISOString() };
      write(records.map(row => row.id === id ? updated : row));
      return updated;
    },
    async updatePreparation(id, payload) {
      const records = read();
      const previous = records.find(row => row.id === id);
      if (!previous) throw new Error('发货单不存在');
      if (!canUpdatePreparation(previous, currentUser.value)) throw new Error('只能更新分配给自己的发货单');
      if (previous.version !== payload.version) throw Object.assign(new Error('发货单已更新，请重新加载核对；当前填写内容已保留'), { code: 'SHIPMENT_VERSION_CONFLICT' });
      if (payload.actualWeight != null && (payload.actualWeight <= 0 || !Number.isFinite(payload.actualWeight)
        || payload.actualWeight > 999999999.999 || !/^\d+(\.\d{1,3})?$/.test(String(payload.actualWeight)))) throw new Error('实际重量必须大于零，最多3位小数');
      const now = new Date().toISOString();
      const updated: Shipment = { ...previous, content: { ...previous.content, status: payload.status },
        preparation: { actualWeight: payload.actualWeight ?? previous.preparation?.actualWeight ?? null, updatedBy: operator(), employeeId: currentUser.value?.employeeId ?? null, updatedAt: now },
        version: previous.version + 1, updatedBy: operator(), updatedAt: now };
      write(records.map(row => row.id === id ? updated : row));
      return updated;
    }
  };
}
