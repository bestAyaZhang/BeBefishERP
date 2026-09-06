import { flushPromises, mount } from '@vue/test-utils'
import { afterEach, describe, expect, it } from 'vitest'
import WarehouseCanvasView from './views/WarehouseCanvasView.vue'
import WarehouseFloorCanvas from './warehouseCanvas/components/WarehouseFloorCanvas.vue'
import WarehouseBlueprintScene from './warehouseCanvas/components/WarehouseBlueprintScene.vue'
import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { parse } from '@vue/compiler-sfc'
import { routeLocationKey } from 'vue-router'

const mounted: ReturnType<typeof mount>[] = []
const styleElements: HTMLStyleElement[] = []
function applyPageStyles() {
  const { descriptor } = parse(readFileSync(resolve(process.cwd(), 'src/features/inventory/views/WarehouseCanvasView.vue'), 'utf8'))
  const style = document.createElement('style')
  style.textContent = descriptor.styles.map((item) => item.content).join('\n')
  document.head.append(style)
  styleElements.push(style)
}
async function mountPage(routeQuery: Record<string, string> = {}) {
  const wrapper = mount(WarehouseCanvasView, {
    attachTo: document.body,
    global: { provide: { [routeLocationKey as symbol]: { query: routeQuery } } },
  })
  mounted.push(wrapper)
  await flushPromises()
  return wrapper
}
afterEach(() => { mounted.splice(0).forEach((wrapper) => wrapper.unmount()); styleElements.splice(0).forEach((style) => style.remove()) })
type Page = Awaited<ReturnType<typeof mountPage>>
function floor(wrapper: Page) { return wrapper.getComponent(WarehouseFloorCanvas) }
async function selectBlock(wrapper: Page, id = 'block-blue-a') { floor(wrapper).vm.$emit('select-block', id); await flushPromises() }
async function selectArea(wrapper: Page, id = 'area-a') { floor(wrapper).vm.$emit('select-area', id); await flushPromises() }
function selectedBlock(wrapper: Page) { return floor(wrapper).props('state').blocks.find((block) => block.id === floor(wrapper).props('selectedBlockId')) }

