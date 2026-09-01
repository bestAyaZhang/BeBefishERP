# Home And Product Residual Fixes Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Resolve the four Important residual findings from the completed home/product redesign review, remove the accidentally tracked internal report, and repeat end-to-end verification.

**Architecture:** Keep the existing API and editor architecture intact. The backend fix extends legacy omitted-SKU-lifecycle normalization so a retained persisted default wins over a newly inserted fallback; the frontend fix moves simple-product structure validation to the SKU boundary, makes decimal validation total for every JavaScript number, and binds save callbacks to a monotonic route identity plus an exact expected destination.

**Tech Stack:** Java 21, Spring Boot, Maven, JUnit 5, Vue 3, TypeScript, Vue Router, Vitest, Vite.

**Spec:** `docs/superpowers/specs/2026-09-01-home-product-redesign-implementation-design.md`

## Global Constraints

- Work only in the existing isolated worktree on `codex/home-product-redesign`; do not merge or push during implementation.
- Preserve compatibility for legacy product payloads that omit every SKU `defaultSku` and `status` field.
- Explicit SKU lifecycle payloads remain authoritative and must still require exactly one enabled default SKU.
- Product-type cleanup must be possible through the wizard, but an invalid simple-product structure must never reach the save API.
- Numeric validation must return validation errors for unsupported values and must never throw for any JavaScript `number`.
- A save callback may update UI or navigate only while its exact source route identity is still current; only its own saved-product detail destination may bypass the leave guard.
- Use the dedicated `bebefish_erp_test` database for backend integration tests; never inspect or mutate `bebefish_erp`.
- Follow test-driven development, commit each task independently, and do not commit secrets, generated artifacts, or SDD scratch reports.

---

### Task 1: Preserve The Persisted Legacy Default SKU

**Files:**
- Modify: `backend/src/main/java/com/bebefish/erp/product/application/ProductService.java:126-375`
- Test: `backend/src/test/java/com/bebefish/erp/product/application/ProductServiceTest.java`

**Interfaces:**
- Consumes: `Product existing`, `List<Sku> rawSkus`, and `boolean explicitSkuControls` from `ProductService.toProduct(...)`.
- Produces: `normalizeSkuDefaults(List<Sku> skus, boolean explicitSkuControls, Product existing)` that preserves a retained, enabled persisted default in legacy mode and otherwise chooses the first enabled fallback.

- [ ] **Step 1: Write the failing legacy-update regression test**

Create a variant product with two persisted SKUs and a default that is not the first SKU in the update order. Submit a legacy update where all SKU lifecycle fields are omitted and a new SKU is inserted before the retained rows. Assert the retained persisted default SKU id remains the only default:

```java
assertThat(updated.skus()).filteredOn(Sku::isDefault).singleElement()
        .extracting(Sku::id).isEqualTo(persistedDefault.id());
```

- [ ] **Step 2: Run the focused test and verify RED**

Run from `backend` with the dedicated test database environment:

```powershell
mvn -Dtest=ProductServiceTest test
```

Expected: the new regression fails because the newly inserted first SKU becomes default.

- [ ] **Step 3: Implement persisted-default preference**

Pass `existing` into legacy normalization. In omitted-control mode, resolve the enabled retained SKU whose id equals the enabled persisted default id before falling back to the first enabled SKU. Reconcile every row with `withState(sku == selectedDefault, sku.status())`. Keep explicit-control validation unchanged:

```java
var persistedDefaultId = existing == null ? null : existing.skus().stream()
        .filter(Sku::isDefault)
        .filter(sku -> "enabled".equals(sku.status()))
        .map(Sku::id)
        .findFirst()
        .orElse(null);
```

The selected persisted id must also exist and remain enabled in `skus`; otherwise select the first enabled row.

- [ ] **Step 4: Run focused and backend regression tests**

```powershell
mvn -Dtest=ProductServiceTest test
mvn test
```

Expected: both commands pass against `bebefish_erp_test`.

- [ ] **Step 5: Commit Task 1**

```powershell
git add backend/src/main/java/com/bebefish/erp/product/application/ProductService.java backend/src/test/java/com/bebefish/erp/product/application/ProductServiceTest.java
git commit -m "fix: preserve legacy default sku"
```

---

### Task 2: Harden Product Editor Validation And Route Identity

**Files:**
- Modify: `frontend/src/features/product/editor/productEditorState.ts:411-464`
- Modify: `frontend/src/features/product/editor/ProductEditorView.vue:37-301`
- Test: `frontend/src/features/product/editor/productEditorState.test.ts`
- Test: `frontend/src/features/product/editor/ProductEditorView.test.ts`

**Interfaces:**
- Consumes: `validateStep(state, step, context)`, `isValidDecimal(value, integerDigits, fractionDigits)`, Vue Router route updates, and `productService.createProduct/updateProduct` promises.
- Produces: total decimal validation, navigable variant-to-simple cleanup, and route-generation-bound save completion with exact expected detail navigation authorization.

- [ ] **Step 1: Write failing state-validation tests**

Add tests proving:

```ts
expect(() => isValidDecimal(1e21, 15, 4)).not.toThrow();
expect(isValidDecimal(1e21, 15, 4)).toBe(false);
expect(isValidDecimal(Number.MAX_VALUE, 15, 4)).toBe(false);
```

