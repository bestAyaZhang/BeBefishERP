# Warehouse Canvas Frontend Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement the approved Figma warehouse-canvas MVP as a functional desktop Vue page where users draw areas, place and resize SKU blocks, edit authoritative unit counts, and move whole or partial inventory while preserving totals.

**Architecture:** Add a focused `warehouseCanvas` feature under the existing inventory module. Keep geometry and inventory rules in pure TypeScript, wrap them in a small Vue controller with undo/redo and a module-scoped mock repository, and render the approved three-panel workspace inside the existing ERP shell. Add one authenticated route and one inventory-navigation entry; do not add backend endpoints in this phase.

**Tech Stack:** Vue 3 Composition API, TypeScript, Vue Router, Tailwind CSS, `lucide-vue-next`, Vitest, Vue Test Utils.

**Spec:** `docs/superpowers/specs/2026-09-05-warehouse-canvas-inventory-design.md`

## Global Constraints

- Implement the approved Figma source `jIz9HNkSoXH63gvTc3yOtj`, especially Overview `1187:2`, Draw Area `1190:516`, Add SKU `1193:39676`, SKU Selected `1193:40024`, Partial Move `1193:40372`, and Warning `1210:2466`.
- Add route `/inventory/warehouse-canvas` with route name `warehouse-canvas` and permission `inventory:view`.
- Reuse the existing `ErpLayout`, `SidebarNav`, Tailwind palette, Noto Sans SC/Inter fonts, and `lucide-vue-next`; do not add a UI framework, new font, handcrafted SVG, or raster placeholder.
- Keep a logical floor coordinate system of `728×672`. Responsive rendering may scale this plane visually, but saved `x`, `y`, `width`, and `height` remain logical integers.
- Inventory `units` is the only authoritative stored quantity. `unitsPerCase`, whole cases, and remainder are derived display data.
- Area and SKU geometry never derives from or changes inventory quantity.
- Whole moves change only `areaId` and geometry. Partial moves require an integer `0 < movedUnits < source.units` and preserve `sourceAfter + target = sourceBefore`.
- Prevent save when any visible SKU block lies outside its area or overlaps another visible block in the same area. Warnings never mutate inventory.
- Implement local/mock persistence only. Do not add or change backend APIs, migrations, authentication, batches, expiry, stocktake, capacity, shelves, racks, CAD, 3D, or cross-warehouse transfer.
- Preserve the existing 32-file/314-test baseline and keep every new domain behavior under test-first red/green cycles.
- The final handoff requires `design-qa.md` with `final result: passed`, a clean browser console, a passing production build, and the local preview kept open.

## File Structure

- Create `frontend/src/features/inventory/warehouseCanvas/types.ts` — stable domain interfaces and command payloads.
- Create `frontend/src/features/inventory/warehouseCanvas/warehouseCanvasModel.ts` — pure quantity, geometry, validation, summary, and movement functions.
- Create `frontend/src/features/inventory/warehouseCanvas/warehouseCanvasModel.test.ts` — unit and invariant tests.
- Create `frontend/src/features/inventory/warehouseCanvas/mockWarehouseCanvasData.ts` — exact approved MVP seed data and in-memory load/save adapter.
- Create `frontend/src/features/inventory/warehouseCanvas/useWarehouseCanvas.ts` — reactive controller, selection, history, and save state.
- Create `frontend/src/features/inventory/warehouseCanvas/useWarehouseCanvas.test.ts` — controller behavior tests.
- Create `frontend/src/features/inventory/warehouseCanvas/components/WarehouseFloorCanvas.vue` — area/SKU rendering and pointer interactions.
- Create `frontend/src/features/inventory/warehouseCanvas/components/WarehouseFloorCanvas.test.ts` — component interaction tests.
- Create `frontend/src/features/inventory/warehouseCanvas/components/WarehouseObjectPanel.vue` — selected-area/SKU properties and inline issues.
- Create `frontend/src/features/inventory/warehouseCanvas/components/WarehouseProductDrawer.vue` — add-SKU and partial-move forms.
- Create `frontend/src/features/inventory/views/WarehouseCanvasView.vue` — page header, filters, toolbar, three panels, dialogs, and orchestration.
- Create `frontend/src/features/inventory/WarehouseCanvasView.test.ts` — end-to-end component-flow tests.
- Modify `frontend/src/router/index.ts` — lazy route registration.
- Modify `frontend/src/features/navigation/mockNavigationService.ts` — inventory child navigation.
- Modify `frontend/src/layouts/ErpLayout.vue` — breadcrumb metadata and route-scoped flush content.
- Modify `frontend/src/features/masterdata/views/WarehouseView.vue` — row action to open the selected warehouse canvas.
- Modify `frontend/src/router/index.test.ts`, `frontend/src/AppRouting.test.ts`, `frontend/src/components/navigation/SidebarNav.test.ts`, and `frontend/src/features/masterdata/views/MasterdataViews.test.ts` — integration coverage.
- Modify `design-qa.md` — final Figma-versus-browser evidence and pass state.

