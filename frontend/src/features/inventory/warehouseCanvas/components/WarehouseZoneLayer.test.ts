import { mount } from '@vue/test-utils'
import { afterEach, describe, expect, it, vi } from 'vitest'
import WarehouseBlueprintScene from './WarehouseBlueprintScene.vue'
import { createWarehouseStructure } from '../warehouseStructure'
import type { WarehouseStructure } from '../warehouseStructure'
import { warehousePlannerScene } from '../warehousePlannerScene'
import type { PlannerZone } from '../warehousePlannerScene'

const mounted: ReturnType<typeof mount>[] = []
const zone = { id: 'zone-test', label: '存货区', tone: 'green' as const, left: 40, top: 40, width: 10, height: 10 }
function setup(zones: PlannerZone[] = [zone]) {
  const wrapper = mount(WarehouseBlueprintScene, { props: {
    gridSnapping: false, measurementEnabled: false, selectedPalletId: null, zoneEditing: true,
    structure: { ...createWarehouseStructure(), columns: [], zones },
    palletGroups: [{ ...warehousePlannerScene.palletGroups[0]!, left: 42, top: 42, width: 2, height: 2 }],
  } })
  mounted.push(wrapper)
  expect(wrapper.find('[data-testid="zone-canvas"]').exists()).toBe(true)
  const svg = wrapper.get('[data-testid="zone-canvas"]')
  Object.defineProperty(svg.element, 'getBoundingClientRect', { value: () => ({ left: 0, top: 0, width: 1000, height: 800 }) })
  Object.defineProperty(svg.element, 'setPointerCapture', { value: vi.fn() })
  return { wrapper, svg }
}
afterEach(() => { mounted.splice(0).forEach(w => w.unmount()); vi.useRealTimers(); vi.unstubAllGlobals() })

