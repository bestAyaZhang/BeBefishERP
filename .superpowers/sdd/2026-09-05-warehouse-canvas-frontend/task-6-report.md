# Task 6 — Full Verification And Figma Design QA

Status: complete. Implementation/evidence commit: **c81ee17 — feat(inventory): verify and refine warehouse canvas against Figma**. This report is committed separately as **docs: record warehouse canvas task 6 verification report** so it can identify the implementation commit without a self-referential SHA.

## Requirements and execution

- Binding brief: C:\Users\张振亚\.codex\worktrees\46fe\BeBeFishERP\.superpowers\sdd\2026-09-05-warehouse-canvas-frontend\task-6-brief.md.
- Approved spec: C:\Users\张振亚\.codex\worktrees\46fe\BeBeFishERP\docs\superpowers\specs\2026-09-05-warehouse-canvas-inventory-design.md.
- Read the design-qa skill, QA rubric, computer-use skill/guidance/confirmations, critical overrides, communication protocol and Figma design-to-code instructions before their respective work. Applied regression-first behavior fixes and verification-before-completion.
- No subagents or reviewers dispatched. No Playwright CLI, external/headless browser, production writes, deployment, backend changes, destructive repository cleanup, or repository-wide style overhaul.
- Root design-qa.md retains every previous section; appended Warehouse Canvas Design QA. Its final result is exactly passed.
- All original screenshot evidence outside the new warehouse-canvas directory is intact.

## Automated verification

| Run | Result |
| --- | --- |
| Baseline npm run test:run | 385 tests across 36 files passed |
| Baseline npm run build | vue-tsc and production Vite build passed |
| Initial preview/jitter/count regressions, 01:21 | 4 failed / 40 passed (expected RED), before implementation |
| Source-composition regression, 01:23 | 1 failed / 28 passed (expected RED), before source fixture update |
| Library-preservation search regression, 01:25 | 1 failed / 28 passed (expected RED), before replacing filter-removal with dimming |
| Post-layout focused suite, 01:26 | 76 tests passed |
| Draw-inside-existing-area and locked-preview regressions, 01:28 | 2 failed / 16 passed (expected RED), before pointer dispatch/lock fixes |
| Post-pointer focused suite, 01:29 and 01:35 | 78 tests passed |
| Warning collapse/repair regression, 01:40:02 | 2 failed / 28 passed (new collapse behavior plus new locate selector expected RED) |
| Warning collapse/repair GREEN, 01:40:57 | 30 view tests passed |
| Final npm run test:run, 01:44:09 | **393 tests / 36 files passed**, exit 0 |
| Final npm run build | **passed**, exit 0; vue-tsc --noEmit; 1744 Vite modules; built in 3.32 s |
| git diff --check | exit 0; no whitespace errors, only Git CRLF conversion notices |
| Post-implementation-commit git status --short | clean |

Times are the local tool output times on 2026-09-06 (Asia/Shanghai). The final focused warehouse total is 79 tests: model 11, controller 16, floor 18, view 30, adjacent InventoryFeature 4. There are eight new tests in this task: five pointer tests plus three view tests. Existing scenario fixtures were updated to match source geometry without removing safeguards. Successful transfer tests use C-01 because the exact overview B-01 layout has insufficient free space at unchanged block sizes.

The initial default-sandbox test attempt hit Windows child-process EPERM; rerunning the same local commands with approved execution succeeded. Source-download default networking hit a proxy restriction; downloading the exact returned Figma screenshot URLs with approved network execution succeeded. These were tooling retries, not design-QA iterations.

## Server and browser handoff

- Correct checkout: C:\Users\张振亚\.codex\worktrees\46fe\BeBeFishERP\frontend.
- Running command: npm run dev -- --host 0.0.0.0 --port 5186.
- Running exec session: **77019**. The server remains running.
- Verified route: **http://127.0.0.1:5186/inventory/warehouse-canvas**.
- Browser: **Codex in-app browser**, browser ID **1**, tab ID **1**; marked deliverable. It remains open on the final overview, search 蓝, no selected object, no warnings, 1,026 units.
- Viewport restored to **1440 × 1024**, browser-reported devicePixelRatio **1**. Floor actual DOM rectangle **456,352,728,672**. Document scrollWidth/scrollHeight are 1440/1024.
- Existing dev demo login was used through the normal login UI; no credential/storage shortcuts or auth bypass.
- Port 5173 was initially found serving another checkout. It was not used for the final implementation comparison. The isolated 5186 server proved the source marker and correct application.
- Subagent IAB cannot request visible:true (tool reports IAB visibility is not supported in a subagent thread). Hidden IAB capture/control works and is healthy. **Controller should surface the existing verified tab in the parent task during user handoff**, using its in-app browser panel. No new browser is needed.
- Final console read returned no warnings/errors for 5186 and no errors at all. Two old router warnings from 5173 remain in the tab history and are explicitly preserved in evidence.md.

