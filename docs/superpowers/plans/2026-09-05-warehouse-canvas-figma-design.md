# Warehouse Canvas MVP Figma Design Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Redesign the BeBeFish ERP warehouse experience as a simple desktop canvas where users draw named areas, place and resize SKU blocks, record inventory in individual units, see package conversions, and move all or part of a block between areas.

**Architecture:** Preserve the existing BeBeFish foundations, shell, fields, buttons, drawers, dialogs, and feedback patterns. Keep the previous spatial design as a clearly archived exploration, add one clean `05 Warehouse Canvas MVP` page, and make its six screens the only prototype path for the simplified warehouse flow. Treat inventory quantity in individual units as authoritative; area geometry and SKU-block size are user-authored visual planning metadata only.

**Tech Stack:** Figma Design, Figma Plugin API through `use_figma`, existing BeBeFish ERP variables and local components, Noto Sans SC and Inter, desktop frames at `1440×1024`.

**Spec:** `docs/superpowers/specs/2026-09-05-warehouse-canvas-inventory-design.md`

## Global Constraints

- Modify the existing Figma file `jIz9HNkSoXH63gvTc3yOtj`; do not create a new Figma file.
- Preserve `01 Foundations`, `02 Components`, and `03 Master Data` and reuse their existing visual language.
- Preserve the previous `04 Inventory & Warehouse` work as a non-destructive archived exploration; it must not be a prototype start or MVP navigation target.
- Create exactly one new product page named `05 Warehouse Canvas MVP`.
- Target desktop only at `1440×1024`; do not add mobile, tablet, PDA, or scanner screens.
- Keep the existing `244px` sidebar, top bar, page header, buttons, fields, tags, drawers, dialogs, and feedback-state conventions.
- Reuse the existing variables: brand `#536DFF`, page `#F6F7FB`, panel `#FFFFFF`, subtle `#F8FAFC`, primary text `#25314D`, secondary `#64748B`, muted `#94A3B8`, border `#E2E8F0`, success `#16A36A`, info `#22B8CF`, warning `#F59E0B`, and danger `#EF476F`.
- Use the existing spacing ramp `4/8/12/16/20/24/32/40`, radius `6/8`, `Noto Sans SC` for Chinese, and `Inter` for numbers and English.
- Use existing Lucide-compatible icon instances; do not draw replacement icons with primitive lines, emoji, text glyphs, or handcrafted SVG.
- Inventory is stored only as a non-negative integer count of individual units. Package counts are derived from the SKU's `unitsPerCase` and are never independently editable.
- Area geometry and SKU-block size are manually authored visual planning metadata. They never calculate or mutate inventory quantity.
- One area may contain multiple SKU blocks, and the same SKU may be split across multiple blocks.
- Whole-block moves preserve quantity and change only `areaId`; partial moves split a block while preserving total units.
- Prevent saving when a SKU block is outside its area or overlaps another SKU block; show the issue inline in the canvas and property panel.
- Do not introduce 3D, box dimensions, stacking rules, capacity/utilization calculations, publish workflows, outbound allocation, stocktake, racks, shelves, CAD, batches, expiry, or cross-warehouse transfer.
- Use realistic Chinese labels and data. Do not use lorem ipsum, `TBD`, `TODO`, blank placeholders, or placeholder image boxes.
- Build every screen shell before its child regions. Capture full and close screenshots and run geometry, typography, instance-linkage, and content checks before recording each task complete.
- Do not modify frontend or backend code. During execution, local changes are limited to this plan's checkbox/execution-note tracking and task reports in the plan-specific SDD workspace.

## File And Artifact Map

