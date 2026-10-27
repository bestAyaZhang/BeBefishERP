import { enableAutoUnmount, flushPromises, mount } from '@vue/test-utils';
import { createMemoryHistory, createRouter } from 'vue-router';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { clearCurrentUser, saveCurrentUser } from '../../services/authSession';
import { clearMessages, messages } from '../../components/feedback/message';
import ShipmentFormView from './ShipmentFormView.vue';
import type { Shipment } from './types';
import type { Quote } from '../logistics/types';

const service = vi.hoisted(() => ({ formOptions: vi.fn(), logisticsAvailability: vi.fn(), get: vi.fn(),
  getLogisticsOrder: vi.fn(), create: vi.fn(), update: vi.fn(), updatePreparationContent: vi.fn(), placeLogisticsOrder: vi.fn() }));
const freight = vi.hoisted(() => ({ getFreightSelection: vi.fn() }));
enableAutoUnmount(afterEach);
vi.mock('./shippingService', () => ({ shippingService: service }));
vi.mock('../logistics/logisticsService', () => ({ logisticsService: freight }));
vi.mock('../logistics/components/ShipmentQuotePanel.vue', () => ({ default: {
  name: 'ShipmentQuotePanelStub', props: ['input', 'modelValue', 'disabled'],
  emits: ['update:modelValue', 'loaded', 'pending'], template: '<div data-testid="quote-panel-stub" />'
} }));

function quote(apiReady = true): Quote {
  return { quoteId: 'selected-quote', companyId: 7, companyName: '合作物流验收', provider: apiReady ? 'ANE' : 'MANUAL',
    accountId: 8, accountVersionId: 9, templateId: 10, templateVersion: 1, productCode: '24', productName: '精准零担',
    amount: 26.5, billedQuantity: 18.5, chargeMode: 'weight', matchedRegion: '浙江省', lowerExclusive: 0,
    upperInclusive: null, firstQuantity: 1, firstFee: 9.5, additionalQuantity: 1, additionalFee: 1,
    renewalCount: 17, available: true, reason: '', apiReady, apiMessage: apiReady ? '' : '需线下下单',
    recommended: true, expiresAt: '2099-10-26T10:00:00Z' };
}

function shipment(overrides: Partial<Shipment> = {}): Shipment {
  const base: Shipment = {
    id: 18, shipmentNo: 'FH20260924-18', version: 0, createdBy: 'employee:1', updatedBy: 'employee:1',
    logisticsOrderState: null,
    createdAt: '2026-09-24T09:00:00', updatedAt: '2026-09-24T09:00:00',
    content: { shipmentDate: '2026-09-24', platform: '淘宝', shopName: '贝贝鱼淘宝旗舰店', preparers: ['小周', '阿杰'],
      senderName: '测试发货人', senderPhone: '13800000000', senderProvince: '浙江省', senderCity: '杭州市',
      senderCounty: '余杭区', senderDetailAddress: '测试路1号',
      recipientName: '林女士', recipientPhone: '13800006028', recipientProvince: '浙江省', recipientCity: '杭州市',
      recipientCounty: '余杭区', recipientDetailAddress: '示例路18号2栋101室', preparationContent: '水族箱 × 2\n滤材 × 6',
      remark: '', estimatedFreight: null, orderDraft: { cargoName: '水族用品', packType: '纸箱', weight: 18.5,
        volume: 0.12, pieceAmount: 2, productTypeId: 524, goodsType: 180, payType: 104, logisticsRemark: '' },
      status: 'unfinished', orderer: '李主管', logisticsCompany: '', trackingNo: '' }
  };
  return { ...base, ...overrides };
}

async function render(path = '/shipping/new') {
  const router = createRouter({ history: createMemoryHistory(), routes: [
    { path: '/shipping/list', name: 'shipping-list', component: { template: '<div>list</div>' } },
    { path: '/shipping/new', name: 'shipping-new', component: ShipmentFormView },
    { path: '/shipping/:id/edit', name: 'shipping-edit', component: ShipmentFormView },
    { path: '/shipping/:id', name: 'shipping-detail', component: { template: '<div>detail</div>' } }
  ] });
  await router.push(path); await router.isReady();
  const wrapper = mount(ShipmentFormView, { attachTo: document.body, global: { plugins: [router] } });
  await flushPromises();
  return { wrapper, router };
}

async function fillRequired(wrapper: ReturnType<typeof mount>) {
  await wrapper.get('[data-testid="shipment-platform"]').setValue('1');
  await wrapper.get('[data-testid="preparer-toggle"]').trigger('click');
  await wrapper.get('[data-testid="preparer-option-1"]').trigger('click');
  await wrapper.get('[data-testid="preparer-option-2"]').trigger('click');
  await wrapper.get('[data-testid="recipient-raw"]').setValue('林女士 13800006028 浙江省杭州市余杭区 示例路18号2栋101室');
  await wrapper.get('[data-testid="recipient-recognize"]').trigger('click');
  await wrapper.get('[data-testid="shipment-preparation-content"]').setValue('水族箱 × 2\n滤材 × 6');
}