describe('Warehouse canvas overview', () => {
  it('renders the professional planner and drives UI-only toggles', async () => {
    applyPageStyles()
    const wrapper = await mountPage()

    expect(wrapper.get('[data-testid="planner-title"]').text()).toContain('一号仓 · 平面规划')
    expect(getComputedStyle(wrapper.get('[data-testid="warehouse-canvas-view"]').element).height).toBe('100%')
    expect(wrapper.get('.planner-prototype').attributes('data-fullscreen')).toBe('true')
    expect(getComputedStyle(wrapper.get('.planner-prototype').element).height).toBe('100%')
    expect(wrapper.get('[data-testid="planner-tool-goods"]').attributes('aria-pressed')).toBe('true')

    await wrapper.get('[data-testid="planner-measure-toggle"]').trigger('click')
    await wrapper.get('[data-testid="planner-grid-toggle"]').trigger('click')
    expect(wrapper.get('[data-testid="warehouse-blueprint-scene"]').attributes('data-measuring')).toBe('true')
    expect(wrapper.get('[data-testid="warehouse-blueprint-scene"]').attributes('data-grid-snapping')).toBe('false')

    expect(wrapper.find('.scene-inspector').exists()).toBe(false)
    await wrapper.get('[data-testid="planner-complete"]').trigger('click')
    expect(wrapper.get('[role="status"]').text()).toContain('点击货堆查看详情')
    expect(wrapper.get('[data-testid="planner-complete"]').text()).toContain('返回规划')
    expect(wrapper.get('.scene-inspector').text()).toContain('SKU-FISH-500ML-蓝')

    await wrapper.get('[data-testid="planner-complete"]').trigger('click')
    expect(wrapper.find('.scene-inspector').exists()).toBe(false)
  })

  it('undoes and redoes visible planner changes', async () => {
    const wrapper = await mountPage()

    expect(wrapper.get('[data-testid="planner-undo"]').attributes('disabled')).toBeDefined()
    expect(wrapper.get('[data-testid="planner-redo"]').attributes('disabled')).toBeDefined()

    await wrapper.get('[data-testid="planner-grid-toggle"]').trigger('click')
    expect(wrapper.get('[data-testid="warehouse-blueprint-scene"]').attributes('data-grid-snapping')).toBe('false')
    expect(wrapper.get('[data-testid="planner-undo"]').attributes('disabled')).toBeUndefined()

    await wrapper.get('[data-testid="planner-undo"]').trigger('click')
    expect(wrapper.get('[data-testid="warehouse-blueprint-scene"]').attributes('data-grid-snapping')).toBe('true')
    expect(wrapper.get('[data-testid="planner-redo"]').attributes('disabled')).toBeUndefined()

    await wrapper.get('[data-testid="planner-redo"]').trigger('click')
    expect(wrapper.get('[data-testid="warehouse-blueprint-scene"]').attributes('data-grid-snapping')).toBe('false')
  })

  it('stores pile movement in planner history without changing product quantities', async () => {
    const wrapper = await mountPage()
    const scene = wrapper.getComponent(WarehouseBlueprintScene)
    const pile = () => wrapper.get('[data-testid="planner-pallet-pallet-c018"]')

    expect(pile().attributes('style')).toContain('left: 58.5%')
    scene.vm.$emit('move-pallet', { id: 'pallet-c018', left: 60, top: 50 })
    await flushPromises()

    expect(pile().attributes('style')).toContain('left: 60%')
    expect(pile().attributes('style')).toContain('top: 50%')
    expect(pile().attributes('aria-label')).toContain('3 种商品，共 250 个')

    await wrapper.get('[data-testid="planner-undo"]').trigger('click')
    expect(pile().attributes('style')).toContain('left: 58.5%')

    await wrapper.get('[data-testid="planner-redo"]').trigger('click')
    expect(pile().attributes('style')).toContain('left: 60%')
  })

  it('shows a visible warning when a non-demo warehouse is requested', async () => {
    const wrapper = await mountPage({ warehouseId: '8' })

    expect(wrapper.get('[data-testid="planner-warehouse-notice"]').text()).toContain('仓库 ID 8')
  })

  it('shows per-area SKU counts and aggregate case conversions', async () => {
    const wrapper = await mountPage()
    expect(wrapper.get('.area-row').text()).toContain('2 个 SKU')
    expect(wrapper.get('[data-testid="warehouse-area-area-a"]').text()).toContain('2 SKU · 270 个')
    expect(wrapper.get('[data-testid="library-sku-101"]').text()).toContain('10 件 + 10 个')
  })

  it('loads the approved spatial composition without initial layout warnings', async () => {
    const wrapper = await mountPage()
    const state = floor(wrapper).props('state')
    expect(state.areas[0]).toMatchObject({ x: 16, y: 16, width: 696, height: 220 })
    expect(state.areas[1]).toMatchObject({ x: 16, y: 252, width: 340, height: 400 })
    expect(state.blocks[0]).toMatchObject({ x: 48, y: 64, width: 210, height: 135, units: 150 })
    expect(floor(wrapper).props('issues')).toHaveLength(0)
  })
  it('loads the approved inventory into the floor, summary and product library', async () => {
    const wrapper = await mountPage()
    expect(wrapper.get('[data-testid="warehouse-canvas-view"]').text()).toContain('仓库画布')
    expect(wrapper.get('[data-testid="warehouse-canvas-summary"]').text()).toContain('1,026 个库存总量')
    expect(wrapper.find('[data-testid="warehouse-canvas-floor"]').exists()).toBe(true)
    expect(wrapper.get('[data-testid="warehouse-product-library"]').text()).toContain('库存以“个”为权威值')
    expect(wrapper.getComponent(WarehouseFloorCanvas).props('state').blocks).toHaveLength(5)
    expect(wrapper.get('[data-testid="library-sku-101"]').text()).toContain('250 个')
  })

  it('keeps summary inside the 196px left panel and preserves the 760px desktop center allocation', async () => {
    applyPageStyles()
    const wrapper = await mountPage()
    expect(wrapper.get('.layers').find('[data-testid="warehouse-canvas-summary"]').exists()).toBe(true)
    const pageStyle = getComputedStyle(wrapper.element)
    const workspaceStyle = getComputedStyle(wrapper.get('.workspace').element)
    const centerStyle = getComputedStyle(wrapper.get('.floor-panel').element)
    expect(pageStyle.paddingLeft).toBe('0px')
    expect(pageStyle.paddingRight).toBe('0px')
    expect(workspaceStyle.gridTemplateColumns).toBe('196px minmax(560px,1fr) 240px')
    expect(Number.parseFloat(workspaceStyle.gap)).toBe(0)
    expect(centerStyle.paddingLeft).toBe('16px')
    expect(centerStyle.paddingRight).toBe('16px')
    expect(centerStyle.borderLeftWidth).toBe('0px')
    expect(centerStyle.borderRightWidth).toBe('0px')
    // 1440px viewport minus 244px ERP sidebar, with no outer losses.
    expect(1440 - 244 - 196 - 240 - 2 * Number.parseFloat(pageStyle.paddingLeft) - 2 * Number.parseFloat(workspaceStyle.gap)).toBe(760)
  })
})