- Modify Figma file: `https://www.figma.com/design/jIz9HNkSoXH63gvTc3yOtj`
- Reuse Figma pages: `01 Foundations`, `02 Components`, `03 Master Data`
- Archive in place: current `04 Inventory & Warehouse`
- Create Figma page: `05 Warehouse Canvas MVP`
- Reference: `frontend/src/layouts/ErpLayout.vue` — desktop shell and top-bar behavior
- Reference: `frontend/src/features/masterdata/views/WarehouseView.vue` — warehouse naming and existing actions
- Reference: `frontend/src/features/inventory/types.ts` — SKU and inventory terminology
- Visual reference: `frontend/prototype-screenshots/products/product-catalog-figma-reference.png` — approved density and surface treatment
- Specification: `docs/superpowers/specs/2026-09-05-warehouse-canvas-inventory-design.md`
- Modify during execution: `docs/superpowers/plans/2026-09-05-warehouse-canvas-figma-design.md`

## Required Figma Naming

Reusable additions on `02 Components`:

```text
Warehouse/Area
Warehouse/SKU Block
Warehouse/Canvas Toolbar
Warehouse/Object Properties
```

Top-level frames on `05 Warehouse Canvas MVP`:

```text
Warehouse Canvas / Overview
Warehouse Canvas / Draw Area
Warehouse Canvas / Add SKU
Warehouse Canvas / SKU Selected
Warehouse Canvas / Partial Move
Warehouse Canvas / Boundary And Overlap Warning
```

---

### Task 1: Re-Audit The Existing File And Prepare The MVP Page

**Artifacts:**
- Inspect: Figma file `jIz9HNkSoXH63gvTc3yOtj`
- Preserve and mark as archived: current `04 Inventory & Warehouse`
- Create: page `05 Warehouse Canvas MVP`
- Create: labeled sections for scope, core canvas, product placement, and state/QA

**Interfaces:**
- Consumes: the approved MVP specification and existing Figma variables/components
- Produces: verified reusable node IDs, the MVP page ID, and empty section IDs for Tasks 2–5

- [ ] **Step 1: Load the required Figma skills**

Read `figma:figma-use` and `figma:figma-generate-design` completely, including every routed reference required for writing into an existing Figma Design file. Use `figma-use,figma-generate-design` in every `use_figma` call.

- [ ] **Step 2: Inspect the live file before any write**

Read the page tree, local variables, text styles, component sets, current selection, and current page count. Confirm the exact IDs of `01 Foundations`, `02 Components`, `03 Master Data`, and the current `04 Inventory & Warehouse` page.

Expected: the inspection records the existing page and design-system IDs before the first mutation.

- [ ] **Step 3: Reconfirm reusable shell and form components**

Record the current node IDs and exposed properties for the sidebar, top bar, page header, primary/secondary/danger buttons, search field, number field, select, tag, drawer, confirm dialog, and empty/error states.

Expected: later tasks can create real instances without detaching or rebuilding controls.

- [ ] **Step 4: Mark the previous exploration as archived**

Rename the existing page to `Archive — 04 Inventory & Warehouse (Superseded)` or add an equally explicit archive prefix if Figma prevents that exact punctuation. Do not delete, flatten, detach, or mutate its contained designs.

Expected: the previous complex work remains recoverable but is unambiguously outside the MVP.

- [ ] **Step 5: Create the MVP page and sections**

Create exactly one page named `05 Warehouse Canvas MVP`. Add four top-level sections:

```text
00 Scope
01 Core Canvas
02 Product Placement
03 State & QA
```

Place sections vertically with at least `320px` separation. Size `01 Core Canvas` for two `1440×1024` frames, `02 Product Placement` for three, and `03 State & QA` for the warning frame plus QA evidence.

- [ ] **Step 6: Add a concise scope cover**

Inside `00 Scope`, add a title, one-sentence core flow, and explicit exclusions: `无 3D · 无堆码规则 · 无容量计算 · 无货架`. Reuse existing text styles and semantic colors.

- [ ] **Step 7: Validate and record the checkpoint**

Verify the file has one new page only, the old page remains intact under its archive name, the four sections are contained and nonoverlapping, and variables/styles/component counts are unchanged. Record page/section IDs in the plan-specific task report and check only Task 1 boxes.

- [ ] **Step 8: Commit the checkpoint**

