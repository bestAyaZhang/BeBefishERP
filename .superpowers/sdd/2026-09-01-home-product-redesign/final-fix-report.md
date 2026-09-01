# Home/Product Redesign Final Fix Report

## Status

**PASS / COMPLETE.** All eight Important findings and all listed Minor follow-ups are implemented. Focused and full backend/frontend tests pass, the frontend production build passes, `git diff --check` passes, and the dedicated test schema was left at Flyway V8.

## Commits

- `48c9d9fc2d8747f4256e1a6dfd4ec97f769e1cb6` - `fix: resolve final home product review findings`
- `docs: record final fix verification` - report-only follow-up commit containing this file; its hash is reported by the final implementer response.

Fix base: `e0e69f480c7c92d897b66bc599dc16c4a867525c`.

## Files Changed

Implementation commit: 37 files, 1,642 insertions, 180 deletions.

- `README.md`
- `backend/src/main/java/com/bebefish/erp/common/api/GlobalExceptionHandler.java`
- `backend/src/main/java/com/bebefish/erp/dashboard/api/DashboardController.java`
- `backend/src/main/java/com/bebefish/erp/product/api/SaveProductRequest.java`
- `backend/src/main/java/com/bebefish/erp/product/api/SaveSupplierQuoteRequest.java`
- `backend/src/main/java/com/bebefish/erp/product/application/DecimalConstraints.java`
- `backend/src/main/java/com/bebefish/erp/product/application/ProductService.java`
- `backend/src/main/java/com/bebefish/erp/product/application/ProductSupplierQuoteSynchronizer.java`
- `backend/src/main/java/com/bebefish/erp/product/application/SaveProductCommand.java`
- `backend/src/main/java/com/bebefish/erp/product/application/SaveSkuCommand.java`
- `backend/src/main/java/com/bebefish/erp/product/application/SupplierQuoteService.java`
- `backend/src/main/java/com/bebefish/erp/product/domain/Sku.java`
- `backend/src/test/java/com/bebefish/erp/common/persistence/FlywayMigrationTest.java`
- `backend/src/test/java/com/bebefish/erp/dashboard/api/DashboardControllerTest.java`
- `backend/src/test/java/com/bebefish/erp/product/api/ProductControllerTest.java`
- `backend/src/test/java/com/bebefish/erp/product/api/SupplierQuoteControllerTest.java`
- `backend/src/test/java/com/bebefish/erp/product/application/ProductServiceTest.java`
- `backend/src/test/java/com/bebefish/erp/product/application/SupplierQuoteServiceTest.java`
- `frontend/src/components/AccessibleDialog.vue`
- `frontend/src/features/product/ProductFeature.test.ts`
- `frontend/src/features/product/components/ProductTable.vue`
- `frontend/src/features/product/editor/PackagingEditorDialog.vue`
- `frontend/src/features/product/editor/ProductEditorView.test.ts`
- `frontend/src/features/product/editor/ProductEditorView.vue`
- `frontend/src/features/product/editor/SkuEditorDialog.vue`
- `frontend/src/features/product/editor/productEditorState.test.ts`
- `frontend/src/features/product/editor/productEditorState.ts`
- `frontend/src/features/product/editor/steps/ProductBasicStep.vue`
- `frontend/src/features/product/editor/steps/ProductConfirmStep.vue`
- `frontend/src/features/product/editor/steps/ProductPackagingStep.vue`
- `frontend/src/features/product/editor/steps/ProductProcurementStep.vue`
- `frontend/src/features/product/editor/steps/ProductSkuStep.vue`
- `frontend/src/features/product/mockProductService.test.ts`
- `frontend/src/features/product/mockProductService.ts`
- `frontend/src/features/product/productFormMapper.test.ts`
- `frontend/src/features/product/productFormMapper.ts`
- `frontend/src/features/product/types.ts`

This report adds `.superpowers/sdd/2026-09-01-home-product-redesign/final-fix-report.md`. The SDD ledger was not edited.

