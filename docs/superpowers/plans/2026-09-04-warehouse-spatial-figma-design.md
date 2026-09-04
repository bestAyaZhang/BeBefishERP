# Warehouse Spatial Figma Design Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Extend the existing BeBeFish ERP Figma design file with a complete, reviewable desktop design for measurable floor-stacked warehouse planning, location-authoritative inventory, derived 3D browsing, outbound allocation, location movement, and location-scoped stocktake.

**Architecture:** Keep the existing foundations, component library, application shell, and master-data screens intact. Add warehouse-spatial primitives to `02 Components`, then compose a new `04 Inventory & Warehouse` page from component instances. Treat the 2D layout as the operational source of spatial truth, the 3D scene as a rule-derived read model, and every inventory screen as location-and-stack aware. The design must show both fixed ground-stack positions and free stacking zones, never a rack/shelf model.

**Tech Stack:** Figma Design, Figma Plugin API through `use_figma`, the existing BeBeFish ERP variables and local components, Lucide-compatible icon assets already used by the product, desktop frames at `1440px` width.

**Spec:** `docs/superpowers/specs/2026-09-04-warehouse-spatial-inventory-design.md`

## Global Constraints

- Modify the existing Figma Design file `jIz9HNkSoXH63gvTc3yOtj`; do not create a new file.
- Preserve pages `01 Foundations`, `02 Components`, and `03 Master Data` and their current visual language.
- Create exactly one new product page named `04 Inventory & Warehouse`.
- Target desktop at `1440px` only. Mobile, tablet, PDA, and scanner-specific views are out of scope.
- Reuse the `244px` full-height sidebar, top toolbar, page header, buttons, fields, tables, drawers, dialogs, pagination, and feedback states already defined in `02 Components`.
- Use existing variables instead of raw duplicates: brand `#536DFF`, page `#F6F7FB`, panel `#FFFFFF`, subtle `#F8FAFC`, primary text `#25314D`, secondary `#64748B`, muted `#94A3B8`, border `#E2E8F0`, success `#16A36A`, info `#22B8CF`, warning `#F59E0B`, and danger `#EF476F`.
- Use the existing spacing ramp `4/8/12/16/20/24/32/40`, radius `6/8`, `Noto Sans SC` for Chinese, and `Inter` for numbers and English.
- Use Lucide-compatible icons from the existing product library. Do not draw replacement icons with primitive lines, emoji, text glyphs, or handcrafted SVG.
- Model a box-stacking warehouse: floor zones, fixed ground locations, free stacking zones, aisles, obstacles, temporary locations, and vertical stacks. Do not introduce racks, shelves, bays, levels, or bins as the core spatial metaphor.
- One vertical stack represents one SKU and one storage package level. A location may contain multiple adjacent stacks when mixing is allowed.
- Show stock in base SKU units and show packaging level, conversion, dimensions, rows, columns, layers, orientation, and utilization as placement metadata.
- The 3D design is rule-derived from placement data; do not add per-box coordinate editing.
- Capacity warnings, layout validation, stocktake freezes, and exception states must remain understandable without relying on color alone.
- Use realistic Chinese labels and sample data throughout. No lorem ipsum, `TBD`, `TODO`, unlabeled placeholders, or blank image boxes.
- Build each screen wrapper before its child sections. Create major sections in separate Figma write calls and record every created or mutated node ID.
- After each major section, capture a screenshot, inspect geometry and legibility, and correct defects before continuing.
- Do not modify frontend or backend code in this plan. The only local file changed during execution should be this plan's checkbox tracking unless a separately approved spec correction is required.
- Explicitly exclude CAD import, rack storage, cross-warehouse transfer, batch/expiry workflows, mobile operations, AGV routing, and manual per-box positioning.

## File And Artifact Map

- Modify Figma file: `https://www.figma.com/design/jIz9HNkSoXH63gvTc3yOtj`
- Reuse Figma page: `01 Foundations`
- Extend Figma page: `02 Components`
- Reference Figma page: `03 Master Data`
- Create Figma page: `04 Inventory & Warehouse`
- Reference: `frontend/src/layouts/ErpLayout.vue` — application shell and top-toolbar behavior
- Reference: `frontend/src/features/masterdata/views/WarehouseView.vue` — warehouse list, actions, and existing drawer conventions
- Reference: `frontend/src/features/inventory/views/InventoryBalanceView.vue` — current balance filters and table behavior
- Reference: `frontend/src/features/inventory/views/InventoryLedgerView.vue` — current ledger filters and movement history behavior
- Reference: `frontend/src/features/inventory/views/StockAdjustmentView.vue` — current adjustment terminology and form conventions
- Reference: `frontend/src/features/inventory/types.ts` — current inventory labels and data shapes
- Reference: `frontend/src/router/index.ts` — current inventory and warehouse navigation structure
- Visual reference: `frontend/prototype-screenshots/products/product-catalog-figma-reference.png` — approved shell, density, table, and surface treatment
- Local specification: `docs/superpowers/specs/2026-09-04-warehouse-spatial-inventory-design.md`
- Modify during execution: `docs/superpowers/plans/2026-09-04-warehouse-spatial-figma-design.md` — checkbox and execution-note tracking only

