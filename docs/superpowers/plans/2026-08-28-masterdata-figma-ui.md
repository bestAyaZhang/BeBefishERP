# BeBefish ERP Master Data Figma UI Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Create and validate a componentized Figma design file for the BeBefish ERP desktop master-data experience, covering products, categories, customers, suppliers, and warehouses.

**Architecture:** Build one Figma design file in three layers: semantic foundations, reusable components, and 1440px business screens. The business screens must consume the foundation variables and component instances, use a full-width application shell with a 244px full-height sidebar, and expose the approved list, form, drawer, upload, state, and prototype flows.

**Tech Stack:** Figma Design, Figma Plugin API via `use_figma`, Figma variables and components, Lucide-compatible SVG icons from the Vue codebase, local BeBefish ERP at `http://localhost:5174`, Vue 3 source references.

**Spec:** `docs/superpowers/specs/2026-08-28-masterdata-figma-ui-design.md`

## Global Constraints

- Target only a `1440px` desktop viewport in this plan.
- Use a full-width shell with no outer maximum-width wrapper and no left/right body margin.
- Use a fixed `244px` sidebar flush with the left edge and spanning the full viewport height.
- Start the top toolbar at `x=244` and fill the remaining width.
- Use `24px` or `32px` internal horizontal padding and a `24px` layout gap.
- Use `#536DFF` for the brand primary color, `#F6F7FB` for the page background, `#FFFFFF` for surfaces, and `#25314D` for primary text.
- Use semantic green, cyan, amber, and red only for business status communication.
- Use `8px` card/panel radius and `6px` to `8px` control radius.
- Avoid large gradients, decorative blobs, floating page-section cards, and excessive shadows.
- Verify `Noto Sans SC` availability for Chinese text and `Inter` for English/numbers before creating text styles. Record and consistently apply a verified Chinese sans-serif fallback if `Noto Sans SC` is unavailable.
- Use Lucide-compatible SVG icons; do not redraw icons from primitive lines or shapes.
- Build reusable items as components and place instances in screens.
- Every `use_figma` write returns every created or mutated node ID.
- Build the outer wrapper before child sections and build each major section in a separate call.
- Take a screenshot after each section and fix clipping or overlap before moving on.
- Do not modify the Vue application or backend as part of this plan.

## File And Artifact Map

- Create Figma file: `BeBefish ERP — Master Data UI`
- Create Figma page: `01 Foundations`
- Create Figma page: `02 Components`
- Create Figma page: `03 Master Data`
- Reference: `frontend/src/layouts/ErpLayout.vue` — shell and top toolbar behavior
- Reference: `frontend/src/components/navigation/SidebarNav.vue` — navigation groups and labels
- Reference: `frontend/src/features/product/views/ProductListView.vue` — product-list route behavior
- Reference: `frontend/src/features/product/views/ProductDetailView.vue` — product-detail behavior
- Reference: `frontend/src/features/product/components/ProductForm.vue` — product form fields and sections
- Reference: `frontend/src/features/masterdata/views/CategoryView.vue` — category list and drawer behavior
- Reference: `frontend/src/features/masterdata/views/CustomerView.vue` — customer list behavior
- Reference: `frontend/src/features/masterdata/views/CustomerFormView.vue` — customer form behavior
- Reference: `frontend/src/features/masterdata/views/SupplierView.vue` — supplier behavior
- Reference: `frontend/src/features/masterdata/views/WarehouseView.vue` — warehouse behavior
- Modify: `docs/superpowers/plans/2026-08-28-masterdata-figma-ui.md` — mark checkboxes during execution only

---

### Task 1: Create The Figma File And Capture The Existing Product Context

**Artifacts:**
- Create: Figma file `BeBefish ERP — Master Data UI`
- Create: pages `01 Foundations`, `02 Components`, `03 Master Data`
- Capture: current desktop references for products, categories, customers, suppliers, and warehouses

**Interfaces:**
- Consumes: approved design spec and the running local app at `http://localhost:5174`
- Produces: Figma file key, three page IDs, and five source-reference screenshots for later tasks

