import { mount } from '@vue/test-utils'
import { describe, expect, it, vi } from 'vitest'
import { seedWarehouseCanvas } from '../mockWarehouseCanvasData'
import type { LayoutIssue, WarehouseCanvasState } from '../types'
import WarehouseFloorCanvas from './WarehouseFloorCanvas.vue'

function mountFloor(overrides: Partial<{
  state: WarehouseCanvasState
  tool: 'select' | 'draw' | 'pan'
  selectedAreaId: string | null
  selectedBlockId: string | null
  issues: LayoutIssue[]
  searchQuery: string
}> = {}) {
  return mount(WarehouseFloorCanvas, {
    props: {
      state: seedWarehouseCanvas,
      tool: 'select',
      selectedAreaId: null,
      selectedBlockId: null,
      issues: [],
      searchQuery: '',
      ...overrides,
    },
  })
}

function setScaledFloorBounds(wrapper: ReturnType<typeof mountFloor>) {
  const floor = wrapper.get('[data-testid="warehouse-canvas-floor"]')
  Object.defineProperty(floor.element, 'getBoundingClientRect', {
    configurable: true,
    value: () => ({
      x: 10,
      y: 20,
      left: 10,
      top: 20,
      right: 374,
      bottom: 356,
      width: 364,
      height: 336,
      toJSON: () => ({}),
    }),
  })
  Object.defineProperty(floor.element, 'setPointerCapture', {
    configurable: true,
    value: vi.fn(),
  })
  Object.defineProperty(floor.element, 'releasePointerCapture', {
    configurable: true,
    value: vi.fn(),
  })
  return floor
}

