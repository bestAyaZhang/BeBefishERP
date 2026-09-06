import { mockRegionService } from '../regionService';
import { flushPromises, mount } from '@vue/test-utils';
import { describe, expect, it, vi } from 'vitest';
import type { MasterdataService } from '../types';
import CategoryView from './CategoryView.vue';
import CustomerView from './CustomerView.vue';
import CustomerFormView from './CustomerFormView.vue';
import SupplierView from './SupplierView.vue';
import WarehouseView from './WarehouseView.vue';
import router from '../../../router';
import { ACCESS_TOKEN_STORAGE_KEY } from '../../../types/auth';

function createFakeService(): MasterdataService {
  return {
    listCategories: vi.fn().mockResolvedValue({
      records: [{ id: 1, categoryCode: 'CUP', categoryName: '水杯', parentId: null, level: 1, sortOrder: 10, status: 'enabled', remark: '' }],
      page: 1,
      pageSize: 20,
      total: 1
    }),
    createCategory: vi.fn().mockResolvedValue({ id: 2, categoryCode: 'GLASS', categoryName: '玻璃杯', parentId: null, level: 1, sortOrder: 0, status: 'enabled', remark: '' }),
    updateCategory: vi.fn(),
    changeCategoryStatus: vi.fn(),
    listCustomers: vi.fn().mockResolvedValue({ records: [], page: 1, pageSize: 20, total: 0 }),
    createCustomer: vi.fn(),
    updateCustomer: vi.fn(),
    changeCustomerStatus: vi.fn(),
    listSuppliers: vi.fn().mockResolvedValue({
      records: [{ id: 3, supplierNo: 'SUP-01', supplierName: '义乌玻璃厂', contactPerson: '李经理', mobile: '', telephone: '', address: '', status: 'enabled', remark: '' }],
      page: 1,
      pageSize: 20,
      total: 1
    }),
    createSupplier: vi.fn(),
    updateSupplier: vi.fn(),
    changeSupplierStatus: vi.fn().mockResolvedValue({}),
    listWarehouses: vi.fn().mockResolvedValue({
      records: [
        { id: 1, warehouseNo: 'WH-01', warehouseName: '主仓', address: '杭州', defaultWarehouse: true, status: 'enabled', remark: '' },
        { id: 2, warehouseNo: 'WH-02', warehouseName: '备货仓', address: '义乌', defaultWarehouse: false, status: 'enabled', remark: '' }
      ],
      page: 1,
      pageSize: 20,
      total: 2
    }),
    createWarehouse: vi.fn(),
    updateWarehouse: vi.fn(),
    changeWarehouseStatus: vi.fn(),
    setDefaultWarehouse: vi.fn().mockResolvedValue({}),
    listActiveCustomers: vi.fn().mockResolvedValue([]),
    listActiveWarehouses: vi.fn().mockResolvedValue([])
  };
}

function mountWarehouseView(service: MasterdataService) {
  return mount(WarehouseView, { global: { plugins: [router], provide: { masterdataService: service, regionService: mockRegionService } } });
}