beforeEach(() => {
  freight.getFreightSelection.mockReset().mockResolvedValue(null);
  clearMessages();
  localStorage.clear();
  clearCurrentUser();
  saveCurrentUser({ accessToken: 'test', mobile: null, displayName: '李主管', roles: [],
    permissions: ['shipping:view', 'shipping:create', 'shipping:edit', 'shipping:order'], loginMethod: 'feishu' });
  service.formOptions.mockReset().mockResolvedValue({ shopNames: ['贝贝鱼淘宝旗舰店'], platforms: [{ id: 1, name: '淘宝' }], shops: [{ id: 1, platformId: 1, name: '贝贝鱼淘宝旗舰店' }], preparers: [
    { employeeId: 1, employeeName: '小周' }, { employeeId: 2, employeeName: '阿杰' }] });
  service.logisticsAvailability.mockReset().mockResolvedValue({
    available: true,
    testEnvironment: false,
    message: '安能物流下单服务已配置',
    sender: {
      name: '测试发货人', phone: '13800000000', province: '浙江省', city: '杭州市', county: '余杭区',
      address: '测试路1号'
    }
  });
  service.get.mockReset().mockResolvedValue(shipment());
  service.getLogisticsOrder.mockReset().mockResolvedValue(null);
  service.create.mockReset().mockResolvedValue(shipment());
  service.update.mockReset().mockResolvedValue(shipment({ version: 1 }));
  service.updatePreparationContent.mockReset().mockResolvedValue(shipment({ version: 1 }));
  service.placeLogisticsOrder.mockReset().mockResolvedValue({ shipmentId: 18, orderNo: 'BF018', state: 'succeeded',
    trackingNo: 'ANE202609180018', childTrackingNos: '', message: '成功', updatedAt: '2026-09-24T10:00:00', testEnvironment: false });
});

afterEach(() => { clearCurrentUser(); clearMessages(); });

