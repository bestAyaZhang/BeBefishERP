# Warehouse Pile Resize and Safe Deletion Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let a planner resize or delete a goods pile while preserving authoritative warehouse stock and returning deleted-pile allocations to “待分配”.

**Architecture:** The scene previews pointer-driven resize and emits a rectangle; the view owns undoable planning state and delete confirmation. Layout save accepts confirmed allocation snapshots and releases removed-pile locations in the same backend transaction as the revisioned layout update.

**Tech Stack:** Vue 3, TypeScript, Vitest, Java/Spring Boot, JDBC, JUnit, MySQL-compatible test DB.

**Spec:** `docs/superpowers/specs/2026-10-02-warehouse-pile-resize-delete-design.md`

## Global Constraints

- Only planning edit mode may resize/delete; inventory detail mode remains read-only.
- Deleting a pile never deletes SKU master data or changes `inventory_balance`.
- Both positive and zero-unit SKU links count as allocations requiring confirmation.
- A failed save must roll back both layout and location changes; a successful save refreshes actual inventory.
- Follow existing warehouse → layout → balance → location lock order; preserve revision conflict behavior.

## Review Focus

- A zero-unit SKU link must appear in confirmation and be removed on save — Task 1 backend and Task 4 view tests.
- An allocation added after confirmation must cause a save conflict, not silently move new stock — Task 1 test.
- Resize at 200% zoom must commit the same board coordinates as at 100% — Task 3 test.
- Delete pressed while a form input has focus must not remove a pile — Task 4 test.
- Undoing a staged deletion must omit its release confirmation from the save request — Task 4 test.

---

### Task 1: Atomic layout save and pile allocation release

**Files:**
- Create: `backend/src/main/java/com/bebefish/erp/inventory/application/WarehousePileReleaseService.java` — compare/release location rows and reconcile `UNALLOCATED`.
- Modify: `backend/src/main/java/com/bebefish/erp/masterdata/application/WarehouseLayoutService.java` — coordinate release inside revisioned save.
- Modify: `backend/src/main/java/com/bebefish/erp/masterdata/api/WarehouseLayoutController.java` — accept save command.
- Test: `backend/src/test/java/com/bebefish/erp/masterdata/application/WarehouseLayoutPileDeletionTest.java` — transactional integration cases.
- Test: `backend/src/test/java/com/bebefish/erp/masterdata/api/WarehouseLayoutDeletionControllerTest.java` — request and authority boundary.
- Test: existing `WarehouseLayoutServiceTest.java` and `WarehouseLayoutServiceConcurrencyTest.java` — constructor/signature adaptation.

**Interfaces:** `WarehouseLayoutService.SaveCommand(long revision, JsonNode document, List<WarehousePileReleaseService.ReleaseConfirmation> releasedPileAllocations)`; `ReleaseConfirmation(String palletId, List<AllocationSnapshot> allocations)`; `AllocationSnapshot(long skuId, BigDecimal units)`. Retain `save(long, Layout)` as a compatibility overload delegating with an empty list. `WarehousePileReleaseService.releaseRemovedPiles(long warehouseId, JsonNode before, JsonNode after, List<ReleaseConfirmation> confirmations)` runs inside the caller's transaction.