## Manual interaction results

1. Drawn D-01 over empty interior space of A-01 using real pointer drag (934,398) → (1154,532), producing logical x478/y46/w220/h134; naming confirmation creates the area.
2. Added blue SKU, 250 authoritative units, to D-01; case display 10 件 + 10 个; warehouse total 1,276.
3. Real resize of the new block: 120 × 80 → 138 × 97; units stay 250 and all compact quantity lines remain readable.
4. Whole-block pointer move D-01 → C-01; units stay 250, D-01 becomes zero, C-01 becomes 350, warehouse total stays 1,276.
5. Partial move C-01 → D-01 of 60 shows preview 190 + 60 = 250; confirmation yields source 190, target 60, warehouse total 1,276. Values 0, -10, 1.5 and 250 keep confirmation disabled.
6. Undo restores source 250 and removes target 60; redo restores 190/60; total unchanged. Save displays saved state and current-demo-session notice.
7. Warning state: original blue A x518 produces out-of-area; tea B x80/y390 overlaps pink B. Both issue types are present, quantities/total 1,026 unchanged, save disabled.
8. Verified warning close/reopen and locate behavior. Locate collapses details and focuses the correct SKU so coordinates are editable. Repair blue x48 and tea x48/y456; only after both clear does save become enabled.
9. Nonempty A-01 deletion: first dialog includes cancel/move-first/delete; delete advances to a second explicit confirmation. Final confirmation deletes A and two blocks, total 1,026 → 756. Undo restores three areas/five blocks/1,026 units. No external inventory is affected; this is in-page mock state.
10. Native HTML product-library drag from blue card into C-01 opens the matching add drawer with target C-01. Cancel leaves state unchanged.
11. Area lock makes SKU units input disabled; unlocking restores editing. Hiding C removes its visible block while preserving 1,026 authoritative total; showing restores it.
12. Zoom 150% and pan produce scrollLeft=130, scrollTop=140 without data mutation.
13. Query warehouseId=99 displays “当前仅支持演示仓库（ID 1）；所选仓库 ID 99 尚未加载。” Default copy explicitly states that save lasts only in the current page and refresh resets the mock.
14. Desktop resilience at 1280 × 900 and 1024 × 768: no global horizontal overflow (document client widths 1265 and 1009 with native scrollbar); the narrow dense workspace has its own horizontal scrolling and the toolbar remains reachable. Mobile excluded by approved spec.
15. Final selected-object panel: clientHeight=854, scrollHeight=854, final button bottom=1000.17 within viewport1024; there is no hidden primary/destructive action.
16. Final computed font: Noto Sans SC Variable, Inter, sans-serif; title24px/32px. Search 非匹配块 computed opacity0.25 immediately, no intermediate transition capture.

## Files changed and self-review

Implementation/evidence commit changes 72 files: the 10 code/test files listed below, root design-qa.md, and 61 new evidence-directory files (59 PNGs, comparison script, evidence manifest). This report is an additional task metadata file.

