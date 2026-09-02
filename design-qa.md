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
