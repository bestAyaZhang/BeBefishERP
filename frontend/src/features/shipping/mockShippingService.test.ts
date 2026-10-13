import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { clearCurrentUser, saveCurrentUser } from '../../services/authSession';
import { createMockShippingService } from './mockShippingService';
import { emptyShipmentForm } from './types';

function login(employeeId = 42) {
  saveCurrentUser({ accessToken: 'test', employeeId, mobile: null, displayName: '同名下单人', roles: [],
    permissions: ['shipping:view'], loginMethod: 'feishu' });
}
beforeEach(() => { localStorage.clear(); login(); });
afterEach(clearCurrentUser);
describe('mock shipment preparation content', () => {
  it('records a stable creator and persists only the owned content and remark', async () => {
    const service = createMockShippingService();
    const previous = await service.create({ ...emptyShipmentForm(), preparationContent: '原物品', remark: '原备注' });
    expect(previous.createdBy).toBe('employee:42');
    const saved = await service.updatePreparationContent(previous.id, { preparationContent: ' 新物品 ', remark: '', version: 0 });
    expect((await service.get(previous.id)).content).toEqual({ ...previous.content, preparationContent: '新物品', remark: '' });
    expect(saved.version).toBe(1);
    expect(saved.preparation).toEqual(previous.preparation);
  });
  it('rejects other creators, stale versions and edits after preparation is completed', async () => {
    const service = createMockShippingService();
    const previous = await service.create({ ...emptyShipmentForm(), preparationContent: '原物品' });
    const payload = { preparationContent: '新物品', remark: '', version: 0 };
    login(43);
    await expect(service.updatePreparationContent(previous.id, payload)).rejects.toMatchObject({ code: 'SHIPMENT_CONTENT_FORBIDDEN' });
    login();
    const saved = await service.updatePreparationContent(previous.id, payload);
    await expect(service.updatePreparationContent(previous.id, payload)).rejects.toMatchObject({ code: 'SHIPMENT_VERSION_CONFLICT' });
    await service.update(previous.id, saved.content, 'completed', saved.version);
    await expect(service.updatePreparationContent(previous.id, { ...payload, version: 2 })).rejects.toMatchObject({ code: 'SHIPMENT_PREPARATION_COMPLETED' });
    await expect(service.update(previous.id, { ...saved.content, preparationContent: '绕过锁定' }, 'unfinished', 2))
      .rejects.toMatchObject({ code: 'SHIPMENT_PREPARATION_COMPLETED' });
  });
});