- frontend/src/features/inventory/views/WarehouseCanvasView.vue — source layout, summaries, product dimming, pending properties/name dialog, working drawer/partial/warning/delete states and explicit demo scope.
- frontend/src/features/inventory/warehouseCanvas/components/WarehouseFloorCanvas.vue — transient move/resize preview, jitter guard, draw pointer routing, lock guard, source surfaces and per-area summaries.
- frontend/src/features/inventory/warehouseCanvas/components/WarehouseObjectPanel.vue — source-aligned selected panel, readable values and actions.
- frontend/src/features/inventory/warehouseCanvas/components/WarehouseProductDrawer.vue — source-aligned drawer and quantity/packaging hierarchy.
- frontend/src/features/inventory/warehouseCanvas/mockWarehouseCanvasData.ts — exact overview fixture names, packaging, quantities and geometry.
- frontend/src/features/inventory/warehouseCanvas/warehouseCanvasModel.ts — user-readable warning copy only; validation rules unchanged.
- frontend/src/features/inventory/WarehouseCanvasView.test.ts — new source/count and warning-repair regressions; fixture-aligned move/search scenarios.
- frontend/src/features/inventory/warehouseCanvas/components/WarehouseFloorCanvas.test.ts — five new regression-first pointer tests and fixture expectation updates.
- frontend/src/features/inventory/warehouseCanvas/useWarehouseCanvas.test.ts — source fixture names/coordinate expectations.
- frontend/src/features/inventory/warehouseCanvas/warehouseCanvasModel.test.ts — warning scenario coordinates adapted to source geometry.
- design-qa.md — warehouse section appended, existing content untouched.
- frontend/prototype-screenshots/warehouse-canvas/compare.ps1 — image-bound-checked, non-resampling actual side-by-side compositor.
- frontend/prototype-screenshots/warehouse-canvas/evidence.md — complete PNG dimensions, actual Figma URLs and console provenance.
- Every PNG path/dimension is listed in the manifest below.

Self-review read all changed production files and test diffs, checked fixtures still exercise actual overlap/outside/min-size/locked/cancel/undo/conservation cases, checked no quantity derives from area, checked no unrequested backend/database work, and inspected final full/focused artifacts. The warning overlay repair regression was discovered during that review and fixed before final verification. Removed an unused Boxes import. No actionable self-review finding remains. Parent/controller performs the independent task and branch reviews.

## Concerns / explicit limitations

- No blocking concern remains.
- Frontend-only demo: warehouse ID 1 only; no backend layout persistence. Refresh resets state, clearly stated in the UI.
- Runtime source-state differences are intentionally explicit, not pixel-perfect claims: preexisting permission-based shell; Figma pedagogical before/after composites; static partial-frame summary mismatch; B-01 staged examples shrink blocks unlike overview. The approved invariant forbids automatic resizing from quantity, so real placement remains geometry-safe and user-controlled.
- P3 follow-up only: native-select/Lucide glyph differences and small spacing adaptations for functional target selection/close controls.
- The controller must surface the verified IAB tab because subagent visibility is unsupported.
- No source images, generated assets, or image-quality blocker is missing.

## Full design QA audit and comparison history


# Warehouse Canvas Design QA

## Scope and capture

Completed 2026-09-06 against the approved Warehouse Canvas MVP spec and the six Figma nodes in file `jIz9HNkSoXH63gvTc3yOtj`. This section is appended; all earlier QA sections and unrelated screenshot evidence are preserved.

Evidence directory: `frontend/prototype-screenshots/warehouse-canvas/`. Every source PNG was downloaded from the actual Figma screenshot tool response and opened. Every implementation PNG is an actual Codex in-app browser capture of the routed Vue application, not a reconstruction. The comparison script crops both original images at 1:1 and joins them with source on the left.

| State | Actual Figma node | Source visual truth | Final implementation | Full-view comparison |
| --- | --- | --- | --- | --- |
| Overview | 1187:2 | source-overview.png | implementation-overview-after.png | comparison-overview-full-after.png |
| Draw/name area | 1190:516 | source-draw-area.png | implementation-draw-area-after.png | comparison-draw-area-full-after.png |
| Add SKU | 1193:39676 | source-add-sku.png | implementation-add-sku-after.png | comparison-add-sku-full-after.png |
| Selected SKU | 1193:40024 | source-sku-selected.png | implementation-sku-selected-after.png | comparison-sku-selected-full-after.png |
| Partial move | 1193:40372 | source-partial-move.png | implementation-partial-move-after.png | comparison-partial-move-full-after.png |
| Warnings | 1210:2466 | source-warning.png | implementation-warning-after.png | comparison-warning-full-after.png |

Final source and implementation images are each **1440 × 1024 pixels**, with a **1440 × 1024 CSS viewport and devicePixelRatio 1**. Full comparisons are **2880 × 1024**, with no rescaling or invented pixels. Initial overflowing captures for selected SKU, partial move, and overview iteration 1 are 1425 × 1013; their historical comparisons use the same 1425 × 1013 crop from both sides (2850 × 1013). These are diagnostic history, not final normalization.

The final floor measures x=456, y=352, width=728, height=672. The existing 244 px application sidebar and 64 px top bar are retained. Workspace tracks are 196 px / 760 px / 240 px. Selected-object properties overlay x=1080, y=168, width=360; the add/partial drawer is x=720, y=264, width=720, height=760. Selected-panel scrollHeight and clientHeight are both 854; the last destructive-action button ends at y=1000, without clipping.

