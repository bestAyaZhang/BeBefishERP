import { mount } from '@vue/test-utils'
import { describe, expect, it, vi } from 'vitest'
import WarehouseBlueprintScene from './WarehouseBlueprintScene.vue'
import { warehousePlannerScene } from '../warehousePlannerScene'

function setDrawingBoardBounds(wrapper: ReturnType<typeof mount>) {
  const board = wrapper.get('.drawing-board')
  Object.defineProperty(board.element, 'getBoundingClientRect', {
    configurable: true,
    value: () => ({
      x: 0,
      y: 0,
      left: 0,
      top: 0,
      right: 1000,
      bottom: 800,
      width: 1000,
      height: 800,
      toJSON: () => ({}),
    }),
  })
  Object.defineProperty(board.element, 'setPointerCapture', {
    configurable: true,
    value: vi.fn(),
  })
  Object.defineProperty(board.element, 'releasePointerCapture', {
    configurable: true,
    value: vi.fn(),
  })
  return board
}

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
    expect(wrapper.get('[data-testid="planner-pallet-pallet-c018"]').attributes('aria-pressed')).toBe('true')
    expect(wrapper.get('[data-testid="planner-pallet-pallet-c018"]').attributes('aria-label')).toContain('3 种商品，共 250 个')
    expect(wrapper.get('[data-testid="planner-pallet-pallet-a01"]').attributes('aria-pressed')).toBe('false')
    expect(wrapper.find('[data-testid="planner-minimap"]').exists()).toBe(true)
    expect(wrapper.get('[data-testid="planner-coordinate-status"]').text()).toContain('比例  1:100')

    await wrapper.get('[data-testid="planner-pallet-pallet-a01"]').trigger('click')
    expect(wrapper.emitted('select-pallet')?.[0]).toEqual(['pallet-a01'])

    await wrapper.get('[data-testid="planner-inspector-close"]').trigger('click')
    expect(wrapper.emitted('select-pallet')?.[1]).toEqual([null])
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

  it('renders half-meter ruler ticks with distinct meter and five-meter hierarchy', () => {
    const wrapper = mount(WarehouseBlueprintScene, {
      props: {
        gridSnapping: true,
        measurementEnabled: false,
        selectedPalletId: 'pallet-c018',
      },
    })

    expect(wrapper.findAll('[data-testid="planner-ruler-x-tick"]')).toHaveLength(121)
    expect(wrapper.findAll('[data-testid="planner-ruler-y-tick"]')).toHaveLength(81)
    expect(wrapper.findAll('[data-testid="planner-ruler-x-tick"][data-tick-kind="major"]')).toHaveLength(13)
    expect(wrapper.findAll('[data-testid="planner-ruler-y-tick"][data-tick-kind="major"]')).toHaveLength(9)
    expect(wrapper.findAll('[data-testid="planner-ruler-x-tick"][data-tick-kind="meter"]')).toHaveLength(48)
    expect(wrapper.findAll('[data-testid="planner-ruler-y-tick"][data-tick-kind="meter"]')).toHaveLength(32)
    expect(wrapper.get('[data-testid="planner-ruler-x"]').text()).toContain('60')
    expect(wrapper.get('[data-testid="planner-ruler-y"]').text()).toContain('40')
  })

  it('keeps utility rooms inside the lower warehouse wall', () => {
    const wrapper = mount(WarehouseBlueprintScene, {
      props: {
        gridSnapping: true,
        measurementEnabled: false,
        selectedPalletId: 'pallet-c018',
      },
    })

    const rooms = wrapper.findAll('.utility-room')
    expect(rooms).toHaveLength(2)
    for (const room of rooms) {
      const style = room.attributes('style') ?? ''
      const left = Number(style.match(/left:\s*([\d.]+)%/)?.[1])
      const top = Number(style.match(/top:\s*([\d.]+)%/)?.[1])
      const height = Number(style.match(/height:\s*([\d.]+)%/)?.[1])
      expect(left).toBeGreaterThanOrEqual(6.8)
      expect(top + height).toBeLessThanOrEqual(89.9)
    }
  })

  it('previews a pile drag and snaps its committed position to half-meter grid lines', async () => {
    const wrapper = mount(WarehouseBlueprintScene, {
      props: {
        gridSnapping: true,
        measurementEnabled: false,
        selectedPalletId: 'pallet-c018',
        palletGroups: warehousePlannerScene.palletGroups,
      },
    })
    const board = setDrawingBoardBounds(wrapper)
    const pile = wrapper.get('[data-testid="planner-pallet-pallet-c018"]')
    const originalStyle = pile.attributes('style')

    await pile.trigger('pointerdown', { button: 0, pointerId: 11, clientX: 600, clientY: 400 })
    await board.trigger('pointermove', { pointerId: 11, clientX: 613, clientY: 409 })

    expect(pile.attributes('style')).not.toBe(originalStyle)
    expect(pile.attributes('aria-label')).toContain('3 种商品，共 250 个')
    expect(wrapper.emitted('move-pallet')).toBeUndefined()

    await board.trigger('pointerup', { pointerId: 11, clientX: 613, clientY: 409 })

    expect(wrapper.emitted('move-pallet')?.[0]).toEqual([{ id: 'pallet-c018', left: 60, top: 50 }])
  })

  it('keeps a dragged pile fully inside the sloped warehouse boundary', async () => {
    const wrapper = mount(WarehouseBlueprintScene, {
      props: {
        gridSnapping: false,
        measurementEnabled: false,
        selectedPalletId: 'pallet-c018',
        palletGroups: warehousePlannerScene.palletGroups,
      },
    })
    const board = setDrawingBoardBounds(wrapper)
    const pile = wrapper.get('[data-testid="planner-pallet-pallet-c018"]')

    await pile.trigger('pointerdown', { button: 0, pointerId: 12, clientX: 600, clientY: 400 })
    await board.trigger('pointermove', { pointerId: 12, clientX: 1600, clientY: 1400 })
    await board.trigger('pointerup', { pointerId: 12, clientX: 1600, clientY: 1400 })

    expect(wrapper.emitted('move-pallet')?.[0]).toEqual([{ id: 'pallet-c018', left: 80.44, top: 84.4 }])
  })

  it('does not turn a selection click into a snapped position edit', async () => {
    const wrapper = mount(WarehouseBlueprintScene, {
      props: {
        gridSnapping: true,
        measurementEnabled: false,
        selectedPalletId: 'pallet-c018',
        palletGroups: warehousePlannerScene.palletGroups,
      },
    })
    const board = setDrawingBoardBounds(wrapper)

    await wrapper.get('[data-testid="planner-pallet-pallet-c018"]').trigger('pointerdown', {
      button: 0,
      pointerId: 13,
      clientX: 600,
      clientY: 400,
    })
    await board.trigger('pointerup', { pointerId: 13, clientX: 600, clientY: 400 })

    expect(wrapper.emitted('move-pallet')).toBeUndefined()
  })

  it('keeps the inventory panel inside the board when its pile reaches the lower-right boundary', () => {
    const movedPallets = warehousePlannerScene.palletGroups.map((pallet) => (
      pallet.id === 'pallet-c018' ? { ...pallet, left: 80.44, top: 84.4 } : pallet
    ))
    const wrapper = mount(WarehouseBlueprintScene, {
      props: {
        gridSnapping: true,
        measurementEnabled: false,
        selectedPalletId: 'pallet-c018',
        palletGroups: movedPallets,
      },
    })

    const style = wrapper.get('.scene-inspector').attributes('style') ?? ''
    expect(style).toContain('bottom:')
    expect(style).toContain('right:')
    expect(style).not.toContain('top:')
    expect(style).not.toContain('left:')
  })
})