---

### Task 1: Domain Model, Geometry, And Inventory Invariants

**Files:**
- Create: `frontend/src/features/inventory/warehouseCanvas/types.ts`
- Create: `frontend/src/features/inventory/warehouseCanvas/warehouseCanvasModel.ts`
- Create: `frontend/src/features/inventory/warehouseCanvas/warehouseCanvasModel.test.ts`
- Create: `frontend/src/features/inventory/warehouseCanvas/mockWarehouseCanvasData.ts`

**Interfaces:**
- Consumes: the approved unit/case, area geometry, overlap, whole-move, and partial-move rules.
- Produces: `WarehouseCanvasState`, `formatCaseBreakdown`, `summarizeCanvas`, `validateCanvasLayout`, `moveWholeBlock`, `splitBlock`, and `findOpenPosition` for later tasks.

- [ ] **Step 1: Write failing quantity and summary tests**

```ts
expect(formatCaseBreakdown(250, 24)).toBe('10 件 + 10 个');
expect(formatCaseBreakdown(576, 24)).toBe('24 件');
expect(formatCaseBreakdown(0, 24)).toBe('0 件');
expect(summarizeCanvas(seed).totalUnits).toBe(1026);
expect(summarizeCanvas(seed).skuCount).toBe(4);
expect(summarizeCanvas(seed).areaTotals).toEqual({ 'area-a': 270, 'area-b': 656, 'area-c': 100 });
```

- [ ] **Step 2: Run the quantity tests and verify RED**

Run: `cd frontend && npm run test:run -- src/features/inventory/warehouseCanvas/warehouseCanvasModel.test.ts`

Expected: FAIL because the warehouse-canvas module does not exist.

- [ ] **Step 3: Add the domain interfaces and minimal quantity functions**

```ts
export type CanvasTool = 'select' | 'draw' | 'pan';
export type CanvasRect = { x: number; y: number; width: number; height: number };
export type WarehouseArea = CanvasRect & { id: string; name: string; visible: boolean; locked: boolean };
export type WarehouseSku = {
  id: string;
  skuId: number;
  skuCode: string;
  productName: string;
  unitsPerCase: number;
  accent: 'blue' | 'pink' | 'green' | 'cyan';
};
export type WarehouseSkuBlock = CanvasRect & { id: string; skuId: number; areaId: string; units: number };
export type WarehouseCanvasState = {
  warehouseId: number;
  warehouseName: string;
  areas: WarehouseArea[];
  catalog: WarehouseSku[];
  blocks: WarehouseSkuBlock[];
};
export type LayoutIssue = {
  id: string;
  type: 'outside-area' | 'overlap';
  blockIds: string[];
  message: string;
};

export function formatCaseBreakdown(units: number, unitsPerCase: number): string;
export function summarizeCanvas(state: WarehouseCanvasState): {
  totalUnits: number;
  skuCount: number;
  areaTotals: Record<string, number>;
};
```

- [ ] **Step 4: Write failing layout and movement tests**