Hydrate a variant product, switch `productType` to `simple`, and assert `validateStep(state, 'basic')` permits navigation while `validateStep(state, 'sku')` reports `productType` until extra SKU/specification structure is removed. After cleanup, assert SKU validation passes.

- [ ] **Step 2: Write failing view workflow and route-race tests**

Add component tests that prove:

```ts
// Loaded variant -> simple can leave Basic, lands on SKU cleanup, and cannot submit until reconciled.
// A pending save from route A is ignored after A -> B -> A even though fullPath equals the original again.
// An unrelated route transition is not authorized while the save-owned navigation window is active.
// The exact saved product detail target is authorized without an unsaved-change prompt.
```

For large-number input, set an SKU numeric field to `1e21`, attempt final submit, assert the SKU error is rendered and neither create nor update service is called.

- [ ] **Step 3: Run focused frontend tests and verify RED**

```powershell
npm run test:run -- src/features/product/editor/productEditorState.test.ts src/features/product/editor/ProductEditorView.test.ts
```

Expected: new tests fail on the existing Basic-step gate, `BigInt` conversion, and ABA route acceptance.

- [ ] **Step 4: Make decimal validation total**

Reject values that cannot be represented as a plain fixed-point string before `BigInt`, wrap conversion-free range checking around integer/fraction text, and return `false` for exponent or oversized values. Keep valid boundary decimals accepted and avoid precision-widening beyond the backend schema.

- [ ] **Step 5: Move simple-product structural validation to the SKU boundary**

Keep Basic validation limited to required identity/category fields. In the SKU validation branch, report `errors.productType = '单规格商品不能包含规格维度或规格值'` when a simple product still has multiple SKUs, non-product-dimension specifications, or populated SKU specification values. Final validation must therefore route the user to SKU cleanup and block the API until resolved.

- [ ] **Step 6: Bind save callbacks to monotonic route identity**

Introduce a route identity generation incremented by the route watcher for every `fullPath` change, including A -> B -> A. Capture it at submit and require equality in success, failure, and `finally` handling. Replace the broad boolean bypass with an exact expected saved-product detail target; `confirmLeave(to)` returns true only when `to.name === 'product-detail'` and `String(to.params.id) === String(expectedSavedProductId)`. Clear authorization after navigation, route change, or unmount.

- [ ] **Step 7: Run focused and full frontend verification**

```powershell
npm run test:run -- src/features/product/editor/productEditorState.test.ts src/features/product/editor/ProductEditorView.test.ts
npm run test:run
npm run build
```

Expected: focused tests, all frontend tests, TypeScript checking, and Vite build pass.

- [ ] **Step 8: Commit Task 2**

```powershell
git add frontend/src/features/product/editor/productEditorState.ts frontend/src/features/product/editor/ProductEditorView.vue frontend/src/features/product/editor/productEditorState.test.ts frontend/src/features/product/editor/ProductEditorView.test.ts
git commit -m "fix: harden product editor residual flows"
```

---

### Task 3: Clean The Branch And Repeat Final Verification

**Files:**
- Add: `docs/superpowers/plans/2026-09-01-home-product-residual-fixes.md`
- Delete: `.superpowers/sdd/2026-09-01-home-product-redesign/final-fix-report.md`
- Verify: all files changed since residual-cycle base `a39cbad`

**Interfaces:**
- Consumes: Task 1 backend behavior and Task 2 editor behavior.
- Produces: a merge-ready tracked tree without internal SDD reports and a fresh verification record in the current residual-cycle workspace only.

- [ ] **Step 1: Remove the accidentally tracked internal report**

Track this residual-cycle plan and delete only the obsolete tracked report:

```text
.superpowers/sdd/2026-09-01-home-product-redesign/final-fix-report.md
```

Confirm `.superpowers/` remains ignored and no other plan workspace is staged.

- [ ] **Step 2: Run repository hygiene checks**

```powershell
git status --short
git diff --check a39cbad..HEAD
git ls-files .superpowers
```

Expected: no whitespace errors; the deleted report is absent from the final tree; no new SDD scratch artifact is tracked.

- [ ] **Step 3: Run fresh backend verification**

From `backend`, with only the dedicated `bebefish_erp_test` environment configured:

```powershell
mvn test
mvn package -DskipTests
```

Expected: all backend tests pass and the executable JAR packages successfully.

- [ ] **Step 4: Run fresh frontend verification**

From `frontend`:

```powershell
npm run test:run
npm run build
```

Expected: all frontend tests and production build pass.

- [ ] **Step 5: Commit cleanup**

```powershell
git add docs/superpowers/plans/2026-09-01-home-product-residual-fixes.md
git add -u .superpowers/sdd/2026-09-01-home-product-redesign/final-fix-report.md
git commit -m "chore: clean residual review artifacts"
```

- [ ] **Step 6: Run final whole-cycle review and smoke verification**

Review the complete residual range `a39cbad..HEAD`. Then start the packaged backend against `bebefish_erp_test` and the frontend in real API mode. In a real browser, verify normal product edit/save navigation, variant-to-simple cleanup, and large-number rejection without API submission. Leave the development servers running for user verification.