describe('masterdata views', () => {
  it('creates a level-one category and refreshes the list', async () => {
    const fakeService = createFakeService();
    const wrapper = mount(CategoryView, { global: { provide: { masterdataService: fakeService, regionService: mockRegionService } } });
    await flushPromises();

    await wrapper.get('[data-testid="add-category"]').trigger('click');
    await wrapper.get('[data-testid="category-name"]').setValue('玻璃杯');
    await wrapper.get('[data-testid="save-category"]').trigger('click');
    await flushPromises();

    const payload = vi.mocked(fakeService.createCategory).mock.calls[0][0];
    expect(payload).not.toHaveProperty('categoryCode');
    expect(payload.categoryName).toBe('玻璃杯');
    expect(fakeService.listCategories).toHaveBeenCalledTimes(2);
  });

  it('saves the customer transport method and settlement cycle', async () => {
    const fakeService = createFakeService();
    vi.mocked(fakeService.createCustomer).mockResolvedValue({} as never);
    await router.push('/customers/new');
    await router.isReady();
    const wrapper = mount(CustomerFormView, { global: { plugins: [router], provide: { masterdataService: fakeService, regionService: mockRegionService } } });
    await flushPromises();

    expect(wrapper.find('[data-testid="customer-no"]').exists()).toBe(false);
    await wrapper.get('[data-testid="customer-name"]').setValue('杭州酒店');
    await wrapper.get('[data-testid="customer-contact"]').setValue('张经理');
    await wrapper.get('[data-testid="customer-mobile"]').setValue('13800138000');
    await wrapper.get('[data-testid="customer-province"]').setValue('浙江省');
    await wrapper.get('[data-testid="customer-city"]').setValue('杭州市');
    await wrapper.get('[data-testid="customer-district"]').setValue('余杭区');
    await wrapper.get('[data-testid="customer-detail-address"]').setValue('良渚街道88号');
    await wrapper.get('[data-testid="customer-transport"]').setValue('delivery');
    await wrapper.get('[data-testid="customer-settlement"]').setValue('monthly');
    await wrapper.get('[data-testid="customer-form-save"]').trigger('click');
    await flushPromises();

    const payload = vi.mocked(fakeService.createCustomer).mock.calls[0][0];
    expect(payload).not.toHaveProperty('customerNo');
    expect(payload).toEqual(expect.objectContaining({ customerName: '杭州酒店', province: '浙江省', city: '杭州市', district: '余杭区', transportMethod: 'delivery', settlementCycle: 'monthly' }));
  });

  it('parses a pasted address and validates required customer fields before saving', async () => {
    const fakeService = createFakeService();
    await router.push('/customers/new');
    await router.isReady();
    const wrapper = mount(CustomerFormView, { global: { plugins: [router], provide: { masterdataService: fakeService, regionService: mockRegionService } } });
    await flushPromises();

    await wrapper.get('[data-testid="customer-name"]').setValue('杭州酒店');
    await wrapper.get('[data-testid="customer-parse-address"]').setValue('浙江省杭州市余杭区良渚街道88号');
    await wrapper.get('[data-testid="parse-address"]').trigger('click');
    await flushPromises();

    expect((wrapper.get('[data-testid="customer-province"]').element as HTMLSelectElement).value).toBe('浙江省');
    expect((wrapper.get('[data-testid="customer-city"]').element as HTMLSelectElement).value).toBe('杭州市');
    expect((wrapper.get('[data-testid="customer-district"]').element as HTMLSelectElement).value).toBe('余杭区');
    expect((wrapper.get('[data-testid="customer-detail-address"]').element as HTMLInputElement).value).toBe('良渚街道88号');

    await wrapper.get('[data-testid="customer-form-save"]').trigger('click');
    expect(fakeService.createCustomer).not.toHaveBeenCalled();
    expect(wrapper.text()).toContain('请填写联系人');

    await wrapper.get('[data-testid="customer-contact"]').setValue('张经理');
    await wrapper.get('[data-testid="customer-mobile"]').setValue('13800138000');
    await wrapper.get('[data-testid="customer-form-save"]').trigger('click');
    await flushPromises();

    expect(fakeService.createCustomer).toHaveBeenCalledWith(expect.objectContaining({ detailAddress: '良渚街道88号' }));
  });

  it('parses customer identity and address from one pasted line at the top of the form', async () => {
    const fakeService = createFakeService();
    await router.push('/customers/new');
    await router.isReady();
    const wrapper = mount(CustomerFormView, { global: { plugins: [router], provide: { masterdataService: fakeService, regionService: mockRegionService } } });
    await flushPromises();

    const form = wrapper.get('form');
    const parseInput = wrapper.get('[data-testid="customer-parse-address"]');
    await parseInput.setValue('杭州酒店用品店 张经理 13800138000 浙江省杭州市余杭区良渚街道88号');
    await wrapper.get('[data-testid="parse-address"]').trigger('click');
    await flushPromises();

    expect(form.find('[data-testid="customer-parse-address"]').exists()).toBe(true);
    expect(form.find('[data-testid="customer-parse-address"]').element.compareDocumentPosition(form.find('[data-testid="customer-name"]').element) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
    expect((wrapper.get('[data-testid="customer-name"]').element as HTMLInputElement).value).toBe('杭州酒店用品店');
    expect((wrapper.get('[data-testid="customer-contact"]').element as HTMLInputElement).value).toBe('张经理');
    expect((wrapper.get('[data-testid="customer-mobile"]').element as HTMLInputElement).value).toBe('13800138000');
  });

  it('loads cascading region options when editing an existing customer', async () => {
    const fakeService = createFakeService();
    vi.mocked(fakeService.listCustomers).mockResolvedValue({
      records: [{ id: 5, customerNo: 'CUS-OLD-001', customerName: '杭州酒店', contactPerson: '', mobile: '', telephone: '', province: '浙江省', city: '杭州市', district: '余杭区', detailAddress: '', transportMethod: 'delivery', settlementCycle: 'monthly', system: false, status: 'enabled', remark: '' }],
      page: 1,
      pageSize: 20,
      total: 1
    });
    localStorage.setItem(ACCESS_TOKEN_STORAGE_KEY, 'test-token');
    await router.push('/customers/5/edit');
    await router.isReady();
    const wrapper = mount(CustomerFormView, { global: { plugins: [router], provide: { masterdataService: fakeService, regionService: mockRegionService } } });
    await flushPromises();

    expect(wrapper.find('[data-testid="customer-no"]').exists()).toBe(false);
    expect(wrapper.get('[data-testid="customer-city"]').text()).toContain('杭州市');
    expect(wrapper.get('[data-testid="customer-district"]').text()).toContain('余杭区');
  });

  it('changes a supplier status from the list', async () => {
    const fakeService = createFakeService();
    const wrapper = mount(SupplierView, { global: { provide: { masterdataService: fakeService, regionService: mockRegionService } } });
    await flushPromises();

    await wrapper.get('[data-testid="supplier-status-3"]').trigger('click');
    await flushPromises();

    expect(fakeService.changeSupplierStatus).toHaveBeenCalledWith(3, 'disabled');
  });

  it('hides generated identifier columns from masterdata lists', async () => {
    const fakeService = createFakeService();
    const categoryWrapper = mount(CategoryView, { global: { provide: { masterdataService: fakeService, regionService: mockRegionService } } });
    const customerWrapper = mount(CustomerView, { global: { plugins: [router], provide: { masterdataService: fakeService, regionService: mockRegionService } } });
    const warehouseWrapper = mountWarehouseView(fakeService);
    await flushPromises();

    expect(categoryWrapper.text()).not.toContain('分类编码');
    expect(customerWrapper.text()).not.toContain('客户编号');
    expect(warehouseWrapper.text()).not.toContain('仓库编号');
  });

  it('hides the generated supplier number and omits it from save payloads', async () => {
    const fakeService = createFakeService();
    vi.mocked(fakeService.createSupplier).mockResolvedValue({} as never);
    const wrapper = mount(SupplierView, { global: { provide: { masterdataService: fakeService, regionService: mockRegionService } } });
    await flushPromises();

    expect(wrapper.text()).not.toContain('供应商编号');
    expect(wrapper.find('[data-testid="supplier-no"]').exists()).toBe(false);

    await wrapper.get('[data-testid="add-supplier"]').trigger('click');
    expect(wrapper.text()).not.toContain('供应商编号');
    expect(wrapper.find('[data-testid="supplier-no"]').exists()).toBe(false);
    await wrapper.get('[data-testid="supplier-name"]').setValue('自动编号供应商');
    await wrapper.get('[data-testid="save-supplier"]').trigger('click');
    await flushPromises();

    const payload = vi.mocked(fakeService.createSupplier).mock.calls[0][0];
    expect(payload).not.toHaveProperty('supplierNo');
    expect(payload.supplierName).toBe('自动编号供应商');
  });

  it('marks exactly one warehouse as default', async () => {
    const fakeService = createFakeService();
    const wrapper = mountWarehouseView(fakeService);
    await flushPromises();

    await wrapper.get('[data-testid="set-default-warehouse-2"]').trigger('click');
    await flushPromises();

    expect(fakeService.setDefaultWarehouse).toHaveBeenCalledWith(2);
  });

  it('opens the warehouse canvas for the selected warehouse row', async () => {
    const fakeService = createFakeService();
    localStorage.setItem(ACCESS_TOKEN_STORAGE_KEY, 'test-token');
    await router.push('/warehouses');
    await router.isReady();
    const push = vi.spyOn(router, 'push');
    const wrapper = mountWarehouseView(fakeService);
    await flushPromises();

    await wrapper.get('[data-testid="open-warehouse-canvas-1"]').trigger('click');
    await push.mock.results[0]?.value;
    await flushPromises();

    expect(push).toHaveBeenCalledWith({ name: 'warehouse-canvas', query: { warehouseId: '1' } });
    expect(router.currentRoute.value.name).toBe('warehouse-canvas');
    expect(router.currentRoute.value.query).toEqual({ warehouseId: '1' });
    push.mockRestore();
  });

  it('uses the prototype product-list table styling for master data lists', async () => {
    const fakeService = createFakeService();
    const wrapper = mount(CategoryView, { global: { provide: { masterdataService: fakeService, regionService: mockRegionService } } });
    await flushPromises();

    const table = wrapper.get('[data-testid="masterdata-table"]');
    expect(table.classes()).toContain('rounded-2xl');
    expect(wrapper.get('thead').classes()).toContain('bg-slate-50');
    expect(wrapper.get('tbody').classes()).toContain('divide-y');
    expect(wrapper.get('tbody tr').classes()).toContain('min-h-[76px]');
    expect(wrapper.get('[data-testid="masterdata-table-pagination"]').classes()).toContain('rounded-2xl');
  });

  it('uses the prototype header and filter controls on the warehouse page', async () => {
    const fakeService = createFakeService();
    const wrapper = mountWarehouseView(fakeService);
    await flushPromises();

    expect(wrapper.get('[data-testid="masterdata-page-header"]').classes()).toContain('sm:items-end');
    expect(wrapper.get('[data-testid="add-warehouse"]').classes()).toContain('rounded-xl');
    expect(wrapper.get('[data-testid="add-warehouse"]').classes()).toContain('bg-[#536dff]');
    expect(wrapper.get('[data-testid="masterdata-filter-bar"]').classes()).toContain('rounded-[18px]');
    expect(wrapper.get('[data-testid="masterdata-search-button"]').classes()).toContain('rounded-xl');
  });

  it('keeps warehouse row actions in one line', async () => {
    const fakeService = createFakeService();
    const wrapper = mountWarehouseView(fakeService);
    await flushPromises();

    const action = wrapper.get('[data-testid="set-default-warehouse-2"]');
    expect(action.classes()).toContain('whitespace-nowrap');
    expect(action.classes()).toContain('shrink-0');
    expect(action.element.parentElement?.className).toContain('whitespace-nowrap');
  });

  it('opens a right-side drawer for the small warehouse form', async () => {
    const fakeService = createFakeService();
    const wrapper = mountWarehouseView(fakeService);
    await flushPromises();

    await wrapper.get('[data-testid="add-warehouse"]').trigger('click');

    const drawer = wrapper.get('[data-testid="masterdata-drawer"]');
    expect(drawer.classes()).toContain('fixed');
    expect(drawer.classes()).toContain('right-0');
    expect(drawer.classes()).toContain('w-[min(100vw,520px)]');
    expect(drawer.text()).toContain('新增仓库');
    expect(wrapper.find('[data-testid="warehouse-no"]').exists()).toBe(false);
  });

  it('creates a warehouse without asking the user for its number', async () => {
    const fakeService = createFakeService();
    vi.mocked(fakeService.createWarehouse).mockResolvedValue({} as never);
    const wrapper = mountWarehouseView(fakeService);
    await flushPromises();

    await wrapper.get('[data-testid="add-warehouse"]').trigger('click');
    await wrapper.get('[data-testid="warehouse-name"]').setValue('新仓库');
    await wrapper.get('[data-testid="save-warehouse"]').trigger('click');
    await flushPromises();

    const payload = vi.mocked(fakeService.createWarehouse).mock.calls[0][0];
    expect(payload).not.toHaveProperty('warehouseNo');
    expect(payload.warehouseName).toBe('新仓库');
  });

  it('uses the same right-side drawer for category and supplier forms', async () => {
    const categoryWrapper = mount(CategoryView, { global: { provide: { masterdataService: createFakeService() } } });
    const supplierWrapper = mount(SupplierView, { global: { provide: { masterdataService: createFakeService() } } });
    await flushPromises();

    await categoryWrapper.get('[data-testid="add-category"]').trigger('click');
    await supplierWrapper.get('[data-testid="add-supplier"]').trigger('click');

    expect(categoryWrapper.get('[data-testid="masterdata-drawer"]').text()).toContain('新增分类');
    expect(supplierWrapper.get('[data-testid="masterdata-drawer"]').text()).toContain('新增供应商');
  });

  it('opens the right-side drawer when editing a small master-data record', async () => {
    const categoryWrapper = mount(CategoryView, { global: { provide: { masterdataService: createFakeService() } } });
    await flushPromises();

    const editButton = categoryWrapper.findAll('button').find((button) => button.text() === '编辑');
    expect(editButton).toBeDefined();
    await editButton!.trigger('click');

    expect(categoryWrapper.get('[data-testid="masterdata-drawer"]').text()).toContain('编辑分类');
    expect((categoryWrapper.get('[data-testid="category-name"]').element as HTMLInputElement).value).toBe('水杯');
  });
});
it('keeps customer creation usable when region options are unavailable', async () => {
 const fakeService = createFakeService();
 vi.mocked(fakeService.createCustomer).mockResolvedValue({} as never);
 await router.push('/customers/new'); await router.isReady();
 const wrapper = mount(CustomerFormView, {global: {plugins: [router], provide: {masterdataService: fakeService, regionService: {listProvinces: async () => {throw new Error('地区选项暂不可用');}, listCities: vi.fn(), listDistricts: vi.fn()}}}});
 await flushPromises();
 expect(wrapper.text()).toContain('手工填写');
 for (const [field, value] of Object.entries({name:'客户', contact:'联系人',mobile:'13800138000',province:'浙江省',city:'杭州市',district:'余杭区','detail-address':'88号'})) {
 await wrapper.get(`input[data-testid="customer-${field}"]`).setValue(value);
 }
 await wrapper.get('[data-testid="customer-form-save"]').trigger('click'); await flushPromises();
 expect(fakeService.createCustomer).toHaveBeenCalledWith(expect.objectContaining({province:'浙江省',city:'杭州市',district:'余杭区'}));
 wrapper.unmount();
});