## Focused comparisons

All focused artifacts below use matching original-image crops, source left and implementation right, without scale changes; they were opened and inspected in addition to all six full comparisons.

| Artifact | Crop x,y,w,h on each side | Combined pixels |
| --- | --- | --- |
| comparison-overview-toolbar-after.png | 936,190,504,90 | 1008 × 90 |
| comparison-overview-area-sku-after.png | 472,368,696,224 | 1392 × 224 |
| comparison-draw-area-draft-after.png | 892,376,276,360 | 552 × 360 |
| comparison-add-sku-drawer-after.png | 720,264,720,760 | 1440 × 760 |
| comparison-sku-selected-properties-after.png | 1080,168,360,856 | 720 × 856 |
| comparison-partial-move-arithmetic-after.png | 744,376,672,544 | 1344 × 544 |
| comparison-warning-issues-after.png | 1080,488,360,536 | 720 × 536 |
| comparison-warning-confirmation-delete.png | 588,448,440,240 | 880 × 240 |

The warning reference composes a selected SKU, a two-issue panel, and an area-delete dialog simultaneously. Runtime warning and area-delete states are captured separately rather than faking simultaneous selections. The last focused comparison uses `implementation-warning-delete.png`, copied from the actual first-confirmation capture. The issue panel and deletion confirmation are both verified.

## Findings and iteration history

Initial result was blocked. No actionable P0/P1/P2 finding remains after the following iterations.

| Severity / location | Evidence and impact | Fix and verified post-fix evidence |
| --- | --- | --- |
| P1: overview composition and mock geometry | comparison-overview-full-before.png: wrong area arrangement, small blocks, extra grid, mismatched inventory fixtures and density | Match actual overview geometry, names, quantities, packaging, page/header/workspace rhythm. Source geometry regression RED then GREEN. comparison-overview-full-after.png and area-sku crop |
| P2: area/library summaries and search | Before overview lacks per-area distinct SKU counts and aggregate whole-case/remainder copy; library filtering collapses source context | Add distinct counts and computed aggregate cases; keep all library cards and dim nonmatches. Regressions RED then GREEN. Final overview and toolbar/area crops |
| P1: drag/resize feedback; selection jitter | Deferred live-preview issue confirmed in pointer tests; objects jump only at release and click jitter can create history | Render transient geometry from active pointer interaction, commit once on release, cancel without mutation, ignore sub-4-unit jitter. New tests RED then GREEN; interaction-resized-250.png and interaction-whole-move-250.png |
| P1: draw over existing area and locked preview | implementation-draw-area-before.png shows the attempted draw selecting the covering A-01; locked blocks could visually preview a move | Forward draw-tool pointer input through area/SKU surfaces; prevent locked preview. Two new tests RED then GREEN. comparison-draw-area-full-after.png and draft crop |
| P1: selected-object and drawer proportions | comparison-sku-selected-full-before.png / comparison-add-sku-full-before.png / comparison-partial-move-full-before.png: incorrect panel width, form density and hierarchy | Match source overlays, drawer body/footer, read-only packaging, quantity and conservation hierarchy. All final full and focused panel comparisons |
| P2: footer/compact block readability | interaction-property-overflow-before.png: property actions overflow; compact 250-unit block needs readable quantity lines | Tighten property rhythm and use compact-block container styling. All property actions now fit 854 px; quantity remains readable after resize. Final properties crop and interaction-resized-250.png |
| P1: warning placement and repair access | comparison-warning-full-before.png: issues sit below the clipped floor; a fixed panel then obscured coordinate inputs | Move issue panel to the source-aligned right overlay; add close/reopen summary and collapse on locate, preserving count/save lock. Warning repair test RED then GREEN. comparison-warning-issues-after.png, interaction-warnings-repaired.png |
| P2: source-surface polish | Final draft comparison showed blue fill instead of neutral dashed outline; opacity animation produced inconsistent intermediate search captures | Neutral transparent dashed draft; immediate search opacity. Final draft and overview area/SKU comparisons inspected again |

## Required fidelity surfaces