- [ ] **Step 1: Load the required skills and tool schemas**

Read the complete instructions for `figma:figma-create-new-file`, `figma:figma-use`, `figma:figma-generate-design`, `figma:figma-generate-library`, and `browser:control-in-app-browser`. Load `create_new_file`, `use_figma`, `get_metadata`, `get_screenshot`, `search_design_system`, and any Figma capture tool schemas in one tool-discovery call.

- [ ] **Step 2: Capture the five current module pages at 1440px**

Use the authenticated local app and capture these URLs or route states:

```text
http://localhost:5174/products
http://localhost:5174/categories
http://localhost:5174/customers
http://localhost:5174/suppliers
http://localhost:5174/warehouses
```

Expected: each capture shows the current full shell, title, filters, table, and pagination without browser chrome. If authentication has expired, log in with the documented local development account before capturing.

- [ ] **Step 3: Create a new Figma Design file**

Create `BeBefish ERP — Master Data UI` as a design file. Do not use FigJam or Slides.

Expected: the returned URL matches `figma.com/design/...` and provides a file key usable by subsequent calls.

- [ ] **Step 4: Inspect the empty file before writing**

Return the editor type, current pages, local variable collections, local components, and available fonts. Confirm `editorType === "figma"`.

- [ ] **Step 5: Create and name the three pages**

Create `01 Foundations`, `02 Components`, and `03 Master Data`. Delete or rename the initial default page so exactly these three page names remain.

Expected metadata:

```text
01 Foundations
02 Components
03 Master Data
```

- [ ] **Step 6: Commit the task checkpoint**

No repository files change in this task except checkbox tracking. Record the Figma file URL and page IDs in the execution notes before continuing.

---

### Task 2: Build Semantic Foundation Variables And Text Styles

**Artifacts:**
- Modify: Figma page `01 Foundations`
- Create: local variable collections `Color`, `Space`, `Radius`
- Create: text styles under `Typography/*`

**Interfaces:**
- Consumes: page ID for `01 Foundations` and verified font names from Task 1
- Produces: variable IDs and text-style IDs used by every later component and screen

- [ ] **Step 1: Create the color variable collection**

Create these variables with explicit scopes:

```text
Color/Brand/Primary       #536DFF  FRAME_FILL, SHAPE_FILL, TEXT_FILL, STROKE_COLOR
Color/Brand/PrimaryHover  #465EEA  FRAME_FILL, SHAPE_FILL
Color/Surface/Page        #F6F7FB  FRAME_FILL
Color/Surface/Panel       #FFFFFF  FRAME_FILL
Color/Surface/Subtle      #F8FAFC  FRAME_FILL
Color/Text/Primary        #25314D  TEXT_FILL
Color/Text/Secondary      #64748B  TEXT_FILL
Color/Text/Muted          #94A3B8  TEXT_FILL
Color/Border/Default      #E2E8F0  STROKE_COLOR
Color/Status/Success      #16A36A  FRAME_FILL, SHAPE_FILL, TEXT_FILL
Color/Status/Info         #22B8CF  FRAME_FILL, SHAPE_FILL, TEXT_FILL
Color/Status/Warning      #F59E0B  FRAME_FILL, SHAPE_FILL, TEXT_FILL
Color/Status/Danger       #EF476F  FRAME_FILL, SHAPE_FILL, TEXT_FILL
```

Expected: one `Color` collection and 12 variables, all returned by ID.

- [ ] **Step 2: Create spacing and radius variables**

Create number variables with explicit scopes:

```text
Space/4=4, Space/8=8, Space/12=12, Space/16=16, Space/20=20,
Space/24=24, Space/32=32, Space/40=40
Radius/6=6, Radius/8=8
```

Use `GAP` and padding scopes for spacing variables and corner-radius scopes for radius variables.

- [ ] **Step 3: Create the text-style ramp**

Create and name these styles using verified font family/style strings:

```text
Typography/Page Title       24/32, Bold
Typography/Section Title    18/26, Semi Bold
Typography/Card Title       15/22, Semi Bold
Typography/Body             14/22, Regular
Typography/Body Strong      14/22, Semi Bold
Typography/Label            12/18, Medium
Typography/Caption          12/18, Regular
Typography/Table Number     14/20, Semi Bold, Inter
```

Expected: Chinese styles use the verified Chinese font; `Typography/Table Number` uses Inter.

- [ ] **Step 4: Build a visible foundation specimen frame**

Create a `1440px`-wide specimen containing color swatches, spacing markers, radius samples, and each text style with Chinese sample copy. Bind specimen nodes to the variables/styles rather than duplicating raw values.

- [ ] **Step 5: Validate foundations**

Take screenshots of the color and typography sections. Verify no Chinese glyph fallback boxes, no clipped labels, all colors match the approved values, and every visible sample is bound to its corresponding variable or style.

- [ ] **Step 6: Commit the task checkpoint**

Update the plan checkbox state only after the foundation page passes metadata and screenshot validation.

---

### Task 3: Build The Application Shell Components

**Artifacts:**
- Modify: Figma page `02 Components`
- Create components: `Navigation/Sidebar`, `Navigation/Sidebar Item`, `Navigation/Sidebar Group`, `Header/Top Bar`, `Header/Page Header`

**Interfaces:**
- Consumes: foundation variable IDs, text styles, and Lucide-compatible SVG source
- Produces: component IDs used by all business frames

- [ ] **Step 1: Create the sidebar-item component set**

Create variants for:

```text
State=Default
State=Hover
State=Active
State=Disabled
```

Each variant is `212x44`, uses an imported `20x20` SVG icon, exposes text and icon swap properties, and binds colors/radii to foundation variables.

- [ ] **Step 2: Create grouped navigation**

Create `Navigation/Sidebar Group` with collapsed and expanded states. Use `ChevronDown` SVG for the disclosure indicator and nested sidebar-item instances for children.

- [ ] **Step 3: Create the full-height sidebar component**

Build a `244x1024` component with brand area, main navigation, grouped entries, and pinned user section. The component must be a continuous full-height surface with no outer radius and no external margin.

- [ ] **Step 4: Create the top toolbar component**

Build a `1196x64` component containing breadcrumb, global search, account, settings, and logout icon buttons. Bind fills, borders, gaps, and text styles.

- [ ] **Step 5: Create the page-header component**

Create title, description, optional count badge, and primary action slot. Provide `Action=Shown/Hidden` variants.

- [ ] **Step 6: Validate shell components**

Take individual screenshots of the sidebar, toolbar, and page header. Verify the sidebar is exactly `244px` wide, has no outer radius, and the active item is readable without relying on color alone.

- [ ] **Step 7: Commit the task checkpoint**

Record component keys/IDs for Task 6 onward.

---

### Task 4: Build Form, Feedback, And Overlay Components

**Artifacts:**
- Modify: Figma page `02 Components`
- Create component sets: buttons, fields, selects, tags, upload, pagination, drawer, dialog, feedback states

**Interfaces:**
- Consumes: foundation variables and text styles
- Produces: reusable form and overlay components used by every master-data screen

- [ ] **Step 1: Create button components**

Create `Button/Command` with properties:

```text
Variant=Primary|Secondary|Danger|Ghost
Size=Medium|Small
State=Default|Hover|Disabled
Icon=Shown|Hidden
Label=Text property
```

Use heights `40px` and `32px`, radius `8px`, and Lucide-compatible SVG icon slots.

- [ ] **Step 2: Create field components**

Create `Field/Text`, `Field/Number`, `Field/Textarea`, and `Field/Search` with `Default`, `Focus`, `Error`, `Disabled`, and `Filled` states. Include label, required indicator, optional helper/error line, and consistent `40px` or `44px` control height.

- [ ] **Step 3: Create select components**

Create `Field/Select` and `Overlay/Select Menu`. The select menu includes selected, hover, default, and disabled option rows and uses a subtle shadow only on the floating menu.

- [ ] **Step 4: Create semantic status tags**

Create `Tag/Status` variants:

```text
Status=Enabled|Disabled|Pending|Warning|Error|Info
```

Use light-tint backgrounds and high-contrast text bound to semantic color variables.

- [ ] **Step 5: Create the upload component set**

Create `Upload/Image` states:

```text
State=Empty|Uploading|Success|Error
```

Include a 1:1 preview slot, progress treatment, retry action, replace action, and delete icon control.

- [ ] **Step 6: Create pagination**

Create total-count label, previous/next icon buttons, current/default page buttons, ellipsis, and page-size select in one horizontal component.

- [ ] **Step 7: Create drawer and dialog components**

Create `Overlay/Drawer` at `720px` width for a 1440px screen, with title, description, body slot, and pinned action footer. Create `Overlay/Confirm Dialog` with normal and dangerous variants.

- [ ] **Step 8: Create state components**

Create `State/Loading`, `State/Empty`, `State/Error`, and `State/No Results` with concise Chinese copy and appropriate retry/reset/create action slots.

- [ ] **Step 9: Validate form and overlay components**

Screenshot each component set at a readable scale. Verify labels are not clipped, control heights are stable across variants, upload previews do not resize the layout, and the drawer is exactly half the 1440px viewport.

- [ ] **Step 10: Commit the task checkpoint**

Record component IDs and property names used by later screen composition tasks.

---

### Task 5: Build Data Table And List-Page Components

**Artifacts:**
- Modify: Figma page `02 Components`
- Create components: `Table/Header Cell`, `Table/Data Cell`, `Table/Row`, `Table/Container`, `Filter/Bar`, `Action/Bulk Bar`

**Interfaces:**
- Consumes: form components, status tags, buttons, pagination, text styles
- Produces: reusable table and list-page sections for all five modules

- [ ] **Step 1: Create table cells and rows**

Create header and data cells with fixed column-width options, alignment properties, and text-overflow-safe layout. Create row variants for default, hover, selected, and disabled states at a stable `52px` height.

- [ ] **Step 2: Create the table container**

Assemble header, repeated row instances, bottom border treatment, and pagination. Do not wrap the table in a decorative nested card.

- [ ] **Step 3: Create the filter bar**

Compose search, selects, search button, reset button, and optional advanced-filter action. Use `24px` section padding and a consistent `12px` control gap.

- [ ] **Step 4: Create the bulk-action bar**

Include selected-count text and enabled/disabled bulk actions. The bar must occupy stable height so selecting rows does not shift the table.

- [ ] **Step 5: Create a generic list-page section**

Compose page header, filter bar, bulk-action slot, table container, and four state variants. The section fills its parent width and does not impose a maximum width.

- [ ] **Step 6: Validate the list-page component**

Screenshot the table header, three representative rows, filter bar, and pagination separately. Verify long Chinese names truncate cleanly, numeric columns align consistently, and action labels do not wrap.

- [ ] **Step 7: Commit the task checkpoint**

Record the generic list-page component ID and exposed properties.

---

### Task 6: Compose Product Screens

**Artifacts:**
- Modify: Figma page `03 Master Data`
- Create frames: `Products / List`, `Products / Detail Drawer`, `Products / Create`, `Products / Edit`

**Interfaces:**
- Consumes: shell, list-page, form, upload, select, status, drawer, and pagination component IDs
- Produces: four validated product frames and prototype node IDs

- [ ] **Step 1: Create the product-list wrapper first**

Create a `1440x1024` top-level frame with shell instances. Position the sidebar at `x=0`, top toolbar at `x=244`, and content at `x=244` with `32px` internal padding.

- [ ] **Step 2: Build the product-list content**

Use the approved filters: 货号、品牌、供应商、分类、状态. Include columns for 商品信息、分类/品牌、供应商、库存、价格、资料状态、更新时间、操作. Use realistic sample values from the current prototype.

- [ ] **Step 3: Add product-list states**

Add nearby named frames for loading, no results, and error states using component instances rather than rebuilding the page.

- [ ] **Step 4: Create the product-detail drawer frame**