describe('WarehouseFloorCanvas rendering and selection', () => {
  it('renders visible areas and SKU blocks with authoritative and derived quantities', () => {
    const wrapper = mountFloor()

    expect(wrapper.findAll('[data-testid^="warehouse-area-"]')).toHaveLength(3)
    expect(wrapper.findAll('[data-testid^="warehouse-sku-block-"]')).toHaveLength(5)
    const blueBlock = wrapper.get('[data-testid="warehouse-sku-block-block-blue-a"]')
    expect(blueBlock.text()).toContain('深海矿物水 500ml 蓝')
    expect(blueBlock.text()).toContain('SKU-FISH-500ML-蓝')
    expect(blueBlock.text()).toContain('150 个')
    expect(blueBlock.text()).toContain('6 件 + 6 个')
  })

  it('emits semantic block and area selections', async () => {
    const wrapper = mountFloor()

    await wrapper.get('[data-testid="warehouse-sku-block-block-blue-a"]').trigger('click')
    await wrapper.get('[data-testid="warehouse-area-area-b"]').trigger('click')

    expect(wrapper.emitted('select-block')?.[0]).toEqual(['block-blue-a'])
    expect(wrapper.emitted('select-area')?.[0]).toEqual(['area-b'])
  })

  it('shows selected outlines and resize handles while identifying locked areas', () => {
    const state: WarehouseCanvasState = {
      ...seedWarehouseCanvas,
      areas: seedWarehouseCanvas.areas.map((area) => (
        area.id === 'area-a' ? { ...area, locked: true } : area
      )),
    }
    const wrapper = mountFloor({ state, selectedAreaId: 'area-a', selectedBlockId: 'block-blue-a' })

    expect(wrapper.get('[data-testid="warehouse-area-area-a"]').attributes('data-selected')).toBe('true')
    expect(wrapper.get('[data-testid="warehouse-sku-block-block-blue-a"]').attributes('data-selected')).toBe('true')
    expect(wrapper.get('[data-testid="area-lock-area-a"]').attributes('aria-label')).toContain('已锁定')
    expect(wrapper.findAll('[data-testid^="warehouse-resize-handle-"]')).toHaveLength(2)
  })

  it('hides blocks from invisible areas and keeps the remaining area boundaries visible', () => {
    const state: WarehouseCanvasState = {
      ...seedWarehouseCanvas,
      areas: seedWarehouseCanvas.areas.map((area) => (
        area.id === 'area-a' ? { ...area, visible: false } : area
      )),
    }
    const wrapper = mountFloor({ state })

    expect(wrapper.find('[data-testid="warehouse-area-area-a"]').exists()).toBe(false)
    expect(wrapper.find('[data-testid="warehouse-sku-block-block-blue-a"]').exists()).toBe(false)
    expect(wrapper.find('[data-testid="warehouse-sku-block-block-cyan-a"]').exists()).toBe(false)
    expect(wrapper.findAll('[data-testid^="warehouse-area-"]')).toHaveLength(2)
  })

  it('shows explicit warning text and dims search nonmatches without dimming areas', () => {
    const issues: LayoutIssue[] = [{
      id: 'outside-area:block-blue-a',
      type: 'outside-area',
      blockIds: ['block-blue-a'],
      message: '产品块 block-blue-a 超出所属区域边界',
    }]
    const wrapper = mountFloor({ issues, searchQuery: '蓝' })

    const warningBlock = wrapper.get('[data-testid="warehouse-sku-block-block-blue-a"]')
    expect(warningBlock.attributes('data-warning')).toBe('true')
    expect(warningBlock.text()).toContain('超出所属区域边界')
    expect(wrapper.get('[data-testid="warehouse-sku-block-block-cyan-a"]').attributes('data-search-match')).toBe('false')
    expect(wrapper.get('[data-testid="warehouse-sku-block-block-blue-c"]').attributes('data-search-match')).toBe('true')
    expect(wrapper.get('[data-testid="warehouse-area-area-a"]').attributes('data-search-dimmed')).toBeUndefined()
  })

  it('uses mutually exclusive area borders and separate selection and warning channels', () => {
    const issues: LayoutIssue[] = [{
      id: 'outside-area:block-blue-a',
      type: 'outside-area',
      blockIds: ['block-blue-a'],
      message: '产品块 block-blue-a 超出所属区域边界',
    }]
    const wrapper = mountFloor({ selectedAreaId: 'area-a', selectedBlockId: 'block-blue-a', issues })

    const selectedAreaClasses = wrapper.get('[data-testid="warehouse-area-area-a"]').classes()
    expect(selectedAreaClasses).toContain('border-solid')
    expect(selectedAreaClasses).toContain('border-blue-600')
    expect(selectedAreaClasses).not.toContain('border-dashed')
    expect(selectedAreaClasses).not.toContain('border-slate-300')

    const selectedWarningClasses = wrapper.get('[data-testid="warehouse-sku-block-block-blue-a"]').classes()
    expect(selectedWarningClasses).toContain('border-amber-500')
    expect(selectedWarningClasses).toContain('ring-blue-600')
    expect(selectedWarningClasses).not.toContain('border-blue-300')
    expect(selectedWarningClasses).not.toContain('ring-amber-300')
  })

  it('places warning text above a block at the clipped floor bottom edge', () => {
    const state: WarehouseCanvasState = {
      ...seedWarehouseCanvas,
      blocks: seedWarehouseCanvas.blocks.map((block) => (
        block.id === 'block-blue-a' ? { ...block, y: 592 } : block
      )),
    }
    const issues: LayoutIssue[] = [{
      id: 'outside-area:block-blue-a',
      type: 'outside-area',
      blockIds: ['block-blue-a'],
      message: '产品块 block-blue-a 超出所属区域边界',
    }]
    const wrapper = mountFloor({ state, issues })

    const warning = wrapper.get('[data-testid="warehouse-sku-block-block-blue-a"]').get('[role="status"]')
    expect(warning.attributes('data-warning-placement')).toBe('above')
    expect(warning.classes()).toContain('bottom-[calc(100%+4px)]')
    expect(warning.classes()).not.toContain('top-[calc(100%+4px)]')
    expect(warning.text()).toContain('超出所属区域边界')
  })
})