## Required Figma Naming

Component additions on `02 Components`:

```text
Spatial/Canvas Toolbar
Spatial/Layer Tree Row
Spatial/Zone Shape
Spatial/Fixed Location Shape
Spatial/Temporary Location Shape
Spatial/Stack Marker
Spatial/Capacity Legend
Spatial/Risk Badge
Spatial/Location Inspector
Inventory/Source Target Selector
Inventory/Stack Allocation Row
Inventory/Stocktake Count Row
Inventory/Publish Validation Item
```

Top-level frames on `04 Inventory & Warehouse`:

```text
Warehouse Spatial / 2D Monitor
Warehouse Spatial / 2D SKU Search
Warehouse Spatial / 2D Risk
Warehouse Spatial / 2D Location Selected
Warehouse Layout / Draft Editor
Warehouse Layout / Publish Validation
Warehouse Spatial / 3D Browse
Warehouse Spatial / 3D Risk Selected
Warehouse Spatial / 3D Unavailable
Inventory / Location Balances
Inventory / Outbound Allocation
Inventory / Movement List
Inventory / Movement Create
Inventory / Stocktake List
Inventory / Stocktake Scope
Inventory / Stocktake Counting
Inventory / Stocktake Review
```

---

### Task 1: Re-Audit The Existing File And Prepare The New Page

**Artifacts:**
- Inspect: Figma file `jIz9HNkSoXH63gvTc3yOtj`
- Inspect: pages `01 Foundations`, `02 Components`, `03 Master Data`
- Create: page `04 Inventory & Warehouse`
- Create: labeled page sections for components, spatial screens, inventory operations, stocktake, and QA

**Interfaces:**
- Consumes: the approved specification, existing Figma variables/components, and current source screenshots
- Produces: confirmed reusable node IDs, the new page ID, and empty section IDs for all later tasks

- [x] **Step 1: Load the required Figma skills and tool schema**

Read the complete instructions for `figma:figma-use` and `figma:figma-generate-design`. Load the schema for `use_figma` and the screenshot/metadata capabilities required by those skills. Do not load `figma-create-new-file` because this plan modifies an existing file.

- [x] **Step 2: Inspect the existing file before any write**

Read the page tree, local variables, text styles, components, component sets, and current selection. Confirm the file is a Figma Design file and confirm the exact IDs of `01 Foundations`, `02 Components`, and `03 Master Data`.

Expected: all three existing pages remain available, and their names and IDs are recorded in execution notes.

- [x] **Step 3: Inventory reusable design-system assets**

Record the node IDs and property names for the existing sidebar, top toolbar, page header, primary/secondary/danger buttons, search field, select, status tag, table cells/rows/container, pagination, drawer, confirm dialog, and loading/empty/error states.

Expected: later screens can use component instances rather than copied raw groups.

- [x] **Step 4: Inspect the existing warehouse and inventory references**

Capture a screenshot of the warehouse list in `03 Master Data` and inspect the current inventory pages or their stored reference frames if present. Compare the shell, density, padding, controls, and table treatment with `frontend/prototype-screenshots/products/product-catalog-figma-reference.png`.

Expected: the new work uses the same shell and visual density and does not invent a second design system.

- [x] **Step 5: Create and structure the new page**

Create page `04 Inventory & Warehouse`, then create labeled top-level section frames in this order:

```text
00 Cover & Flow Map
01 Spatial Monitoring
02 Layout Authoring
03 Inventory Operations
04 Stocktake
05 State & QA Board
```

Keep at least `160px` between top-level screen frames and at least `320px` between section groups so prototype links and review annotations remain readable.

- [x] **Step 6: Validate the page setup**

Read the updated page list and screenshot the new page overview. Verify there is exactly one new page, no existing node was renamed or deleted, and each section label is visible at page overview scale.

- [x] **Step 7: Record the checkpoint**

Update this task's checkboxes and record the confirmed page, variable, style, and component IDs. Do not proceed if a required existing component is missing; instead, list it as an explicit component addition in Task 2.

---

### Task 2: Build Warehouse-Spatial Component Extensions

**Artifacts:**
- Modify: Figma page `02 Components`
- Create: all components listed in `Required Figma Naming`
- Create: component documentation frame `Spatial & Inventory / Component Extensions`