- **Fonts/typography:** actual computed family is Noto Sans SC Variable / Inter / sans-serif, local project fonts. Page title is 24/32/700; section headings 18/26/500. Forms use 14 px values, readable hierarchy, native truncation and read-only states. Small source labels were checked in focused crops, not only scaled full views.
- **Spacing/layout:** source frame, three regions, floor coordinates, drawer/selected-panel bounds, 40 px primary controls and 8 px panel radii are reproduced. The source has static instructional composites; the functional property panel makes room for a target selector. No primary or destructive action is clipped at the target viewport.
- **Colors/tokens:** white and #F8FAFC surfaces, #E2E8F0 borders, project blue #536DFF, green conservation/save, pink destructive actions and amber warnings preserve the source semantics. Search matches have blue outlines; nonmatches are dimmed without changing totals or hiding areas. Warnings also have text, not color alone.
- **Images/assets/icons:** these six frames contain no product photos/illustrations to reconstruct. Existing application branding is preserved; controls use the installed Lucide library. No new fake raster imagery, handcrafted SVG, CSS illustration, emoji, or placeholder asset substitutes were introduced. Standard-native select chevrons and small Lucide glyph differences are P3.
- **Copy/content:** source SKU fixtures and packaging now match. User-facing Chinese copy states units are authoritative and size is visual only. Internal block IDs were removed from visible warning messages. Conservation uses live values, not static instructional text. The visible single-warehouse and page-memory limitation is explicit.

## Expected constraints and P3 differences

- The existing authenticated application shell has a permission-based navigation list, live account identity, and its existing global-search placeholder; it is not replaced by the static source shell.
- The Figma selected/partial frames show both before/after or preview blocks and explanatory implementation notes. Runtime only commits real blocks on confirmation; it does not duplicate inventory for illustration or expose implementation-language notes. The partial frame's static summary is inconsistent with its 250-unit source; runtime correctly totals 1,126 after changing the original 150 to 250.
- The source Add/Selected staged examples shrink existing B-01 blocks to make extra space, unlike the overview. Runtime retains manually chosen sizes, rejects placement into a full target, and succeeds in C-01 or newly drawn D-01. No automatic resizing or capacity logic was added.
- Layout coordinates remain visual canvas coordinates; the source's decorative metre labels are not treated as physical dimensions.
- The first nonempty-area dialog retains an explicit Cancel control and the existing second confirmation, stronger than the static source. The warning panel can collapse so its covered properties remain editable.
- P3 only: minor native-select/icon stroke differences, extra close controls, and small typography/spacing differences required by functional target selection. No mobile fidelity claim is made; mobile is explicitly outside the approved MVP.

## Interaction and console verification

- Draw/name D-01 (x=478,y=46,w=220,h=134), add blue SKU 250 = 10 件 + 10 个.
- Resize 120 × 80 to 138 × 97; quantity stays 250. Drag whole block D-01 → C-01; quantity stays 250 and total stays 1,276.
- Split 60 back into D-01: source 190 + target 60 = 250; warehouse total stays 1,276. Zero, negative, fractional and source-equal quantities disable confirmation.
- Undo/redo restores/reapplies the split without changing total.
- Create out-of-area and overlap warnings together; save is disabled. Locate/collapse each warning, repair coordinates, and save becomes enabled only after both clear.
- Nonempty A-01 deletion requires first and second confirmation. Confirmed demo deletion changes total 1,026 → 756; undo restores all products/areas and total 1,026.
- Native library drag opens the correct C-01 drawer. Lock disables inventory editing; hide removes only visible geometry, preserving 1,026 total. Zoom 150% and pan change viewport offsets (130,140), not stock.
- Query `warehouseId=99` visibly states that only demo warehouse ID 1 is supported and ID 99 was not loaded. Save is explicitly current-page memory and refresh resets the demo.
- 1280 × 900 and 1024 × 768 desktop checks: no document horizontal overflow; dense workspace scrolls internally at 1024; toolbar controls remain reachable. Native scrollbar-excluded screenshots are 1265 × 889 and 1009 × 757. The final preview is restored to 1440 × 1024.
- Final 5186 application console: zero warnings and zero errors. Two older router warnings in the same browser tab came from an unrelated checkout on port 5173 before the correct dev server was opened; those URLs/timestamps are recorded in the evidence log, not hidden.
- Before changes: 385 tests / 36 files passed, production build passed.
- Final: `npm run test:run` **393 tests / 36 files passed**; `npm run build` **passed** (vue-tsc and Vite, 1744 modules). Final focused warehouse coverage totals 79 tests across its component/view/controller/model files.

## Implementation checklist