describe('Draw and add products', () => {
  it.each(['add', 'partial'])('uses the approved accessible 720px right drawer surface for %s', async (flow) => {
    applyPageStyles()
    const wrapper = await mountPage()
    if (flow === 'partial') {
      await selectBlock(wrapper)
      await wrapper.get('[data-testid="open-partial-move"]').trigger('click')
    } else {
      await wrapper.get('[data-testid="add-warehouse-product"]').trigger('click')
    }
    const dialog = wrapper.get(`[data-testid="${flow === 'add' ? 'add-product' : 'partial-move'}-dialog"]`)
    expect(dialog.attributes('role')).toBe('dialog')
    expect(dialog.attributes('aria-modal')).toBe('true')
    const style = getComputedStyle(dialog.element)
    expect(style.position).toBe('absolute')
    expect(style.width).toBe('720px')
    expect(style.top).toBe('264px')
    expect(style.right).toBe('0px')
    expect(style.bottom).toBe('0px')
    expect(style.borderRadius).toBe('8px')
    // At the approved 1440 × 1024 viewport: x = 720 and height = 760.
    expect(1440 - Number.parseFloat(style.width) - Number.parseFloat(style.right)).toBe(720)
    expect(1024 - Number.parseFloat(style.top) - Number.parseFloat(style.bottom)).toBe(760)
    await dialog.trigger('keydown', { key: 'Escape' })
    expect(wrapper.find('[role="dialog"]').exists()).toBe(false)
  })

  it('previews a named rectangle before creating an area and supports cancellation', async () => {
    const wrapper = await mountPage()
    await wrapper.get('[data-testid="canvas-tool-draw"]').trigger('click')
    wrapper.getComponent(WarehouseFloorCanvas).vm.$emit('create-area', { x: 520, y: 24, width: 160, height: 120 })
    await flushPromises()
    expect(wrapper.get('[data-testid="area-name-dialog"]').attributes('role')).toBe('dialog')
    expect(wrapper.find('[data-testid="pending-area-preview"]').exists()).toBe(true)
    await wrapper.get('[data-testid="area-name-input"]').setValue('D-01')
    await wrapper.get('[data-testid="create-area-confirm"]').trigger('click')
    expect(wrapper.getComponent(WarehouseFloorCanvas).props('state').areas).toContainEqual(expect.objectContaining({ name: 'D-01', x: 520, y: 24, width: 160, height: 120 }))
    wrapper.getComponent(WarehouseFloorCanvas).vm.$emit('create-area', { x: 10, y: 10, width: 80, height: 60 })
    await flushPromises()
    await wrapper.get('[data-testid="create-area-cancel"]').trigger('click')
    expect(wrapper.getComponent(WarehouseFloorCanvas).props('state').areas).toHaveLength(4)
  })

  it('adds a SKU with authoritative units and derived read-only packaging to an open position', async () => {
    const wrapper = await mountPage()
    await wrapper.get('[data-testid="add-warehouse-product"]').trigger('click')
    await wrapper.get('[data-testid="add-product-area"]').setValue('area-c')
    await wrapper.get('[data-testid="add-product-units"]').setValue('250')
    expect(wrapper.get('[data-testid="add-product-case-copy"]').text()).toContain('10 件 + 10 个')
    expect(wrapper.get('[data-testid="add-product-packaging"]').attributes('readonly')).toBeDefined()
    await wrapper.get('[data-testid="add-product-confirm"]').trigger('click')
    expect(wrapper.text()).toContain('放入 C-01')
    expect(wrapper.getComponent(WarehouseFloorCanvas).props('state').blocks).toContainEqual(expect.objectContaining({ areaId: 'area-c', skuId: 101, units: 250 }))
    expect(wrapper.getComponent(WarehouseFloorCanvas).props('issues')).toHaveLength(0)
    expect(wrapper.get('[data-testid="warehouse-canvas-summary"]').text()).toContain('1,276 个库存总量')
  })

  it.each(['-1', '2.5', ''])('rejects invalid add units %s without changing inventory', async (value) => {
    const wrapper = await mountPage()
    await wrapper.get('[data-testid="add-warehouse-product"]').trigger('click')
    await wrapper.get('[data-testid="add-product-units"]').setValue(value)
    await wrapper.get('[data-testid="add-product-confirm"]').trigger('click')
    expect(wrapper.get('[data-testid="add-product-dialog"]').text()).toContain('整数')
    expect(wrapper.getComponent(WarehouseFloorCanvas).props('state').blocks).toHaveLength(5)
  })

  it('leaves state unchanged when the target area has no room', async () => {
    const wrapper = await mountPage()
    wrapper.getComponent(WarehouseFloorCanvas).vm.$emit('create-area', { x: 520, y: 500, width: 80, height: 60 })
    await flushPromises()
    await wrapper.get('[data-testid="area-name-input"]').setValue('Tiny')
    await wrapper.get('[data-testid="create-area-confirm"]').trigger('click')
    const tiny = wrapper.getComponent(WarehouseFloorCanvas).props('state').areas.find((area) => area.name === 'Tiny')!
    await wrapper.get('[data-testid="add-warehouse-product"]').trigger('click')
    await wrapper.get('[data-testid="add-product-area"]').setValue(tiny.id)
    await wrapper.get('[data-testid="add-product-confirm"]').trigger('click')
    expect(wrapper.text()).toContain('目标区域没有足够的空白位置')
    expect(wrapper.getComponent(WarehouseFloorCanvas).props('state').blocks).toHaveLength(5)
  })
})