```bash
git add docs/superpowers/plans/2026-09-05-warehouse-canvas-figma-design.md
git commit -m "docs: track warehouse canvas Figma task 1"
```

---

### Task 2: Build The Minimal Reusable Canvas Components

**Artifacts:**
- Create component sets: `Warehouse/Area`, `Warehouse/SKU Block`, `Warehouse/Canvas Toolbar`, `Warehouse/Object Properties`
- Create a compact documentation frame on `02 Components`

**Interfaces:**
- Consumes: Task 1's confirmed design-system IDs and existing field/button/icon components
- Produces: stable component IDs and property names consumed by all six MVP frames

- [ ] **Step 1: Load the component-library instructions**

Read `figma:figma-use` and `figma:figma-generate-library` completely. Use `figma-use,figma-generate-library` in every Task 2 `use_figma` call and keep all calls sequential.

- [ ] **Step 2: Define the Area component set**

Create `Warehouse/Area` with variants `State=Default|Selected|Locked|Warning`. Include editable area name, subtle grid-safe fill, border, selection handles, a compact summary slot, and lock/visibility cues using existing icon instances. Keep the area body visually quiet so multiple SKU blocks remain readable.

- [ ] **Step 3: Define the SKU Block component set**

Create `Warehouse/SKU Block` with variants `State=Default|Selected|Warning`. Expose text properties for product name, SKU code, authoritative units, derived case copy, and optional area code. Include resize handles only in `Selected`; `Warning` must show an icon and explicit text rather than color alone.

- [ ] **Step 4: Define the Canvas Toolbar component set**

Create `Warehouse/Canvas Toolbar` with `Tool=Select|Draw Area|Pan` and `State=Default|Hover|Active|Disabled`. Reuse existing icon/button patterns and expose zoom text plus undo/redo enabled properties.

- [ ] **Step 5: Define the Object Properties component set**

Create `Warehouse/Object Properties` with `Context=Area|SKU`. Compose it entirely from real existing field/select/button instances. Area context exposes name, x, y, width, height, lock, and delete. SKU context exposes SKU identity, units, read-only package conversion, x, y, width, height, whole move, partial move, and delete.

- [ ] **Step 6: Bind existing variables and component properties**

Bind color, spacing, radius, and typography to the current BeBeFish variables/styles wherever the Plugin API permits. Keep real nested instance links intact and do not add new global styles, effects, or variables.

- [ ] **Step 7: Build the compact documentation frame**

Show every variant at readable size, including `250 个 → 10 件 + 10 个` for an SKU with `24 个/件`. Label the rule `库存只记录个；件数自动换算`.

- [ ] **Step 8: Validate and record the checkpoint**

Check component names, variant axes, exposed properties, nested instance links, text contrast, resize-handle visibility, and lack of overflow. Record component-set IDs and all property IDs in the task report.

- [ ] **Step 9: Commit the checkpoint**

```bash
git add docs/superpowers/plans/2026-09-05-warehouse-canvas-figma-design.md
git commit -m "docs: track warehouse canvas Figma task 2"
```

---

### Task 3: Design The Core Canvas And Area Creation

**Artifacts:**
- Create frames: `Warehouse Canvas / Overview`, `Warehouse Canvas / Draw Area`
- Create reusable demo group: `Demo Data / Warehouse Canvas MVP`

**Interfaces:**
- Consumes: Task 2 component IDs and existing application-shell instances
- Produces: the base canvas, area geometry, demo inventory data, and drawing interaction used by Tasks 4–5

- [ ] **Step 1: Build the Overview shell**

Create `Warehouse Canvas / Overview` at `1440×1024` inside `01 Core Canvas`. Use the existing sidebar and top bar. The content header must show warehouse selection, SKU search, undo, redo, save status, and a primary `添加产品` action.

- [ ] **Step 2: Compose the three-panel workspace**

Use a compact left panel for areas/layers, a dominant central canvas, and a right product library. The central canvas must retain at least half of the content width and must not be visually compressed by the side panels.