describe('ShipmentFormView', () => {
  it.each([null,0,0.5])('orders Yimi with optional or nonnegative weight/volume %s while retaining ANE product fields',async value=>{
    const {wrapper}=await render('/shipping/18/edit');
    wrapper.findComponent({name:'ShipmentQuotePanelStub'}).vm.$emit('update:modelValue',{
      ...quote(),provider:'YIMI',productCode:'1',productName:'港到门'
    });await flushPromises();
    await wrapper.get('[data-testid="yimi-visit-delivery"]').setValue('0');
    await wrapper.get('[data-testid="ane-weight"]').setValue(value??'');
    await wrapper.get('[data-testid="ane-volume"]').setValue(value??'');
    expect(wrapper.get('[data-testid="ane-weight"]').attributes('min')).toBe('0');
    expect(wrapper.get('[data-testid="ane-volume"]').attributes('min')).toBe('0');
    await wrapper.get('[data-testid="shipment-save-and-order"]').trigger('click');await flushPromises();
    expect(service.update).toHaveBeenCalledWith(18,expect.objectContaining({orderDraft:expect.objectContaining({weight:value,volume:value,productTypeId:524})}),'unfinished',0);
    expect(service.placeLogisticsOrder).toHaveBeenCalledTimes(1);
  });
  it('rejects nonzero third-decimal Yimi weight before saving and ordering',async()=>{
    const {wrapper}=await render('/shipping/18/edit');
    wrapper.findComponent({name:'ShipmentQuotePanelStub'}).vm.$emit('update:modelValue',{
      ...quote(),provider:'YIMI',productCode:'1',productName:'港到门'
    });await flushPromises();
    await wrapper.get('[data-testid="yimi-visit-delivery"]').setValue('0');
    await wrapper.get('[data-testid="ane-weight"]').setValue('0.501');
    await wrapper.get('[data-testid="shipment-save-and-order"]').trigger('click');await flushPromises();
    expect(service.update).not.toHaveBeenCalled();expect(service.placeLogisticsOrder).not.toHaveBeenCalled();
    expect(messages.value.at(-1)?.text).toContain('两位小数');
  });
  it('requires explicit Yimi pickup and elevator choices only before ordering, keeping payment meaning and ANE product intact', async () => {
    const yimi={...quote(),provider:'YIMI',companyName:'壹米滴答',productCode:'1',productName:'港到门'};
    const {wrapper}=await render('/shipping/18/edit');
    const panel=wrapper.findComponent({name:'ShipmentQuotePanelStub'});
    panel.vm.$emit('update:modelValue',yimi);await flushPromises();
    expect(wrapper.find('[data-testid="shipment-logistics-product"]').exists()).toBe(false);
    expect(wrapper.get('[data-testid="yimi-service-product"]').text()).toContain('港到门');
    expect(wrapper.get('[data-testid="yimi-visit-delivery"]').element).toHaveProperty('value','');
    await wrapper.get('[data-testid="shipment-save-and-order"]').trigger('click');await flushPromises();
    expect(service.update).not.toHaveBeenCalled();
    expect(messages.value.at(-1)?.text).toContain('揽货');
    await wrapper.get('[data-testid="yimi-visit-delivery"]').setValue('0');
    await wrapper.get('[data-testid="shipment-goods-type"]').setValue('179');
    await wrapper.get('[data-testid="shipment-save-and-order"]').trigger('click');await flushPromises();
    expect(service.update).not.toHaveBeenCalled();
    expect(messages.value.at(-1)?.text).toContain('电梯');
    await wrapper.get('[data-testid="yimi-delivery-flag"]').setValue('3');
    await wrapper.get('[data-testid="yimi-floor-no"]').setValue('7');
    await wrapper.get('[data-testid="shipment-save-and-order"]').trigger('click');await flushPromises();
    expect(service.update).toHaveBeenCalledWith(18,expect.objectContaining({orderDraft:expect.objectContaining({
      productTypeId:524,payType:104,yimi:{visitDelivery:0,deliveryFlag:3,floorNo:'7'}
    })}),'unfinished',0);
    expect(service.placeLogisticsOrder).toHaveBeenCalledTimes(1);
  });
  it('allows saving a Yimi draft when account readiness or pickup selection is incomplete', async () => {
    const {wrapper}=await render('/shipping/18/edit');
    wrapper.findComponent({name:'ShipmentQuotePanelStub'}).vm.$emit('update:modelValue',{
      ...quote(),provider:'YIMI',productCode:'1',productName:'港到门',apiReady:false,apiMessage:'缺少订购ID'
    });await flushPromises();
    expect(wrapper.get('[data-testid="shipment-save-and-order"]').attributes('disabled')).toBeDefined();
    expect(wrapper.text()).not.toContain('线下下单');
    await wrapper.get('[data-testid="shipment-save-only"]').trigger('click');await flushPromises();
    expect(service.update).toHaveBeenCalledTimes(1);
    expect(service.placeLogisticsOrder).not.toHaveBeenCalled();
  });
  it('uses the selected carrier sender defaults while retaining an explicitly edited sender', async () => {
    const { wrapper } = await render();
    service.logisticsAvailability.mockResolvedValue({ available: true, testEnvironment: true, managedTemplatesEnabled: true,
      message: '合作账号已配置', sender: {name: '合作发货人', phone: '13900000001', province: '浙江省', city: '杭州市', county: '余杭区', address: '合作仓库8号'} });
    const panel = wrapper.findComponent({ name: 'ShipmentQuotePanelStub' });
    panel.vm.$emit('update:modelValue', quote()); await flushPromises();
    expect(service.logisticsAvailability).toHaveBeenLastCalledWith(7);
    expect((wrapper.get('[data-testid="sender-name"]').element as HTMLInputElement).value).toBe('合作发货人');
    await wrapper.get('[data-testid="sender-name"]').setValue('本单指定发货人');
    service.logisticsAvailability.mockResolvedValue({ available: true, testEnvironment: true, sender: {name: '另一仓库', phone: '13900000002', province: '浙江省', city: '杭州市', county: '余杭区', address: '另一仓库9号'} });
    panel.vm.$emit('update:modelValue', {...quote(), companyId: 9}); await flushPromises();
    expect((wrapper.get('[data-testid="sender-name"]').element as HTMLInputElement).value).toBe('本单指定发货人');
  });
  it('binds the logistics product to the selected template', async () => {
    const { wrapper } = await render();
    wrapper.findComponent({ name: 'ShipmentQuotePanelStub' }).vm.$emit('update:modelValue', quote()); await flushPromises();
    expect(wrapper.get('[data-testid="shipment-logistics-product"]').attributes('disabled')).toBeDefined();
  });
  it('saves the selected company quotation separately from the editable shipment fields', async () => {
    const { wrapper } = await render();
    await fillRequired(wrapper);
    const panel = wrapper.findComponent({ name: 'ShipmentQuotePanelStub' });
    expect(panel.exists()).toBe(true);
    panel.vm.$emit('loaded', { quotes: [quote()], legacyOrderingAllowed: false, generatedAt: '2026-10-26T00:00:00Z' });
    panel.vm.$emit('update:modelValue', quote()); await flushPromises();
    await wrapper.get('[data-testid="shipment-save-only"]').trigger('click'); await flushPromises();
    expect(service.create).toHaveBeenCalledWith(expect.objectContaining({
      logisticsChoice: { quoteId: 'selected-quote', preferredCompanyId: 7, clear: false }, estimatedFreight: 26.5,
      orderDraft: expect.objectContaining({ productTypeId: 24 })
    }));
  });
  it('can save an offline company quotation while disabling API ordering', async () => {
    const { wrapper } = await render();
    await fillRequired(wrapper);
    const panel = wrapper.findComponent({ name: 'ShipmentQuotePanelStub' });
    panel.vm.$emit('loaded', { quotes: [quote(false)], legacyOrderingAllowed: false, generatedAt: '2026-10-26T00:00:00Z' });
    panel.vm.$emit('update:modelValue', quote(false)); await flushPromises();
    expect(wrapper.get('[data-testid="shipment-save-and-order"]').attributes('disabled')).toBeDefined();
    await wrapper.get('[data-testid="shipment-save-only"]').trigger('click'); await flushPromises();
    expect(service.create).toHaveBeenCalled();
    expect(service.placeLogisticsOrder).not.toHaveBeenCalled();
  });
  it('keeps draft saving available when managed pricing has no selected valid quote', async () => {
    service.logisticsAvailability.mockResolvedValue({ available: true, managedTemplatesEnabled: true, testEnvironment: true, message: '已启用合作报价' });
    const { wrapper } = await render();
    await fillRequired(wrapper);
    expect(wrapper.get('[data-testid="shipment-save-and-order"]').attributes('disabled')).toBeDefined();
    expect(wrapper.get('[data-testid="shipment-save-only"]').attributes('disabled')).toBeUndefined();
    await wrapper.get('[data-testid="shipment-save-only"]').trigger('click'); await flushPromises();
    expect(service.create).toHaveBeenCalled();
  });
  it('lets the creator use the edit page to save only items and remark without general edit permission', async () => {
    saveCurrentUser({ accessToken: 'test', employeeId: 1, mobile: '13900000000', roles: [],
      permissions: ['shipping:view', 'shipping:order'], loginMethod: 'password' });
    const item = shipment({ createdBy: '13800000000', creatorEmployeeId: 1 });
    // Historical unrelated fields must not prevent a content-only correction.
    item.content.recipientPhone = '';
    item.content.preparers = [];
    service.get.mockResolvedValue(item);
    const { wrapper, router } = await render('/shipping/18/edit');
    expect(wrapper.get('[data-testid="shipment-platform"]').attributes('disabled')).toBeDefined();
    expect(wrapper.get('[data-testid="sender-name"]').attributes('disabled')).toBeDefined();
    expect(wrapper.get('[data-testid="recipient-name"]').attributes('disabled')).toBeDefined();
    expect(wrapper.get('[data-testid="ane-weight"]').attributes('disabled')).toBeDefined();
    expect(wrapper.find('[data-testid="shipment-status"]').exists()).toBe(false);
    expect(wrapper.find('[data-testid="shipment-reselect-preparers"]').exists()).toBe(false);
    expect(wrapper.find('[data-testid="shipment-save-and-order"]').exists()).toBe(false);
    await wrapper.get('[data-testid="shipment-preparation-content"]').setValue('修改商品 × 3');
    await wrapper.get('[data-testid="shipment-remark"]').setValue('分两批备货');
    await wrapper.get('[data-testid="shipment-save-only"]').trigger('click'); await flushPromises();
    expect(service.updatePreparationContent).toHaveBeenCalledWith(18, { preparationContent: '修改商品 × 3', remark: '分两批备货', version: 0 });
    expect(service.update).not.toHaveBeenCalled();
    expect(service.create).not.toHaveBeenCalled();
    expect(service.placeLogisticsOrder).not.toHaveBeenCalled();
    expect(router.currentRoute.value.name).toBe('shipping-detail');
    expect(messages.value.at(-1)).toMatchObject({ type: 'success', text: '备货清单与备注已保存' });
  });
  it.each(['other-creator', 'completed', 'reassigned-phone'])('blocks a content-only editor when %s', async reason => {
    saveCurrentUser({ accessToken: 'test', employeeId: 1, mobile: '13800000000', roles: [], permissions: ['shipping:view'], loginMethod: 'password' });
    const item = shipment({ creatorEmployeeId: reason === 'completed' ? 1 : 2, createdBy: reason === 'reassigned-phone' ? '13800000000' : 'employee:2' });
    if (reason === 'completed') item.content.status = 'completed';
    service.get.mockResolvedValue(item);
    const { wrapper } = await render('/shipping/18/edit');
    expect(wrapper.get('[data-testid="shipment-preparation-content"]').attributes('disabled')).toBeDefined();
    expect(wrapper.get('[data-testid="shipment-remark"]').attributes('disabled')).toBeDefined();
    expect(wrapper.get('[data-testid="shipment-save-only"]').attributes('disabled')).toBeDefined();
    await wrapper.get('[data-testid="shipment-save-only"]').trigger('click'); await flushPromises();
    expect(service.updatePreparationContent).not.toHaveBeenCalled();
    expect(service.update).not.toHaveBeenCalled();
    expect(service.create).not.toHaveBeenCalled();
  });
  it('retains content on a version conflict and requires loading the latest state before saving again', async () => {
    saveCurrentUser({ accessToken: 'test', employeeId: 1, mobile: null, roles: [], permissions: ['shipping:view'], loginMethod: 'feishu' });
    service.updatePreparationContent.mockRejectedValue(Object.assign(new Error('发货单已更新'), { code: 'SHIPMENT_VERSION_CONFLICT' }));
    const { wrapper } = await render('/shipping/18/edit');
    await wrapper.get('[data-testid="shipment-preparation-content"]').setValue('保留本次输入');
    await wrapper.get('[data-testid="shipment-save-only"]').trigger('click'); await flushPromises();
    expect(wrapper.get('[data-testid="shipment-preparation-content"]').element).toHaveProperty('value', '保留本次输入');
    expect(wrapper.get('[data-testid="shipment-save-only"]').attributes('disabled')).toBeDefined();
    service.get.mockResolvedValue({ ...shipment(), version: 1, content: { ...shipment().content, status: 'completed' } });
    await wrapper.get('[data-testid="shipment-edit-reload"]').trigger('click'); await flushPromises();
    expect(wrapper.text()).toContain('备货已完成');
    expect(wrapper.get('[data-testid="shipment-preparation-content"]').attributes('disabled')).toBeDefined();
    expect(wrapper.get('[data-testid="shipment-save-only"]').attributes('disabled')).toBeDefined();
  });
  it('does not create a new shipment when an edit record fails to load', async () => {
    service.get.mockRejectedValue(new Error('发货单不存在'));
    const { wrapper } = await render('/shipping/18/edit');
    expect(wrapper.get('[data-testid="shipment-save-only"]').attributes('disabled')).toBeDefined();
    expect(service.create).not.toHaveBeenCalled();
  });
  it('keeps a retry path when reloading a content conflict fails temporarily', async () => {
    saveCurrentUser({ accessToken: 'test', employeeId: 1, mobile: null, roles: [], permissions: ['shipping:view'], loginMethod: 'feishu' });
    service.updatePreparationContent.mockRejectedValue(Object.assign(new Error('发货单已更新'), { code: 'SHIPMENT_VERSION_CONFLICT' }));
    const { wrapper } = await render('/shipping/18/edit');
    await wrapper.get('[data-testid="shipment-preparation-content"]').setValue('本次未保存内容');
    await wrapper.get('[data-testid="shipment-save-only"]').trigger('click'); await flushPromises();
    service.get.mockRejectedValueOnce(new Error('临时网络异常'));
    await wrapper.get('[data-testid="shipment-edit-reload"]').trigger('click'); await flushPromises();
    expect(wrapper.get('[data-testid="shipment-save-only"]').attributes('disabled')).toBeDefined();
    expect(wrapper.get('[data-testid="shipment-preparation-content"]').element).toHaveProperty('value', '本次未保存内容');
    service.get.mockResolvedValue(shipment({ version: 2 }));
    await wrapper.get('[data-testid="shipment-form-retry"]').trigger('click'); await flushPromises();
    expect(wrapper.get('[data-testid="shipment-save-only"]').attributes('disabled')).toBeUndefined();
    expect(wrapper.get('[data-testid="shipment-preparation-content"]').element).toHaveProperty('value', shipment().content.preparationContent);
    expect(wrapper.find('[data-testid="shipment-edit-reload"]').exists()).toBe(false);
  });
  it('locks preparation items and remark after preparation is completed', async () => {
    const completed = shipment();
    completed.content.status = 'completed';
    service.get.mockResolvedValue(completed);
    const { wrapper } = await render('/shipping/18/edit');
    expect(wrapper.get('[data-testid="shipment-preparation-content"]').attributes('disabled')).toBeDefined();
    expect(wrapper.get('[data-testid="shipment-remark"]').attributes('disabled')).toBeDefined();
    wrapper.unmount();
  });
  it.each([['SHIPMENT_VERSION_CONFLICT', 'warning'], ['VALIDATION_FAILED', 'warning'], ['SERVICE_UNAVAILABLE', 'error']])('keeps form input and shows %s with the appropriate message', async (code, type) => {
    service.update.mockRejectedValue(Object.assign(new Error('请核对后重新保存'), { code }));
    const { wrapper, router } = await render('/shipping/18/edit');
    await wrapper.get('[data-testid="shipment-preparation-content"]').setValue('本次新增备货内容');
    await wrapper.get('[data-testid="shipment-save-only"]').trigger('click'); await flushPromises();
    expect(messages.value.at(-1)).toMatchObject({ type, text: '请核对后重新保存' });
    expect(router.currentRoute.value.name).toBe('shipping-edit');
    expect(wrapper.get('[data-testid="shipment-preparation-content"]').element).toHaveProperty('value', '本次新增备货内容');
    expect(wrapper.get('[data-testid="shipment-save-only"]').attributes('disabled')).toBeUndefined();
    wrapper.unmount();
  });
  it('can clear stale employee accounts and reassign an unsubmitted shipment to active preparers', async () => {
    const previous = shipment({ preparerEmployeeIds: [99] });
    previous.content.preparers = ['停用备货员'];
    service.get.mockResolvedValue(previous);
    service.getLogisticsOrder.mockResolvedValue(null);
    const { wrapper } = await render('/shipping/18/edit');
    await wrapper.get('[data-testid="shipment-reselect-preparers"]').trigger('click');
    await fillRequired(wrapper);
    await wrapper.get('[data-testid="shipment-save-only"]').trigger('click'); await flushPromises();
    expect(service.update).toHaveBeenCalledWith(18, expect.objectContaining({ preparerEmployeeIds: [1, 2], preparers: ['小周', '阿杰'] }), 'unfinished', 0);
    wrapper.unmount();
  });
  it('defaults sender fields from configuration and saves the edited shipment snapshot', async () => {
    const { wrapper } = await render();

    expect((wrapper.get('[data-testid="sender-name"]').element as HTMLInputElement).value).toBe('测试发货人');
    expect((wrapper.get('[data-testid="sender-phone"]').element as HTMLInputElement).value).toBe('13800000000');
    expect((wrapper.get('[data-testid="sender-detail-address"]').element as HTMLInputElement).value).toBe('测试路1号');

    await wrapper.get('[data-testid="sender-name"]').setValue('本单发货人');
    await wrapper.get('[data-testid="sender-phone"]').setValue('13900000000');
    await wrapper.get('[data-testid="sender-detail-address"]').setValue('本单发货路2号');
    await fillRequired(wrapper);
    await wrapper.get('[data-testid="shipment-save-only"]').trigger('click');
    await flushPromises();

    expect(service.create).toHaveBeenCalledWith(expect.objectContaining({
      senderName: '本单发货人', senderPhone: '13900000000', senderProvince: '浙江省', senderCity: '杭州市',
      senderCounty: '余杭区', senderDetailAddress: '本单发货路2号'
    }));
    wrapper.unmount();
  });

  it('defaults a new order to the documented cash payment type', async () => {
    const { wrapper } = await render();
    const paymentSelect = wrapper.findAll('select').find(select => select.text().includes('月结') && select.text().includes('现金'));

    expect(paymentSelect).toBeDefined();
    expect((paymentSelect!.element as HTMLSelectElement).value).toBe('102');
    wrapper.unmount();
  });

  it('marks every field required for an ANE order and leaves optional fields unmarked', async () => {
    const { wrapper } = await render();
    const labels = wrapper.findAll('label.field-label');
    const labelText = (name: string) => labels.find(label => label.text().startsWith(name))?.text() || '';

    expect(labelText('备货人（可多选）')).toContain('*');
    for (const name of ['货物名称', '包装方式', '件数', '重量（kg）', '体积（m³）', '物流产品', '送货方式', '付款方式']) {
      expect(labelText(name), `${name} should be marked required`).toContain('*');
    }
    expect(labelText('运费预测')).not.toContain('*');
    expect(labelText('物流备注')).not.toContain('*');
    wrapper.unmount();
  });

  it('requires at least one preparer before saving a shipment', async () => {
    const { wrapper } = await render();
    await wrapper.get('[data-testid="recipient-raw"]').setValue('林女士 13800006028 浙江省杭州市余杭区 示例路18号2栋101室');
    await wrapper.get('[data-testid="recipient-recognize"]').trigger('click');
    await wrapper.get('[data-testid="shipment-preparation-content"]').setValue('水族箱 × 2');

    await wrapper.get('[data-testid="shipment-save-only"]').trigger('click');

    expect(messages.value.at(-1)).toMatchObject({ type: 'warning', text: '请至少选择一名备货人' });
    expect(service.create).not.toHaveBeenCalled();
    wrapper.unmount();
  });

  it('keeps server fields hidden, recognizes inline and saves the full-width form', async () => {
    const { wrapper, router } = await render();
    expect(wrapper.find('[data-testid="shipment-date"]').exists()).toBe(false);
    expect(wrapper.find('[data-testid="shipment-orderer"]').exists()).toBe(false);
    expect(wrapper.find('[data-testid="shipment-status"]').exists()).toBe(false);
    expect((wrapper.get('[data-testid="shipment-shop"]').element as HTMLSelectElement).value).toBe('1');
    await fillRequired(wrapper);
    expect((wrapper.get('[data-testid="recipient-county"]').element as HTMLInputElement).value).toBe('余杭区');
    await wrapper.get('[data-testid="shipment-save-only"]').trigger('click'); await flushPromises();
    expect(service.create).toHaveBeenCalledWith(expect.objectContaining({ shopName: '贝贝鱼淘宝旗舰店',
      preparers: ['小周', '阿杰'], preparationContent: '水族箱 × 2\n滤材 × 6' }));
    expect(router.currentRoute.value.name).toBe('shipping-detail');
    expect(messages.value.at(-1)).toMatchObject({ type: 'success', text: '发货单已保存' });
    wrapper.unmount();
  });

  it('creates once, submits the returned version and opens the result dialog', async () => {
    const { wrapper } = await render();
    await fillRequired(wrapper);
    await wrapper.get('[data-testid="ane-cargo-name"]').setValue('水族用品');
    await wrapper.get('[data-testid="ane-weight"]').setValue('18.5');
    await wrapper.get('[data-testid="ane-volume"]').setValue('0.12');
    await wrapper.get('[data-testid="shipment-save-and-order"]').trigger('click'); await flushPromises();
    expect(service.create).toHaveBeenCalledTimes(1);
    expect(service.placeLogisticsOrder).toHaveBeenCalledWith(18, { version: 0 });
    expect(wrapper.get('[role="dialog"]').text()).toContain('ANE202609180018');
    wrapper.unmount();
  });

  it('shows the test-environment warning after a test order succeeds', async () => {
    service.logisticsAvailability.mockResolvedValue({ available: true, testEnvironment: true,
      message: '安能测试环境已配置', sender: {
        name: '测试发货人', phone: '13800000000', province: '浙江省', city: '杭州市', county: '余杭区',
        address: '测试路1号'
      } });
    service.placeLogisticsOrder.mockResolvedValue({ shipmentId: 18, orderNo: 'BF018', state: 'succeeded',
      trackingNo: '123456789012', childTrackingNos: '', message: '成功',
      updatedAt: '2026-09-24T10:00:00', testEnvironment: true });
    const { wrapper } = await render();
    await fillRequired(wrapper);
    await wrapper.get('[data-testid="ane-cargo-name"]').setValue('水族用品');
    await wrapper.get('[data-testid="ane-weight"]').setValue('18.5');
    await wrapper.get('[data-testid="ane-volume"]').setValue('0.12');
    await wrapper.get('[data-testid="shipment-save-and-order"]').trigger('click'); await flushPromises();
    expect(wrapper.get('[role="dialog"]').text()).toContain('测试环境下单成功');
    expect(wrapper.get('[role="dialog"]').text()).toContain('不可用于实际走货');
    wrapper.unmount();
  });

  it('hides the logistics order action without shipping:order permission', async () => {
    saveCurrentUser({ accessToken: 'test', mobile: null, displayName: '录入人', roles: [],
      permissions: ['shipping:view', 'shipping:create'], loginMethod: 'feishu' });
    const { wrapper } = await render();
    expect(wrapper.find('[data-testid="shipment-save-and-order"]').exists()).toBe(false);
    expect(wrapper.find('[data-testid="shipment-save-only"]').exists()).toBe(true);
    wrapper.unmount();
  });

  it('keeps the saved shipment, refreshes logistics state and locks key fields when ordering fails', async () => {
    service.placeLogisticsOrder.mockRejectedValue(new Error('安能暂时不可用'));
    service.get.mockResolvedValue(shipment({ version: 1 }));
    service.getLogisticsOrder.mockResolvedValue({ shipmentId: 18, orderNo: 'BF018', state: 'processing', trackingNo: '',
      childTrackingNos: '', message: '提交中', updatedAt: '2026-09-24T10:00:00', testEnvironment: false });
    const { wrapper, router } = await render();
    await fillRequired(wrapper);
    await wrapper.get('[data-testid="ane-cargo-name"]').setValue('水族用品');
    await wrapper.get('[data-testid="ane-weight"]').setValue('18.5');
    await wrapper.get('[data-testid="ane-volume"]').setValue('0.12');
    await wrapper.get('[data-testid="shipment-save-and-order"]').trigger('click'); await flushPromises();
    expect(service.create).toHaveBeenCalledTimes(1);
    expect(service.get).toHaveBeenCalledWith(18);
    expect(service.getLogisticsOrder).toHaveBeenCalledWith(18);
    expect(router.currentRoute.value).toMatchObject({ name: 'shipping-edit', params: { id: '18' } });
    expect(messages.value.at(-1)).toMatchObject({ type: 'warning', text: expect.stringContaining('发货单已保存') });
    expect(wrapper.get('[data-testid="shipment-platform"]').attributes('disabled')).toBeDefined();
    wrapper.unmount();
  });

  it('disables save actions while a request is active', async () => {
    let release!: (value: Shipment) => void;
    service.create.mockReturnValue(new Promise<Shipment>(resolve => { release = resolve; }));
    const { wrapper } = await render();
    await fillRequired(wrapper);
    await wrapper.get('[data-testid="ane-cargo-name"]').setValue('水族用品');
    await wrapper.get('[data-testid="ane-weight"]').setValue('18.5');
    await wrapper.get('[data-testid="ane-volume"]').setValue('0.12');
    await wrapper.get('[data-testid="shipment-save-and-order"]').trigger('click');
    expect(wrapper.get('[data-testid="shipment-save-and-order"]').attributes('disabled')).toBeDefined();
    expect(wrapper.get('[data-testid="shipment-save-only"]').attributes('disabled')).toBeDefined();
    release(shipment()); await flushPromises();
    wrapper.unmount();
  });
  it.each([['rejected', 'error'], ['unknown', 'warning']])('preserves a %s order message after routing to detail', async (state, type) => {
    service.placeLogisticsOrder.mockResolvedValue({ shipmentId: 18, orderNo: 'BF018', state, trackingNo: '',
      childTrackingNos: '', message: '请核对物流订单', updatedAt: '2026-09-24T10:00:00', testEnvironment: false });
    const { wrapper, router } = await render();
    await fillRequired(wrapper);
    await wrapper.get('[data-testid="ane-cargo-name"]').setValue('水族用品');
    await wrapper.get('[data-testid="ane-weight"]').setValue('18.5');
    await wrapper.get('[data-testid="ane-volume"]').setValue('0.12');
    await wrapper.get('[data-testid="shipment-save-and-order"]').trigger('click'); await flushPromises();
    expect(router.currentRoute.value.name).toBe('shipping-detail');
    expect(messages.value.at(-1)).toMatchObject({ type, text: expect.stringContaining('请核对物流订单') });
    expect(wrapper.find('[role="dialog"]').exists()).toBe(false);
    wrapper.unmount();
  });

  it('does not replace a missing historical sender after the logistics order is locked', async () => {
    const legacy = shipment();
    Object.assign(legacy.content, {
      senderName: '', senderPhone: '', senderProvince: '', senderCity: '', senderCounty: '', senderDetailAddress: ''
    });
    service.get.mockResolvedValue(legacy);
    service.getLogisticsOrder.mockResolvedValue({ shipmentId: 18, orderNo: 'BF018', state: 'processing', trackingNo: '',
      childTrackingNos: '', message: '提交中', updatedAt: '2026-09-24T10:00:00', testEnvironment: false });

    const { wrapper } = await render('/shipping/18/edit');

    expect((wrapper.get('[data-testid="sender-name"]').element as HTMLInputElement).value).toBe('');
    expect(wrapper.get('[data-testid="sender-name"]').attributes('disabled')).toBeDefined();
    wrapper.unmount();
  });

  it('locks order fields after submission but updates preparation, remark and status with the loaded values', async () => {
    service.getLogisticsOrder.mockResolvedValue({ shipmentId: 18, orderNo: 'BF018', state: 'processing', trackingNo: '',
      childTrackingNos: '', message: '提交中', updatedAt: '2026-09-24T10:00:00', testEnvironment: false });
    const { wrapper } = await render('/shipping/18/edit');
    expect(wrapper.get('[data-testid="shipment-platform"]').attributes('disabled')).toBeDefined();
    expect(wrapper.get('[data-testid="recipient-name"]').attributes('disabled')).toBeDefined();
    await wrapper.get('[data-testid="shipment-preparation-content"]').setValue('已发第一批');
    await wrapper.get('[data-testid="shipment-remark"]').setValue('剩余待补');
    await wrapper.get('[data-testid="shipment-status"]').setValue('partially_shipped');
    await wrapper.get('[data-testid="shipment-save-only"]').trigger('click'); await flushPromises();
    expect(service.update).toHaveBeenCalledWith(18, expect.objectContaining({ platform: '淘宝', recipientName: '林女士',
      preparationContent: '已发第一批', remark: '剩余待补' }), 'partially_shipped', 0);
    wrapper.unmount();
  });
});