**Interfaces:**
- Consumes: existing variables, text styles, fields, buttons, tags, drawers, table primitives, and Lucide icon components
- Produces: stable component IDs and documented properties used by every screen in Tasks 3–8

- [x] **Step 1: Create the canvas toolbar component**

Create `Spatial/Canvas Toolbar` with tool properties `Pan`, `Select`, `Draw Zone`, `Draw Location`, `Draw Aisle`, `Draw Obstacle`, and `Measure`; state properties `Default`, `Hover`, `Active`, and `Disabled`; plus zoom-out, zoom percentage, zoom-in, fit-to-screen, undo, and redo controls.

Expected: icon buttons are `36px` or `40px` high, active state includes both a filled treatment and a visible label/indicator, and the toolbar works on both monitor and editor frames.

- [x] **Step 2: Create the layer-tree row component**

Create `Spatial/Layer Tree Row` with type properties `Warehouse`, `Fixed Zone`, `Free Zone`, `Aisle`, `Obstacle`, and `Location`; state properties `Default`, `Selected`, `Hidden`, `Locked`, and `Warning`; indentation properties for at least three hierarchy levels; and text, visibility, lock, disclosure, and item-count properties.

- [x] **Step 3: Create zone and location shapes**

Create:

```text
Spatial/Zone Shape                 Type=Fixed|Free, State=Default|Selected|Warning|Disabled
Spatial/Fixed Location Shape       State=Empty|Occupied|Selected|Warning|Danger|Frozen
Spatial/Temporary Location Shape   State=Occupied|Selected|Warning|Danger|Frozen|Archived
```

Each shape must expose label, dimensions, utilization, and status text where appropriate. Fixed and temporary locations must remain distinguishable in grayscale through line style and icon/label, not color alone.

- [x] **Step 4: Create the stack marker component**

Create `Spatial/Stack Marker` with state properties `Default`, `Highlighted`, `Selected`, `Partial`, `Capacity Warning`, and `Frozen`. Expose SKU, package level, quantity, rows, columns, layers, orientation, and inbound-age text. Use a compact top/plan marker for 2D and a reusable label chip for 3D selection.

- [x] **Step 5: Create capacity and risk components**

Create `Spatial/Capacity Legend` for utilization bands `Empty`, `<50%`, `50–80%`, `80–100%`, and `Over Capacity`. Create `Spatial/Risk Badge` variants `Capacity`, `Overlap`, `Outside Boundary`, `Aisle Intrusion`, `Frozen`, and `Unpublished`. Every variant includes an icon and explicit text label.

- [x] **Step 6: Create the location inspector**

Create `Spatial/Location Inspector` as a `360px`-wide right-side panel with variants `Summary`, `Stacks`, and `Validation`. Include location identity, location type, zone, dimensions, used/available volume, SKU count, mixing policy, frozen state, stack rows, three recent ledger entries, and primary actions `移库`, `盘点`, and `查看流水`.

- [x] **Step 7: Create source/target and allocation components**

Create `Inventory/Source Target Selector` with source and target variants, warehouse/location search, selected location summary, capacity preview, and warning state. Create `Inventory/Stack Allocation Row` with recommendation rank, location, stack ID, SKU, package level, available quantity, suggested quantity, editable allocated quantity, inbound time, partial-stack indicator, and validation state.

- [x] **Step 8: Create stocktake and publish-validation rows**

Create `Inventory/Stocktake Count Row` with states `Blind Uncounted`, `Blind Counted`, `Matched`, `Difference`, `Recount Required`, and `Approved`. Hide the book quantity in blind-count variants. Create `Inventory/Publish Validation Item` with severity, rule, affected object, readable description, locate-on-canvas action, and resolved state.

- [x] **Step 9: Build the component documentation frame**

Place every new component set and its meaningful variants in `Spatial & Inventory / Component Extensions`. Add concise Chinese usage notes explaining fixed versus free zones, permanent versus temporary locations, one-stack-one-SKU/package rule, warning semantics, and blind-count behavior.

- [x] **Step 10: Validate the extensions**

Screenshot each component family at readable scale. Inspect text clipping, icon consistency, property naming, variable binding, contrast, hit-area size, and behavior in grayscale. Fix the smallest component-level defect and recheck all affected variants.

- [x] **Step 11: Record the checkpoint**

Record every component or component-set ID and property name. Later tasks must use these instances; raw duplication of component internals is a failure.

---

### Task 3: Design The 2D Warehouse Monitoring Experience

**Artifacts:**
- Create frames: `Warehouse Spatial / 2D Monitor`, `Warehouse Spatial / 2D SKU Search`, `Warehouse Spatial / 2D Risk`, `Warehouse Spatial / 2D Location Selected`
- Create reusable sample plan object group: `Demo Data / Shanghai Main Warehouse 2D`

