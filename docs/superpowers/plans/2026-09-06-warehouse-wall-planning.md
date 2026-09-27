# Warehouse Wall Planning Implementation Plan

> **For agentic workers:** Execute inline with superpowers:executing-plans, tracking the tasks below.

**Goal:** Make the approved manual wall workflow usable in the existing warehouse planner.

**Architecture:** A pure structure model owns geometry and wall/door relationships. A focused Vue structure layer edits it through preview and single commit events. The existing page owns shared structure/pallet snapshots and completion validation.

**Tech Stack:** Vue 3, TypeScript, SVG geometry, existing Lucide icons, Vitest.

**Spec:** `docs/superpowers/specs/2026-09-06-warehouse-wall-planning-design.md`

## Global Constraints

- One irregular exterior polygon; open partition polylines; attached loading and ordinary doors.
- No SKU or size inspector during planning; details only after completion.
- No inventory writes, backend persistence, or automatic movement of piles.
- Moving piles remain on top, show guides, reject overlapping drops.
- Shared 0–100 drawing coordinates, 60m × 40m metric conversion.

## Task 1: Structure model and geometry

Files: create `frontend/src/features/inventory/warehouseCanvas/warehouseStructure.ts` and `warehouseStructure.test.ts`.

Interfaces: `StructurePoint`, `StructureWall`, `StructureDoor`, `WarehouseStructure`; `createWarehouseStructure()`, `structureSegments()`, `validateStructure()`, `palletStructureConflicts()`, `moveStructureNode()`, `insertStructureNode()`, `removeStructureNode()`.

- [x] Add tests for self-intersecting polygons, concave cutouts crossing rectangles, attached door movement, wall shortening, attached partition endpoints, node insertion/deletion, and initial fixture validity.
- [x] Implement segment projection, metric distance, polygon containment and wall thickness collision. Example invariant: `expect(palletStructureConflicts(concave, crossingPile)).not.toEqual([])` even if every rectangle corner lies inside.
- [x] Model door locations with stable wall/segment identity plus relative offset; update attachments transactionally when nodes change.
- [x] Run `npm run test:run -- src/features/inventory/warehouseCanvas/warehouseStructure.test.ts`.

## Task 2: Structure editing layer

Files: create `frontend/src/features/inventory/warehouseCanvas/components/WarehouseStructureLayer.vue` and `WarehouseStructureLayer.test.ts`.

Interface: props `structure`, `editing`, `gridSnapping`, `issues`; emits `preview`, `commit`, `busy`. Draw nodes and doors over the same SVG geometry used for boundaries.

- [x] Add a compact structure toolbar, visible selected nodes, insertion/deletion, exterior closure, partition finish, door snapping and dragging.
- [x] Use pointer capture and frame-coalesced previews; Esc/pointercancel restores input state. One completed gesture emits one `commit`.
- [x] Render cutouts in walls for doors; invalid geometry uses red preview and rejects commit. Existing pile conflicts remain committed and visible for correction.
- [x] Verify creation/cancel, node drag commit count, ordinary/loading door placement, invalid outline rejection with component tests.

## Task 3: Page and pile integration

Files: modify `WarehouseBlueprintScene.vue`, `WarehouseCanvasView.vue`, `WarehousePlannerChrome.vue` and their tests.

- [x] Supply structure from the page and include it in existing undo snapshots; apply structure preview without adding history.
- [x] Replace fixed wall/room lines and duplicate right-edge constraints with the shared model. Render the minimap from the same exterior polygon.
- [x] Merge pallet overlap and structure collision IDs for feedback; on invalid drop retain original pile position.
- [x] Show a compact issue list with locate controls. Disable completion during edits or issues and validate again in the completion handler.
- [x] Verify undo restores walls/doors, wall edits mark affected piles, details mode hides editing, and existing pile interactions pass.
- [x] Run affected Vitest suites and `npm run build`; inspect the live route in the in-app browser.

## Review

Coverage includes all approved drawing/editing/attachment behaviors, concave geometry, state transitions, draft cancellation, unified validation and session-only persistence. No independent subsystem or package installation is required.

## Execution results — 2026-09-07

- Implemented inline in the existing `codex/warehouse-planner-ui-local` worktree.
- Warehouse geometry, editor, scene and page test suites passed; type checking and production build passed.
- Browser verified L-shaped partition creation, a snapped ordinary door, node dragging with door rotation, wall/pile collision highlighting, disabled completion, issue expansion/location, exterior redraw across an existing door, undo restoring original walls/doors, and the completed read-only detail mode.
- Browser checks identified and fixed toolbar/door label overlap, a legacy issue-toggle CSS collision, scrolling caused by SVG focus, and door hit targets swallowing exterior drawing clicks.
- Preview remains session-only and is served at `http://127.0.0.1:5187/inventory/warehouse-canvas?preview=wall-planning`.