Duplicate the base list frame, add a dimmed overlay, and place a `720px` drawer flush right. Include product images, 货号, 规格, 外箱尺寸, 单价, 内盒包装, 单杯条码, 渠道商, 内盒尺寸, 内盒重量, 外箱图片, 内盒包装图, 克重, 净重, 毛重.

- [ ] **Step 5: Create the product create/edit form shell**

Create one reusable form section structure with tabs or anchored sections for 基础资料、规格包装、价格库存、渠道信息、图片素材. Use upload instances for image fields and select instances for categorical fields.

- [ ] **Step 6: Create create and edit frames**

Use the form shell in `Products / Create` with empty/default values and `Products / Edit` with filled values, preview images, helper copy, and one field error example.

- [ ] **Step 7: Validate product screens**

Screenshot each frame plus separate crops for the filter bar, product table, detail drawer, and image-upload section. Assert no outer body gap, a `244px` sidebar, a `720px` drawer, and no text or image overlap.

- [ ] **Step 8: Commit the task checkpoint**

Record the four frame IDs for prototype wiring.

---

### Task 7: Compose Category Screens

**Artifacts:**
- Modify: Figma page `03 Master Data`
- Create frames: `Categories / List`, `Categories / Drawer`

**Interfaces:**
- Consumes: shell, generic list page, table, pagination, and drawer components
- Produces: two validated category frames

- [ ] **Step 1: Create the category-list frame**

Use columns 分类名称、排序号、状态、备注、操作 and filters 关键词、状态. Fill the full content width and use compact table rows.

- [ ] **Step 2: Create the category-drawer frame**

Duplicate the list frame and add the `720px` drawer with 分类名称、排序号、备注 plus cancel/save actions. Use the same drawer component as the product detail treatment.

- [ ] **Step 3: Validate category screens**

Screenshot list and drawer frames. Verify the list no longer sits inside a centered max-width container and the drawer footer stays pinned.

- [ ] **Step 4: Commit the task checkpoint**

Record both frame IDs.

---

### Task 8: Compose Customer Screens

**Artifacts:**
- Modify: Figma page `03 Master Data`
- Create frames: `Customers / List`, `Customers / Form`

**Interfaces:**
- Consumes: shell, generic list page, field, select, and status components
- Produces: two validated customer frames

- [ ] **Step 1: Create the customer-list frame**

Include filters for 客户名称/手机号、渠道、状态 and columns 客户信息、联系人、电话、渠道、结算方式、欠款、状态、更新时间、操作.

- [ ] **Step 2: Create the customer-form frame**

Use sections 基础信息、联系方式、渠道与结算、地址与备注. Maintain a stable two-column field grid with full-width address and remarks rows.

- [ ] **Step 3: Validate customer screens**

Screenshot list and form plus a crop of the form actions. Verify labels align, field widths do not shift, and long addresses wrap without overlapping following content.

- [ ] **Step 4: Commit the task checkpoint**

Record both frame IDs.

---

### Task 9: Compose Supplier And Warehouse Screens

**Artifacts:**
- Modify: Figma page `03 Master Data`
- Create frames: `Suppliers / List`, `Suppliers / Drawer`, `Warehouses / List`, `Warehouses / Drawer`

**Interfaces:**
- Consumes: shell, generic list page, drawer, field, select, status, and confirm-dialog components
- Produces: four validated frames

- [ ] **Step 1: Create supplier list and drawer**

Supplier list columns: 供应商信息、联系人、电话、主营分类、结算方式、状态、更新时间、操作. Drawer fields: 名称、联系人、电话、主营分类、结算方式、地址、备注.

- [ ] **Step 2: Create warehouse list and drawer**

Warehouse list columns: 仓库信息、类型、负责人、地址、默认仓、状态、更新时间、操作. Drawer fields: 名称、编码、类型、负责人、联系电话、地址、默认仓设置、备注.

- [ ] **Step 3: Add the default-warehouse confirmation state**

Create one nearby frame using `Overlay/Confirm Dialog` that explains the current default warehouse will be replaced.