```ts
expect(validateCanvasLayout(outsideState)[0]).toMatchObject({ type: 'outside-area', blockIds: ['block-blue-a'] });
expect(validateCanvasLayout(overlapState)[0]).toMatchObject({ type: 'overlap', blockIds: ['block-pink-b', 'block-tea-b'] });

const whole = moveWholeBlock(seed, 'block-blue-a', 'area-b', { x: 76, y: 500 });
expect(whole.blocks.find((block) => block.id === 'block-blue-a')).toMatchObject({ areaId: 'area-b', units: 150 });

const partial = splitBlock(seed, { blockId: 'block-blue-a', targetAreaId: 'area-b', movedUnits: 60, target: { x: 76, y: 500 } });
expect(partial.blocks.find((block) => block.id === 'block-blue-a')?.units).toBe(90);
expect(partial.blocks.reduce((total, block) => block.skuId === 101 ? total + block.units : total, 0)).toBe(250);
expect(() => splitBlock(seed, { blockId: 'block-blue-a', targetAreaId: 'area-b', movedUnits: 150, target: { x: 76, y: 500 } })).toThrow('移动个数必须大于 0 且小于来源库存');
```

- [ ] **Step 5: Run the movement tests and verify RED**

Run: `cd frontend && npm run test:run -- src/features/inventory/warehouseCanvas/warehouseCanvasModel.test.ts`

Expected: FAIL because validation and movement functions are missing.

- [ ] **Step 6: Implement rectangle validation and immutable movement functions**

```ts
export function validateCanvasLayout(state: WarehouseCanvasState): LayoutIssue[];
export function moveWholeBlock(
  state: WarehouseCanvasState,
  blockId: string,
  targetAreaId: string,
  target: Pick<CanvasRect, 'x' | 'y'>
): WarehouseCanvasState;
export function splitBlock(
  state: WarehouseCanvasState,
  command: { blockId: string; targetAreaId: string; movedUnits: number; target: Pick<CanvasRect, 'x' | 'y'> }
): WarehouseCanvasState;
export function findOpenPosition(
  state: WarehouseCanvasState,
  areaId: string,
  size: Pick<CanvasRect, 'width' | 'height'>,
  excludedBlockId?: string
): Pick<CanvasRect, 'x' | 'y'> | null;
```

The default seed must contain areas A-01/B-01/C-01, four catalog SKUs, five blocks, blue SKU total `150 + 100 = 250`, area totals `270 / 656 / 100`, and warehouse total `1,026` units.

- [ ] **Step 7: Verify Task 1 GREEN and commit**

Run: `cd frontend && npm run test:run -- src/features/inventory/warehouseCanvas/warehouseCanvasModel.test.ts`

Expected: PASS.

```bash
git add frontend/src/features/inventory/warehouseCanvas
git commit -m "feat: add warehouse canvas domain model"
```

---

### Task 2: Reactive Controller, History, And Save State

**Files:**
- Create: `frontend/src/features/inventory/warehouseCanvas/useWarehouseCanvas.ts`
- Create: `frontend/src/features/inventory/warehouseCanvas/useWarehouseCanvas.test.ts`
- Modify: `frontend/src/features/inventory/warehouseCanvas/mockWarehouseCanvasData.ts`

**Interfaces:**
- Consumes: Task 1 pure model and mock seed.
- Produces: `useWarehouseCanvas()` with state, selection, search, active tool, commands, history, validation, and save status.

- [ ] **Step 1: Write failing controller tests**

```ts
const canvas = useWarehouseCanvas({ repository: createMemoryWarehouseCanvasRepository(seed) });
await canvas.load(8);
canvas.renameArea('area-a', 'A-成品');
expect(canvas.state.value.areas[0].name).toBe('A-成品');
expect(canvas.dirty.value).toBe(true);
canvas.undo();
expect(canvas.state.value.areas[0].name).toBe('A-01');
canvas.redo();
expect(canvas.state.value.areas[0].name).toBe('A-成品');
```

Add separate tests for creating an area, toggling visibility/lock, editing unit counts, resizing without changing units, whole move, partial move conservation, blocked save with issues, successful save without issues, and search matching both blue blocks.

