import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import WarehouseBlueprintScene from './WarehouseBlueprintScene.vue'

describe('WarehouseBlueprintScene', () => {
  it('renders the reference layers and selects floor-stacked goods', async () => {
    const wrapper = mount(WarehouseBlueprintScene, {
      props: {
        gridSnapping: true,
        measurementEnabled: false,
        selectedPalletId: 'pallet-c018',
      },
    })

    expect(wrapper.findAll('[data-testid^="planner-loading-door-"]')).toHaveLength(3)
    expect(wrapper.text()).toContain('收货区')
    expect(wrapper.text()).toContain('暂存区')
    expect(wrapper.text()).toContain('发货区')
    expect(wrapper.text()).toContain('叉车通道 4.0m')
    expect(wrapper.text()).toContain('消防留空区')
    expect(wrapper.get('[data-testid="planner-pallet-pallet-c018"]').attributes('data-selected')).toBe('true')
    expect(wrapper.find('[data-testid="planner-minimap"]').exists()).toBe(true)
    expect(wrapper.get('[data-testid="planner-coordinate-status"]').text()).toContain('比例  1:100')

    await wrapper.get('[data-testid="planner-pallet-pallet-a01"]').trigger('click')
    expect(wrapper.emitted('select-pallet')?.[0]).toEqual(['pallet-a01'])
  })

  it('exposes measuring and grid state without changing the warehouse fixture', async () => {
    const wrapper = mount(WarehouseBlueprintScene, {
      props: {
        gridSnapping: false,
        measurementEnabled: true,
        selectedPalletId: 'pallet-c018',
      },
    })

    const scene = wrapper.get('[data-testid="warehouse-blueprint-scene"]')
    expect(scene.attributes('data-measuring')).toBe('true')
    expect(scene.attributes('data-grid-snapping')).toBe('false')
    expect(wrapper.findAll('[data-testid^="planner-measurement-"]').length).toBeGreaterThanOrEqual(2)
    expect(wrapper.findAll('[data-testid^="planner-pallet-"]').length).toBeGreaterThanOrEqual(18)
  })
})