- [ ] **Step 1: Write failing integration tests.** `deletingAllocatedPileReleasesPositiveAndZeroLinksWithoutChangingBalance`: save a completed pile with 24 units of SKU A and a 0-unit SKU B link, then save a document without that pile plus exact snapshots; assert both location rows gone, `UNALLOCATED` rises by 24, `inventory_balance` unchanged, revision increments. `deletionWithoutConfirmationRollsBack`, `newAllocationAfterConfirmationConflicts`, `staleRevisionConflicts`, `missingInventoryEditPermissionRejectsLinkedPile`, `emptyPileNeedsOnlyWarehouseEdit`, and `concurrentAllocationCannotLeaveOrphanLocation` each assert no partial write. Include duplicate/malformed confirmation rejection.
- [ ] **Step 2: Run the new test to confirm RED.** `mvn -f backend/pom.xml -Dtest=WarehouseLayoutPileDeletionTest test`; expected failure is missing save command/release behavior, not DB setup.
- [ ] **Step 3: Implement the API and transaction.** Diff old/new pile IDs server-side; lock warehouse/layout as existing save does, then lock affected SKU balances in sorted order before their location rows. Compare exact `(skuId, units)` sets, including zero links, to user-confirmed snapshots. Require `inventory:edit` only if actual links exist. Delete confirmed location rows, reconcile `UNALLOCATED` against the unchanged authoritative balances, and write layout/revision in the same transaction. Return explicit 409 for changed allocations and 403 for missing release permission.
- [ ] **Step 4: Run focused backend tests to confirm GREEN.** `mvn -f backend/pom.xml -Dtest=WarehouseLayoutPileDeletionTest,WarehouseLayoutDeletionControllerTest,WarehouseLayoutServiceTest,WarehouseLayoutServiceConcurrencyTest,WarehousePileAllocationServiceTest test`; expected all selected tests pass.
- [ ] **Step 5: Commit.** `git add backend/src/main/java backend/src/test/java && git commit -m "feat: release pile allocations atomically on layout deletion"`.

### Task 2: Frontend save metadata contract

**Files:**
- Modify: `frontend/src/features/inventory/warehouseCanvas/warehouseLayoutService.ts` — typed save request and HTTP/mock handling.
- Modify: `frontend/src/features/inventory/warehouseCanvas/warehouseInventoryService.ts` — in-memory release helper used by mock layout saves.
- Modify: `frontend/src/features/inventory/warehouseCanvas/components/WarehouseLayoutControls.vue` — pass optional release snapshots and emit save success.
- Test: `frontend/src/features/inventory/warehouseCanvas/components/WarehouseLayoutControls.test.ts`.
- Test: `frontend/src/features/inventory/warehouseCanvas/warehouseLayoutService.test.ts` (create if absent).

**Interfaces:** `ReleasedPileAllocation = { palletId: string; allocations: { skuId: number; units: number }[] }`; `WarehouseLayoutSaveInput = { revision: number; document: WarehouseLayoutDocument; releasedPileAllocations?: ReleasedPileAllocation[] }`; `WarehouseLayoutService.save(id, input): Promise<WarehouseLayoutResponse>`. Controls accepts `releasedPileAllocations` prop (default `[]`) and emits `saved` after success; existing `saveDocument(document?)` call remains valid.

- [ ] **Step 1: Write failing tests.** Assert HTTP save serializes exact confirmed snapshot, ordinary saves omit the optional field, mock save preserves revision/document and moves confirmed mock pile allocations to `UNALLOCATED` without changing totals, and controls emits `saved` only after a successful call.
- [ ] **Step 2: Confirm RED.** `npm --prefix frontend run test:run -- src/features/inventory/warehouseCanvas/warehouseLayoutService.test.ts src/features/inventory/warehouseCanvas/components/WarehouseLayoutControls.test.ts`; expected new assertions fail.
- [ ] **Step 3: Implement typed request and controls forwarding.** Do not put confirmation metadata inside persisted `WarehouseLayoutDocument`; keep it request-only. Keep the existing loading, dirty, and error behavior.
- [ ] **Step 4: Confirm GREEN.** Repeat the focused Vitest command; expected both files pass. Run `npm --prefix frontend run build`; expected TypeScript/build success.
- [ ] **Step 5: Commit.** `git add frontend/src/features/inventory/warehouseCanvas && git commit -m "feat: send confirmed pile releases with layout saves"`.

### Task 3: Resize geometry and canvas interaction

**Files:**
- Create: `frontend/src/features/inventory/warehouseCanvas/palletResize.ts` — pure eight-handle rectangle calculations.
- Create: `frontend/src/features/inventory/warehouseCanvas/palletResize.test.ts`.
- Modify: `frontend/src/features/inventory/warehouseCanvas/components/WarehouseBlueprintScene.vue` — handle events, RAF preview, validity feedback, Escape cancellation and emit.
- Test: `frontend/src/features/inventory/warehouseCanvas/components/WarehouseBlueprintScene.test.ts`.