- [ ] **Step 2: Run controller tests and verify RED**

Run: `cd frontend && npm run test:run -- src/features/inventory/warehouseCanvas/useWarehouseCanvas.test.ts`

Expected: FAIL because the composable does not exist.

- [ ] **Step 3: Implement repository and controller API**

```ts
export interface WarehouseCanvasRepository {
  load(warehouseId: number): Promise<WarehouseCanvasState>;
  save(state: WarehouseCanvasState): Promise<WarehouseCanvasState>;
}

export function useWarehouseCanvas(options?: { repository?: WarehouseCanvasRepository }) {
  return {
    state,
    loading,
    dirty,
    saving,
    savedAt,
    selectedAreaId,
    selectedBlockId,
    activeTool,
    searchQuery,
    issues,
    canUndo,
    canRedo,
    load,
    createArea,
    renameArea,
    updateAreaRect,
    toggleAreaVisibility,
    toggleAreaLock,
    deleteArea,
    addBlock,
    updateBlockRect,
    updateBlockUnits,
    moveWholeBlock,
    splitBlock,
    deleteBlock,
    selectArea,
    selectBlock,
    undo,
    redo,
    save
  };
}
```

Every mutating command must snapshot the previous state, clear redo history, preserve integer coordinates, and leave `save()` disabled when `issues.length > 0`.

- [ ] **Step 4: Verify controller GREEN and commit**

Run: `cd frontend && npm run test:run -- src/features/inventory/warehouseCanvas/useWarehouseCanvas.test.ts src/features/inventory/warehouseCanvas/warehouseCanvasModel.test.ts`

Expected: PASS.

```bash
git add frontend/src/features/inventory/warehouseCanvas
git commit -m "feat: add warehouse canvas state controller"
```

---

### Task 3: Interactive Warehouse Floor

**Files:**
- Create: `frontend/src/features/inventory/warehouseCanvas/components/WarehouseFloorCanvas.vue`
- Create: `frontend/src/features/inventory/warehouseCanvas/components/WarehouseFloorCanvas.test.ts`

**Interfaces:**
- Consumes: Task 1 types and formatters.
- Produces: a keyboard-focusable, scaled `728×672` floor that emits semantic draw, select, move, and resize commands.

- [ ] **Step 1: Write failing render and selection tests**

```ts
const wrapper = mount(WarehouseFloorCanvas, { props: { state: seed, tool: 'select', selectedAreaId: null, selectedBlockId: null, issues: [], searchQuery: '' } });
expect(wrapper.findAll('[data-testid^="warehouse-area-"]')).toHaveLength(3);
expect(wrapper.findAll('[data-testid^="warehouse-sku-block-"]')).toHaveLength(5);
expect(wrapper.get('[data-testid="warehouse-sku-block-block-blue-a"]').text()).toContain('150 个');
expect(wrapper.get('[data-testid="warehouse-sku-block-block-blue-a"]').text()).toContain('6 件 + 6 个');
await wrapper.get('[data-testid="warehouse-sku-block-block-blue-a"]').trigger('click');
expect(wrapper.emitted('select-block')?.[0]).toEqual(['block-blue-a']);
```

- [ ] **Step 2: Run component tests and verify RED**

Run: `cd frontend && npm run test:run -- src/features/inventory/warehouseCanvas/components/WarehouseFloorCanvas.test.ts`

Expected: FAIL because the floor component does not exist.

- [ ] **Step 3: Implement the visible floor and warning states**

Render areas and blocks with absolute logical coordinates converted to percentages. Use Lucide `Check`, `Lock`, and `TriangleAlert`. Selected nodes require a solid brand outline and resize handle; warning nodes require an amber outline plus explicit visible text. Search dims nonmatches but keeps area boundaries at full opacity.

- [ ] **Step 4: Write failing pointer-interaction tests**