- [ ] **Step 3: Build authoritative demo data**

Create at least three area instances named `A-01`, `B-01`, and `C-01`. Populate at least five SKU-block instances, including two different SKUs in `A-01` and one SKU split across two areas. Use these exact examples where shown:

```text
SKU-FISH-500ML-蓝 · 250 个 · 24 个/件 · 10 件 + 10 个
SKU-CUP-12OZ · 120 个 · 50 个/件 · 2 件 + 20 个
SKU-FISH-350ML-粉 · 576 个 · 24 个/件 · 24 件
```

Ensure every area total and SKU total equals the sum of its visible blocks.

- [ ] **Step 4: Add summary and search behavior**

Show area count, SKU count, and total individual units as derived read-only summary values. Demonstrate a SKU search that highlights matching blocks and dims nonmatches without hiding area boundaries.

- [ ] **Step 5: Create the Draw Area frame**

Duplicate the validated Overview into `Warehouse Canvas / Draw Area`. Set `Draw Area` active, show crosshair guidance and a new dashed rectangle inside the canvas, and open a compact naming popover with `区域名称: D-01`, `取消`, and `创建区域`.

- [ ] **Step 6: Show manual area geometry controls**

Select the new area and show real fields for x, y, width, and height in meters or canvas units. State explicitly that area size is for layout only and does not calculate capacity or inventory.

- [ ] **Step 7: Validate both frames**

Capture full and close screenshots. Check `1440×1024` geometry, side-panel containment, area/SKU text readability, exact derived summaries, real component linkage, no overlap, no missing fonts, and no excluded concepts.

- [ ] **Step 8: Record and commit the checkpoint**

Record both frame IDs, demo-group ID, demo-data ledger, and screenshot evidence. Check only Task 3 boxes, then commit:

```bash
git add docs/superpowers/plans/2026-09-05-warehouse-canvas-figma-design.md
git commit -m "docs: track warehouse canvas Figma task 3"
```

---

### Task 4: Design Product Placement, Editing, And Partial Movement

**Artifacts:**
- Create frames: `Warehouse Canvas / Add SKU`, `Warehouse Canvas / SKU Selected`, `Warehouse Canvas / Partial Move`

**Interfaces:**
- Consumes: Task 3 base canvas, demo data, areas, and Task 2 components
- Produces: the complete add/edit/move workflow and quantity-conservation evidence

- [ ] **Step 1: Create the Add SKU state**

Duplicate the base canvas into `Warehouse Canvas / Add SKU` inside `02 Product Placement`. Show an SKU card being dragged from the product library over `B-01`, with a valid drop target and the rest of the canvas unchanged.

- [ ] **Step 2: Add the quantity-entry state**

After the visual drop, show a compact popover or drawer with real SKU identity, read-only `24 个/件`, editable `库存个数: 250`, and derived `10 件 + 10 个`. The primary action is `放入 B-01`.

- [ ] **Step 3: Create the SKU Selected state**

Create `Warehouse Canvas / SKU Selected` with one real `Warehouse/SKU Block` in `Selected`. Show manual resize handles and a live `Warehouse/Object Properties` SKU instance. Make the copy explicit: `调整大小只改变画布占地，不改变库存`.

- [ ] **Step 4: Show whole-block movement**

Within the selected state or a clearly labeled local state group, show the SKU block moving from `A-01` to `B-01`. Preserve its exact unit count and derived package copy while changing only its area code.

- [ ] **Step 5: Create the Partial Move frame**

Create `Warehouse Canvas / Partial Move`. Use this exact quantity example:

```text
Source block: A-01 / 250 个
Move: 60 个
Source after move: A-01 / 190 个
New target block: B-01 / 60 个
Conservation: 190 + 60 = 250 个
```

Show target-area selection, a real number field, before/after preview, and confirmation dialog or drawer.

- [ ] **Step 6: Make invalid partial quantities explicit**

