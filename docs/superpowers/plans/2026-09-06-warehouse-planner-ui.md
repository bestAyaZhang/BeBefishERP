# Warehouse Planner UI Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Turn the current warehouse canvas route into a reference-matched, reviewable lightweight professional planner UI while preserving the existing inventory controller and “个” authoritative-unit rules.

**Architecture:** Keep the domain model, `useWarehouseCanvas`, and inventory dialogs unchanged. Add a controlled planner chrome, a typed presentation scene, and a floating inspector, then integrate them into `WarehouseCanvasView`. The scene is an explicit UI fixture for design review; it does not pretend to be a persisted CAD model.

**Tech Stack:** Vue 3, TypeScript, scoped CSS, Tailwind utilities, `lucide-vue-next`, Vitest, Vue Test Utils, Vite.

**Spec:** `docs/superpowers/specs/2026-09-06-warehouse-planner-ui-design.md`

## Global Constraints

- UI prototype only: no backend API, data migration, or new inventory persistence.
- Inventory displays “个” as authoritative; cases are derived and read-only.
- Goods look like floor-stacked cartons or pallets, never warehouse racks.
- Match the supplied visual direction: white drafting surface, gray structure, muted zones, tan cartons, cyan selection, blue primary action.
- Reuse Lucide icons; do not add emoji or custom icon drawings.
- Keep existing controller, dialogs, quantity conservation, and undo/redo code available.
- Optimize for `1440 × 1024`; keep the planning surface usable from `1280px` to `1600px`.
- Compare the implementation and reference at equal scale on one comparison surface and complete at least one correction pass.

---

### Task 1: Controlled planner chrome

**Files:**
- Create: `frontend/src/features/inventory/warehouseCanvas/components/WarehousePlannerChrome.vue`
- Test: `frontend/src/features/inventory/warehouseCanvas/components/WarehousePlannerChrome.test.ts`

**Interfaces:**
- Produces `PlannerUiTool = 'structure' | 'zone' | 'goods' | 'measure'`.
- Consumes warehouse name, active tool, measurement/grid states, and undo/redo availability.
- Emits `change-tool`, `toggle-measurement`, `toggle-grid`, `undo`, `redo`, and `complete`.

- [ ] **Step 1: Write the failing behavior test**

```ts
it('shows the planning tools and emits explicit actions', async () => {
  const wrapper = mount(WarehousePlannerChrome, { props: {
    warehouseName: '一号仓', activeTool: 'goods', measurementEnabled: false,
    gridSnapping: true, canUndo: true, canRedo: false,
  } })
  expect(wrapper.get('[data-testid="planner-title"]').text()).toBe('一号仓 · 平面规划')
  expect(wrapper.get('[data-testid="planner-tool-goods"]').attributes('aria-pressed')).toBe('true')
  await wrapper.get('[data-testid="planner-tool-zone"]').trigger('click')
  await wrapper.get('[data-testid="planner-measure-toggle"]').trigger('click')
  await wrapper.get('[data-testid="planner-grid-toggle"]').trigger('click')
  expect(wrapper.emitted('change-tool')?.[0]).toEqual(['zone'])
  expect(wrapper.emitted('toggle-measurement')).toHaveLength(1)
  expect(wrapper.emitted('toggle-grid')).toHaveLength(1)
})
```

- [ ] **Step 2: Verify RED**

Run: `npm run test:run -- src/features/inventory/warehouseCanvas/components/WarehousePlannerChrome.test.ts`

Expected: FAIL because the component does not exist.

- [ ] **Step 3: Implement the chrome**

Create a 56px top bar and a floating left tool rail. Use Lucide `Undo2`, `Redo2`, `Ruler`, `Grid2X2`, `PanelsTopLeft`, `Scan`, `PackageOpen`, and `MousePointer2`. Bind `aria-pressed` on toggles and `disabled` on undo/redo. Keep the component controlled so the route remains the single state owner.

- [ ] **Step 4: Verify GREEN and commit**

Run: `npm run test:run -- src/features/inventory/warehouseCanvas/components/WarehousePlannerChrome.test.ts`

```bash
git add frontend/src/features/inventory/warehouseCanvas/components/WarehousePlannerChrome.vue frontend/src/features/inventory/warehouseCanvas/components/WarehousePlannerChrome.test.ts
git commit -m "feat: add warehouse planner chrome"
```

---

### Task 2: Typed warehouse blueprint scene

**Files:**
- Create: `frontend/src/features/inventory/warehouseCanvas/warehousePlannerScene.ts`
- Create: `frontend/src/features/inventory/warehouseCanvas/components/WarehouseBlueprintScene.vue`
- Test: `frontend/src/features/inventory/warehouseCanvas/components/WarehouseBlueprintScene.test.ts`

