# Product Catalog Design QA

Source visual truth path: `frontend/prototype-screenshots/products/product-catalog-figma-reference.png`

Figma source: `https://www.figma.com/design/jIz9HNkSoXH63gvTc3yOtj?node-id=138-2`

Implementation screenshot path: `frontend/prototype-screenshots/products/product-catalog-list.png`

Full-view comparison evidence: `frontend/prototype-screenshots/products/product-catalog-figma-comparison.png`

Focused comparison evidence: `frontend/prototype-screenshots/products/product-catalog-figma-main-comparison.png`

## Capture

- Viewport: 1440 x 1024 CSS px, device scale factor 1.
- Source pixels: 1440 x 1024. Implementation pixels: 1440 x 1024.
- Density normalization: none required; source and implementation are equal-size 1x captures.
- State: authenticated product list, all categories, page 1, 20 items per page, live API data.
- Overflow check: document size remained 1440 x 1024; no global horizontal or vertical overflow.
- Main geometry: category panel `x=276, y=208, w=260, h=804`; list panel `x=552, y=208, w=856, h=804`.

## Findings

- No actionable P0, P1, or P2 findings remain.
- Fonts and typography: locally bundled `Noto Sans SC Variable` is used for Chinese UI text and `Inter Variable` for table numbers. Browser-computed values match the Figma tokens: page title `24/32/700`, card title `15/22/500`, body `14/22/400`, and table number `14/20/600`.
- Spacing and layout rhythm: 244 px sidebar, 64 px top bar, 32 px content gutter, 16 px split-panel gap, 40 px table header, 72 px rows, and bottom pagination align with the source.
- Colors and visual tokens: `#536DFF` primary, `#F6F7FB` page background, `#25314D` primary text, slate borders, and semantic status colors align with the source.
- Image quality and asset fidelity: real product images render when present; absent or failed images use stable colored Lucide package placeholders instead of a broken-image state.
- Copy and content: product breadcrumbs, search copy, list labels, actions, and status text are localized and consistent.
- Accepted product constraints: category mutation controls and bulk-selection controls shown in the Figma source are intentionally omitted per approved requirements. Counts, category depth, and product rows use current API data instead of design fixtures.

## Comparison History

1. Initial pass found P2 drift in the English product breadcrumb, narrow global search, circular top-bar actions, narrow sidebar navigation rows, and error-like missing-image placeholders.
2. Fixed the product breadcrumb and search copy, resized top-bar actions, aligned sidebar spacing and branding, renamed the module to 商品管理, and added colored product-image fallbacks.
3. Post-fix full and focused comparisons confirm the source panel coordinates and dimensions, table density, pagination placement, typography, colors, and controls. No P0/P1/P2 visual issue remains.
4. Replaced the system-font fallback with locally hosted Figma font families and re-captured both comparisons. The final page remains 1440 x 1024 with no overflow and no console warnings or errors.

## Interaction Evidence

- Category selection: selecting category 38 changed the URL to `categoryId=38` and returned one row.
- SKU search: searching `TYG1204` changed the URL query and returned one row.
- Reset: restored the unfiltered five-row list and canonical URL.
- Add product: navigated to `/products/new` and rendered the product editor.
- Browser console: no warnings or errors in the final capture.

final result: passed

---

# Product Editor Design QA

Figma source file: `https://www.figma.com/design/jIz9HNkSoXH63gvTc3yOtj`

Source frames: `138:4` (basic), `440:8592` (SKU), `440:8654` (procurement), `440:8716` (unified packaging), `445:9109` (per-SKU packaging), `440:8778` (images), `440:8840` (confirmation), `453:9268` (add SKU), `454:9427` (edit SKU), and `459:9586` (edit packaging).

Reference, implementation, and side-by-side comparison evidence is stored in `frontend/prototype-screenshots/products/product-editor-figma/`.

## Capture

- Viewport: 1440 x 1024 CSS px, device scale factor 1. Mobile behavior is outside the approved scope.
- State: authenticated edit route `/products/28/edit` using live product, category, supplier, SKU, packaging, and image data.
- Desktop geometry: 244 px sidebar, 64 px top bar, 32 px content gutter, and 1132 px editor width.
- Wizard geometry: 64 px page header, 92 px step navigation, 620 px active stage, 80 px footer, and 16 px vertical gaps.
- Dialog geometry: add/edit SKU `760 x 604` at `y=174`; edit packaging `1000 x 748` at `y=128`.

## Findings

- No actionable P0, P1, or P2 findings remain after the final side-by-side pass.
- The edit route now uses the same six-step information architecture as the approved create-wizard frames: basic information, SKU information, procurement and channels, packaging and weight, image materials, and confirmation.
- Typography follows the project Figma standard: locally bundled `Noto Sans SC Variable` for Chinese UI and `Inter Variable` for numeric content. Page titles, stage headings, body copy, labels, and table values retain the approved size and weight hierarchy.
- SKU and packaging tables preserve the Figma density, horizontal separators, row heights, text actions, semantic status tags, and empty-space rhythm without vertical table rules.
- Add/edit SKU dialogs expose real SKU image upload. Per-SKU packaging maintenance exposes real outer-box and inner-package image upload, preserves previews, and keeps validation and focus containment intact.
- Uploaded images are unobscured in their resting state; replacement controls appear on hover or keyboard focus. Active controls use the approved blue focus treatment instead of browser-default black outlines.
- Accepted data constraints: live API values replace Figma fixtures. The Figma channel-pricing concept is mapped to persisted supplier quotes because channel selling prices were explicitly excluded from persistence.

## Interaction Evidence