describe('WarehouseFloorCanvas pointer and keyboard interactions', () => {
  it('draws on unused space inside an existing area without selecting that area', async () => {
    const wrapper = mountFloor({ tool: 'draw' })
    const floor = setScaledFloorBounds(wrapper)
    await wrapper.get('[data-testid="warehouse-area-area-a"]').trigger('pointerdown', { clientX: 250, clientY: 40, pointerId: 30 })
    await floor.trigger('pointermove', { clientX: 350, clientY: 100, pointerId: 30 })
    expect(wrapper.find('[data-testid="warehouse-area-draw-preview"]').exists()).toBe(true)
    await floor.trigger('pointerup', { clientX: 350, clientY: 100, pointerId: 30 })
    expect(wrapper.emitted('create-area')?.[0]).toEqual([{ x: 480, y: 40, width: 200, height: 120 }])
    expect(wrapper.emitted('select-area')).toBeUndefined()
  })

  it('does not preview moving a product in a locked area', async () => {
    const state = { ...seedWarehouseCanvas, areas: seedWarehouseCanvas.areas.map((area) => ({ ...area, locked: true })) }
    const wrapper = mountFloor({ state })
    const floor = setScaledFloorBounds(wrapper)
    const block = wrapper.get('[data-testid="warehouse-sku-block-block-blue-a"]')
    const before = block.attributes('style')
    await block.trigger('pointerdown', { clientX: 58, clientY: 68, pointerId: 34 })
    await floor.trigger('pointermove', { clientX: 83, clientY: 93, pointerId: 34 })
    expect(block.attributes('style')).toBe(before)
  })
  it('previews movement before release without mutating inventory, and cancels cleanly', async () => {
    const wrapper = mountFloor()
    const floor = setScaledFloorBounds(wrapper)
    const block = wrapper.get('[data-testid="warehouse-sku-block-block-blue-a"]')
    const originalStyle = block.attributes('style')
    await block.trigger('pointerdown', { clientX: 58, clientY: 68, pointerId: 31 })
    await floor.trigger('pointermove', { clientX: 83, clientY: 93, pointerId: 31 })
    expect(block.attributes('style')).not.toBe(originalStyle)
    expect(wrapper.emitted('update-block-rect')).toBeUndefined()
    expect(block.text()).toContain('150 个')
    await floor.trigger('pointercancel', { pointerId: 31 })
    expect(block.attributes('style')).toBe(originalStyle)
  })

  it('does not commit click jitter as a geometry edit', async () => {
    const wrapper = mountFloor()
    const floor = setScaledFloorBounds(wrapper)
    await wrapper.get('[data-testid="warehouse-sku-block-block-blue-a"]').trigger('pointerdown', { clientX: 58, clientY: 68, pointerId: 32 })
    await floor.trigger('pointerup', { clientX: 59, clientY: 69, pointerId: 32 })
    expect(wrapper.emitted('update-block-rect')).toBeUndefined()
  })

  it('previews resize before release without changing authoritative stock', async () => {
    const wrapper = mountFloor({ selectedBlockId: 'block-blue-a' })
    const floor = setScaledFloorBounds(wrapper)
    const block = wrapper.get('[data-testid="warehouse-sku-block-block-blue-a"]')
    const originalStyle = block.attributes('style')
    await wrapper.get('[data-testid="warehouse-resize-handle-block-block-blue-a"]').trigger('pointerdown', { clientX: 100, clientY: 100, pointerId: 33 })
    await floor.trigger('pointermove', { clientX: 120, clientY: 110, pointerId: 33 })
    expect(block.attributes('style')).not.toBe(originalStyle)
    expect(block.text()).toContain('150 个')
    expect(wrapper.emitted('update-block-rect')).toBeUndefined()
  })
  it('draws a normalized area rectangle using scaled logical coordinates', async () => {
    const wrapper = mountFloor({ tool: 'draw' })
    const floor = setScaledFloorBounds(wrapper)

    await floor.trigger('pointerdown', { clientX: 120, clientY: 110, pointerId: 1 })
    await floor.trigger('pointermove', { clientX: 60, clientY: 60, pointerId: 1 })
    await floor.trigger('pointerup', { clientX: 60, clientY: 60, pointerId: 1 })

    expect(wrapper.emitted('create-area')?.[0]).toEqual([{ x: 100, y: 80, width: 120, height: 100 }])
    expect((floor.element as HTMLElement & { setPointerCapture: ReturnType<typeof vi.fn> }).setPointerCapture)
      .toHaveBeenCalledWith(1)
  })

  it('clamps newly drawn areas to the minimum area size', async () => {
    const wrapper = mountFloor({ tool: 'draw' })
    const floor = setScaledFloorBounds(wrapper)

    await floor.trigger('pointerdown', { clientX: 30, clientY: 40, pointerId: 2 })
    await floor.trigger('pointermove', { clientX: 50, clientY: 55, pointerId: 2 })
    await floor.trigger('pointerup', { clientX: 50, clientY: 55, pointerId: 2 })

    expect(wrapper.emitted('create-area')?.[0]).toEqual([{ x: 40, y: 40, width: 80, height: 60 }])
  })

  it('moves a SKU block using logical pointer deltas without changing its size or units', async () => {
    const wrapper = mountFloor()
    const floor = setScaledFloorBounds(wrapper)
    const block = wrapper.get('[data-testid="warehouse-sku-block-block-blue-a"]')

    await block.trigger('pointerdown', { clientX: 58, clientY: 68, pointerId: 3 })
    await floor.trigger('pointermove', { clientX: 83, clientY: 93, pointerId: 3 })
    await floor.trigger('pointerup', { clientX: 83, clientY: 93, pointerId: 3 })

    expect(wrapper.emitted('update-block-rect')?.[0]).toEqual([{
      id: 'block-blue-a', x: 98, y: 114, width: 210, height: 135,
    }])
  })

  it('moves unlocked areas but ignores pointer movement for locked areas', async () => {
    const wrapper = mountFloor()
    const floor = setScaledFloorBounds(wrapper)

    await wrapper.get('[data-testid="warehouse-area-area-b"]').trigger('pointerdown', {
      clientX: 30, clientY: 200, pointerId: 4,
    })
    await floor.trigger('pointermove', { clientX: 40, clientY: 210, pointerId: 4 })
    await floor.trigger('pointerup', { clientX: 40, clientY: 210, pointerId: 4 })

    expect(wrapper.emitted('update-area-rect')?.[0]).toEqual([{
      id: 'area-b', x: 36, y: 272, width: 340, height: 400,
    }])

    const lockedState: WarehouseCanvasState = {
      ...seedWarehouseCanvas,
      areas: seedWarehouseCanvas.areas.map((area) => (
        area.id === 'area-a' ? { ...area, locked: true } : area
      )),
    }
    await wrapper.setProps({ state: lockedState })
    await wrapper.get('[data-testid="warehouse-area-area-a"]').trigger('pointerdown', {
      clientX: 40, clientY: 60, pointerId: 5,
    })
    await floor.trigger('pointermove', { clientX: 60, clientY: 80, pointerId: 5 })
    await floor.trigger('pointerup', { clientX: 60, clientY: 80, pointerId: 5 })

    expect(wrapper.emitted('update-area-rect')).toHaveLength(1)
  })

  it('resizes selected areas and blocks while enforcing their minimum sizes', async () => {
    const wrapper = mountFloor({ selectedAreaId: 'area-a', selectedBlockId: 'block-blue-a' })
    const floor = setScaledFloorBounds(wrapper)

    await wrapper.get('[data-testid="warehouse-resize-handle-area-area-a"]').trigger('pointerdown', {
      clientX: 360, clientY: 180, pointerId: 6,
    })
    await floor.trigger('pointermove', { clientX: 40, clientY: 50, pointerId: 6 })
    await floor.trigger('pointerup', { clientX: 40, clientY: 50, pointerId: 6 })
    expect(wrapper.emitted('update-area-rect')?.[0]).toEqual([{
      id: 'area-a', x: 16, y: 16, width: 80, height: 60,
    }])

    await wrapper.get('[data-testid="warehouse-resize-handle-block-block-blue-a"]').trigger('pointerdown', {
      clientX: 108, clientY: 108, pointerId: 7,
    })
    await floor.trigger('pointermove', { clientX: 30, clientY: 40, pointerId: 7 })
    await floor.trigger('pointerup', { clientX: 30, clientY: 40, pointerId: 7 })
    expect(wrapper.emitted('update-block-rect')?.[0]).toEqual([{
      id: 'block-blue-a', x: 48, y: 64, width: 96, height: 72,
    }])
  })

  it('nudges the selected object by one unit, or ten while Shift is held', async () => {
    const wrapper = mountFloor({ selectedBlockId: 'block-blue-a' })
    const block = wrapper.get('[data-testid="warehouse-sku-block-block-blue-a"]')

    await block.trigger('keydown', { key: 'ArrowRight' })
    await block.trigger('keydown', { key: 'ArrowDown', shiftKey: true })

    expect(wrapper.emitted('update-block-rect')).toEqual([
      [{ id: 'block-blue-a', x: 49, y: 64, width: 210, height: 135 }],
      [{ id: 'block-blue-a', x: 48, y: 74, width: 210, height: 135 }],
    ])
  })
})
