import { mount } from '@vue/test-utils'
import { afterEach, describe, expect, it, vi } from 'vitest'
import WarehouseBlueprintScene from './WarehouseBlueprintScene.vue'
import { warehousePlannerScene } from '../warehousePlannerScene'
import { createWarehouseStructure } from '../warehouseStructure'

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

function runAnimationFramesImmediately() {
  vi.stubGlobal('requestAnimationFrame', (callback: FrameRequestCallback) => {
    callback(0)
    return 1
  })
  vi.stubGlobal('cancelAnimationFrame', vi.fn())
}

afterEach(() => {
  vi.useRealTimers()
  vi.unstubAllGlobals()
})

describe('WarehouseBlueprintScene', () => {
  it('renders each saved pile as individual box cells in detail mode', () => {
    const pallet = {
      ...warehousePlannerScene.palletGroups[0]!,
      id: 'pile-box-grid',
      columns: 3,
      rows: 2,
    }
    const wrapper = mount(WarehouseBlueprintScene, {
      props: {
        gridSnapping: true,
        measurementEnabled: false,
        selectedPalletId: null,
        detailMode: true,
        inventoryDetailsVisible: true,
        palletGroups: [pallet],
        structure: createWarehouseStructure(),
      },
    })

    const pile = wrapper.get('[data-testid="planner-pallet-pile-box-grid"]')
    expect(pile.findAll('[data-testid="pallet-box-cell"]')).toHaveLength(6)
  })

  it('reuses the saved blueprint in detail mode without editing rulers and emits zone selection', async () => {
    const structure = createWarehouseStructure()
    const wrapper = mount(WarehouseBlueprintScene, {
      props: {
        gridSnapping: true,
        measurementEnabled: false,
        selectedPalletId: null,
        selectedZoneId: null,
        detailMode: true,
        inventoryDetailsVisible: true,
        palletGroups: warehousePlannerScene.palletGroups,
        structure,
      },
    })

    expect(wrapper.find('[data-testid="planner-ruler-x"]').exists()).toBe(false)
    expect(wrapper.find('[data-testid="planner-ruler-y"]').exists()).toBe(false)
    expect(wrapper.find('[data-testid="planner-minimap"]').exists()).toBe(false)
    expect(wrapper.find('[data-testid="planner-coordinate-status"]').exists()).toBe(false)
    expect(wrapper.find('[data-testid="planner-pallet-pallet-c018"]').exists()).toBe(true)
    expect(wrapper.find('.scene-inspector').exists()).toBe(false)

    await wrapper.get('[data-testid="planner-zone-zone-receiving"]').trigger('click')
    expect(wrapper.emitted('select-zone')?.[0]).toEqual(['zone-receiving'])
  })

  it('creates a pile by reverse dragging and rejects overlapping and outside rectangles', async () => {
    const pile = { ...warehousePlannerScene.palletGroups[0]!, left:40, top:45, width:5, height:5 }
    const structure = { ...createWarehouseStructure(), partitions:[], doors:[], elevators:[], columns:[], zones:[] }
    const wrapper = mount(WarehouseBlueprintScene, { props: { gridSnapping:false, measurementEnabled:false, selectedPalletId:null, goodsEditing:true, palletGroups:[pile], structure } })
    try {
      const board = setDrawingBoardBounds(wrapper)
      await board.trigger('pointerdown',{button:0,pointerId:90,clientX:600,clientY:480})
      await board.trigger('pointermove',{pointerId:90,clientX:500,clientY:400})
      expect(wrapper.get('[data-testid="pile-creation-preview"]').classes()).not.toContain('invalid')
      await board.trigger('pointerup',{pointerId:90,clientX:500,clientY:400})
      expect(wrapper.emitted('create-pallet')?.[0]).toEqual([{left:50,top:50,width:10,height:10}])
      await board.trigger('pointerdown',{button:0,pointerId:91,clientX:390,clientY:350})
      await board.trigger('pointermove',{pointerId:91,clientX:460,clientY:410})
      expect(wrapper.get('[data-testid="pile-creation-preview"]').classes()).toContain('invalid')
      await board.trigger('pointerup',{pointerId:91,clientX:460,clientY:410})
      expect(wrapper.emitted('create-pallet')).toHaveLength(1)
      await board.trigger('pointerdown',{button:0,pointerId:92,clientX:0,clientY:0})
      await board.trigger('pointerup',{pointerId:92,clientX:100,clientY:80})
      expect(wrapper.emitted('create-pallet')).toHaveLength(1)
      await board.trigger('pointerdown',{button:0,pointerId:93,clientX:500,clientY:400})
      window.dispatchEvent(new KeyboardEvent('keydown',{key:'Escape'}))
      await board.trigger('pointerup',{pointerId:93,clientX:600,clientY:480})
      expect(wrapper.emitted('create-pallet')).toHaveLength(1)
    } finally { wrapper.unmount() }
  })
  it.each(['aisle', 'forklift', 'fire'] as const)('marks a pile and %s passage during collision and rejects the drop', async kind => {
    runAnimationFramesImmediately()
    const pile = { ...warehousePlannerScene.palletGroups[0]!, left: 40, top: 45, width: 5, height: 5 }
    const wrapper = mount(WarehouseBlueprintScene, { props: {
      gridSnapping: false, measurementEnabled: false, selectedPalletId: pile.id, palletGroups: [pile],
      structure: { ...createWarehouseStructure(), zones: [{ id: 'route', label: '通道', tone: 'blue', kind, left: 60, top: 45, width: 5, height: 5 }] },
    } })
    try {
      const board = setDrawingBoardBounds(wrapper)
      const moving = wrapper.get(`[data-testid="planner-pallet-${pile.id}"]`)
      const route = wrapper.get('[data-testid="planner-zone-route"]')
      const original = moving.attributes('style')
      await moving.trigger('pointerdown', { button: 0, pointerId: 60, clientX: 425, clientY: 380 })
      await board.trigger('pointermove', { pointerId: 60, clientX: 625, clientY: 380 })
      expect(moving.classes()).toContain('overlapping')
      expect(route.classes()).toContain('invalid')
      await board.trigger('pointerup', { pointerId: 60, clientX: 625, clientY: 380 })
      expect(wrapper.emitted('move-pallet')).toBeUndefined()
      expect(moving.attributes('style')).toBe(original)
      expect(route.classes()).toContain('invalid')
    } finally { wrapper.unmount() }
  })
  it('keeps every collided object highlighted briefly after a rejected elevator drop', async () => {
    vi.useFakeTimers()
    const wrapper = mount(WarehouseBlueprintScene, {
      props: {
        gridSnapping: false, measurementEnabled: false, selectedPalletId: null, structureEditing: true,
        structure: { ...createWarehouseStructure(), zones: [], elevators: [{ id: 'lift', left: 40, top: 15, width: 5, height: 7.5 }] },
        palletGroups: [
          { ...warehousePlannerScene.palletGroups[0]!, id: 'pile-left', left: 40, top: 30, width: 5, height: 7.5 },
          { ...warehousePlannerScene.palletGroups[0]!, id: 'pile-right', left: 45, top: 30, width: 5, height: 7.5 },
        ],
      },
    })
    try {
      const svg = wrapper.get('svg.structure-svg')
      Object.defineProperty(svg.element, 'getBoundingClientRect', { value: () => ({ left: 0, top: 0, width: 1000, height: 800 }) })
      const lift = wrapper.get('[data-testid="planner-elevator-lift"]')
      await lift.trigger('pointerdown', { button: 0, pointerId: 31, clientX: 425, clientY: 150 })
      await svg.trigger('pointerup', { pointerId: 31, clientX: 450, clientY: 270 })

      expect(wrapper.emitted('change-structure')).toBeUndefined()
      expect(lift.attributes('transform')).toBe('translate(240,60)')
      expect(lift.classes()).toContain('invalid')
      for (const id of ['pile-left', 'pile-right']) {
        expect(wrapper.get(`[data-testid="planner-pallet-${id}"]`).classes()).toContain('overlapping')
      }
      expect(wrapper.emitted('structure-issues')?.at(-1)).toEqual([[]])
      await vi.advanceTimersByTimeAsync(800)
      expect(lift.classes()).toContain('invalid')
      await vi.advanceTimersByTimeAsync(500)
      expect(lift.classes()).not.toContain('invalid')
      for (const id of ['pile-left', 'pile-right']) {
        expect(wrapper.get(`[data-testid="planner-pallet-${id}"]`).classes()).not.toContain('overlapping')
      }
    } finally {
      wrapper.unmount()
    }
  })

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
    expect(wrapper.get('[data-testid="planner-zone-aisle-top"]').text()).toContain('叉车通道 2.0m')
    expect(wrapper.text()).toContain('消防留空区')
    expect(wrapper.get('[data-testid="planner-pallet-pallet-c018"]').attributes('data-selected')).toBe('true')
    expect(wrapper.get('[data-testid="planner-pallet-pallet-c018"]').attributes('aria-pressed')).toBe('true')
    expect(wrapper.get('[data-testid="planner-pallet-pallet-c018"]').attributes('aria-label')).toContain('3 种商品，共 250 个')
    expect(wrapper.get('[data-testid="planner-pallet-pallet-a01"]').attributes('aria-pressed')).toBe('false')
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
        showDemoRooms: true,
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
    runAnimationFramesImmediately()
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
    await board.trigger('pointermove', { pointerId: 11, clientX: 629, clientY: 409 })

    expect(pile.attributes('style')).not.toBe(originalStyle)
    expect(pile.attributes('style')).toContain('transform: translate3d(29px, 9px, 0)')
    expect(pile.attributes('aria-label')).toContain('3 种商品，共 250 个')
    expect(wrapper.emitted('move-pallet')).toBeUndefined()

    await board.trigger('pointerup', { pointerId: 11, clientX: 629, clientY: 409 })

    expect(wrapper.emitted('move-pallet')?.[0]).toEqual([{ id: 'pallet-c018', left: 61.67, top: 50 }])
  })

  it('does not render pile inventory details while planning', () => {
    const wrapper = mount(WarehouseBlueprintScene, {
      props: {
        gridSnapping: true,
        inventoryDetailsVisible: false,
        measurementEnabled: false,
        selectedPalletId: 'pallet-c018',
        palletGroups: warehousePlannerScene.palletGroups,
      },
    })
    expect(wrapper.find('.scene-inspector').exists()).toBe(false)
    expect(wrapper.find('[data-testid="planner-inspector-product"]').exists()).toBe(false)
    expect(wrapper.text()).not.toContain('SKU-FISH-500ML-蓝')
  })

  it('shows pile inventory details only in completed detail mode', async () => {
    const wrapper = mount(WarehouseBlueprintScene, {
      props: {
        gridSnapping: true,
        inventoryDetailsVisible: false,
        measurementEnabled: false,
        selectedPalletId: 'pallet-c018',
        palletGroups: warehousePlannerScene.palletGroups,
      },
    })

    expect(wrapper.find('.scene-inspector').exists()).toBe(false)
    await wrapper.setProps({ inventoryDetailsVisible: true })
    expect(wrapper.get('.scene-inspector').text()).toContain('深海矿物水 500ml 蓝')
    expect(wrapper.get('.scene-inspector').text()).toContain('SKU-FISH-500ML-蓝')
  })

  it('keeps piles read-only while completed details are visible', async () => {
    runAnimationFramesImmediately()
    const wrapper = mount(WarehouseBlueprintScene, {
      props: {
        gridSnapping: true,
        inventoryDetailsVisible: true,
        measurementEnabled: false,
        selectedPalletId: 'pallet-c018',
        palletGroups: warehousePlannerScene.palletGroups,
      },
    })
    const board = setDrawingBoardBounds(wrapper)
    const pile = wrapper.get('[data-testid="planner-pallet-pallet-c018"]')

    await pile.trigger('pointerdown', { button: 0, pointerId: 16, clientX: 600, clientY: 400 })
    await board.trigger('pointermove', { pointerId: 16, clientX: 650, clientY: 400 })
    await board.trigger('pointerup', { pointerId: 16, clientX: 650, clientY: 400 })

    expect(wrapper.emitted('move-pallet')).toBeUndefined()
    expect(wrapper.find('.scene-inspector').exists()).toBe(true)
  })

  it('marks both overlapping piles and returns the moved pile when the drop is rejected', async () => {
    vi.useFakeTimers()
    runAnimationFramesImmediately()
    const wrapper = mount(WarehouseBlueprintScene, {
      props: {
        gridSnapping: true,
        measurementEnabled: false,
        selectedPalletId: 'pallet-c018',
        palletGroups: warehousePlannerScene.palletGroups,
      },
    })
    const board = setDrawingBoardBounds(wrapper)
    const movingPile = wrapper.get('[data-testid="planner-pallet-pallet-c018"]')
    const blockedPile = wrapper.get('[data-testid="planner-pallet-pallet-b04"]')
    const originalStyle = movingPile.attributes('style')

    await movingPile.trigger('pointerdown', { button: 0, pointerId: 14, clientX: 600, clientY: 400 })
    await board.trigger('pointermove', { pointerId: 14, clientX: 600, clientY: 450 })

    expect(movingPile.classes()).toContain('overlapping')
    expect(blockedPile.classes()).toContain('overlapping')
    const movingLayer = Number((movingPile.element as HTMLElement).style.zIndex)
    expect(movingLayer).toBeGreaterThan(20)

    await board.trigger('pointerup', { pointerId: 14, clientX: 600, clientY: 450 })

    expect(wrapper.emitted('move-pallet')).toBeUndefined()
    expect(movingPile.attributes('style')).toBe(originalStyle)
    expect(movingPile.classes()).toContain('overlapping')
    expect(blockedPile.classes()).toContain('overlapping')

    await vi.advanceTimersByTimeAsync(800)
    expect(movingPile.classes()).toContain('overlapping')
    expect(blockedPile.classes()).toContain('overlapping')

    await vi.advanceTimersByTimeAsync(500)
    expect(movingPile.classes()).not.toContain('overlapping')
    expect(blockedPile.classes()).not.toContain('overlapping')
  })

  it('shows center alignment guides and lets object alignment win over grid snapping', async () => {
    runAnimationFramesImmediately()
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

    await pile.trigger('pointerdown', { button: 0, pointerId: 15, clientX: 600, clientY: 400 })
    await board.trigger('pointermove', { pointerId: 15, clientX: 410, clientY: 474 })

    expect(wrapper.get('[data-testid="planner-alignment-guide-x"]').attributes('style')).toContain('left: 43%')
    expect(wrapper.get('[data-testid="planner-alignment-guide-y"]').attributes('style')).toContain('top: 61%')
    expect(pile.attributes('style')).toContain('transform: translate3d(-190px, 74px, 0)')

    await board.trigger('pointerup', { pointerId: 15, clientX: 410, clientY: 474 })
    expect(wrapper.emitted('move-pallet')?.[0]).toEqual([{ id: 'pallet-c018', left: 39.5, top: 58.25 }])
  })

  it('rejects an outside drop at the sloped warehouse boundary and restores its origin', async () => {
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

    expect(wrapper.emitted('move-pallet')).toBeUndefined()
    expect(pile.attributes('style')).toContain('left: 58.5%')
    expect(pile.attributes('data-overlapping')).toBe('true')
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
})