**Interfaces:**
- Consumes: existing application shell and all spatial components from Task 2
- Produces: the primary operational overview and the four state frames used by prototype flows

- [x] **Step 1: Create the base 1440px wrapper**

Create `Warehouse Spatial / 2D Monitor` at `1440x1024`. Place the sidebar at `x=0` with width `244`, top toolbar at `x=244`, and page content below the toolbar. Use page title `仓库空间`, warehouse selector `上海主仓`, a `2D/3D` segmented switch, SKU/location search, risk filter, and `编辑布局` action.

- [x] **Step 2: Build the monitoring workspace shell**

Within the content area, create a left layer panel of approximately `248px`, a central canvas that receives all remaining flexible width, and a collapsible right inspector region of `360px`. Keep the canvas visually dominant and avoid wrapping it in nested decorative cards.

- [x] **Step 3: Draw a measurable box-stacking floor plan**

Using component instances, compose a warehouse boundary with:

```text
A 固定地堆区 — fixed locations A-01 through A-12
B 自由堆放区 — temporary locations TMP-20260904-001 and TMP-20260904-002
主通道 — dimensioned and visibly protected
消防栓障碍物 — blocked footprint
收货暂存区 and 发货暂存区 — labeled operational zones
```

Show a scale indicator, measurements in meters, grid visibility control, and several occupied locations with different utilization levels. Keep every stack on the floor; do not use rack elevation or shelf cells.

- [x] **Step 4: Populate the default monitoring state**

Use realistic samples such as `SKU-FISH-500ML-蓝`, `SKU-FISH-350ML-粉`, and `SKU-CUP-12OZ`. Show at least one mixed-SKU location represented by two adjacent stack markers, one nearly full location, one free-zone temporary location, and one empty fixed location.

- [x] **Step 5: Create the SKU-search state**

Duplicate the base frame as `Warehouse Spatial / 2D SKU Search`. Search for `SKU-FISH-500ML-蓝`, dim unrelated locations, highlight every matching stack, automatically focus the first result, show a result summary with total base units and location count, and open a compact result list with previous/next controls connected to the canvas selections.

- [x] **Step 6: Create the risk-filter state**

Duplicate the base frame as `Warehouse Spatial / 2D Risk`. Activate the capacity/risk filter and show at least one over-capacity stack, one aisle-intrusion layout issue, and one frozen stocktake location. Include a visible legend and an issue count; do not rely on red alone.

- [x] **Step 7: Create the selected-location state**

Duplicate the base frame as `Warehouse Spatial / 2D Location Selected`. Select `A-07`, open the `Spatial/Location Inspector`, and show two adjacent vertical stacks with SKU, package level, base-unit quantity, row/column/layer rule, used volume, inbound age, and actions.

- [x] **Step 8: Validate the four 2D frames**

Screenshot the full frames plus close crops of the canvas, legend, search result, and inspector. Verify location labels remain readable, fixed and temporary locations are unmistakable, selected/dimmed states are clear, warnings include text/icon cues, and the canvas does not clip at `1440x1024`.

- [x] **Step 9: Record the checkpoint**

Record the four frame IDs and the reusable 2D demo-plan group ID for prototype wiring and 3D consistency checks.

---

### Task 4: Design Layout Drafting And Publish Validation

**Artifacts:**
- Create frames: `Warehouse Layout / Draft Editor`, `Warehouse Layout / Publish Validation`
- Create state strip: `Layout Editor / Object Properties`

**Interfaces:**
- Consumes: the 2D plan, canvas toolbar, layer tree, zone/location shapes, fields, buttons, dialog, and validation rows
- Produces: a complete draft-edit-validate-publish flow without CAD import

- [ ] **Step 1: Create the draft editor shell**

Duplicate the 2D workspace geometry into `Warehouse Layout / Draft Editor`. Replace monitoring filters with draft status `草稿 v3`, saved-at text, undo/redo, `放弃更改`, `校验并发布`, and the full editing toolbar.

- [ ] **Step 2: Expose editable layers and drawing modes**

Use the layer tree to show warehouse boundary, fixed zone A, free zone B, main aisle, fire-hydrant obstacle, and fixed locations. Show lock/visibility controls and an active `Draw Location` tool. Add visible rulers, snap-grid state, and a small instruction strip describing click-drag drawing and metric dimensions. Include discoverable controls for select/multi-select, move, resize, rotate, align, duplicate, and undo/redo.

- [ ] **Step 3: Design the object-properties panel**