- Navigated all six steps on the existing edit route and verified active/completed/pending states.
- Opened and closed add SKU, edit SKU, per-SKU packaging, and packaging image upload states without layout clipping.
- Verified unified and per-SKU packaging mode transitions and the per-SKU maintenance entry point.
- Final browser reload produced only Vite connection debug messages; no current Vue warnings or runtime errors were emitted.
- Verification: `npm run test:run` passed 256 tests across 31 files; `npm run build` and `git diff --check` completed successfully.

final result: passed

---

# Product Detail Design QA

Source visual truth path: `frontend/prototype-screenshots/products/product-detail-figma-reference.png`

Figma source: `https://www.figma.com/design/jIz9HNkSoXH63gvTc3yOtj?node-id=138-3`

Implementation screenshot path: `frontend/prototype-screenshots/products/product-detail-figma-implementation.png`

Mobile implementation screenshot path: `frontend/prototype-screenshots/products/product-detail-mobile.png`

## Capture

- Desktop viewport: 1440 x 1024 CSS px, device scale factor 1; full-page capture: 1440 x 1760 px.
- Mobile viewport: 390 x 844 CSS px, device scale factor 1.
- State: authenticated product detail with live API data and one SKU.
- Desktop geometry: 244 px sidebar, 64 px top bar, 32 px content gutter, 16 px section gap, and 1132 px content width.
- Section geometry: header `y=88, h=80`; overview `y=184, h=280`; SKU `y=480, h=318`; procurement `y=814, h=232`; packaging `y=1062, h=246`; images `y=1324, h=220`; audit `y=1560, h=162`.
- Inner geometry: overview media `x=300, y=208, w=232, h=232`; overview facts `x=556, y=208, w=828`; SKU title `x=300, y=504, w=1084, h=26`; SKU table `x=300, y=542, w=1084`.

## Findings

- No actionable P0, P1, or P2 findings remain.
- Typography uses the project-standard local `Noto Sans SC Variable`; the browser-computed page title is `24/32/700` and section titles are `18/26/500`, matching the Figma source.
- The overview, SKU, procurement, packaging, image, and audit sections follow the source order, dimensions, padding, borders, and spacing.
- SKU rows expose the approved fields and real SKU images without vertical table rules; the table scrolls internally on narrow viewports and does not obscure adjacent columns.
- Uniform and per-SKU packaging modes both retain the approved information architecture. Product images, outer-box images, and inner-package images use real API assets with stable fallbacks.
- Mobile layout stacks the dense desktop sections without global horizontal overflow or overlapping controls.
- Accepted product constraints: live product/SKU values replace Figma fixtures. The Figma channel-pricing block is intentionally mapped to persisted supplier quotes because channel selling prices were explicitly excluded from persistence.

## Interaction Evidence

- Returning to the product list and editing the product remain available from the detail header.
- SKU links and image previews preserve their existing interactions.
- Browser console: no warnings or errors in the final desktop capture.
- Verification: `npm run test:run` passed 255 tests across 31 files; `npm run build` completed successfully.

final result: passed

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


final result: passed

---

# Warehouse Planner Ruler Detail QA

Source visual truth path: `C:/Users/张振亚/AppData/Local/Temp/codex-clipboard-1c340ee8-a9a7-48fd-8e45-80e65637b888.png`

Implementation screenshot path: `frontend/prototype-screenshots/warehouse-planner/warehouse-planner-ui.png`

Full-view comparison evidence: `frontend/prototype-screenshots/warehouse-planner/reference-and-implementation.png`

Focused ruler comparison evidence: `frontend/prototype-screenshots/warehouse-planner/ruler-reference-and-implementation.png`

## Capture

- Browser: Codex in-app browser at `/inventory/warehouse-canvas`.
- Source pixels: 1487 × 1058. Implementation pixels and CSS viewport: 1286 × 1147 at device scale factor 1.
- Density normalization: the focused comparison rescales both top regions to the same 1200 px content width before cropping. The full-view comparison fits each complete image into an equal-width panel and is used only for composition, not pixel-distance claims.
- State: ground pile C-018 selected, grid snapping on, measurement off. Measurement-on was also exercised and restored before the final capture.

## Findings

- No actionable P0, P1, or P2 finding remains in the ruler refinement.
- Fonts and typography: 5 m labels use compact 10 px tabular numerals, retain the 0/60 and 0/40 endpoints, and remain readable without crowding the warehouse drawing.
- Spacing and layout rhythm: horizontal and vertical baselines meet the drawing-board edges. Half-metre ticks are 4 px, metre ticks are 8 px, and five-metre ticks are 14 px, creating the same engineering hierarchy visible in the reference.
- Colors and visual tokens: slate tick hierarchy and a darker hairline baseline match the existing blueprint palette without competing with selected-object teal or warehouse structure lines.
- Image quality and asset fidelity: no raster or decorative asset was introduced for the ruler; tick marks are native UI geometry and render sharply at device scale factor 1. The source and implementation were opened together in both full and focused comparison images.
- Copy and content: units remain `(m)` and the visible ranges remain 0–60 m horizontally and 0–40 m vertically. Accessible labels explicitly announce the range and 0.5 m minimum division.

## Comparison History

1. The pre-implementation regression test confirmed the old ruler exposed no intermediate tick hierarchy.
2. The first rendered comparison after implementation showed the approved 0.5 m / 1 m / 5 m hierarchy aligned to the plan grid. No visual P0/P1/P2 fix was required, so no additional design-QA iteration was necessary.

## Interaction and Console Verification

- Toggling the top measurement control added the selected pile's 4.8 m and 2.4 m measurement labels; toggling again restored the final capture state.
- The selected C-018 pile, property inspector, grid toggle, toolbar, minimap, and coordinate footer remained intact.
- Final in-app browser console log: zero warnings and zero errors.

final result: passed