**Interfaces:**
- Produces typed doors, zones, aisles, rooms, fire lanes, columns, and pallet-group fixtures.
- Scene props: `gridSnapping: boolean`, `measurementEnabled: boolean`, `selectedPalletId: string | null`.
- Scene emit: `select-pallet: [id: string]`.

- [ ] **Step 1: Write the failing scene test**

```ts
it('renders the reference layers and selects floor-stacked goods', async () => {
  const wrapper = mount(WarehouseBlueprintScene, { props: {
    gridSnapping: true, measurementEnabled: false, selectedPalletId: 'pallet-c018',
  } })
  expect(wrapper.findAll('[data-testid^="planner-loading-door-"]')).toHaveLength(3)
  expect(wrapper.text()).toContain('收货区')
  expect(wrapper.text()).toContain('暂存区')
  expect(wrapper.text()).toContain('发货区')
  expect(wrapper.text()).toContain('叉车通道 4.0m')
  expect(wrapper.text()).toContain('消防留空区')
  expect(wrapper.get('[data-testid="planner-pallet-pallet-c018"]').attributes('data-selected')).toBe('true')
  await wrapper.get('[data-testid="planner-pallet-pallet-a01"]').trigger('click')
  expect(wrapper.emitted('select-pallet')?.[0]).toEqual(['pallet-a01'])
})
```

- [ ] **Step 2: Verify RED**

Run: `npm run test:run -- src/features/inventory/warehouseCanvas/components/WarehouseBlueprintScene.test.ts`

Expected: FAIL because the scene files do not exist.

- [ ] **Step 3: Define literal UI fixture geometry**

Use percentage geometry measured from the supplied reference. Include exactly three loading doors, three top zones, four labeled forklift aisles, two bottom rooms, two fire-lane segments, structural columns, and at least eighteen pallet groups. Define `pallet-c018` with `32.4m`, `21.8m`, `4.8m × 2.4m`, `90°`, `SKU-C-018`, `250 个`, and `24 个/件`.

- [ ] **Step 4: Implement the scene**

Render top/left meter rulers, a dense drafting grid, irregular gray warehouse shell with a slanted right side, loading doors, muted dashed zones, aisle arrows, rooms, fire-lane hatching, columns, and tan box groups. The selected group uses cyan outline, eight resize points, and a rotation handle. Measurement labels render only when `measurementEnabled` is true.

- [ ] **Step 5: Verify GREEN and commit**

Run: `npm run test:run -- src/features/inventory/warehouseCanvas/components/WarehouseBlueprintScene.test.ts`

```bash
git add frontend/src/features/inventory/warehouseCanvas/warehousePlannerScene.ts frontend/src/features/inventory/warehouseCanvas/components/WarehouseBlueprintScene.vue frontend/src/features/inventory/warehouseCanvas/components/WarehouseBlueprintScene.test.ts
git commit -m "feat: add warehouse blueprint scene"
```

---

### Task 3: Floating inspector, minimap, and status strip

**Files:**
- Create: `frontend/src/features/inventory/warehouseCanvas/components/WarehousePlannerInspector.vue`
- Test: `frontend/src/features/inventory/warehouseCanvas/components/WarehousePlannerInspector.test.ts`
- Modify: `frontend/src/features/inventory/warehouseCanvas/components/WarehouseBlueprintScene.vue`
- Modify: `frontend/src/features/inventory/warehouseCanvas/components/WarehouseBlueprintScene.test.ts`

**Interfaces:**
- Inspector prop: `pallet: PlannerPalletGroup`; emit: `close`.
- Inspector imports `formatCaseBreakdown` and never stores a case quantity.
- Scene exposes `planner-minimap` and `planner-coordinate-status` test IDs.

- [ ] **Step 1: Write the failing readout test**

```ts
it('shows authoritative units and derived cases', () => {
  const pallet = warehousePlannerScene.palletGroups.find((item) => item.id === 'pallet-c018')!
  const wrapper = mount(WarehousePlannerInspector, { props: { pallet } })
  expect(wrapper.text()).toContain('地面货堆 C-018')
  expect(wrapper.get('[data-testid="planner-inspector-units"]').text()).toBe('250 个')
  expect(wrapper.get('[data-testid="planner-inspector-cases"]').text()).toBe('10 件 + 10 个')
  expect(wrapper.text()).toContain('32.4m')
  expect(wrapper.text()).toContain('90°')
})
```

- [ ] **Step 2: Verify RED**

Run: `npm run test:run -- src/features/inventory/warehouseCanvas/components/WarehousePlannerInspector.test.ts`

Expected: FAIL because the inspector does not exist.

- [ ] **Step 3: Implement inspector and navigation aids**

Add a compact white property card beside the selected pallet showing title, X/Y, length, width, rotation, SKU, authoritative units, and derived cases. Add a presentation-only minimap and a status strip with `X 32.4m`, `Y 21.8m`, `比例 1:100`, and zoom.