- [ ] **Step 4: Validate supplier and warehouse screens**

Screenshot all four frames plus the confirmation state. Verify drawer widths match, select controls use the same component variants, and status tags remain semantically consistent.

- [ ] **Step 5: Commit the task checkpoint**

Record frame IDs for prototype wiring.

---

### Task 10: Build The Public State Board And Wire Prototype Flows

**Artifacts:**
- Modify: Figma page `03 Master Data`
- Create frame: `Shared / States`
- Add prototype interactions among the approved business frames

**Interfaces:**
- Consumes: all component and business-frame IDs
- Produces: shared state board and clickable prototype flows

- [ ] **Step 1: Create the shared-state board**

Place loading, empty, no-results, error, disabled-control, upload-progress, upload-error, and form-error examples in a labeled grid. Every example must be an instance of its source component.

- [ ] **Step 2: Connect product flows**

Wire `Products / List` row click to `Products / Detail Drawer`, detail edit to `Products / Edit`, add button to `Products / Create`, and save/cancel actions back to `Products / List`.

- [ ] **Step 3: Connect master-data flows**

Wire list add/edit actions to the corresponding category, customer, supplier, and warehouse form/drawer frames. Wire save/cancel back to each module list.

- [ ] **Step 4: Set the prototype starting point**

Set `Products / List` as the starting frame and use instant or short smart-animate transitions only where they do not distort the full-height shell.

- [ ] **Step 5: Validate the state board and links**

Screenshot the full state board and inspect prototype reactions. Verify every major list has at least one view/edit path and one create path.

- [ ] **Step 6: Commit the task checkpoint**

Record the prototype start-frame ID and interaction count.

---

### Task 11: Final Structural And Visual Verification

**Artifacts:**
- Validate: all three Figma pages, local variables, components, screen frames, and prototype links
- Update: execution notes in this plan

**Interfaces:**
- Consumes: all node IDs and screenshots from Tasks 1–10
- Produces: final Figma URL and evidence-backed completion report

- [ ] **Step 1: Run metadata checks**

Verify exactly three named pages, all required variable collections, all required component sets, all 12 core business frames, and the shared-state board. Confirm business frames use instances and bound variables rather than duplicated raw nodes.

- [ ] **Step 2: Assert frame geometry**

For every business frame, verify width `1440`, sidebar width `244`, sidebar `x=0`, toolbar `x=244`, no outer left/right body margin, and content fills the remaining width.

- [ ] **Step 3: Assert fonts**

Read every text node's font family. Confirm Chinese content uses the approved verified Chinese font and English/numeric styles use Inter where specified. Treat fallback boxes or unapproved font families as failures.

- [ ] **Step 4: Review screenshots section by section**

Capture and inspect:

```text
Foundations color specimen
Foundations typography specimen
Sidebar and top toolbar
Buttons, fields, selects, tags, upload, pagination
Table header and rows
Each of the 12 core business frames
Product detail drawer
Product image-upload section
Shared state board
```

Expected: no clipped text, overlaps, placeholder labels, blank image placeholders, incorrect variants, or excessive empty space.

- [ ] **Step 5: Fix only targeted defects**

For each failed assertion, modify the smallest affected component or section, then re-screenshot that section and every screen consuming the changed component.

- [ ] **Step 6: Verify the prototype flows**

Test all links from the product-list start frame and one create/edit path for each other module. Confirm drawers open from the right and return actions land on the correct module list.

- [ ] **Step 7: Verify the repository state**

Run:

```bash
git status --short --branch
```

Expected: only the intentionally updated plan checkbox file is modified during execution; no frontend or backend source changes exist.

- [ ] **Step 8: Commit execution tracking**

```bash
git add docs/superpowers/plans/2026-08-28-masterdata-figma-ui.md
git commit -m "docs: track masterdata Figma UI execution"
```

- [ ] **Step 9: Deliver the Figma file**

Provide the Figma design URL, summarize the three pages and 12 core frames, report validation results, and explicitly list any residual limitation. Do not claim completion unless every screenshot and metadata assertion above passes.
