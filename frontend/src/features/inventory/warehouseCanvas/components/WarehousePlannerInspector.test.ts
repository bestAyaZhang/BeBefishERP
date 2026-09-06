import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import { warehousePlannerScene } from '../warehousePlannerScene'
import WarehousePlannerInspector from './WarehousePlannerInspector.vue'

describe('WarehousePlannerInspector', () => {
  it('shows authoritative units and derived cases', () => {
    const pallet = warehousePlannerScene.palletGroups.find((item) => item.id === 'pallet-c018')!
    const wrapper = mount(WarehousePlannerInspector, { props: { pallet } })

    expect(wrapper.text()).toContain('地面货堆 C-018')
    expect(wrapper.get('[data-testid="planner-inspector-units"]').text()).toBe('250 个')
    expect(wrapper.get('[data-testid="planner-inspector-cases"]').text()).toBe('10 件 + 10 个')
    expect(wrapper.text()).toContain('32.4m')
    expect(wrapper.text()).toContain('90°')
  })

  it('emits close from its accessible dismiss action', async () => {
    const pallet = warehousePlannerScene.palletGroups.find((item) => item.id === 'pallet-c018')!
    const wrapper = mount(WarehousePlannerInspector, { props: { pallet } })

    await wrapper.get('[data-testid="planner-inspector-close"]').trigger('click')
    expect(wrapper.emitted('close')).toHaveLength(1)
  })
})