```ts
await floor.trigger('pointerdown', { clientX: 100, clientY: 100, pointerId: 1 });
await floor.trigger('pointermove', { clientX: 220, clientY: 190, pointerId: 1 });
await floor.trigger('pointerup', { clientX: 220, clientY: 190, pointerId: 1 });
expect(wrapper.emitted('create-area')?.[0]?.[0]).toMatchObject({ width: expect.any(Number), height: expect.any(Number) });

await block.trigger('pointerdown', { clientX: 130, clientY: 130, pointerId: 2 });
await floor.trigger('pointermove', { clientX: 180, clientY: 180, pointerId: 2 });
await floor.trigger('pointerup', { clientX: 180, clientY: 180, pointerId: 2 });
expect(wrapper.emitted('update-block-rect')?.[0]?.[0]).toMatchObject({ id: 'block-blue-a' });
```

- [ ] **Step 5: Implement draw, drag, resize, and keyboard nudging**

Use pointer capture, floor `getBoundingClientRect()`, and logical scale factors. Ignore area movement when locked. Clamp minimum area size to `80×60` and minimum block size to `96×72`. Arrow keys nudge the selected object by one logical unit; Shift+Arrow uses ten.

- [ ] **Step 6: Verify Task 3 GREEN and commit**

Run: `cd frontend && npm run test:run -- src/features/inventory/warehouseCanvas/components/WarehouseFloorCanvas.test.ts`

Expected: PASS.

```bash
git add frontend/src/features/inventory/warehouseCanvas/components
git commit -m "feat: build interactive warehouse floor"
```

---

### Task 4: Three-Panel Page, Draw/Add/Edit/Move Flows, And Warnings

**Files:**
- Create: `frontend/src/features/inventory/warehouseCanvas/components/WarehouseObjectPanel.vue`
- Create: `frontend/src/features/inventory/warehouseCanvas/components/WarehouseProductDrawer.vue`
- Create: `frontend/src/features/inventory/views/WarehouseCanvasView.vue`
- Create: `frontend/src/features/inventory/WarehouseCanvasView.test.ts`

**Interfaces:**
- Consumes: Task 2 controller and Task 3 floor events.
- Produces: the complete approved user flow within one route.

- [ ] **Step 1: Write a failing Overview test**

```ts
expect(wrapper.get('[data-testid="warehouse-canvas-view"]').text()).toContain('仓库画布');
expect(wrapper.get('[data-testid="warehouse-canvas-summary"]').text()).toContain('1,026 个库存总量');
expect(wrapper.get('[data-testid="warehouse-canvas-floor"]').exists()).toBe(true);
expect(wrapper.get('[data-testid="warehouse-product-library"]').text()).toContain('库存以“个”为权威值');
```

- [ ] **Step 2: Run the page test and verify RED**

Run: `cd frontend && npm run test:run -- src/features/inventory/WarehouseCanvasView.test.ts`

Expected: FAIL because the view does not exist.

- [ ] **Step 3: Build the responsive three-panel shell**

At desktop width, use `196px minmax(560px,1fr) 240px`; below `1180px`, allow horizontal workspace scrolling; below `1024px`, keep the existing mobile sidebar and stack the left/right panels around a minimum-width canvas. Match Figma copy, token colors, 40px controls, 8px radii, and compact typography.

- [ ] **Step 4: Write failing draw and add-SKU flow tests**

```ts
await wrapper.get('[data-testid="canvas-tool-draw"]').trigger('click');
wrapper.getComponent(WarehouseFloorCanvas).vm.$emit('create-area', { x: 520, y: 24, width: 160, height: 120 });
expect(wrapper.get('[data-testid="area-name-dialog"]').exists()).toBe(true);
await wrapper.get('[data-testid="area-name-input"]').setValue('D-01');
await wrapper.get('[data-testid="create-area-confirm"]').trigger('click');
expect(wrapper.text()).toContain('D-01');

await wrapper.get('[data-testid="add-warehouse-product"]').trigger('click');
await wrapper.get('[data-testid="add-product-area"]').setValue('area-b');
await wrapper.get('[data-testid="add-product-units"]').setValue('250');
expect(wrapper.get('[data-testid="add-product-case-copy"]').text()).toContain('10 件 + 10 个');
await wrapper.get('[data-testid="add-product-confirm"]').trigger('click');
expect(wrapper.text()).toContain('放入 B-01');
```