it('uses stable catalog IDs and clears a shop from a different platform', async () => {
  service.formOptions.mockResolvedValue({ shopNames: [], preparers: [], platforms: [{ id: 1, name: '淘宝' }, { id: 2, name: '抖音' }], shops: [{ id: 11, platformId: 1, name: '旗舰店' }, { id: 22, platformId: 2, name: '直播店' }, { id: 23, platformId: 2, name: '分店' }] });
  const { wrapper } = await render();
  expect(wrapper.get('[data-testid="shipment-platform"]').element.tagName).toBe('SELECT');
  await wrapper.get('[data-testid="shipment-platform"]').setValue('1');
  expect((wrapper.get('[data-testid="shipment-shop"]').element as HTMLSelectElement).value).toBe('11');
  await wrapper.get('[data-testid="shipment-platform"]').setValue('2');
  expect((wrapper.get('[data-testid="shipment-shop"]').element as HTMLSelectElement).selectedIndex).toBe(0);
  expect(wrapper.get('[data-testid="shipment-shop"]').text()).not.toContain('旗舰店');
  wrapper.unmount();
});

it('shows the configured store option while retaining the actual shop name in the shipment', async () => {
  service.formOptions.mockResolvedValue({ shopNames: [], preparers: [], platforms: [{ id: 1, name: '拼多多' }],
    shops: [{ id: 11, platformId: 1, name: '笨笨的生活商铺', optionLabel: '电商_拼多多_笨笨的生活商铺' }] });
  const { wrapper } = await render();
  expect(wrapper.get<HTMLSelectElement>('[data-testid="shipment-shop"]').element.selectedOptions[0].textContent).toBe('电商_拼多多_笨笨的生活商铺');
  expect((wrapper.vm as any).form.shopName).toBe('笨笨的生活商铺');
  wrapper.unmount();
});