## Important 1: Dashboard Authorization

### Implementation

`DashboardController.overview` now requires `hasAuthority('dashboard:view')`. Existing authenticated/authorized behavior remains unchanged.

### RED / GREEN

- RED: the focused 54-test dashboard/product run had 9 intended failures; the no-permission dashboard case returned 200 instead of 403.
- GREEN: `DashboardControllerTest` passes 7/7, including authenticated-without-permission 403 and allowed-user data access.

## Important 2: Exactly One Default SKU and SKU Status Round-trip

### Implementation

`defaultSku` and SKU `status` now flow through TypeScript form state/payload, Java request/command/service/domain, JDBC persistence, and response hydration. Explicit payloads must declare both fields on every SKU, contain exactly one default, and keep the default enabled. Generated variants choose the first SKU deterministically. Editor insert/replace/remove operations promote an enabled fallback without re-enabling disabled SKUs; rows render actual state rather than assuming index zero.

### RED / GREEN

- RED: focused backend tests observed ignored explicit status/default declarations and generated variants with no default; frontend state tests observed two enabled SKUs with no default and dropped hydration fields.
- GREEN: controller tests cover explicit create/update and subsequent legacy omitted-field update; service tests cover generated variants and invalid declarations; editor tests cover default selection, disable, current-default deletion, and disabled-state preservation.

## Important 3: Product Type and Product Status Controls

### Implementation

The Basic step exposes product type and status selects. State and payload carry both values explicitly. Backend status is optional for compatibility but normalized atomically when present. Simple products reject SKU variant dimensions/values while retaining the existing product-dimension metadata names (`口径`, `高度`, `容量`, `重量`). Confirmation displays type and status.

### RED / GREEN

- RED: backend focused tests showed product status remaining enabled and accepted a simple/variant structure conflict; editor tests could not find type/status controls.
- GREEN: API/service tests cover explicit status transitions, omitted update preservation, and simple structure rejection; editor tests cover create type/status selection and edit status transition.

## Important 4: SKU Item Number Versus Barcode

### Implementation

The SKU dialog now has an editable `skuCode` field labelled “SKU 货号” and a separate `barcode` field labelled “单杯条码”. Editor rows and confirmation keep both visible. Existing backend uniqueness/persistence/search mappings remain distinct; mock search now includes both fields.

### RED / GREEN

- RED: the editor identity test could not find an editable SKU-code control because the old “SKU 货号” input wrote to barcode.
- GREEN: editor create/edit prefill/update tests assert distinct values; API tests assert response persistence and list search by either SKU code or barcode; detail rendering already keeps separate columns.

## Important 5: Destructive Packaging Mode Confirmation

### Implementation

Switching from per-SKU to unified mode checks packaging values, image IDs, and previews. Differences open an accessible warning; cancel performs no mutation, while confirm copies the first SKU source through independently cloned state and synchronized previews.

### RED / GREEN

- RED: the editor test found no confirmation and the mode changed immediately.
- GREEN: focused tests cover cancel preserving 42/44 differences and confirm producing deterministic 42/42 values; state tests cover independent copies and preview/file-ID synchronization.

## Important 6: Schema-aligned Numeric Precision and Range

### Implementation

Bean validation and application services enforce exact schema precision/scale for prices, costs, purchase price, MOQ, safety stock, dimensions/weights, and volume. `DecimalConstraints` rejects overflow or non-zero excess scale before persistence and returns field-specific 400/business errors. Carton quantity remains Java Integer `1..2147483647`. Frontend validation mirrors these limits and rejects values whose scaled integer exceeds `Number.MAX_SAFE_INTEGER`; inputs use matching step/max attributes.

### RED / GREEN

- RED: the 54-test backend run included numeric service failures and an API 500 on DB overflow; the standalone 9-test quote run had 2 intended failures. Frontend precision runs had 2 intended failures for missing MOQ validation and stale input attributes.
- GREEN: API/service tests cover schema maximum, overflow, and excess scale for embedded and standalone quote paths; frontend state/editor tests cover safe maxima, overflow, scale, submit prevention, and input attributes.