Create the `Layout Editor / Object Properties` strip and use it in the right inspector area. Provide contextual variants for warehouse boundary, fixed zone, free zone, aisle, obstacle, and fixed location. Fields include name/code, x/y, width/depth, rotation, maximum height, maximum weight, maximum volume, maximum SKU count, and single-SKU policy where applicable.

- [ ] **Step 4: Show a realistic in-progress draft**

Select a newly drawn fixed location `A-13`, show its dimension handles and measurements, and display one unresolved overlap with the main aisle plus one out-of-bound corner. The canvas must make clear that draft changes have not affected the published operational map.

- [ ] **Step 5: Create the publish-validation frame**

Duplicate the editor as `Warehouse Layout / Publish Validation`. Open a right-side validation drawer or panel listing blocking errors and warnings with `Inventory/Publish Validation Item` instances. Include summary counts, `在画布中定位`, `返回修改`, and a disabled `发布布局` action while blocking errors remain.

- [ ] **Step 6: Add the resolved publish state within the frame**

Below or beside the blocking state, create a named state group showing all blocking errors resolved, warnings acknowledged, `发布布局` enabled, and a confirmation dialog explaining that existing inventory keeps stable location IDs and only the new published geometry becomes operational.

- [ ] **Step 7: Validate authoring behavior**

Screenshot the editor, selected-object properties, validation list, and confirmation dialog. Verify metric fields are aligned, canvas selection handles do not obscure measurements, unpublished state is visible, and the publish action cannot be mistaken as available when errors exist.

- [ ] **Step 8: Record the checkpoint**

Record both frame IDs, the object-property state IDs, and the publish-confirmation overlay ID.

---

### Task 5: Design The Rule-Derived 3D Browser And Fallback

**Artifacts:**
- Create frames: `Warehouse Spatial / 3D Browse`, `Warehouse Spatial / 3D Risk Selected`, `Warehouse Spatial / 3D Unavailable`
- Create reusable group: `Demo Data / Shanghai Main Warehouse 3D`

**Interfaces:**
- Consumes: the exact zones, locations, stacks, quantities, and risk states used in Task 3
- Produces: a consistent derived scene and a usable 2D fallback path

- [ ] **Step 1: Create the 3D browser wrapper**

Duplicate the base 2D monitor shell into `Warehouse Spatial / 3D Browse`, switch the segmented control to `3D`, and retain warehouse, SKU search, risk filter, and layer visibility controls. Add orbit, pan, zoom, reset view, and floor-label toggles using existing icon-button patterns.

- [ ] **Step 2: Compose a derived box-stack scene**

Represent the same A-zone fixed locations and B-zone temporary locations as simple perspective box stacks. Each visual stack must correspond to a Task 3 stack and communicate packaging dimensions, orientation, rows, columns, layers, and remaining partial layer. Use surfaces and outlines from the existing palette; do not create a photorealistic scene or decorative warehouse illustration.

- [ ] **Step 3: Add orientation and utilization cues**

Show two packaging orientations, a partial top layer, stack height markers, floor labels, and a subtle utilization legend. Do not expose direct drag handles for individual boxes. Editing belongs to stack-rule forms, not the 3D viewport.

- [ ] **Step 4: Create the risk-selected state**

Duplicate the scene as `Warehouse Spatial / 3D Risk Selected`. Select the over-capacity stack from Task 3, outline the full stack, dim nonmatching stacks, and open the right inspector with the same SKU/package/quantity/rule data plus `修正规则` and `查看二维位置` actions.

- [ ] **Step 5: Create the unavailable fallback**

Create `Warehouse Spatial / 3D Unavailable` using the same shell. Replace only the viewport with a concise message that 3D rendering is unavailable, a technical-details disclosure, and a primary `返回二维视图` action. Preserve the selected warehouse, search, and risk filters so context is not lost.

- [ ] **Step 6: Cross-check 2D and 3D consistency**

Compare location codes, stack IDs, SKU, quantity, package level, utilization, and risk states between Tasks 3 and 5. Treat any mismatch as a design defect and correct the derived 3D scene.

- [ ] **Step 7: Validate the three 3D frames**

Screenshot the full scene, selected risk, and fallback. Verify labels do not collide, depth ordering remains understandable, partial layers are visible, no rack/shelf metaphor appears, and the fallback retains a direct operational route to 2D.

- [ ] **Step 8: Record the checkpoint**

Record all three frame IDs and the reusable 3D scene group ID.

---

### Task 6: Design Location Balances And Outbound Allocation

**Artifacts:**
- Create frames: `Inventory / Location Balances`, `Inventory / Outbound Allocation`
- Create overlay state: `Outbound Allocation / Capacity And Availability Validation`

**Interfaces:**
- Consumes: the existing list-page/table components, location inspector, and stack allocation row
- Produces: location-authoritative inventory query and recommended-source confirmation flow