**Interfaces:** `ResizeHandle = 'n' | 'ne' | 'e' | 'se' | 's' | 'sw' | 'w' | 'nw'`; `resizePalletRect(origin: PlannerRect, handle: ResizeHandle, delta: { x: number; y: number }, snap: boolean): PlannerRect`. Scene emits `resize-pallet` with `{ id: string; left: number; top: number; width: number; height: number }` only after a valid drop, and `request-delete-pallet` with an ID when Delete is pressed on the focused selected pile. Min width/height are the creation cell (`100/120`, `100/80` board percent); moved edges snap to the same grid when enabled.

- [ ] **Step 1: Write failing tests.** Cover all eight directions and fixed opposite edge; min size/board clamps; snap on/off; pointer preview and successful emit; 200% zoom coordinate equivalence; overlap/structure conflict red feedback and unchanged drop; Escape; selected handle pointerdown must not emit `move-pallet`; Delete on a focused selected pile emits `request-delete-pallet` only in goods-edit mode.
- [ ] **Step 2: Confirm RED.** `npm --prefix frontend run test:run -- src/features/inventory/warehouseCanvas/palletResize.test.ts src/features/inventory/warehouseCanvas/components/WarehouseBlueprintScene.test.ts`; expected resize cases fail.
- [ ] **Step 3: Implement pure math and scene interaction.** Reuse the existing board measurement and RAF drag pattern. Show functional handles only for a selected pile in goods-edit mode; preserve dragged pile stacking and keep detail mode read-only.
- [ ] **Step 4: Confirm GREEN.** Repeat focused Vitest command and run `npm --prefix frontend run build`; expected pass.
- [ ] **Step 5: Commit.** `git add frontend/src/features/inventory/warehouseCanvas && git commit -m "feat: resize warehouse piles with canvas handles"`.

### Task 4: View state, delete confirmation and end-to-end checks

**Files:**
- Modify: `frontend/src/features/inventory/views/WarehouseCanvasView.vue` — apply resize in history, header delete action, confirmation dialog, release snapshots, refresh.
- Test: `frontend/src/features/inventory/WarehouseCanvasView.test.ts`.

**Interfaces:** `resizePlannerPallet(rect: { id: string; left: number; top: number; width: number; height: number }): void` updates geometry, `xMeters/yMeters/lengthMeters/widthMeters` using existing `.6/.4` scale, and rows/columns using creation formulas; `confirmDeletePlannerPallet(): void` stages deletion through `changePlannerState`; `releasedPileAllocations` is derived from confirmed deletion records that are still absent from the current draft.

- [ ] **Step 1: Write failing view tests.** Resize updates geometry and undo restores it without changing SKU quantities; selected pile exposes delete in the header; cancel keeps the pile; confirmed delete of a pile with positive and zero-unit allocations stages the correct snapshot; deleting a pile without links needs no `inventory:edit`; inventory load failure blocks confirmation; Delete on an input does nothing; undo removes release metadata; successful save refreshes inventory; rejected save leaves draft and error visible.
- [ ] **Step 2: Confirm RED.** `npm --prefix frontend run test:run -- src/features/inventory/WarehouseCanvasView.test.ts`; expected new cases fail.
- [ ] **Step 3: Implement view wiring.** Use `AccessibleDialog`; obtain associations from `actualInventory.allocations`, not demo `contents`; include 0-unit links; disable save-time deletion while inventory is unavailable. Reconcile confirmation records after undo/redo and warehouse switches. Wire controls `saved` to reload inventory.
- [ ] **Step 4: Confirm GREEN and regression.** Run the focused view test, then `npm --prefix frontend run test:run` and `npm --prefix frontend run build`; run `mvn -f backend/pom.xml test`. Manually check resize at 100%/200%, collision rollback, delete/cancel/undo/save/refresh, and unchanged warehouse total stock in the local app.
- [ ] **Step 5: Commit.** `git add frontend/src/features/inventory/views/WarehouseCanvasView.vue frontend/src/features/inventory/WarehouseCanvasView.test.ts && git commit -m "feat: confirm and save warehouse pile deletion"`.