- [x] Open actual Figma sources and routed implementation in Codex IAB.
- [x] Preserve original QA sections and screenshot history.
- [x] Record RED/GREEN before behavioral fixes.
- [x] Capture, compose and inspect full/focused comparisons.
- [x] Verify inventory conservation, safety states, desktop resilience and console.
- [x] Run full tests and production build; self-review the diff.
- [x] Leave the verified 5186 dev server and IAB preview running.

final result: passed


## Complete evidence paths and dimensions

Absolute evidence root: **C:\Users\张振亚\.codex\worktrees\46fe\BeBeFishERP\frontend\prototype-screenshots\warehouse-canvas**. Every filename in the table below resolves under that exact root; all source, implementation, comparison, and interaction artifacts are included. Source/final full dimensions are verified from actual PNG headers.

| Artifact path relative to the absolute evidence root | Pixels |
| --- | --- |
| comparison-add-sku-drawer-after.png | 1440 × 760 |
| comparison-add-sku-full-after.png | 2880 × 1024 |
| comparison-add-sku-full-before.png | 2880 × 1024 |
| comparison-draw-area-draft-after.png | 552 × 360 |
| comparison-draw-area-full-after.png | 2880 × 1024 |
| comparison-draw-area-full-before.png | 2880 × 1024 |
| comparison-overview-area-sku-after.png | 1392 × 224 |
| comparison-overview-full-after.png | 2880 × 1024 |
| comparison-overview-full-before.png | 2880 × 1024 |
| comparison-overview-full-iteration1.png | 2850 × 1013 |
| comparison-overview-toolbar-after.png | 1008 × 90 |
| comparison-partial-move-arithmetic-after.png | 1344 × 544 |
| comparison-partial-move-full-after.png | 2880 × 1024 |
| comparison-partial-move-full-before.png | 2850 × 1013 |
| comparison-sku-selected-full-after.png | 2880 × 1024 |
| comparison-sku-selected-full-before.png | 2850 × 1013 |
| comparison-sku-selected-properties-after.png | 720 × 856 |
| comparison-warning-confirmation-delete.png | 880 × 240 |
| comparison-warning-full-after.png | 2880 × 1024 |
| comparison-warning-full-before.png | 2880 × 1024 |
| comparison-warning-issues-after.png | 720 × 536 |
| implementation-add-sku-after.png | 1440 × 1024 |
| implementation-add-sku-before.png | 1440 × 1024 |
| implementation-add-sku-with-d-01.png | 1440 × 1024 |
| implementation-draw-area-after.png | 1440 × 1024 |
| implementation-draw-area-before.png | 1440 × 1024 |
| implementation-overview-after.png | 1440 × 1024 |
| implementation-overview-before.png | 1440 × 1024 |
| implementation-overview-iteration1.png | 1425 × 1013 |
| implementation-partial-move-after.png | 1440 × 1024 |
| implementation-partial-move-before.png | 1425 × 1013 |
| implementation-sku-selected-after.png | 1440 × 1024 |
| implementation-sku-selected-before.png | 1425 × 1013 |
| implementation-warning-after.png | 1440 × 1024 |
| implementation-warning-before.png | 1440 × 1024 |
| implementation-warning-delete.png | 1440 × 1024 |
| implementation-warning-issues.png | 1440 × 1024 |
| interaction-added-250.png | 1425 × 1013 |
| interaction-delete-confirmation.png | 1440 × 1024 |
| interaction-delete-final-confirmation.png | 1440 × 1024 |
| interaction-desktop-1024.png | 1009 × 757 |
| interaction-desktop-1280.png | 1265 × 889 |
| interaction-invalid-split.png | 1440 × 1024 |
| interaction-library-drop.png | 1440 × 1024 |
| interaction-property-overflow-before.png | 1425 × 1013 |
| interaction-resized-250.png | 1440 × 1024 |
| interaction-split-conserved.png | 1440 × 1024 |
| interaction-split-preview-250.png | 1440 × 1024 |
| interaction-undo-redo.png | 1440 × 1024 |
| interaction-warehouse-query-limitation.png | 1440 × 1024 |
| interaction-warnings-repaired.png | 1440 × 1024 |
| interaction-whole-move-250.png | 1440 × 1024 |
| interaction-zoom-pan.png | 1440 × 1024 |
| source-add-sku.png | 1440 × 1024 |
| source-draw-area.png | 1440 × 1024 |
| source-overview.png | 1440 × 1024 |
| source-partial-move.png | 1440 × 1024 |
| source-sku-selected.png | 1440 × 1024 |
| source-warning.png | 1440 × 1024 |