- [ ] **Step 1: Create the location-balance frame**

Create `Inventory / Location Balances` at `1440x1024` using the existing application shell. Filters: warehouse, zone, location code, SKU, package level, location type, frozen state, utilization/risk, and keyword. Primary actions: `查看空间`, `移库`, and `发起盘点`.

- [ ] **Step 2: Build the balance table**

Use columns:

```text
库位 / 区域
库位类型
SKU / 商品
箱堆
包装层级
基础单位数量
可用 / 冻结
堆码规则
空间占用
最早入库
状态
操作
```

Include a mixed-SKU location as separate stack rows grouped under one location, a temporary location, a frozen row, and a warning row. Show warehouse total as a summary derived from the filtered location rows, not as a separately editable balance.

- [ ] **Step 3: Create the outbound-allocation frame**

Create `Inventory / Outbound Allocation` using a two-stage layout: order demand summary at top and recommended source stacks below. Use sample demand for one SKU with total base-unit quantity and display the allocation policy `优先清空零散箱堆，其次最早入库`.

- [ ] **Step 4: Show system recommendations and operator edits**

Use at least four `Inventory/Stack Allocation Row` instances. Rank a partial stack first, then older full stacks. Let the operator edit allocated quantities, replace a source, and see allocated/required/remaining totals update. Include `在空间中定位` per source.

- [ ] **Step 5: Add validation and confirmation states**

Create `Outbound Allocation / Capacity And Availability Validation` within or beside the frame. Show one over-allocation error, one frozen-source rejection, and the corrected confirm state. The final action `确认出库` remains disabled until the required quantity is exactly allocated and every source is available.

- [ ] **Step 6: Validate both inventory frames**

Screenshot the filters, grouped balance rows, recommendation policy, edited allocation rows, and validation state. Verify numeric alignment, base-unit quantities, package metadata, frozen-state wording, and partial-stack priority are all explicit.

- [ ] **Step 7: Record the checkpoint**

Record both frame IDs and the outbound validation state IDs.

---

### Task 7: Design Location Movement

**Artifacts:**
- Create frames: `Inventory / Movement List`, `Inventory / Movement Create`
- Create overlay states: `Movement / Capacity Warning`, `Movement / Confirmed`

**Interfaces:**
- Consumes: table, filter, source/target selector, allocation row, buttons, dialog, and location data from prior tasks
- Produces: same-warehouse atomic movement design with preview and confirm states

- [ ] **Step 1: Create the movement-list frame**

Create `Inventory / Movement List` with filters for movement number, warehouse, source location, target location, SKU, operator, status, and date range. Columns: movement number, source, target, SKU, package level, base-unit quantity, stack action, status, operator, created time, and action.

- [ ] **Step 2: Create the movement form shell**

Create `Inventory / Movement Create` with a three-part workflow: source selection, movement quantity/stack rule, and target selection. Keep source and target in the same warehouse and make that constraint visible in helper text.

- [ ] **Step 3: Populate the source selection**

Select source location `A-07`, stack `STK-A07-02`, and show available quantity, frozen quantity, package level, dimensions, rows/columns/layers, and current utilization. Support full-stack move and partial-quantity move choices.

- [ ] **Step 4: Populate target selection and preview**

Select target temporary location `TMP-20260904-002`. Show its existing SKU count, mixing policy, capacity before/after, expected utilization, and whether the move will merge into a compatible stack or create a new adjacent stack. Add a compact `2D/3D` preview switch that locates both source and target while preserving the same placement data.

- [ ] **Step 5: Create warning and confirmation overlays**

Create `Movement / Capacity Warning` with a confirmable soft-limit warning and a non-confirmable hard-limit error example. Create `Movement / Confirmed` summarizing one atomic transaction ID, source decrement, target increment, final stack IDs, and `查看流水` action.

- [ ] **Step 6: Validate movement design**

Screenshot the source selector, target selector, before/after capacity preview, warning, and confirmation. Verify source/target cannot be confused, values remain in base units, same-warehouse scope is explicit, and the confirmation communicates atomic completion.

- [ ] **Step 7: Record the checkpoint**

Record the two frame IDs and both overlay state IDs.

---

### Task 8: Design The Location-Scoped Stocktake Flow

**Artifacts:**
- Create frames: `Inventory / Stocktake List`, `Inventory / Stocktake Scope`, `Inventory / Stocktake Counting`, `Inventory / Stocktake Review`
- Create state group: `Stocktake / Freeze And Completion States`

**Interfaces:**
- Consumes: list/table/form components, stocktake count row, spatial shapes, and confirm dialog
- Produces: create-snapshot-freeze-blind-count-review-adjust-unfreeze experience

