# Product Design QA

final result: passed

Prototype URL: http://localhost:5173/
Viewport used: 1280x720, in-app browser default
Reference image: C:\Users\张振亚\AppData\Local\Temp\codex-clipboard-dd728333-4b96-47db-8f9d-407bc839c5de.png

Checked states:
- Dashboard: `frontend/prototype-screenshots/dashboard/workbench-overview.png`
- Task list with context menu: `frontend/prototype-screenshots/tasks/task-list-context-menu.png`
- Task list with calendar popup: `frontend/prototype-screenshots/tasks/task-list-calendar-popup.png`
- Combined visual comparison: `frontend/prototype-screenshots/global/tasklab-reference-comparison.png`

Findings:
- Layout now holds at the default desktop viewport without the right project panel squeezing the main dashboard.
- Sidebar, top search/action bar, cards, charts, task table, menu, and date picker follow the light Tasklab-style visual direction.
- Interactions verified: sidebar navigation, task context menu, and due-date calendar popup.
- Production build passes with `npm run build`.

Intentional adaptation:
- Copy and mock data are localized for BeBefish ERP instead of duplicating the Tasklab text.
- Some dashboard panels stack at 1280px to keep spacing readable; wider screens use the closer two-column reference composition.

## SKU Physical Attributes And Catalog Columns (2026-09-02)

Result: passed desktop visual QA

Prototype URL: http://localhost:5173/
Verification viewport: 1440x1024
Data-backed visual fixture: http://localhost:5174/ with `VITE_DATA_SOURCE=mock`

Evidence:
- Product editor physical fields: `frontend/prototype-screenshots/products/product-edit-physical-attributes.png`
- Product detail physical fields: `frontend/prototype-screenshots/products/product-detail-physical-attributes.png`
- Product catalog column selector: `frontend/prototype-screenshots/products/product-catalog-columns-expanded.png`

Checked states:
- Unified packaging shows outer-box, inner-box, and product dimensions as labeled `length × width × height` inputs with `cm` suffixes; capacity uses `ml`.
- Per-SKU packaging dialog keeps all inputs visible through internal scrolling and preserves the fixed action footer.
- Product detail keeps the approved three-column packaging, weight, and warehouse layout while adding product dimensions and capacity; both unified and two-SKU independent packaging states were verified.
- The field selector stays inside the 1440x1024 viewport, reports the selected count, enforces six configurable fields, and closes with Escape or an outside click.
- Selecting all eight physical fields produces canonical headers and horizontal scrolling without overlapping table cells.
- A populated default SKU rendered `18.5 × 12 × 9 cm`, `500 ml`, `12 只/箱`, `纸盒彩盒`, `3.1 kg`, `2.4 kg`, and `220 g` in the catalog.
- No browser console errors or warnings were recorded in the checked editor, detail, and catalog states.

Reference comparison:
- The packaging editor was compared with `frontend/prototype-screenshots/products/product-editor-figma/04-packaging.png` at the same 1440x1024 viewport. Existing shell, spacing, typography, segmented control, panels, and footer remain aligned; the approved product-dimension and capacity group adds one compact row.
- The detail packaging band was compared with `frontend/prototype-screenshots/products/product-detail-figma-reference.png`. The existing hierarchy and three-column proportions remain intact; new values use the established definition-list typography.

Automated verification:
- `npm run test:run`: 32 files, 304 tests passed.
- `npm run build`: typecheck and production build passed.
- `mvn test`: 118 tests completed before the database-backed contexts failed to start; 80 errors were caused by missing `ERP_TEST_DB_URL`, `ERP_TEST_DB_USERNAME`, and `ERP_TEST_DB_PASSWORD` (`URL must start with 'jdbc'`), with no assertion failures.