it('preserves an unlinked historical source when editing with no active catalog', async () => {
  service.formOptions.mockResolvedValue({ shopNames: [], preparers: [], platforms: [], shops: [] });
  const historical = shipment();
  historical.content.platformId = null;
  historical.content.shopId = null;
  service.get.mockResolvedValue(historical);
  const { wrapper } = await render('/shipping/18/edit');
  expect(wrapper.get<HTMLSelectElement>('[data-testid="shipment-platform"]').element.selectedOptions[0].textContent).toContain('淘宝');
  expect(wrapper.get<HTMLSelectElement>('[data-testid="shipment-shop"]').element.selectedOptions[0].textContent).toContain('贝贝鱼淘宝旗舰店');
  await wrapper.get('[data-testid="shipment-save-only"]').trigger('click'); await flushPromises();
  expect(service.update).toHaveBeenCalledWith(18, expect.objectContaining({ platform: '淘宝', shopName: '贝贝鱼淘宝旗舰店' }), 'unfinished', 0);
  wrapper.unmount();
});

it('blocks creation and explains an empty catalog', async () => {
  service.formOptions.mockResolvedValue({ shopNames: [], preparers: [], platforms: [], shops: [] });
  const { wrapper } = await render();
  expect(wrapper.text()).toContain('暂无可用店铺');
  expect(wrapper.get('[data-testid="shipment-save-only"]').attributes('disabled')).toBeDefined();
  wrapper.unmount();
});