- [ ] **Step 1: Create the stocktake-list frame**

Create `Inventory / Stocktake List` with status summary cards kept compact, filters for stocktake number, warehouse, scope, owner, status, and date, plus a `新建盘点` action. Columns: number, warehouse, scope, location count, SKU count, progress, difference count, status, owner, created time, and action.

- [ ] **Step 2: Create the scope-selection frame**

Create `Inventory / Stocktake Scope` with warehouse selector and three scope methods: zones, individual locations, and map selection. Show selected fixed and temporary locations on a compact 2D preview. Explain that only selected locations will freeze and that a book snapshot is created at start.

- [ ] **Step 3: Add scope validation and start confirmation**

Show one location already frozen by another stocktake, remove it from the valid selection, and display valid location/SKU/stack totals. Add a confirmation dialog listing freeze scope, blind-count policy, snapshot time, and responsible person before `开始盘点`.

- [ ] **Step 4: Create the blind-counting frame**

Create `Inventory / Stocktake Counting` using `Inventory/Stocktake Count Row` instances. Show location and physical stack identity, SKU, package level, count input, packaging conversion helper, notes, photo/attachment affordance if it already exists in the design system, and progress. Do not expose book quantity or expected difference in the initial-count state.

- [ ] **Step 5: Create the difference-review frame**

Create `Inventory / Stocktake Review` showing book quantity, first count, recount, difference, reason, evidence, and approval action only after counting is submitted. Include matched, difference, recount-required, and approved rows. Summarize expected adjustment direction and base-unit amount.

- [ ] **Step 6: Create freeze and completion states**

Build `Stocktake / Freeze And Completion States` showing the selected locations as frozen in 2D, blocked outbound/movement feedback, an approved adjustment ledger reference, completion timestamp, and unfreezing success. Make clear that locations outside the scope remain operational.

- [ ] **Step 7: Validate the four stocktake frames**

Screenshot list, scope preview, start confirmation, blind rows, review rows, and completion state. Verify blind count truly hides book quantity, freeze scope is location-specific, temporary locations remain identifiable, and adjustments are shown only after approval.

- [ ] **Step 8: Record the checkpoint**

Record all four frame IDs and the freeze/completion state IDs.

---

### Task 9: Wire The End-To-End Prototype Flows

**Artifacts:**
- Modify: all top-level frames on `04 Inventory & Warehouse`
- Optionally modify: warehouse row action instance on `03 Master Data` without altering its master component
- Create: `00 Cover & Flow Map` overview content

**Interfaces:**
- Consumes: every validated frame and overlay ID
- Produces: clickable reviewer flows and a documented start-point map

- [ ] **Step 1: Build the cover and flow map**

In `00 Cover & Flow Map`, add the feature title, scope summary, key rules, page legend, and six linked flow cards: spatial monitoring, layout publishing, 3D browse, outbound allocation, movement, and stocktake. Use existing card/surface components and keep the content concise.

- [ ] **Step 2: Link warehouse management to spatial monitoring**

If the existing `Warehouses / List` frame can accept a local action instance without changing its reusable master, add `空间管理` and link it to `Warehouse Spatial / 2D Monitor`. Otherwise, document and demonstrate this entry on the flow map only; do not unexpectedly redesign `03 Master Data`.

- [ ] **Step 3: Wire spatial browsing**

Connect `2D Monitor` to `2D SKU Search`, `2D Risk`, and `2D Location Selected`. Connect the `2D/3D` switch between base 2D and 3D. Connect 3D stack selection to `3D Risk Selected`, and connect both `查看二维位置` and the fallback action back to the matching 2D state.

- [ ] **Step 4: Wire layout authoring**

Connect `编辑布局` to `Warehouse Layout / Draft Editor`, `校验并发布` to `Publish Validation`, every locate action back to the selected offending object in the editor, `返回修改` back to the editor, and the resolved confirmation back to the 2D monitor.

- [ ] **Step 5: Wire inventory query and outbound allocation**

Connect a location-balance row to the selected 2D location, `移库` to `Movement Create`, and the outbound flow through recommendation, operator adjustment, corrected validation, and confirmation back to the balance or movement history view.

- [ ] **Step 6: Wire movement**

Connect `Movement List` create action to `Movement Create`, target capacity preview to the warning overlay, confirm to the success overlay, and `查看流水` back to the relevant movement row or inventory history entry.

- [ ] **Step 7: Wire stocktake**

Connect `Stocktake List` through `Stocktake Scope`, start confirmation, `Stocktake Counting`, `Stocktake Review`, and completion. Link map selection to the scoped 2D preview and link frozen-location feedback to its stocktake detail.

- [ ] **Step 8: Set prototype start points**

