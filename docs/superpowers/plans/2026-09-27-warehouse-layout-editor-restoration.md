# Warehouse Layout Editor Restoration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Restore `/inventory/warehouse-canvas` to the previously completed `Warehouse Layout / Draft Editor` experience and load the warehouse selected by `warehouseId` instead of the later single-demo SKU canvas.

**Architecture:** Keep the existing route and warehouse-list entry, but replace the page implementation with a focused warehouse-layout module. A typed model and in-memory repository own warehouse-specific draft geometry, validation, history, and publish state; Vue components render the layer tree, plan canvas, object inspector, and validation drawer. The first delivery remains front-end/mock backed and does not introduce the broader 3D, outbound, movement, stocktake, or backend migration subsystems.

**Tech Stack:** Vue 3 Composition API, TypeScript, Vue Router, Tailwind CSS, lucide-vue-next, Vitest, Vue Test Utils.

**Spec:** `docs/superpowers/specs/2026-09-04-warehouse-spatial-inventory-design.md` sections 6, 8, 13.1, 14, 18.4, and 20; visual target `Warehouse Layout / Draft Editor` (`1077:3451`) and `Warehouse Layout / Publish Validation` (`1077:3537`) documented in `.superpowers/sdd/2026-09-04-warehouse-spatial-figma-design/task-4-report.md`.

## Global Constraints

- Preserve the existing `/inventory/warehouse-canvas?warehouseId=<id>` route and `规划画布` entry from warehouse management.
- Use the selected active warehouse's ID, code, name, and address; do not silently substitute demo warehouse ID 1.
- Desktop target is the existing ERP shell and the Figma `1440×1024` composition with a compact layer tree, dominant canvas, and right inspector.
- Inventory quantities are not edited on this page; this page edits layout objects and publishes geometry.
- Layout objects cover warehouse boundary, fixed zone, free zone, aisle, obstacle, and fixed location.
- Keep the implementation mock/local for this delivery; do not add backend APIs, database migrations, 3D, outbound allocation, movement, or stocktake.
- Reuse the existing color, typography, spacing, button, form, dialog, and icon conventions.

## Review Focus

- Missing, invalid, or disabled `warehouseId` shows a clear load error and never falls back to another warehouse.
- Loading IDs 8 and 9 produces independent draft state and the correct warehouse identity.
- Undo, redo, discard, and save/publish never mutate the last saved draft unexpectedly.
- Objects outside the boundary or intersecting an aisle create blocking issues and prevent publish.
- Selecting an object keeps the layer tree, canvas highlight, and inspector fields synchronized.

---

### Task 1: Warehouse-specific layout model and repository

**Files:**
- Create: `frontend/src/features/inventory/warehouseLayout/types.ts`
- Create: `frontend/src/features/inventory/warehouseLayout/warehouseLayoutModel.ts`
- Create: `frontend/src/features/inventory/warehouseLayout/mockWarehouseLayoutRepository.ts`
- Test: `frontend/src/features/inventory/warehouseLayout/warehouseLayoutModel.test.ts`

**Interfaces:**
- Produces `WarehouseLayoutState`, `LayoutObject`, `LayoutIssue`, `LayoutTool`, and `WarehouseLayoutRepository`.
- Produces `validateWarehouseLayout(state)`, `updateLayoutObject(state, id, patch)`, and `createWarehouseLayoutRepository()`.

- [x] **Step 1: Write failing model tests** for warehouse-specific seeds, independent repository state, geometry updates, out-of-bound validation, and aisle-intersection validation.
- [x] **Step 2: Run the model test file and verify the expected failures.**
- [x] **Step 3: Implement the minimal typed state, validators, mutation helpers, and repository.**
- [x] **Step 4: Run the model tests and verify they pass.**

### Task 2: Draft lifecycle and editor controller

**Files:**
- Create: `frontend/src/features/inventory/warehouseLayout/useWarehouseLayout.ts`
- Test: `frontend/src/features/inventory/warehouseLayout/useWarehouseLayout.test.ts`

**Interfaces:**
- Consumes `WarehouseLayoutRepository` and the Task 1 model.
- Produces refs and commands for `load`, `selectObject`, `setTool`, `createObject`, `updateSelectedObject`, `toggleObjectLock`, `toggleObjectVisibility`, `undo`, `redo`, `discard`, `saveDraft`, and `publish`.

- [x] **Step 1: Write failing controller tests** for ID-based load, selection, edits, history, discard, save, publish blocking, and successful publish.
- [x] **Step 2: Run the controller test file and verify the expected failures.**
- [x] **Step 3: Implement the controller with immutable snapshots and warehouse-isolated persistence.**
- [x] **Step 4: Run the controller tests and verify they pass.**

### Task 3: Restore the Figma layout editor page

**Files:**
- Create: `frontend/src/features/inventory/warehouseLayout/components/WarehouseLayoutCanvas.vue`
- Create: `frontend/src/features/inventory/warehouseLayout/components/WarehouseLayerTree.vue`
- Create: `frontend/src/features/inventory/warehouseLayout/components/WarehouseObjectInspector.vue`
- Modify: `frontend/src/features/inventory/views/WarehouseCanvasView.vue`
- Replace tests in: `frontend/src/features/inventory/WarehouseCanvasView.test.ts`

**Interfaces:**
- Consumes the Task 2 controller and `masterdataService.listActiveWarehouses()`.
- Emits object selection, drawing, property edits, visibility, and locking operations through typed component events.

- [ ] **Step 1: Replace the page tests with failing tests** for warehouse identity, original page title/copy, three-column composition, editor toolbar, synchronized selection, property editing, and responsive containment.
- [ ] **Step 2: Run the view test and verify failures against the simplified SKU canvas.**
- [ ] **Step 3: Implement the layer tree, canvas, inspector, and restored page composition.**
- [ ] **Step 4: Run view tests and fix interaction/accessibility failures.**

### Task 4: Publish validation flow and integrated verification

**Files:**
- Create: `frontend/src/features/inventory/warehouseLayout/components/WarehousePublishDrawer.vue`
- Modify: `frontend/src/features/inventory/views/WarehouseCanvasView.vue`
- Modify: `frontend/src/features/inventory/WarehouseCanvasView.test.ts`
- Modify: `frontend/src/router/index.test.ts` only if current route coverage needs the restored title/behavior.
- Update: `design-qa.md`

**Interfaces:**
- Consumes `LayoutIssue[]` and Task 2 `publish()`.
- Produces accessible validation UI with issue counts, locate actions, return-to-edit, disabled publish while blocking issues remain, and published confirmation.

- [ ] **Step 1: Write failing view tests** for validation drawer, locate action, blocked publish, corrected publish, and published version feedback.
- [ ] **Step 2: Run the targeted tests and verify failures.**
- [ ] **Step 3: Implement the validation drawer and publish flow.**
- [ ] **Step 4: Run all warehouse-layout and routing tests, then the full frontend test suite and production build.**
- [ ] **Step 5: Open warehouse IDs 8 and 9 in the running app, exercise selection/edit/undo/validation, capture the same editor state as Figma, and complete `design-qa.md` with `final result: passed`.**