Non-image artifact paths:
- C:\Users\张振亚\.codex\worktrees\46fe\BeBeFishERP\frontend\prototype-screenshots\warehouse-canvas\compare.ps1
- C:\Users\张振亚\.codex\worktrees\46fe\BeBeFishERP\frontend\prototype-screenshots\warehouse-canvas\evidence.md
- C:\Users\张振亚\.codex\worktrees\46fe\BeBeFishERP\design-qa.md
- C:\Users\张振亚\.codex\worktrees\46fe\BeBeFishERP\.superpowers\sdd\2026-09-05-warehouse-canvas-frontend\task-6-report.md

## Final outcome

Implementation and evidence committed as c81ee17. Report committed separately under the subject recorded at the top. 393/393 tests and production build pass; six full and eight focused actual comparisons inspected; no actionable P0/P1/P2; verified server and browser are left running.

## Task 6 fix round 1 — completed

Review received two P2 findings: search selector specificity hid warning borders under blue search paint; the SKU search input removed its focus outline with no replacement. Both were confirmed against source CSS, compiled selectors and the controller's real IAB RED assertions.

Files changed in this round:
- frontend/src/features/inventory/warehouseCanvas/components/WarehouseFloorCanvas.vue — add the warning-state exclusion to search border paint.
- frontend/src/features/inventory/views/WarehouseCanvasView.vue — add the wrapper focus-within outline.
- frontend/src/features/inventory/warehouseCanvas/warehouseCanvasStyles.test.ts — two regression-first compiled-style/DOM checks, no production test hooks or new dependencies.
- frontend/prototype-screenshots/warehouse-canvas/compare.ps1 — optional source/input filenames for genuine runtime before/after and source comparison, preserving existing defaults and bounds checks.
- design-qa.md — fix-round section inside the appended warehouse QA, final result passed; all earlier sections preserved.
- frontend/prototype-screenshots/warehouse-canvas/evidence.md — append exact fix-round evidence/provenance/dimensions.
- This report — fix-round append.
- Thirteen new fix1 image artifacts, individually listed below. Earlier images are untouched.

Automated output:
- RED 01:59:02: style tests2 failed (invalid block eligible for search paint; no focus outline).
- Initial GREEN diagnosis: the search regression passed, but jsdom did not propagate focus-within or expand the outline longhands. The test now emulates only that unsupported pseudo-state and verifies the real compiled outline shorthand; actual native keyboard focus is separately verified in IAB.
- Finalized selector mutation RED02:02:58:1 failed/1 passed with only the old production search selector temporarily restored for a correctly sized before capture.
- Final restored affected run02:04:29:3 files/50 tests passed (style2, floor18, view30).
- Final restored full run02:04:33:37 files/395 tests passed, exit0.
- Final restored production build: vue-tsc --noEmit and Vite passed, exit0;1744 modules;3.50 s.
- Self-review: no unrelated files/data mutation, CSS fixes remain two narrowly scoped rules; no new functional behavior, inventory computation or geometry mutation. Confirmed tests include a valid-blue search control case rather than merely suppressing every search border.

Browser coordination: this resumed subagent no longer had an IAB provider after tool reset. The controller confirmed the IAB is parent-task scoped and performed before/after UI assertions and screenshots on the already authorized in-app surface. The agent did not substitute Chrome and performed all file/composite inspections locally. This is not a new subagent dispatch.


## Warehouse Canvas fix round 1 — reviewed combined states

The prior pass was reopened by two P2 review findings; both are now fixed. This section supersedes the earlier acceptance for the combined search/warning and keyboard-focus states only. No earlier QA section or screenshot was removed.