Set `00 Cover & Flow Map` as the reviewer start point and `Warehouse Spatial / 2D Monitor` as the operational-flow start point. Use instant transitions or short smart-animate transitions only where the full-height shell remains stable.

- [ ] **Step 9: Test every link**

Run each of the six flows from start to finish. Verify back/cancel actions return to the correct state, overlays dismiss predictably, no link lands on a component documentation frame, and 2D/3D states preserve warehouse and filter context.

- [ ] **Step 10: Record the checkpoint**

Record both starting-point IDs and the tested interaction count. List any deliberately visual-only secondary control.

---

### Task 10: Final Structural, Visual, And Scope Verification

**Artifacts:**
- Validate: pages, components, frames, interactions, variables, and screenshots
- Complete: `05 State & QA Board`
- Update: execution notes and checkboxes in this plan

**Interfaces:**
- Consumes: all recorded Figma node IDs and screenshots from Tasks 1–9
- Produces: evidence-backed Figma handoff with no unreviewed residual state

- [ ] **Step 1: Build the QA board**

In `05 State & QA Board`, place representative instances for empty location, occupied location, temporary location, capacity warning, frozen location, 3D unavailable, allocation invalid, move warning, blind uncounted, difference review, layout blocking error, layout warning, and permission-disabled primary actions for layout publishing, movement confirmation, and stocktake approval. Label the rule demonstrated by each state.

- [ ] **Step 2: Run the page and naming audit**

Verify the existing three pages plus `04 Inventory & Warehouse`; all required component names; all 17 top-level frame names; both prototype starting points; and all recorded node IDs. There must be no duplicate page with a suffix and no unnamed `Frame 123` at the top level.

- [ ] **Step 3: Run the component and token audit**

Inspect new business frames to confirm they use existing or newly approved component instances and bound variables. Search for raw duplicate colors, unapproved typefaces, recreated buttons/fields/tables, and detached instances. Replace each unintended duplicate with the correct instance or variable.

- [ ] **Step 4: Run frame-geometry assertions**

For every product frame, verify width `1440`, sidebar width `244`, sidebar `x=0`, toolbar `x=244`, content aligned to the established shell, no outer body margin, no horizontal clipping, and no overlay outside frame bounds. Verify right inspectors are consistently `360px` and do not make the central workspace unusably narrow.

- [ ] **Step 5: Review all core screenshots**

Capture and inspect:

```text
Spatial component extensions
2D monitor, SKU search, risk, and selected location
Layout editor and publish validation
3D browse, risk selection, and unavailable fallback
Location balances and outbound allocation
Movement list, form, warning, and confirmation
Stocktake list, scope, blind count, review, and completion
Cover flow map and QA board
```

Check for broken auto layout, clipped Chinese text, inconsistent padding, incorrect font weight, wrong radius, unintended shadows, overlapping map labels, ambiguous warnings, or excessive empty space.

- [ ] **Step 6: Compare against the existing visual reference**

Place the relevant `03 Master Data` screenshot and a representative new inventory-list screenshot together at the same viewport scale. Compare shell geometry, page-title treatment, filter density, table row height, color, typography, border, and radius. Fix visible mismatches in the new work, then capture the comparison again.

- [ ] **Step 7: Run the domain-rule audit**

Verify the design visibly satisfies all approved rules:

```text
location inventory is authoritative
warehouse balance is derived
fixed and free stacking coexist
temporary location codes are trackable
one vertical stack has one SKU and one package level
mixed locations use adjacent stacks
3D is derived from stack rules
outbound clears partial stacks before oldest inbound
stocktake freezes selected locations only
blind count hides book quantity
```

Verify no rack metaphor, mobile flow, CAD import, cross-warehouse transfer, batch/expiry workflow, AGV routing, or per-box coordinate editor appears.

- [ ] **Step 8: Fix targeted defects and recheck consumers**

For every failed assertion, correct the smallest affected component or screen. If a component changes, screenshot every representative frame consuming it. Do not redesign already-approved areas during QA.

- [ ] **Step 9: Verify repository scope**

Run:

```powershell
git status --short --branch
git diff --check
```

Expected: only this plan file is modified for execution tracking unless an approved spec amendment is separately documented. No frontend or backend source file changes are present.

- [ ] **Step 10: Commit execution tracking**

```powershell
git add docs/superpowers/plans/2026-09-04-warehouse-spatial-figma-design.md
git commit -m "docs: track warehouse spatial Figma execution"
```

- [ ] **Step 11: Deliver the Figma design**

Provide the existing Figma file URL, identify the added page and principal frames, summarize the six tested flows, report the structural and visual validation evidence, and list any true residual limitation. Do not claim completion unless every required frame, component, screenshot check, and prototype flow passes.