describe('zone planning gestures', () => {
  it('applies width to the horizontal footprint of a vertical fire lane', async () => {
    const { wrapper, svg } = setup([])
    await wrapper.setProps({ palletGroups: [] })
    await wrapper.get('[data-testid="zone-kind-picker"]').setValue('fire')
    await svg.trigger('pointerdown', { button: 0, pointerId: 24, clientX: 400, clientY: 320 })
    await svg.trigger('pointerup', { pointerId: 24, clientX: 450, clientY: 480 })
    await wrapper.get('[data-testid="passage-width"]').setValue('1.2')
    await wrapper.get('[data-testid="zone-save"]').trigger('submit')
    const next = wrapper.emitted('change-structure')![0]![0] as WarehouseStructure
    expect(next.zones![0]).toMatchObject({ kind: 'fire', axis: 'vertical', left: 40, top: 40, width: 2, height: 20 })
    await wrapper.setProps({ structure: next })
    expect(wrapper.find('[data-testid="passage-fire-hatch"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="passage-arrows"]').exists()).toBe(false)
    expect(wrapper.get('[data-testid="passage-label"]').text()).toContain('1.2m')
  })
  it('creates a forklift passage with physical width and one-way arrows', async () => {
    const { wrapper, svg } = setup([])
    await wrapper.setProps({ palletGroups: [] })
    expect(wrapper.find('[data-testid="zone-kind-picker"]').exists()).toBe(true)
    await wrapper.get('[data-testid="zone-kind-picker"]').setValue('forklift')
    await svg.trigger('pointerdown', { button: 0, pointerId: 20, clientX: 400, clientY: 320 })
    await svg.trigger('pointerup', { pointerId: 20, clientX: 600, clientY: 360 })
    await wrapper.get('[data-testid="passage-width"]').setValue('3')
    await wrapper.get('[data-testid="passage-traffic"]').setValue('forward')
    await wrapper.get('[data-testid="zone-save"]').trigger('submit')
    const next = wrapper.emitted('change-structure')![0]![0] as WarehouseStructure
    expect(next.zones![0]).toMatchObject({ kind: 'forklift', axis: 'horizontal', traffic: 'forward', width: 20, height: 7.5 })
    await wrapper.setProps({ structure: next })
    expect(wrapper.get('[data-testid="passage-label"]').text()).toContain('3.0m')
    expect(wrapper.get('[data-testid="passage-arrows"]').text()).toContain('→')
    expect(wrapper.get('[data-testid="passage-arrows"]').text()).not.toContain('←')
  })
  it('draws a connecting passage from inside an existing segment', async () => {
    const route: PlannerZone = { ...zone, id: 'route', kind: 'aisle', width: 20, height: 5 }
    const { wrapper, svg } = setup([route])
    await wrapper.setProps({ palletGroups: [] })
    await wrapper.get('[data-testid="zone-kind-picker"]').setValue('aisle')
    await wrapper.get('[data-testid="planner-zone-route"]').trigger('pointerdown', { button: 0, pointerId: 21, clientX: 550, clientY: 330 })
    await svg.trigger('pointerup', { pointerId: 21, clientX: 600, clientY: 500 })
    await wrapper.get('[data-testid="zone-save"]').trigger('submit')
    const next = wrapper.emitted('change-structure')![0]![0] as WarehouseStructure
    expect(next.zones).toHaveLength(2)
    expect(next.zones![0]).toEqual(route)
    expect(next.zones![1]).toMatchObject({ kind: 'aisle', axis: 'vertical' })
  })
  it('refuses passage creation over goods and rolls back a blocked passage move', async () => {
    const { wrapper, svg } = setup([])
    expect(wrapper.find('[data-testid="zone-kind-picker"]').exists()).toBe(true)
    await wrapper.get('[data-testid="zone-kind-picker"]').setValue('fire')
    await svg.trigger('pointerdown', { button: 0, pointerId: 22, clientX: 400, clientY: 320 })
    await svg.trigger('pointerup', { pointerId: 22, clientX: 500, clientY: 400 })
    expect(wrapper.find('[data-testid="zone-name"]').exists()).toBe(false)
    expect(wrapper.emitted('change-structure')).toBeUndefined()
    const route: PlannerZone = { ...zone, id: 'fire', kind: 'fire', left: 55, width: 5 }
    await wrapper.setProps({ structure: { ...createWarehouseStructure(), columns: [], zones: [route] } })
    await wrapper.get('[data-testid="zone-tool-select"]').trigger('click')
    await wrapper.get('[data-testid="planner-zone-fire"]').trigger('pointerdown', { button: 0, pointerId: 23, clientX: 570, clientY: 350 })
    await svg.trigger('pointerup', { pointerId: 23, clientX: 430, clientY: 350 })
    expect(wrapper.emitted('change-structure')).toBeUndefined()
    expect(wrapper.get('[data-testid="planner-zone-fire"]').classes()).toContain('invalid')
    expect(wrapper.get('[data-testid="planner-pallet-pallet-a01"]').classes()).toContain('overlapping')
    expect(wrapper.get('[data-testid="planner-zone-fire"]').attributes('transform')).toBe('translate(330,160)')
  })
  it('rejects an invalid or obstructed width edit without changing the saved passage', async () => {
    const { wrapper } = setup([{ ...zone, id: 'route', kind: 'forklift', axis: 'horizontal', top: 38, width: 20, height: 2 }])
    await wrapper.get('[data-testid="planner-zone-route"]').trigger('keydown', { key: 'Enter' })
    await wrapper.get('[data-testid="zone-edit"]').trigger('click')
    expect(wrapper.find('[data-testid="passage-width"]').exists()).toBe(true)
    for (const width of ['', '0', '-1', '4']) {
      await wrapper.get('[data-testid="passage-width"]').setValue(width)
      await wrapper.get('[data-testid="zone-save"]').trigger('submit')
      expect(wrapper.emitted('change-structure')).toBeUndefined()
      expect(wrapper.find('[role="alert"]').exists()).toBe(true)
    }
    await wrapper.get('[data-testid="zone-cancel"]').trigger('click')
    expect(wrapper.get('[data-testid="planner-zone-route"] .zone-body').attributes('height')).toBe('8')
  })
  it('does not create an undo step when unchanged zone properties are confirmed', async () => {
    const { wrapper } = setup([zone, { ...zone, id: 'zone-other', left: 60 }])
    await wrapper.get('[data-testid="planner-zone-zone-test"]').trigger('keydown', { key: 'Enter' })
    await wrapper.get('[data-testid="zone-edit"]').trigger('click')
    await wrapper.get('[data-testid="zone-save"]').trigger('submit')
    expect(wrapper.emitted('change-structure')).toBeUndefined()
    expect(wrapper.find('[data-testid="zone-name"]').exists()).toBe(false)
  })
  it('snaps drawing to the half-meter grid and cancels a captured move on tool change', async () => {
    const { wrapper, svg } = setup([])
    await wrapper.setProps({ gridSnapping: true })
    await svg.trigger('pointerdown', { button: 0, pointerId: 8, clientX: 401, clientY: 321 })
    await svg.trigger('pointerup', { pointerId: 8, clientX: 502, clientY: 401 })
    await wrapper.get('[data-testid="zone-save"]').trigger('submit')
    const added = wrapper.emitted('change-structure')![0]![0] as WarehouseStructure
    expect(added.zones![0]).toMatchObject({ left: 40, top: 40, width: 10, height: 10 })
    await wrapper.setProps({ structure: added })
    await wrapper.get(`[data-testid="planner-zone-${added.zones![0]!.id}"]`).trigger('pointerdown', { button: 0, pointerId: 9, clientX: 450, clientY: 360 })
    await wrapper.setProps({ zoneEditing: false })
    await svg.trigger('pointerup', { pointerId: 9, clientX: 550, clientY: 360 })
    expect(wrapper.emitted('change-structure')).toHaveLength(1)
    expect(wrapper.emitted('structure-busy')?.at(-1)).toEqual([false])
  })
  it('draws a zone in reverse direction and commits its name and color together', async () => {
    const { wrapper, svg } = setup([])
    await wrapper.get('[data-testid="zone-tool-draw"]').trigger('click')
    await svg.trigger('pointerdown', { button: 0, pointerId: 1, clientX: 500, clientY: 400 })
    await svg.trigger('pointerup', { pointerId: 1, clientX: 400, clientY: 320 })
    expect(wrapper.emitted('change-structure')).toBeUndefined()
    await wrapper.get('[data-testid="zone-name"]').setValue('  混放区 A+B  ')
    await wrapper.get('[data-testid="zone-tone-purple"]').trigger('click')
    await wrapper.get('[data-testid="zone-save"]').trigger('submit')
    const next = wrapper.emitted('change-structure')![0]![0] as WarehouseStructure
    expect(next.zones).toEqual([expect.objectContaining({ label: '混放区 A+B', tone: 'purple', left: 40, top: 40, width: 10, height: 10 })])
    expect(wrapper.emitted('move-pallet')).toBeUndefined()
    expect(wrapper.emitted('structure-busy')?.at(-1)).toEqual([false])
  })
  it('edits and clears the optional second line of an area caption', async () => {
    const { wrapper } = setup([{ ...zone, detail: '原区域说明' }])
    const area = wrapper.get('[data-testid="planner-zone-zone-test"]')
    expect(area.text()).toContain('原区域说明')

    await area.trigger('keydown', { key: 'Enter' })
    await wrapper.get('[data-testid="zone-edit"]').trigger('click')
    expect((wrapper.get('[data-testid="zone-detail"]').element as HTMLInputElement).value).toBe('原区域说明')
    await wrapper.get('[data-testid="zone-detail"]').setValue('  冷藏待检  ')
    await wrapper.get('[data-testid="zone-save"]').trigger('submit')
    const updated = wrapper.emitted('change-structure')![0]![0] as WarehouseStructure
    expect(updated.zones![0]).toMatchObject({ detail: '冷藏待检' })

    await wrapper.setProps({ structure: updated })
    expect(wrapper.get('[data-testid="planner-zone-zone-test"]').text()).toContain('冷藏待检')
    await wrapper.get('[data-testid="planner-zone-zone-test"]').trigger('keydown', { key: 'Enter' })
    await wrapper.get('[data-testid="zone-edit"]').trigger('click')
    await wrapper.get('[data-testid="zone-detail"]').setValue('   ')
    await wrapper.get('[data-testid="zone-save"]').trigger('submit')
    const cleared = wrapper.emitted('change-structure')![1]![0] as WarehouseStructure
    expect(cleared.zones![0]).not.toHaveProperty('detail')
  })
  it('moves then resizes a zone without moving its goods', async () => {
    const { wrapper, svg } = setup()
    const goodsBefore = wrapper.get('[data-testid="planner-pallet-pallet-a01"]').attributes('style')
    await wrapper.get('[data-testid="planner-zone-zone-test"]').trigger('pointerdown', { button: 0, pointerId: 2, clientX: 450, clientY: 360 })
    await svg.trigger('pointerup', { pointerId: 2, clientX: 550, clientY: 360 })
    const moved = wrapper.emitted('change-structure')![0]![0] as WarehouseStructure
    expect(moved.zones![0]).toMatchObject({ left: 50, top: 40, width: 10, height: 10 })
    await wrapper.setProps({ structure: moved })
    await wrapper.get('[data-testid="zone-handle-se"]').trigger('pointerdown', { button: 0, pointerId: 3, clientX: 600, clientY: 400 })
    await svg.trigger('pointerup', { pointerId: 3, clientX: 650, clientY: 440 })
    const resized = wrapper.emitted('change-structure')![1]![0] as WarehouseStructure
    expect(resized.zones![0]).toMatchObject({ left: 50, top: 40, width: 15 })
    expect(resized.zones![0]!.height).toBeCloseTo(15)
    expect(wrapper.emitted('move-pallet')).toBeUndefined()
    expect(wrapper.get('[data-testid="planner-pallet-pallet-a01"]').attributes('style')).toBe(goodsBefore)
  })
  it('renames and deletes only the selected zone', async () => {
    const { wrapper } = setup()
    await wrapper.get('[data-testid="planner-zone-zone-test"]').trigger('keydown', { key: 'Enter' })
    await wrapper.get('[data-testid="zone-edit"]').trigger('click')
    await wrapper.get('[data-testid="zone-name"]').setValue(' ')
    await wrapper.get('[data-testid="zone-save"]').trigger('submit')
    expect(wrapper.emitted('change-structure')).toBeUndefined()
    await wrapper.get('[data-testid="zone-name"]').setValue('待发货区')
    await wrapper.get('[data-testid="zone-save"]').trigger('submit')
    const renamed = wrapper.emitted('change-structure')![0]![0] as WarehouseStructure
    expect(renamed.zones![0]!.label).toBe('待发货区')
    await wrapper.setProps({ structure: renamed })
    await wrapper.get('[data-testid="zone-delete"]').trigger('click')
    expect((wrapper.emitted('change-structure')![1]![0] as WarehouseStructure).zones).toEqual([])
    expect(wrapper.find('[data-testid="planner-pallet-pallet-a01"]').exists()).toBe(true)
    expect(wrapper.emitted('move-pallet')).toBeUndefined()
  })
  it('rejects a zone collision with both red outlines and restores the original position', async () => {
    vi.useFakeTimers()
    const { wrapper, svg } = setup([zone, { ...zone, id: 'zone-other', left: 55 }])
    const moving = wrapper.get('[data-testid="planner-zone-zone-test"]')
    await moving.trigger('pointerdown', { button: 0, pointerId: 4, clientX: 450, clientY: 360 })
    await svg.trigger('pointerup', { pointerId: 4, clientX: 550, clientY: 360 })
    expect(wrapper.emitted('change-structure')).toBeUndefined()
    expect(moving.attributes('transform')).toBe('translate(240,160)')
    expect(moving.classes()).toContain('invalid')
    expect(wrapper.get('[data-testid="planner-zone-zone-other"]').classes()).toContain('invalid')
    await vi.advanceTimersByTimeAsync(1300)
    expect(moving.classes()).not.toContain('invalid')
  })
  it('cancels unfinished drawing and property edits without recording changes', async () => {
    const { wrapper, svg } = setup([])
    await svg.trigger('pointerdown', { button: 0, pointerId: 5, clientX: 400, clientY: 320 })
    await svg.trigger('pointerup', { pointerId: 5, clientX: 500, clientY: 400 })
    await wrapper.get('[data-testid="zone-cancel"]').trigger('click')
    expect(wrapper.emitted('change-structure')).toBeUndefined()
    expect(wrapper.find('[data-testid^="planner-zone-"]').exists()).toBe(false)
    expect(wrapper.emitted('structure-busy')?.at(-1)).toEqual([false])
    await svg.trigger('pointerdown', { button: 0, pointerId: 6, clientX: 400, clientY: 320 })
    window.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' }))
    await svg.trigger('pointerup', { pointerId: 6, clientX: 500, clientY: 400 })
    expect(wrapper.find('[data-testid="zone-name"]').exists()).toBe(false)
    expect(wrapper.emitted('change-structure')).toBeUndefined()
  })
  it('ignores zone and pile drags in completed mode', async () => {
    const { wrapper, svg } = setup()
    await wrapper.setProps({ inventoryDetailsVisible: true })
    expect(wrapper.find('[data-testid="zone-tool-draw"]').exists()).toBe(false)
    await wrapper.get('[data-testid="planner-zone-zone-test"]').trigger('pointerdown', { button: 0, pointerId: 7, clientX: 450, clientY: 360 })
    await svg.trigger('pointerup', { pointerId: 7, clientX: 550, clientY: 360 })
    expect(wrapper.emitted('change-structure')).toBeUndefined()
  })
})