- **P2: search paint overriding warnings.** The source warning frame retains an active SKU search. The earlier runtime warning capture did not, so it failed to exercise the competing selectors. The high-specificity search rule painted blue over the amber warning border. The search rule now excludes `data-warning="true"`, preserving amber warning borders while valid search matches stay blue.
- **P2: absent keyboard search focus.** The borderless input had `outline:none` without replacement. Its wrapper now has `:focus-within { outline:2px solid #536dff; outline-offset:2px; }`, with no layout displacement.
- Regression-first validation: two new compiled-style tests failed before production edits. The warning test exercises the real compiled selector against valid/invalid matched DOM nodes. The focus test exercises compiled outline paint with descendant focus; it explicitly emulates only `:focus-within` for jsdom, whose selector engine does not propagate that state to ancestors. Real IAB keyboard focus and computed styles are the authoritative browser checks.
- Finalized mutation check: temporarily restoring the original search selector produced the expected 1 failed / 1 passed at 02:02:58. Restoring the fix yielded **50/50 affected tests** at 02:04:29. Final full suite **395 tests / 37 files passed** at 02:04:33; production typecheck/build passed, 1744 modules, 3.50 s.
- IAB was scoped to the parent task during this resumed round, so the controller captured the original browser bytes and computed styles on port 5186. No Chrome or external/headless browser was used. I inspected the resulting actual source/implementation and before/after composites.
- Browser RED: matching invalid blue A-01 block had `warning=true`, `searchMatch=true`, 2 px border `rgb(83,109,255)`. Browser GREEN: same combined state has 2 px `rgb(245,158,11)`. Quantity is 150 and total is 1,026; warning text and save lock remain.
- Native Tab focus RED: input `:focus-visible=true`, wrapper outline style none. GREEN: native keyboard focus remains true, wrapper outline solid `rgb(83,109,255)`, 2 px width and 2 px offset; base border is unchanged.
- All final comparison inputs are 1440 × 1024 at viewport1440 × 1024/DPR1. Full composites are 2880 × 1024. A first warning RED capture was actually1026 × 897; the compositor rejected it, it remains diagnostic only, and it was recaptured at1440 × 1024 before comparison. Parent IAB bytes are JPEG-encoded despite their supplied .png filename extension; they remain untouched. Composites are actual PNGs, decoded/cropped 1:1 without rescaling.
- Source correspondence: warning comparisons use `source-warning.png` with the same active SKU search and the targeted invalid blue block. The source's second overlap and delete composite are unrelated to this narrow regression; runtime isolates the single warning. Focus comparisons use `source-overview.png` for geometry; the reference does not define keyboard focus, so the before/after runtime focus delta proves the required accessible state instead of claiming it exists in the source.
- No actionable P0/P1/P2 remains in this review round. Previously documented demo limitations and P3 source/runtime differences are unchanged.
- Final parent-IAB console audit: **0 warnings, 0 errors**. The controller reloaded the restored GREEN route to a clean overview and marked the visible tab for handoff; port5186 remains running.

All paths below are relative to `frontend/prototype-screenshots/warehouse-canvas/`.

| Fix-round artifact | Actual pixels |
| --- | --- |
| comparison-overview-full-focus-fix1-after.png | 2880 × 1024 |
| comparison-overview-full-focus-fix1-before.png | 2880 × 1024 |
| comparison-overview-search-focus-fix1-after.png | 688 × 96 |
| comparison-search-focus-search-fix1-delta.png | 688 × 96 |
| comparison-warning-full-search-fix1-after.png | 2880 × 1024 |
| comparison-warning-full-search-fix1-before-1440.png | 2880 × 1024 |
| comparison-warning-invalid-block-search-fix1-after.png | 480 × 190 |
| comparison-warning-search-invalid-block-fix1-delta.png | 480 × 190 |
| implementation-search-focus-fix1-after.png | 1440 × 1024 |
| implementation-search-focus-fix1-before.png | 1440 × 1024 |
| implementation-warning-search-fix1-after.png | 1440 × 1024 |
| implementation-warning-search-fix1-before-1440.png | 1440 × 1024 |
| implementation-warning-search-fix1-before.png | 1026 × 897 |

Focused crops: warning x960,y408,w240,h190 on each side →480 × 190; search x588,y176,w344,h96 →688 × 96. Full source/implementation pairs and both focused deltas were opened and inspected.


Updated risks:
- No remaining blocking finding. Single-warehouse/current-page memory limitations and P3 native-control/source-composite differences remain explicit.
- jsdom cannot prove browser specificity/focus-within behavior by itself; real parent-IAB computed-style and screenshot assertions close this gap.
- JPEG capture encoding is explicitly documented; no image enhancement/upscaling or reconstructed browser state is used.
- Corrected 1440 before capture replaces the smaller diagnostic input for final evidence; the diagnostic original is preserved.
- Parent's visible IAB and port5186 server remain the handoff surface.

Commit for this round: **fix(inventory): preserve warning and search focus states** (exact SHA returned in the handoff, avoiding a self-referential report hash).