describe('Object properties, movement and safeguards', () => {
  it('edits authoritative units, derives cases and keeps inventory independent of rectangle size', async () => {
    const wrapper = await mountPage()
    await selectBlock(wrapper)
    await wrapper.get('[data-testid="sku-units-input"]').setValue('240')
    expect(wrapper.get('[data-testid="sku-case-copy"]').text()).toContain('10 件')
    expect(wrapper.get('[data-testid="sku-case-readonly"]').attributes('readonly')).toBeDefined()
    await wrapper.get('[data-testid="block-width-input"]').setValue('130')
    expect(selectedBlock(wrapper)).toMatchObject({ units: 240, width: 130 })
    expect(wrapper.get('[data-testid="warehouse-canvas-summary"]').text()).toContain('1,116 个库存总量')
  })

  it('moves a whole block into free space without changing its inventory', async () => {
    const wrapper = await mountPage()
    await selectBlock(wrapper)
    await wrapper.get('[data-testid="sku-units-input"]').setValue('240')
    await wrapper.get('[data-testid="move-whole-area"]').setValue('area-c')
    await wrapper.get('[data-testid="move-whole-confirm"]').trigger('click')
    expect(selectedBlock(wrapper)).toMatchObject({ areaId: 'area-c', units: 240 })
    expect(floor(wrapper).props('issues')).toHaveLength(0)
    expect(wrapper.get('[data-testid="warehouse-canvas-summary"]').text()).toContain('1,116 个库存总量')
  })

  it('previews and commits a partial move with conserved inventory', async () => {
    const wrapper = await mountPage()
    await selectBlock(wrapper)
    await wrapper.get('[data-testid="sku-units-input"]').setValue('250')
    await wrapper.get('[data-testid="open-partial-move"]').trigger('click')
    await wrapper.get('[data-testid="partial-move-units"]').setValue('60')
    await wrapper.get('[data-testid="partial-move-area"]').setValue('area-c')
    expect(wrapper.get('[data-testid="partial-move-conservation"]').text()).toContain('190 + 60 = 250 个')
    await wrapper.get('[data-testid="partial-move-confirm"]').trigger('click')
    expect(floor(wrapper).props('state').blocks).toContainEqual(expect.objectContaining({ id: 'block-blue-a', units: 190, areaId: 'area-a' }))
    expect(floor(wrapper).props('state').blocks).toContainEqual(expect.objectContaining({ skuId: 101, units: 60, areaId: 'area-c' }))
    expect(wrapper.find('[data-testid="partial-move-dialog"]').exists()).toBe(false)
    expect(wrapper.get('[data-testid="warehouse-canvas-summary"]').text()).toContain('1,126 个库存总量')
  })

  it.each(['0', '-1', '1.5', '150', '151', ''])('rejects invalid partial move %s without mutation', async (value) => {
    const wrapper = await mountPage()
    await selectBlock(wrapper)
    await wrapper.get('[data-testid="open-partial-move"]').trigger('click')
    await wrapper.get('[data-testid="partial-move-units"]').setValue(value)
    expect(wrapper.get('[data-testid="partial-move-confirm"]').attributes('disabled')).toBeDefined()
    expect(wrapper.find('[data-testid="partial-move-conservation"]').exists()).toBe(false)
    await wrapper.get('[data-testid="partial-move-confirm"]').trigger('click')
    expect(wrapper.get('[data-testid="partial-move-dialog"]').text()).toContain('移动个数必须')
    expect(selectedBlock(wrapper)?.units).toBe(150)
    expect(floor(wrapper).props('state').blocks).toHaveLength(5)
  })

  it('shows both issue types, locates the affected block and blocks save until repaired', async () => {
    const wrapper = await mountPage()
    floor(wrapper).vm.$emit('update-block-rect', { id: 'block-blue-a', x: -10, y: 64, width: 400, height: 135 })
    await flushPromises()
    const warnings = wrapper.get('[data-testid="warehouse-layout-issues"]')
    expect(warnings.text()).toContain('越出')
    expect(warnings.text()).toContain('重叠')
    expect(wrapper.get('[data-testid="save-warehouse-layout"]').attributes('disabled')).toBeDefined()
    await warnings.get('[data-testid="locate-layout-issue"]').trigger('click')
    expect(selectedBlock(wrapper)?.id).toBe('block-blue-a')
    expect(document.activeElement?.getAttribute('data-testid')).toBe('warehouse-sku-block-block-blue-a')
    await wrapper.get('[data-testid="canvas-undo"]').trigger('click')
    expect(wrapper.find('[data-testid="warehouse-layout-issues"]').exists()).toBe(false)
    expect(wrapper.get('[data-testid="save-warehouse-layout"]').attributes('disabled')).toBeUndefined()
  })

  it('lets users collapse warning details to repair properties without hiding the warning count', async () => {
    const wrapper = await mountPage()
    floor(wrapper).vm.$emit('update-block-rect', { id: 'block-blue-a', x: -10, y: 64, width: 210, height: 135 })
    await flushPromises()
    await wrapper.get('[data-testid="collapse-layout-issues"]').trigger('click')
    expect(wrapper.find('[data-testid="warehouse-layout-issues"]').exists()).toBe(false)
    expect(wrapper.get('[data-testid="expand-layout-issues"]').text()).toContain('1')
    expect(wrapper.get('[data-testid="save-warehouse-layout"]').attributes('disabled')).toBeDefined()
    await wrapper.get('[data-testid="expand-layout-issues"]').trigger('click')
    await wrapper.get('[data-testid="locate-layout-issue"]').trigger('click')
    expect(selectedBlock(wrapper)?.id).toBe('block-blue-a')
    expect(wrapper.find('[data-testid="warehouse-layout-issues"]').exists()).toBe(false)
    await wrapper.get('[data-testid="block-x-input"]').setValue('48')
    expect(wrapper.find('[data-testid="expand-layout-issues"]').exists()).toBe(false)
    expect(wrapper.get('[data-testid="save-warehouse-layout"]').attributes('disabled')).toBeUndefined()
  })

  it('edits area properties, toggles layers and reconciles removed selections after undo/redo', async () => {
    const wrapper = await mountPage()
    await selectArea(wrapper)
    await wrapper.get('[data-testid="area-name-property"]').setValue('A-02')
    await wrapper.get('[data-testid="area-width-input"]').setValue('370')
    await wrapper.get('[data-testid="area-lock-property"]').trigger('click')
    expect(floor(wrapper).props('state').areas[0]).toMatchObject({ name: 'A-02', width: 370, locked: true })
    expect(wrapper.get('[data-testid="area-width-input"]').attributes('disabled')).toBeDefined()
    await wrapper.get('[data-testid="layer-visibility-area-a"]').trigger('click')
    expect(floor(wrapper).props('state').areas[0].visible).toBe(false)
    await wrapper.get('[data-testid="layer-lock-area-a"]').trigger('click')
    await selectBlock(wrapper)
    await wrapper.get('[data-testid="delete-warehouse-block"]').trigger('click')
    expect(wrapper.find('[data-testid="warehouse-block-properties"]').exists()).toBe(false)
    await wrapper.get('[data-testid="canvas-undo"]').trigger('click')
    expect(floor(wrapper).props('state').blocks).toHaveLength(5)
    await selectBlock(wrapper)
    await wrapper.get('[data-testid="canvas-redo"]').trigger('click')
    expect(wrapper.find('[data-testid="warehouse-block-properties"]').exists()).toBe(false)
    expect(floor(wrapper).props('selectedBlockId')).toBeNull()
  })

  it('requires a second confirmation to delete a non-empty area and supports moving first', async () => {
    const wrapper = await mountPage()
    await selectArea(wrapper)
    await wrapper.get('[data-testid="delete-warehouse-area"]').trigger('click')
    expect(wrapper.get('[data-testid="delete-area-dialog"]').attributes('role')).toBe('dialog')
    expect(floor(wrapper).props('state').blocks).toHaveLength(5)
    await wrapper.get('[data-testid="delete-area-move-first"]').trigger('click')
    expect(selectedBlock(wrapper)?.areaId).toBe('area-a')
    expect(wrapper.find('[data-testid="delete-area-dialog"]').exists()).toBe(false)
    await selectArea(wrapper)
    await wrapper.get('[data-testid="delete-warehouse-area"]').trigger('click')
    await wrapper.get('[data-testid="delete-area-together"]').trigger('click')
    expect(floor(wrapper).props('state').areas).toHaveLength(3)
    await wrapper.get('[data-testid="delete-area-confirm"]').trigger('click')
    expect(floor(wrapper).props('state').areas).toHaveLength(2)
    expect(floor(wrapper).props('state').blocks).toHaveLength(3)
    expect(wrapper.find('[data-testid="warehouse-area-properties"]').exists()).toBe(false)
    expect(floor(wrapper).props('selectedAreaId')).toBeNull()
  })

  it('supports search, tool selection and saving changes with undo/redo state', async () => {
    const wrapper = await mountPage()
    await wrapper.get('[data-testid="warehouse-sku-search"]').setValue('500ML')
    expect(floor(wrapper).props('searchQuery')).toBe('500ML')
    expect(wrapper.get('[data-testid="warehouse-product-library"]').findAll('article')).toHaveLength(4)
    expect(wrapper.get('[data-testid="warehouse-product-library"]').findAll('article.is-dimmed')).toHaveLength(3)
    await wrapper.get('[data-testid="canvas-tool-pan"]').trigger('click')
    expect(floor(wrapper).props('tool')).toBe('pan')
    await wrapper.get('[data-testid="canvas-tool-select"]').trigger('click')
    expect(floor(wrapper).props('tool')).toBe('select')
    await selectBlock(wrapper)
    await wrapper.get('[data-testid="sku-units-input"]').setValue('240')
    expect(wrapper.get('[data-testid="warehouse-save-state"]').text()).toContain('未保存')
    await wrapper.get('[data-testid="save-warehouse-layout"]').trigger('click')
    await flushPromises()
    expect(wrapper.get('[data-testid="warehouse-save-state"]').text()).toContain('已保存')
    await wrapper.get('[data-testid="canvas-undo"]').trigger('click')
    expect(selectedBlock(wrapper)?.units).toBe(150)
    await wrapper.get('[data-testid="canvas-redo"]').trigger('click')
    expect(selectedBlock(wrapper)?.units).toBe(240)
  })

  it('interprets a whole-block drag fully into another region as a conserved whole move', async () => {
    const wrapper = await mountPage()
    floor(wrapper).vm.$emit('update-block-rect', { id: 'block-blue-a', x: 404, y: 456, width: 210, height: 135 })
    await flushPromises()
    expect(floor(wrapper).props('state').blocks[0]).toMatchObject({ areaId: 'area-c', units: 150, x: 404, y: 456 })
    expect(floor(wrapper).props('issues')).toHaveLength(0)
  })

  it('does not transfer a selected block on zero-distance pointer-up inside an overlapping area', async () => {
    const wrapper = await mountPage()
    floor(wrapper).vm.$emit('create-area', { x: 60, y: 80, width: 160, height: 120 })
    await flushPromises()
    await wrapper.get('[data-testid="area-name-input"]').setValue('Overlapping')
    await wrapper.get('[data-testid="create-area-confirm"]').trigger('click')
    const blockElement = wrapper.get('[data-testid="warehouse-sku-block-block-blue-a"]')
    await blockElement.trigger('pointerdown', { button: 0, pointerId: 1, clientX: 100, clientY: 120 })
    await wrapper.get('[data-testid="warehouse-canvas-floor"]').trigger('pointerup', { pointerId: 1, clientX: 100, clientY: 120 })
    expect(selectedBlock(wrapper)).toMatchObject({ id: 'block-blue-a', areaId: 'area-a', x: 48, y: 64, units: 150 })
    // The selection must not add history: undo removes the overlapping area creation.
    await wrapper.get('[data-testid="canvas-undo"]').trigger('click')
    expect(floor(wrapper).props('state').areas).toHaveLength(3)
  })

  it('opens the dragged catalog SKU at its dropped area, and ignores drops outside areas', async () => {
    const wrapper = await mountPage()
    const transfer = { setData: (_type: string, _value: string) => {}, getData: () => '104', effectAllowed: '' }
    await wrapper.get('[data-testid="library-sku-104"]').trigger('dragstart', { dataTransfer: transfer })
    await wrapper.get('[data-testid="warehouse-area-area-b"]').trigger('drop', { dataTransfer: transfer })
    expect((wrapper.get('[data-testid="add-product-sku"]').element as HTMLSelectElement).value).toBe('104')
    expect((wrapper.get('[data-testid="add-product-area"]').element as HTMLSelectElement).value).toBe('area-b')
    await wrapper.get('[data-testid="add-product-cancel"]').trigger('click')
    await wrapper.get('[data-testid="warehouse-canvas-floor"]').trigger('drop', { dataTransfer: transfer })
    expect(wrapper.find('[data-testid="add-product-dialog"]').exists()).toBe(false)
    expect(floor(wrapper).props('state').blocks).toHaveLength(5)
  })

  it('zooms the canvas viewport and pans by scrolling without changing model geometry', async () => {
    const wrapper = await mountPage()
    await wrapper.get('[data-testid="canvas-zoom"]').setValue('125')
    expect(wrapper.get('[data-testid="canvas-scaled-floor"]').attributes('style')).toContain('125%')
    await wrapper.get('[data-testid="canvas-tool-pan"]').trigger('click')
    const viewport = wrapper.get('[data-testid="canvas-viewport"]')
    viewport.element.scrollLeft = 100
    await viewport.trigger('pointerdown', { button: 0, pointerId: 1, clientX: 100, clientY: 50 })
    await viewport.trigger('pointermove', { pointerId: 1, clientX: 80, clientY: 40 })
    await viewport.trigger('pointerup', { pointerId: 1 })
    expect(viewport.element.scrollLeft).toBe(120)
    expect(floor(wrapper).props('state').blocks[0]).toMatchObject({ x: 48, y: 64 })
  })
})