- [ ] **Step 4: Verify GREEN and commit**

Run: `npm run test:run -- src/features/inventory/warehouseCanvas/components/WarehousePlannerInspector.test.ts src/features/inventory/warehouseCanvas/components/WarehouseBlueprintScene.test.ts`

```bash
git add frontend/src/features/inventory/warehouseCanvas/components/WarehousePlannerInspector.vue frontend/src/features/inventory/warehouseCanvas/components/WarehousePlannerInspector.test.ts frontend/src/features/inventory/warehouseCanvas/components/WarehouseBlueprintScene.vue frontend/src/features/inventory/warehouseCanvas/components/WarehouseBlueprintScene.test.ts
git commit -m "feat: add planner inspector and minimap"
```

---

### Task 4: Integrate the planner into the route

**Files:**
- Modify: `frontend/src/features/inventory/views/WarehouseCanvasView.vue`
- Modify: `frontend/src/features/inventory/WarehouseCanvasView.test.ts`

**Interfaces:**
- View owns `plannerTool`, `measurementEnabled`, `gridSnapping`, and `selectedPalletId`.
- Existing tool mapping: `zone -> draw`, `structure -> select`, `goods -> select`, `measure -> pan`.
- Existing `useWarehouseCanvas` remains responsible for inventory and history.

- [ ] **Step 1: Write the failing route-shell test**

```ts
it('renders the professional planner and drives UI-only toggles', async () => {
  const wrapper = await mountPage()
  expect(wrapper.get('[data-testid="planner-title"]').text()).toContain('一号仓 · 平面规划')
  expect(wrapper.get('[data-testid="planner-tool-goods"]').attributes('aria-pressed')).toBe('true')
  await wrapper.get('[data-testid="planner-measure-toggle"]').trigger('click')
  await wrapper.get('[data-testid="planner-grid-toggle"]').trigger('click')
  expect(wrapper.get('[data-testid="warehouse-blueprint-scene"]').attributes('data-measuring')).toBe('true')
  expect(wrapper.get('[data-testid="warehouse-blueprint-scene"]').attributes('data-grid-snapping')).toBe('false')
  await wrapper.get('[data-testid="planner-complete"]').trigger('click')
  expect(wrapper.get('[role="status"]').text()).toContain('UI 预览')
})
```

- [ ] **Step 2: Verify RED**

Run: `npm run test:run -- src/features/inventory/WarehouseCanvasView.test.ts`

Expected: FAIL because the route still uses the old header, toolbar, and three-column layout.

- [ ] **Step 3: Integrate the new UI**

Replace the visible old page chrome with `WarehousePlannerChrome` and `WarehouseBlueprintScene`. Retain the existing product, area-name, partial-move, deletion, and issue dialogs in the component so controller wiring is not discarded. The complete action shows `UI 预览已完成，功能将在视觉确认后继续设计`.

- [ ] **Step 4: Apply the full-bleed layout**

Reserve 56px for the top bar and 48px for status. The scene fills the remaining surface. Below 1280px, keep the rail visible and scroll only the central drafting viewport. Remove obsolete presentation assertions but retain controller tests for units, derived cases, undo/redo, validation, and quantity conservation.

- [ ] **Step 5: Verify GREEN and commit**

Run: `npm run test:run -- src/features/inventory/WarehouseCanvasView.test.ts`

```bash
git add frontend/src/features/inventory/views/WarehouseCanvasView.vue frontend/src/features/inventory/WarehouseCanvasView.test.ts
git commit -m "feat: redesign warehouse canvas as planner UI"
```

---

### Task 5: Visual and regression verification

**Files:**
- Modify as required: planner components from Tasks 1–4.
- Create: `prototype-screenshots/warehouse-planner/warehouse-planner-ui.png`
- Create: `prototype-screenshots/warehouse-planner/reference-and-implementation.png`

**Interfaces:** No new production interfaces.

- [ ] **Step 1: Run all tests**

Run: `npm run test:run`

Expected: all Vitest files pass with zero failures.

- [ ] **Step 2: Run the production build**

Run: `npm run build`

Expected: `vue-tsc --noEmit` and Vite both exit `0`.

- [ ] **Step 3: Preview in the selected Codex in-app browser**

Run: `npm run dev -- --port 5187`

Open `/inventory/warehouse-canvas` at `1440 × 1024` and capture `warehouse-planner-ui.png`.

- [ ] **Step 4: Compare and correct**

Place the supplied reference and implementation screenshot side-by-side at equal scale in one local comparison surface. Inspect density, ruler alignment, warehouse silhouette, tool proportions, type scale, selection treatment, and clipping. Fix every material mismatch found, then repeat tests, build, and the comparison capture.

- [ ] **Step 5: Commit verified polish**

```bash
git add frontend/src/features/inventory prototype-screenshots/warehouse-planner
git commit -m "test: verify warehouse planner UI"
```

