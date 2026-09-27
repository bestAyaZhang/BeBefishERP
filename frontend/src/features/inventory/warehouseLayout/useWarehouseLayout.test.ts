import { describe, expect, it } from 'vitest'
import { createWarehouseLayoutRepository, seedWarehouseLayouts } from './mockWarehouseLayoutRepository'
import { useWarehouseLayout } from './useWarehouseLayout'

function createEditor() {
  return useWarehouseLayout({ repository: createWarehouseLayoutRepository(seedWarehouseLayouts) })
}

describe('useWarehouseLayout', () => {
  it('loads the requested warehouse without substituting demo data', async () => {
    const editor = createEditor()

    await editor.load(9)

    expect(editor.state.value).toMatchObject({ warehouseId: 9, warehouseName: '义乌备货仓' })
    expect(editor.selectedObjectId.value).toBeNull()
  })

  it('synchronizes selection and property edits with undo and redo', async () => {
    const editor = createEditor()
    await editor.load(8)

    editor.selectObject('location-a13')
    editor.updateSelectedObject({ name: '成品定位 A-13 新', x: 86, y: 56 })

    expect(editor.selectedObject.value?.name).toBe('成品定位 A-13 新')
    expect(editor.dirty.value).toBe(true)
    expect(editor.canUndo.value).toBe(true)

    editor.undo()
    expect(editor.selectedObject.value?.name).toBe('成品定位 A-13')

    editor.redo()
    expect(editor.selectedObject.value?.name).toBe('成品定位 A-13 新')
  })

  it('discards unsaved edits and restores the saved draft', async () => {
    const editor = createEditor()
    await editor.load(8)
    editor.selectObject('location-a13')
    editor.updateSelectedObject({ name: '临时名称' })

    editor.discard()

    expect(editor.selectedObject.value?.name).toBe('成品定位 A-13')
    expect(editor.dirty.value).toBe(false)
    expect(editor.canUndo.value).toBe(false)
  })

  it('saves a corrected draft and reloads it for the same warehouse', async () => {
    const repository = createWarehouseLayoutRepository(seedWarehouseLayouts)
    const editor = useWarehouseLayout({ repository })
    await editor.load(9)
    editor.selectObject('location-a13')
    editor.updateSelectedObject({ x: 86, y: 56 })

    await editor.saveDraft()

    expect(editor.dirty.value).toBe(false)
    expect((await repository.load(9)).objects.find((object) => object.id === 'location-a13')).toMatchObject({ x: 86, y: 56 })
  })

  it('blocks publishing while errors remain and selects the first problem object', async () => {
    const editor = createEditor()
    await editor.load(8)

    const published = await editor.publish()

    expect(published).toBe(false)
    expect(editor.selectedObjectId.value).toBe('location-a13')
    expect(editor.issues.value.filter((issue) => issue.severity === 'error')).toHaveLength(2)
  })

  it('publishes a corrected layout and advances its version', async () => {
    const editor = createEditor()
    await editor.load(8)
    editor.selectObject('location-a13')
    editor.updateSelectedObject({ x: 86, y: 56 })

    const published = await editor.publish()

    expect(published).toBe(true)
    expect(editor.state.value).toMatchObject({ status: 'published', version: 4, publishedVersion: 4 })
    expect(editor.dirty.value).toBe(false)
  })

  it('creates a new layout object with history and makes it the selection', async () => {
    const editor = createEditor()
    await editor.load(8)

    editor.createObject({
      id: 'obstacle-created-1', type: 'obstacle', code: '障碍-02', name: '消防栓',
      x: 90, y: 60, width: 6, height: 5, rotation: 0, visible: true, locked: false,
    })

    expect(editor.selectedObjectId.value).toBe('obstacle-created-1')
    expect(editor.state.value.objects).toContainEqual(expect.objectContaining({ id: 'obstacle-created-1', name: '消防栓' }))
    editor.undo()
    expect(editor.state.value.objects.some((object) => object.id === 'obstacle-created-1')).toBe(false)
  })
})
