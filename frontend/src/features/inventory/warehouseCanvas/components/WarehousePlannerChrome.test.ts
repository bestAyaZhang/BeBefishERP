import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import WarehousePlannerChrome from './WarehousePlannerChrome.vue'

describe('WarehousePlannerChrome', () => {
  it('shows the planning tools and emits explicit actions', async () => {
    const wrapper = mount(WarehousePlannerChrome, {
      props: {
        warehouseName: '一号仓',
        activeTool: 'goods',
        measurementEnabled: false,
        gridSnapping: true,
        canUndo: true,
        canRedo: false,
      },
    })

    expect(wrapper.get('[data-testid="planner-title"]').text()).toBe('一号仓 · 平面规划')
    expect(wrapper.get('[data-testid="planner-tool-goods"]').attributes('aria-pressed')).toBe('true')

    await wrapper.get('[data-testid="planner-tool-zone"]').trigger('click')
    await wrapper.get('[data-testid="planner-measure-toggle"]').trigger('click')
    await wrapper.get('[data-testid="planner-grid-toggle"]').trigger('click')
    await wrapper.get('[data-testid="planner-complete"]').trigger('click')

    expect(wrapper.emitted('change-tool')?.[0]).toEqual(['zone'])
    expect(wrapper.emitted('toggle-measurement')).toHaveLength(1)
    expect(wrapper.emitted('toggle-grid')).toHaveLength(1)
    expect(wrapper.emitted('complete')).toHaveLength(1)
  })

  it('disables unavailable history actions while leaving undo active', () => {
    const wrapper = mount(WarehousePlannerChrome, {
      props: {
        warehouseName: '一号仓',
        activeTool: 'structure',
        measurementEnabled: true,
        gridSnapping: false,
        canUndo: true,
        canRedo: false,
      },
    })

    expect(wrapper.get('[data-testid="planner-undo"]').attributes('disabled')).toBeUndefined()
    expect(wrapper.get('[data-testid="planner-redo"]').attributes('disabled')).toBeDefined()
    expect(wrapper.get('[data-testid="planner-measure-toggle"]').attributes('aria-pressed')).toBe('true')
    expect(wrapper.get('[data-testid="planner-grid-toggle"]').attributes('aria-pressed')).toBe('false')
  })
})
