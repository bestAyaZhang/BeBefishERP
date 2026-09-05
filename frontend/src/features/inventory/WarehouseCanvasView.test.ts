import { flushPromises, mount } from '@vue/test-utils'
import { afterEach, describe, expect, it } from 'vitest'
import WarehouseCanvasView from './views/WarehouseCanvasView.vue'
import WarehouseFloorCanvas from './warehouseCanvas/components/WarehouseFloorCanvas.vue'

const mounted: ReturnType<typeof mount>[] = []
async function mountPage() {
  const wrapper = mount(WarehouseCanvasView, { attachTo: document.body })
  mounted.push(wrapper)
  await flushPromises()
  return wrapper
}
afterEach(() => { mounted.splice(0).forEach((wrapper) => wrapper.unmount()) })
type Page = Awaited<ReturnType<typeof mountPage>>
function floor(wrapper: Page) { return wrapper.getComponent(WarehouseFloorCanvas) }
async function selectBlock(wrapper: Page, id = 'block-blue-a') { floor(wrapper).vm.$emit('select-block', id); await flushPromises() }
async function selectArea(wrapper: Page, id = 'area-a') { floor(wrapper).vm.$emit('select-area', id); await flushPromises() }
function selectedBlock(wrapper: Page) { return floor(wrapper).props('state').blocks.find((block) => block.id === floor(wrapper).props('selectedBlockId')) }

describe('Warehouse canvas overview', () => {
  it('loads the approved inventory into the floor, summary and product library', async () => {
    const wrapper = await mountPage()
    expect(wrapper.get('[data-testid="warehouse-canvas-view"]').text()).toContain('仓库画布')
    expect(wrapper.get('[data-testid="warehouse-canvas-summary"]').text()).toContain('1,026 个库存总量')
    expect(wrapper.find('[data-testid="warehouse-canvas-floor"]').exists()).toBe(true)
    expect(wrapper.get('[data-testid="warehouse-product-library"]').text()).toContain('库存以“个”为权威值')
    expect(wrapper.getComponent(WarehouseFloorCanvas).props('state').blocks).toHaveLength(5)
    expect(wrapper.get('[data-testid="library-sku-101"]').text()).toContain('250 个')
  })
})

describe('Draw and add products', () => {
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
    await wrapper.get('[data-testid="add-product-area"]').setValue('area-b')
    await wrapper.get('[data-testid="add-product-units"]').setValue('250')
    expect(wrapper.get('[data-testid="add-product-case-copy"]').text()).toContain('10 件 + 10 个')
    expect(wrapper.get('[data-testid="add-product-packaging"]').attributes('readonly')).toBeDefined()
    await wrapper.get('[data-testid="add-product-confirm"]').trigger('click')
    expect(wrapper.text()).toContain('放入 B-01')
    expect(wrapper.getComponent(WarehouseFloorCanvas).props('state').blocks).toContainEqual(expect.objectContaining({ areaId: 'area-b', skuId: 101, units: 250 }))
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
    await wrapper.get('[data-testid="move-whole-area"]').setValue('area-b')
    await wrapper.get('[data-testid="move-whole-confirm"]').trigger('click')
    expect(selectedBlock(wrapper)).toMatchObject({ areaId: 'area-b', units: 240 })
    expect(floor(wrapper).props('issues')).toHaveLength(0)
    expect(wrapper.get('[data-testid="warehouse-canvas-summary"]').text()).toContain('1,116 个库存总量')
  })

  it('previews and commits a partial move with conserved inventory', async () => {
    const wrapper = await mountPage()
    await selectBlock(wrapper)
    await wrapper.get('[data-testid="sku-units-input"]').setValue('250')
    await wrapper.get('[data-testid="open-partial-move"]').trigger('click')
    await wrapper.get('[data-testid="partial-move-units"]').setValue('60')
    await wrapper.get('[data-testid="partial-move-area"]').setValue('area-b')
    expect(wrapper.get('[data-testid="partial-move-conservation"]').text()).toContain('190 + 60 = 250 个')
    await wrapper.get('[data-testid="partial-move-confirm"]').trigger('click')
    expect(floor(wrapper).props('state').blocks).toContainEqual(expect.objectContaining({ id: 'block-blue-a', units: 190, areaId: 'area-a' }))
    expect(floor(wrapper).props('state').blocks).toContainEqual(expect.objectContaining({ skuId: 101, units: 60, areaId: 'area-b' }))
    expect(wrapper.find('[data-testid="partial-move-dialog"]').exists()).toBe(false)
    expect(wrapper.get('[data-testid="warehouse-canvas-summary"]').text()).toContain('1,126 个库存总量')
  })

  it.each(['0', '-1', '1.5', '150', '151', ''])('rejects invalid partial move %s without mutation', async (value) => {
    const wrapper = await mountPage()
    await selectBlock(wrapper)
    await wrapper.get('[data-testid="open-partial-move"]').trigger('click')
    await wrapper.get('[data-testid="partial-move-units"]').setValue(value)
    await wrapper.get('[data-testid="partial-move-confirm"]').trigger('click')
    expect(wrapper.get('[data-testid="partial-move-dialog"]').text()).toContain('移动个数必须')
    expect(selectedBlock(wrapper)?.units).toBe(150)
    expect(floor(wrapper).props('state').blocks).toHaveLength(5)
  })

  it('shows both issue types, locates the affected block and blocks save until repaired', async () => {
    const wrapper = await mountPage()
    floor(wrapper).vm.$emit('update-block-rect', { id: 'block-blue-a', x: -10, y: 96, width: 280, height: 80 })
    await flushPromises()
    const warnings = wrapper.get('[data-testid="warehouse-layout-issues"]')
    expect(warnings.text()).toContain('越出')
    expect(warnings.text()).toContain('重叠')
    expect(wrapper.get('[data-testid="save-warehouse-layout"]').attributes('disabled')).toBeDefined()
    await warnings.get('button').trigger('click')
    expect(selectedBlock(wrapper)?.id).toBe('block-blue-a')
    expect(document.activeElement?.getAttribute('data-testid')).toBe('warehouse-sku-block-block-blue-a')
    await wrapper.get('[data-testid="canvas-undo"]').trigger('click')
    expect(wrapper.find('[data-testid="warehouse-layout-issues"]').exists()).toBe(false)
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
    await wrapper.get('[data-testid="warehouse-sku-search"]').setValue('BLUE')
    expect(floor(wrapper).props('searchQuery')).toBe('BLUE')
    expect(wrapper.get('[data-testid="warehouse-product-library"]').findAll('article')).toHaveLength(1)
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
    floor(wrapper).vm.$emit('update-block-rect', { id: 'block-blue-a', x: 40, y: 360, width: 120, height: 80 })
    await flushPromises()
    expect(floor(wrapper).props('state').blocks[0]).toMatchObject({ areaId: 'area-b', units: 150, x: 40, y: 360 })
    expect(floor(wrapper).props('issues')).toHaveLength(0)
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
    expect(floor(wrapper).props('state').blocks[0]).toMatchObject({ x: 76, y: 96 })
  })
})