- [ ] **Step 5: Implement area creation and Add SKU drawer**

The drawer must show SKU identity, disabled/read-only `unitsPerCase`, integer `units`, derived case copy, target area, cancel, and confirm. `findOpenPosition` must choose a valid position or show `目标区域没有足够的空白位置` without mutating state.

- [ ] **Step 6: Write failing edit, whole-move, partial-move, and warning tests**

```ts
await selectBlueBlock(wrapper);
await wrapper.get('[data-testid="sku-units-input"]').setValue('240');
expect(wrapper.get('[data-testid="sku-case-copy"]').text()).toContain('10 件');

await wrapper.get('[data-testid="move-whole-area"]').setValue('area-b');
await wrapper.get('[data-testid="move-whole-confirm"]').trigger('click');
expect(selectedBlock(wrapper)).toMatchObject({ areaId: 'area-b', units: 240 });

await wrapper.get('[data-testid="open-partial-move"]').trigger('click');
await wrapper.get('[data-testid="partial-move-units"]').setValue('60');
expect(wrapper.get('[data-testid="partial-move-conservation"]').text()).toContain('180 + 60 = 240 个');

wrapper.getComponent(WarehouseFloorCanvas).vm.$emit('update-block-rect', { id: 'block-blue-a', x: -10, y: 20, width: 210, height: 135 });
expect(wrapper.get('[data-testid="warehouse-layout-issues"]').text()).toContain('越出');
expect(wrapper.get('[data-testid="save-warehouse-layout"]').attributes('disabled')).toBeDefined();
```

- [ ] **Step 7: Implement selection properties, moves, warnings, and delete safeguards**

The selected-area panel edits name/x/y/width/height and lock state. The selected-SKU panel edits units/x/y/width/height, exposes whole and partial move, and keeps case copy disabled. Issue cards locate the affected block. Deleting a non-empty area opens a real accessible dialog offering `先移动产品` and `一并删除`.

- [ ] **Step 8: Verify Task 4 GREEN and commit**

Run: `cd frontend && npm run test:run -- src/features/inventory/WarehouseCanvasView.test.ts src/features/inventory/warehouseCanvas`

Expected: PASS.

```bash
git add frontend/src/features/inventory
git commit -m "feat: implement warehouse canvas workflow"
```

---

### Task 5: Route, Navigation, Warehouse Entry Point, And Shell Fidelity

**Files:**
- Modify: `frontend/src/router/index.ts`
- Modify: `frontend/src/features/navigation/mockNavigationService.ts`
- Modify: `frontend/src/layouts/ErpLayout.vue`
- Modify: `frontend/src/features/masterdata/views/WarehouseView.vue`
- Modify: `frontend/src/router/index.test.ts`
- Modify: `frontend/src/AppRouting.test.ts`
- Modify: `frontend/src/components/navigation/SidebarNav.test.ts`
- Modify: `frontend/src/features/masterdata/views/MasterdataViews.test.ts`

**Interfaces:**
- Consumes: Task 4 page.
- Produces: authenticated navigation to the canvas from both Inventory and the warehouse list.

- [ ] **Step 1: Write failing route and navigation tests**

```ts
expect(router.resolve('/inventory/warehouse-canvas').name).toBe('warehouse-canvas');
expect(wrapper.text()).toContain('仓库画布');
expect(wrapper.get('[data-testid="breadcrumb-group"]').text()).toBe('库存管理');
expect(wrapper.get('[data-testid="breadcrumb-current"]').text()).toBe('仓库画布');
```

Add a warehouse-list assertion that clicking `data-testid="open-warehouse-canvas-1"` routes to `{ name: 'warehouse-canvas', query: { warehouseId: '1' } }`.

- [ ] **Step 2: Run integration tests and verify RED**

Run: `cd frontend && npm run test:run -- src/router/index.test.ts src/AppRouting.test.ts src/components/navigation/SidebarNav.test.ts src/features/masterdata/views/MasterdataViews.test.ts`

Expected: FAIL because the route and entry points do not exist.

