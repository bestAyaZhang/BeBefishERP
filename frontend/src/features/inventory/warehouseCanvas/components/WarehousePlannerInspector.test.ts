import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import { warehousePlannerScene } from '../warehousePlannerScene'
import WarehousePlannerInspector from './WarehousePlannerInspector.vue'

describe('WarehousePlannerInspector', () => {
  it('shows every product in the pile instead of geometry details', () => {
    const pallet = warehousePlannerScene.palletGroups.find((item) => item.id === 'pallet-c018')!
    const wrapper = mount(WarehousePlannerInspector, { props: { pallet } })

    expect(wrapper.text()).toContain('地面货堆 C-018')
    expect(wrapper.get('[data-testid="planner-inspector-summary"]').text()).toContain('3 种商品')
    expect(wrapper.get('[data-testid="planner-inspector-summary"]').text()).toContain('共 250 个')
    expect(wrapper.findAll('[data-testid="planner-inspector-product"]')).toHaveLength(3)
    expect(wrapper.text()).toContain('深海矿物水 500ml 蓝')
    expect(wrapper.text()).toContain('SKU-FISH-500ML-蓝')
    expect(wrapper.text()).toContain('120 个')
    expect(wrapper.text()).toContain('5 件')
    expect(wrapper.text()).toContain('12oz 冷饮杯')
    expect(wrapper.text()).toContain('80 个')
    expect(wrapper.text()).toContain('1 件 + 30 个')
    expect(wrapper.text()).toContain('茉莉绿茶 1L')
    expect(wrapper.text()).toContain('50 个')
    expect(wrapper.text()).toContain('2 件 + 10 个')
    expect(wrapper.find('[data-testid="planner-inspector-geometry"]').exists()).toBe(false)
    expect(wrapper.text()).not.toContain('32.4m')
    expect(wrapper.text()).not.toContain('90°')
    expect(wrapper.get('[data-testid="planner-inspector-more"]').attributes('disabled')).toBeDefined()
  })

  it('emits close from its accessible dismiss action', async () => {
    const pallet = warehousePlannerScene.palletGroups.find((item) => item.id === 'pallet-c018')!
    const wrapper = mount(WarehousePlannerInspector, { props: { pallet } })

    await wrapper.get('[data-testid="planner-inspector-close"]').trigger('click')
    expect(wrapper.emitted('close')).toHaveLength(1)
  })
})