In a compact local validation state, show that `0`, negative numbers, decimals, and values greater than or equal to the source quantity are invalid. Keep the final action disabled until the move quantity is a valid positive integer smaller than the source.

- [ ] **Step 7: Validate all three frames**

Capture full and close screenshots. Verify drop target, unit-to-case calculation, selected handles, whole-move invariants, partial-move conservation, button enabled states, text overflow, and real field/component instances.

- [ ] **Step 8: Record and commit the checkpoint**

Record the three frame IDs and every key overlay/state ID. Check only Task 4 boxes, then commit:

```bash
git add docs/superpowers/plans/2026-09-05-warehouse-canvas-figma-design.md
git commit -m "docs: track warehouse canvas Figma task 4"
```

---

### Task 5: Design Inline Warnings, Wire The Prototype, And Run Final QA

**Artifacts:**
- Create frame: `Warehouse Canvas / Boundary And Overlap Warning`
- Wire one end-to-end MVP prototype path
- Create a compact QA evidence board inside `03 State & QA`

**Interfaces:**
- Consumes: all Task 2 components and Task 3–4 frames
- Produces: the final reviewable Figma MVP and verification record

- [ ] **Step 1: Create the warning frame**

Duplicate the selected-SKU state into `Warehouse Canvas / Boundary And Overlap Warning`. Show one SKU block crossing its area boundary and one pair of overlapping SKU blocks. Use warning outlines, icons, and explicit Chinese messages; do not rely on color alone.

- [ ] **Step 2: Show inline correction behavior**

In the property panel, list both issues with `定位问题`. Keep `保存布局` disabled while issues remain. Add a small corrected-state group where both blocks are valid and the save action is enabled; do not create a separate publish or validation page.

- [ ] **Step 3: Add destructive-action safeguards**

Show one real confirm dialog for deleting a non-empty area. The dialog must explain that the user must first move its SKU blocks or explicitly choose to delete them together. Destructive action uses the existing danger-button treatment.

- [ ] **Step 4: Wire the primary prototype path**

Create prototype interactions for:

```text
Overview → Draw Area → Add SKU → SKU Selected → Partial Move
SKU Selected → Boundary And Overlap Warning → corrected state → Overview
```

Use short dissolve or smart-animate transitions consistent with the current file. Set `Warehouse Canvas / Overview` as the only MVP prototype start.

- [ ] **Step 5: Audit functional semantics**

Verify through visible data and node properties that units are authoritative, case copy is derived, manual resize does not change units, whole moves change only area, partial moves conserve units, and warning states block save.

- [ ] **Step 6: Audit visual quality**

Capture all six full frames at the same scale plus close crops for the canvas, SKU block, property panel, partial-move math, warning messages, and delete dialog. Check padding, alignment, typography, borders, radii, contrast, clipping, and overlap like a senior product designer.

- [ ] **Step 7: Audit file hygiene**

Confirm:

```text
Exactly one MVP page named 05 Warehouse Canvas MVP
Exactly six required top-level MVP frames
Exactly four required Warehouse/* component sets
No missing component masters
No missing fonts
No new global variables, paint styles, effects, grid styles, or text styles
No 3D, stack-rule, capacity, rack, shelf, CAD, outbound, or stocktake concepts on the MVP page
The archived exploration is not a prototype start or interaction destination
```

- [ ] **Step 8: Build the QA evidence board**

Inside `03 State & QA`, add a compact checklist with pass/fail evidence for package conversion, whole-move invariance, partial-move conservation, boundary blocking, overlap blocking, prototype starts, component links, and scope exclusions.

- [ ] **Step 9: Record final evidence and commit**

Record all six frame IDs, four component-set IDs, prototype start ID, key interaction destination IDs, screenshot evidence, and final audit counts. Check Task 5 boxes, then commit:

```bash
git add docs/superpowers/plans/2026-09-05-warehouse-canvas-figma-design.md
git commit -m "docs: complete warehouse canvas Figma design"
```