- [ ] **Step 3: Register route and navigation**

```ts
{
  path: 'inventory/warehouse-canvas',
  name: 'warehouse-canvas',
  component: () => import('../features/inventory/views/WarehouseCanvasView.vue'),
  meta: { flushContent: true }
}
```

Add `仓库画布` as the first child of `库存管理`, before balances. Add the breadcrumb mapping `{ group: '库存管理', groupRoute: 'inventory-balances', title: '仓库画布', titleRoute: 'warehouse-canvas' }`. Bind the ERP page-content padding to `route.meta.flushContent` so only this route uses `p-0`; every existing route keeps its current classes.

- [ ] **Step 4: Add warehouse-row entry action**

Use `useRouter()` in `WarehouseView.vue` and add a row action labeled `规划画布`. It routes with the selected warehouse ID and does not alter edit/default/status behavior.

- [ ] **Step 5: Verify integration GREEN and commit**

Run: `cd frontend && npm run test:run -- src/router/index.test.ts src/AppRouting.test.ts src/components/navigation/SidebarNav.test.ts src/features/masterdata/views/MasterdataViews.test.ts`

Expected: PASS.

```bash
git add frontend/src/router/index.ts frontend/src/features/navigation/mockNavigationService.ts frontend/src/layouts/ErpLayout.vue frontend/src/features/masterdata/views/WarehouseView.vue frontend/src/router/index.test.ts frontend/src/AppRouting.test.ts frontend/src/components/navigation/SidebarNav.test.ts frontend/src/features/masterdata/views/MasterdataViews.test.ts
git commit -m "feat: connect warehouse canvas navigation"
```

---

### Task 6: Full Verification And Figma Design QA

**Files:**
- Modify: `design-qa.md`
- Modify only as required by confirmed QA findings: files created or changed in Tasks 1–5.

**Interfaces:**
- Consumes: the complete route and Figma source frames.
- Produces: a locally running, visually verified warehouse-canvas MVP.

- [ ] **Step 1: Run the full automated suite**

Run: `cd frontend && npm run test:run`

Expected: 0 failed files and 0 failed tests.

- [ ] **Step 2: Run the production build**

Run: `cd frontend && npm run build`

Expected: `vue-tsc --noEmit` and Vite build both exit 0.

- [ ] **Step 3: Start the local application**

Run: `cd frontend && npm run dev -- --host 0.0.0.0`

Keep the returned session running. Open `/inventory/warehouse-canvas` in the Codex in-app browser at a `1440×1024` viewport using an authenticated local session.

- [ ] **Step 4: Capture matching source and implementation states**

Capture Figma Overview `1187:2`, Draw Area `1190:516`, Add SKU `1193:39676`, SKU Selected `1193:40024`, Partial Move `1193:40372`, and Warning `1210:2466`. Capture the corresponding browser states at the same viewport. Include focused comparisons for toolbar, area/SKU blocks, object properties, partial-move arithmetic, warnings, and delete dialog.

- [ ] **Step 5: Write `design-qa.md` and iterate**

The report must include source/implementation paths, `1440×1024` viewport, pixel dimensions, state, full-view evidence, focused evidence, fonts, spacing, colors, icons, copy, interactions, console errors, and iteration history. Fix every P0/P1/P2 finding, recapture, and recompare until the file ends with:

```text
final result: passed
```

- [ ] **Step 6: Manually verify the complete workflow**

Verify: draw and name D-01; add a 250-unit blue SKU and see `10 件 + 10 个`; resize without quantity change; whole move without quantity change; split 60 and see conservation; create outside/overlap warnings; save disabled until corrected; undo/redo; non-empty-area delete confirmation; no console errors.

- [ ] **Step 7: Re-run the full test suite**

Run: `cd frontend && npm run test:run`

Expected: 0 failed files and 0 failed tests.

- [ ] **Step 8: Re-run the production build and commit**

Run: `cd frontend && npm run build`

Expected: `vue-tsc --noEmit` and Vite build both exit 0.

```bash
git add design-qa.md frontend/src
git commit -m "test: verify warehouse canvas experience"
```