## Important 7: Save/Upload Navigation Races

### Implementation

Any active upload or save blocks step changes, route leave/update, and browser unload with operation-specific feedback. Saves carry a monotonically increasing request generation and source full-path. Unmount or source-route changes invalidate late callbacks, preventing stale success/error messages and redirects. Successful current saves temporarily authorize only their own detail navigation.

### RED / GREEN

- RED: editor tests showed upload step exits succeeding, save route exits reaching the list, unload not prevented during save, and late unmounted success redirecting.
- GREEN: deferred-promise tests cover leave during upload/save, own successful navigation, unmount late success/failure, and forced route-identity late success/failure.

## Important 8: Accessible Modal Behavior

### Implementation

`AccessibleDialog.vue` is shared by SKU editing, packaging editing, and destructive confirmation. It supplies modal role/labeling, initial focus, Tab/Shift+Tab trapping, Escape cancellation, document-level focus containment, opener restoration, backdrop interaction blocking, and the existing wide scroll-body/sticky-footer geometry.

### RED / GREEN

- RED: editor tests found no focusable close control, no keyboard trap/Escape handling, and no opener restoration.
- GREEN: focused editor keyboard/focus tests pass; existing SKU and packaging geometry tests continue to pass.

## Minor Follow-ups

1. `FlywayMigrationTest` now creates a genuine V7 schema, inserts representative product/SKU rows, migrates only `bebefish_erp_test` to V8, verifies preservation/defaults/nulls/check constraints, and migrates the dedicated schema to latest in `@AfterEach`. Initial RED was 1/8 due MySQL translating a check violation through the general Spring `DataAccessException` boundary; GREEN is 8/8 with the portable assertion.
2. Mock keyword search includes SKU code/barcode and category counts walk the supplied parent hierarchy with cycle protection. Parity tests pass.
3. `ProductTable` prefers `product.categoryName` and falls back to the local category map. Stale-map test passes.
4. README primary route changed from `/dashboard` to `/workbench`.

## Backward Compatibility Rules

- Product `status` omitted or blank: create defaults to `enabled`; update preserves persisted status.
- All SKU `defaultSku`/`status` fields omitted: legacy mode applies. Create chooses the deterministic first enabled default; update preserves matching persisted SKU state by ID/code where possible, then promotes the first already-enabled SKU only if the old default disappeared or became invalid.
- Any SKU lifecycle declaration present: every SKU must explicitly provide both fields. Mixed declarations are rejected.
- Explicit SKU declarations require exactly one enabled default. Disabled SKUs are never silently re-enabled.
- Existing Java command constructors remain available and delegate with omitted lifecycle controls.
- Omitted `supplierQuotes` continues to preserve existing quotes; an explicit empty list clears them.

## Verification

All backend commands used only:

```text
ERP_TEST_DB_URL=jdbc:mysql://localhost:3306/bebefish_erp_test?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true&useSSL=false
ERP_TEST_DB_USERNAME=bebefish_test
ERP_TEST_DB_PASSWORD=<provided dedicated test credential>
```

- Focused backend: 71/71 (`DashboardControllerTest` 7, `ProductControllerTest` 21, `ProductServiceTest` 26, `SupplierQuoteControllerTest` 3, `SupplierQuoteServiceTest` 6, `FlywayMigrationTest` 8).
- Focused frontend: 94/94 across editor state/view, form mapper, mock service, and product feature/table tests.
- Full backend Maven suite: 189/189, 0 failures, 0 errors, 0 skipped; `BUILD SUCCESS`.
- Full frontend Vitest suite: 242/242 across 31 files.
- Production frontend build: `vue-tsc --noEmit && vite build` passed; 1,717 modules transformed.
- `git diff --check`: passed. CRLF conversion notices were informational and produced no whitespace errors.

## Unresolved Concerns

None.
