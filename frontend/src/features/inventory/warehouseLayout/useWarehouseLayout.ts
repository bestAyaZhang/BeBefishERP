import { computed, ref } from 'vue'
import { createWarehouseLayoutRepository } from './mockWarehouseLayoutRepository'
import { addLayoutObject, cloneWarehouseLayout, updateLayoutObject, validateWarehouseLayout } from './warehouseLayoutModel'
import type { LayoutObject, LayoutObjectPatch, LayoutTool, WarehouseLayoutRepository, WarehouseLayoutState } from './types'

const emptyState: WarehouseLayoutState = {
  warehouseId: 0,
  warehouseCode: '',
  warehouseName: '',
  warehouseAddress: '',
  version: 0,
  publishedVersion: 0,
  status: 'draft',
  savedAt: '',
  objects: [],
}

export function useWarehouseLayout(options: { repository?: WarehouseLayoutRepository } = {}) {
  const repository = options.repository ?? createWarehouseLayoutRepository()
  const state = ref<WarehouseLayoutState>(cloneWarehouseLayout(emptyState))
  const savedState = ref<WarehouseLayoutState>(cloneWarehouseLayout(emptyState))
  const selectedObjectId = ref<string | null>(null)
  const activeTool = ref<LayoutTool>('select')
  const loading = ref(false)
  const saving = ref(false)
  const error = ref('')
  const notice = ref('')
  const undoHistory = ref<WarehouseLayoutState[]>([])
  const redoHistory = ref<WarehouseLayoutState[]>([])

  const issues = computed(() => validateWarehouseLayout(state.value))
  const selectedObject = computed(() => state.value.objects.find((object) => object.id === selectedObjectId.value))
  const dirty = computed(() => !statesEqual(state.value, savedState.value))
  const canUndo = computed(() => undoHistory.value.length > 0)
  const canRedo = computed(() => redoHistory.value.length > 0)

  async function load(warehouseId: number): Promise<void> {
    loading.value = true
    error.value = ''
    notice.value = ''
    try {
      const loaded = cloneWarehouseLayout(await repository.load(warehouseId))
      state.value = loaded
      savedState.value = cloneWarehouseLayout(loaded)
      selectedObjectId.value = null
      activeTool.value = 'select'
      undoHistory.value = []
      redoHistory.value = []
    } catch (cause) {
      state.value = cloneWarehouseLayout(emptyState)
      savedState.value = cloneWarehouseLayout(emptyState)
      error.value = cause instanceof Error ? cause.message : '仓库布局加载失败'
      throw cause
    } finally {
      loading.value = false
    }
  }

  function selectObject(objectId: string | null): void {
    if (objectId !== null && !state.value.objects.some((object) => object.id === objectId)) {
      throw new Error(`未找到布局对象：${objectId}`)
    }
    selectedObjectId.value = objectId
  }

  function setTool(tool: LayoutTool): void {
    activeTool.value = tool
  }

  function updateSelectedObject(patch: LayoutObjectPatch): void {
    if (!selectedObjectId.value) throw new Error('请先选择布局对象')
    mutate((current) => updateLayoutObject(current, selectedObjectId.value!, patch))
  }

  function createObject(object: LayoutObject): void {
    mutate((current) => addLayoutObject(current, object))
    selectedObjectId.value = object.id
    activeTool.value = 'select'
  }

  function toggleObjectVisibility(objectId: string): void {
    const object = requireObject(objectId)
    mutate((current) => updateLayoutObject(current, objectId, { visible: !object.visible }))
  }

  function toggleObjectLock(objectId: string): void {
    const object = requireObject(objectId)
    mutate((current) => updateLayoutObject(current, objectId, { locked: !object.locked }))
  }

  function undo(): void {
    const previous = undoHistory.value.pop()
    if (!previous) return
    redoHistory.value.push(cloneWarehouseLayout(state.value))
    state.value = previous
    normalizeSelection()
  }

  function redo(): void {
    const next = redoHistory.value.pop()
    if (!next) return
    undoHistory.value.push(cloneWarehouseLayout(state.value))
    state.value = next
    normalizeSelection()
  }

  function discard(): void {
    state.value = cloneWarehouseLayout(savedState.value)
    undoHistory.value = []
    redoHistory.value = []
    notice.value = '已放弃未保存的更改'
    normalizeSelection()
  }

  async function saveDraft(): Promise<void> {
    saving.value = true
    error.value = ''
    try {
      const saved = cloneWarehouseLayout(await repository.saveDraft(state.value))
      state.value = saved
      savedState.value = cloneWarehouseLayout(saved)
      undoHistory.value = []
      redoHistory.value = []
      notice.value = '草稿已保存'
    } catch (cause) {
      error.value = cause instanceof Error ? cause.message : '草稿保存失败'
      throw cause
    } finally {
      saving.value = false
    }
  }

  async function publish(): Promise<boolean> {
    const firstBlocking = issues.value.find((issue) => issue.severity === 'error')
    if (firstBlocking) {
      selectedObjectId.value = firstBlocking.objectId || null
      return false
    }
    saving.value = true
    error.value = ''
    try {
      const published = cloneWarehouseLayout(await repository.publish(state.value))
      state.value = published
      savedState.value = cloneWarehouseLayout(published)
      undoHistory.value = []
      redoHistory.value = []
      notice.value = `布局 v${published.version} 已发布`
      return true
    } catch (cause) {
      error.value = cause instanceof Error ? cause.message : '布局发布失败'
      throw cause
    } finally {
      saving.value = false
    }
  }

  function mutate(operation: (current: WarehouseLayoutState) => WarehouseLayoutState): void {
    const next = operation(state.value)
    if (statesEqual(next, state.value)) return
    undoHistory.value.push(cloneWarehouseLayout(state.value))
    state.value = next
    redoHistory.value = []
    notice.value = ''
  }

  function requireObject(objectId: string): LayoutObject {
    const object = state.value.objects.find((candidate) => candidate.id === objectId)
    if (!object) throw new Error(`未找到布局对象：${objectId}`)
    return object
  }

  function normalizeSelection(): void {
    if (selectedObjectId.value && !state.value.objects.some((object) => object.id === selectedObjectId.value)) {
      selectedObjectId.value = null
    }
  }

  return {
    state,
    selectedObjectId,
    selectedObject,
    activeTool,
    loading,
    saving,
    error,
    notice,
    issues,
    dirty,
    canUndo,
    canRedo,
    load,
    selectObject,
    setTool,
    updateSelectedObject,
    createObject,
    toggleObjectVisibility,
    toggleObjectLock,
    undo,
    redo,
    discard,
    saveDraft,
    publish,
  }
}

function statesEqual(left: WarehouseLayoutState, right: WarehouseLayoutState): boolean {
  return JSON.stringify(left) === JSON.stringify(right)
}
