import { flushPromises, mount } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'
import { afterEach, describe, expect, it } from 'vitest'
import WarehouseCanvasView from './views/WarehouseCanvasView.vue'
import WarehouseLayerTree from './warehouseLayout/components/WarehouseLayerTree.vue'
import WarehouseLayoutCanvas from './warehouseLayout/components/WarehouseLayoutCanvas.vue'
import WarehouseObjectInspector from './warehouseLayout/components/WarehouseObjectInspector.vue'

const mounted: ReturnType<typeof mount>[] = []

async function mountPage(warehouseId = 9) {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [{ path: '/inventory/warehouse-canvas', name: 'warehouse-canvas', component: WarehouseCanvasView }],
  })
  await router.push({ name: 'warehouse-canvas', query: { warehouseId: String(warehouseId) } })
  await router.isReady()
  const wrapper = mount(WarehouseCanvasView, { attachTo: document.body, global: { plugins: [router] } })
  mounted.push(wrapper)
  await flushPromises()
  return wrapper
}

afterEach(() => mounted.splice(0).forEach((wrapper) => wrapper.unmount()))

describe('restored warehouse layout editor', () => {
  it('loads the selected warehouse and restores the completed draft-editor copy', async () => {
    const wrapper = await mountPage(9)

    expect(wrapper.get('[data-testid="warehouse-layout-view"]').text()).toContain('仓库布局编辑')
    expect(wrapper.text()).toContain('义乌备货仓')
    expect(wrapper.text()).toContain('WH-YW-SPARE')
    expect(wrapper.text()).toContain('草稿 v3')
    expect(wrapper.text()).not.toContain('当前仅支持演示仓库')
    expect(wrapper.text()).not.toContain('库存仅以“个”为权威值')
  })

  it('renders the original three-column editor composition', async () => {
    const wrapper = await mountPage(8)

    expect(wrapper.findComponent(WarehouseLayerTree).exists()).toBe(true)
    expect(wrapper.findComponent(WarehouseLayoutCanvas).exists()).toBe(true)
    expect(wrapper.findComponent(WarehouseObjectInspector).exists()).toBe(true)
    expect(wrapper.get('[data-testid="warehouse-layer-tree"]').text()).toContain('可编辑图层')
    expect(wrapper.get('[data-testid="warehouse-layout-canvas"]').attributes('aria-label')).toContain('杭州主仓')
    expect(wrapper.get('[data-testid="warehouse-object-inspector"]').text()).toContain('对象属性')
  })

  it('shows the layout-authoring toolbar and draft actions from Figma', async () => {
    const wrapper = await mountPage(8)

    for (const testId of [
      'layout-tool-select', 'layout-tool-pan', 'layout-tool-fixed-zone', 'layout-tool-free-zone',
      'layout-tool-aisle', 'layout-tool-obstacle', 'layout-tool-location', 'layout-undo', 'layout-redo',
      'save-layout-draft', 'discard-layout-draft', 'validate-layout',
    ]) expect(wrapper.find(`[data-testid="${testId}"]`).exists()).toBe(true)
  })

  it('keeps layer, canvas and inspector selection synchronized', async () => {
    const wrapper = await mountPage(8)

    await wrapper.get('[data-testid="layout-object-location-a13"]').trigger('click')

    expect(wrapper.get('[data-testid="layout-layer-location-a13"]').attributes('aria-pressed')).toBe('true')
    expect(wrapper.get('[data-testid="layout-object-location-a13"]').attributes('aria-pressed')).toBe('true')
    expect(wrapper.get('[data-testid="warehouse-object-inspector"]').text()).toContain('A-13')
    expect((wrapper.get('[data-testid="layout-object-name"]').element as HTMLInputElement).value).toBe('成品定位 A-13')
  })

  it('edits selected-object geometry and supports undo and redo', async () => {
    const wrapper = await mountPage(8)
    await wrapper.get('[data-testid="layout-object-location-a13"]').trigger('click')

    await wrapper.get('[data-testid="layout-object-x"]').setValue('86')
    await wrapper.get('[data-testid="layout-object-y"]').setValue('56')
    expect((wrapper.get('[data-testid="layout-object-x"]').element as HTMLInputElement).value).toBe('86')
    expect(wrapper.get('[data-testid="layout-dirty-state"]').text()).toContain('未保存')

    await wrapper.get('[data-testid="layout-undo"]').trigger('click')
    expect((wrapper.get('[data-testid="layout-object-y"]').element as HTMLInputElement).value).toBe('43')
    await wrapper.get('[data-testid="layout-redo"]').trigger('click')
    expect((wrapper.get('[data-testid="layout-object-y"]').element as HTMLInputElement).value).toBe('56')
  })

  it('toggles layer visibility and lock state from the layer tree', async () => {
    const wrapper = await mountPage(8)

    await wrapper.get('[data-testid="layout-layer-visibility-zone-a"]').trigger('click')
    expect(wrapper.find('[data-testid="layout-object-zone-a"]').exists()).toBe(false)

    await wrapper.get('[data-testid="layout-layer-lock-zone-b"]').trigger('click')
    expect(wrapper.get('[data-testid="layout-layer-lock-zone-b"]').attributes('aria-pressed')).toBe('true')
  })

  it('shows a clear error for an unknown warehouse instead of fallback data', async () => {
    const wrapper = await mountPage(404)

    expect(wrapper.get('[role="alert"]').text()).toContain('未找到仓库布局（ID 404）')
    expect(wrapper.find('[data-testid="warehouse-layout-canvas"]').exists()).toBe(false)
  })
})
