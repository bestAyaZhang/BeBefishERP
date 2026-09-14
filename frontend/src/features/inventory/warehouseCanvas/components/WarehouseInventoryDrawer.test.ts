import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import WarehouseInventoryDrawer from './WarehouseInventoryDrawer.vue'
import { createMockWarehouseInventoryService } from '../warehouseInventoryService'
import { warehousePlannerScene } from '../warehousePlannerScene'

describe('WarehouseInventoryDrawer', () => {
  it('shows multiple actual SKUs for a selected pile in units with case conversion', async () => {
    const inventory = await createMockWarehouseInventoryService().load(8)
    const pallet = warehousePlannerScene.palletGroups.find(item => item.id === 'pallet-c018')!
    const wrapper = mount(WarehouseInventoryDrawer, { props: { inventory, pallet, zone: null, open: true, canEdit: false, saving: false, allocationError: '' } })

    expect(wrapper.get('[data-testid="inventory-detail-title"]').text()).toContain('C-018')
    expect(wrapper.findAll('[data-testid="inventory-detail-sku"]')).toHaveLength(3)
    expect(wrapper.text()).toContain('SKU-FISH-500ML-蓝')
    expect(wrapper.text()).toContain('120 个')
    expect(wrapper.text()).toContain('5 箱')
  })

  it('closes without mutating inventory', async () => {
    const inventory = await createMockWarehouseInventoryService().load(8)
    const zone = warehousePlannerScene.zones[0]!
    const wrapper = mount(WarehouseInventoryDrawer, { props: { inventory, pallet: null, zone, open: true, canEdit: false, saving: false, allocationError: '' } })
    await wrapper.get('[aria-label="关闭库存详情"]').trigger('click')
    expect(wrapper.emitted('close')).toHaveLength(1)
  })

  it('shows 添加 SKU only for an editable selected pile', async () => {
    const inventory = await createMockWarehouseInventoryService().load(8)
    const pallet = warehousePlannerScene.palletGroups.find(item => item.id === 'pallet-a03')!

    const viewOnly = mount(WarehouseInventoryDrawer, {
      props: { inventory, pallet, zone: null, open: true, canEdit: false, saving: false, allocationError: '' },
    })
    const editable = mount(WarehouseInventoryDrawer, {
      props: { inventory, pallet, zone: null, open: true, canEdit: true, saving: false, allocationError: '' },
    })

    expect(viewOnly.find('[data-testid="inventory-add-sku"]').exists()).toBe(false)
    expect(editable.find('[data-testid="inventory-add-sku"]').exists()).toBe(true)
  })

  it('never shows 添加 SKU for a selected zone', async () => {
    const inventory = await createMockWarehouseInventoryService().load(8)
    const zone = warehousePlannerScene.zones[0]!
    const wrapper = mount(WarehouseInventoryDrawer, {
      props: { inventory, pallet: null, zone, open: true, canEdit: true, saving: false, allocationError: '' },
    })

    expect(wrapper.find('[data-testid="inventory-add-sku"]').exists()).toBe(false)
  })

  it('allocates whole unallocated units from the selected SKU', async () => {
    const inventory = await createMockWarehouseInventoryService().load(8)
    const pallet = warehousePlannerScene.palletGroups.find(item => item.id === 'pallet-a03')!
    const wrapper = mount(WarehouseInventoryDrawer, {
      props: { inventory, pallet, zone: null, open: true, canEdit: true, saving: false, allocationError: '' },
    })

    await wrapper.get('[data-testid="inventory-add-sku"]').trigger('click')

    expect(wrapper.get('[data-testid="inventory-allocation-sku"]').text()).toContain('SKU-PENDING-001')
    expect(wrapper.text()).toContain('可分配 72 个')
    expect(wrapper.text()).toContain('12 个/箱（仅供参考）')

    await wrapper.get('[data-testid="inventory-allocation-units"]').setValue('24')
    await wrapper.get('[data-testid="inventory-allocation-form"]').trigger('submit')

    expect(wrapper.emitted('allocate')?.[0]).toEqual([{
      palletId: 'pallet-a03', skuId: 104, units: 24,
    }])
  })

  it.each(['0', '-1', '1.5', '73', ''])('keeps invalid quantity %s from submitting', async units => {
    const inventory = await createMockWarehouseInventoryService().load(8)
    const pallet = warehousePlannerScene.palletGroups.find(item => item.id === 'pallet-a03')!
    const wrapper = mount(WarehouseInventoryDrawer, {
      props: { inventory, pallet, zone: null, open: true, canEdit: true, saving: false, allocationError: '' },
    })
    await wrapper.get('[data-testid="inventory-add-sku"]').trigger('click')
    await wrapper.get('[data-testid="inventory-allocation-units"]').setValue(units)

    expect(wrapper.get('[data-testid="inventory-allocation-submit"]').attributes('disabled')).toBeDefined()
    expect(wrapper.get('[data-testid="inventory-allocation-validation"]').text()).toBe('请输入不超过可分配库存的正整数个数')
    expect(wrapper.emitted('allocate')).toBeUndefined()
  })

  it('keeps the allocation form open and disables every control while saving', async () => {
    const inventory = await createMockWarehouseInventoryService().load(8)
    const pallet = warehousePlannerScene.palletGroups.find(item => item.id === 'pallet-a03')!
    const wrapper = mount(WarehouseInventoryDrawer, {
      props: { inventory, pallet, zone: null, open: true, canEdit: true, saving: false, allocationError: '' },
    })
    await wrapper.get('[data-testid="inventory-add-sku"]').trigger('click')
    await wrapper.setProps({ saving: true, allocationError: '库存已变更，请重试' })

    expect(wrapper.get('[data-testid="inventory-allocation-sku"]').attributes('disabled')).toBeDefined()
    expect(wrapper.get('[data-testid="inventory-allocation-units"]').attributes('disabled')).toBeDefined()
    expect(wrapper.get('[data-testid="inventory-allocation-submit"]').attributes('disabled')).toBeDefined()
    expect(wrapper.text()).toContain('库存已变更，请重试')
    expect(wrapper.find('[data-testid="inventory-allocation-form"]').exists()).toBe(true)
  })

  it('disables 添加 SKU when there is no unallocated inventory', async () => {
    const inventory = await createMockWarehouseInventoryService().load(8)
    inventory.allocations = inventory.allocations.filter(item => item.palletId !== 'UNALLOCATED')
    const pallet = warehousePlannerScene.palletGroups.find(item => item.id === 'pallet-a03')!
    const wrapper = mount(WarehouseInventoryDrawer, {
      props: { inventory, pallet, zone: null, open: true, canEdit: true, saving: false, allocationError: '' },
    })

    expect(wrapper.get('[data-testid="inventory-add-sku"]').attributes('disabled')).toBeDefined()
    expect(wrapper.text()).toContain('暂无待分配库存')
  })

  it.each(['success', 'close', 'pile'])('resets form and quantity on %s', async action => {
    const inventory = await createMockWarehouseInventoryService().load(8)
    const pallet = warehousePlannerScene.palletGroups.find(item => item.id === 'pallet-a03')!
    const wrapper = mount(WarehouseInventoryDrawer, {
      props: { inventory, pallet, zone: null, open: true, canEdit: true },
    })
    await wrapper.get('[data-testid="inventory-add-sku"]').trigger('click')
    await wrapper.get('[data-testid="inventory-allocation-units"]').setValue('24')
    if (action === 'success') await wrapper.setProps({ allocationSuccessGeneration: 1 })
    else if (action === 'close') {
      await wrapper.setProps({ open: false })
      await wrapper.setProps({ open: true })
    } else await wrapper.setProps({ pallet: warehousePlannerScene.palletGroups.find(item => item.id === 'pallet-c018')! })

    expect(wrapper.find('[data-testid="inventory-allocation-form"]').exists()).toBe(false)
    await wrapper.get('[data-testid="inventory-add-sku"]').trigger('click')
    expect((wrapper.get('[data-testid="inventory-allocation-units"]').element as HTMLInputElement).value).toBe('')
  })

  it('preserves quantity and form for a failed request in the same context', async () => {
    const inventory = await createMockWarehouseInventoryService().load(8)
    const pallet = warehousePlannerScene.palletGroups.find(item => item.id === 'pallet-a03')!
    const wrapper = mount(WarehouseInventoryDrawer, { props: { inventory, pallet, zone: null, open: true, canEdit: true } })
    await wrapper.get('[data-testid="inventory-add-sku"]').trigger('click')
    await wrapper.get('[data-testid="inventory-allocation-units"]').setValue('24')
    await wrapper.setProps({ saving: true })
    await wrapper.setProps({ saving: false, allocationError: '分配失败，请重试' })
    expect((wrapper.get('[data-testid="inventory-allocation-units"]').element as HTMLInputElement).value).toBe('24')
    expect(wrapper.get('[role="alert"]').text()).toContain('分配失败，请重试')
  })
})
