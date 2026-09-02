# Product Physical Attributes And Catalog Columns Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Persist SKU product dimensions and capacity, expose them throughout product editing and detail views, and let users safely customize product catalog columns without dropping below the current seven-column information density.

**Architecture:** Extend the existing SKU packaging value object and `product_sku` persistence row with four nullable physical fields. Keep the catalog preference entirely in the frontend through a typed column registry and versioned `localStorage`, with the operation column fixed and SKU-derived values resolved by one shared fallback helper.

**Tech Stack:** Java 21, Spring Boot, JDBC, Flyway, MySQL 8, JUnit 5, AssertJ, MockMvc, Vue 3 Composition API, TypeScript, Tailwind CSS, lucide-vue-next, Vitest, Vue Test Utils, Vite.

**Spec:** `docs/superpowers/specs/2026-09-02-product-physical-attributes-column-selection-design.md`

## Global Constraints

- Store `product_length_cm`, `product_width_cm`, `product_height_cm`, and `capacity_ml` on `product_sku` as nullable `decimal(12,3)` values constrained to null or `>= 0`.
- Do not infer or backfill product dimensions from carton or inner-package dimensions.
- Product length, width, and height are required for packaging completeness on every enabled SKU; capacity is optional and never changes completeness.
- Product dimensions render as `长 × 宽 × 高 cm`; capacity renders as `ml`; missing values render as `--`.
- The operation column remains fixed. Users must retain at least six configurable business columns, giving seven total visible columns including operation.
- Column order is canonical and never follows checkbox selection order.
- Persist only known configurable column identifiers under `bebefish.product.catalog.columns.v1`; malformed or below-minimum preferences restore defaults.
- SKU-derived list columns use the enabled default SKU, then the first enabled SKU, then the first SKU.
- Preserve the fixed desktop editor and dialog geometry, with scrolling inside existing content regions and no mobile-specific work.
- Do not add a backend user-preference API, drag-and-drop ordering, or product-level duplicates of SKU physical fields.

---

## File And Interface Map

### Backend data and contract

- Create `backend/src/main/resources/db/migration/V9__product_physical_attributes.sql`: add the four nullable columns and nonnegative check constraints.
- Modify `backend/src/main/java/com/bebefish/erp/product/domain/Packaging.java`: append product dimensions and capacity to the packaging value object.
- Modify `backend/src/main/java/com/bebefish/erp/product/application/PackagingCommand.java`: mirror the domain field order exactly.
- Modify `backend/src/main/java/com/bebefish/erp/product/api/SaveProductRequest.java`: accept, validate, and map the four fields.
- Modify `backend/src/main/java/com/bebefish/erp/product/api/ProductResponse.java`: return the four fields for every SKU.
- Modify `backend/src/main/java/com/bebefish/erp/product/application/ProductService.java`: map command fields to `Packaging` without reordering.
- Modify `backend/src/main/java/com/bebefish/erp/product/infrastructure/ProductJpaAdapter.java`: include fields in insert, update, select, and result mapping.
- Modify `backend/src/main/java/com/bebefish/erp/product/application/ProductCompletenessCalculator.java`: require all three product dimensions for enabled SKUs, expose the separate `产品尺寸` missing label, and keep the existing five equal score groups.

### Frontend data and editing

- Modify `frontend/src/features/product/types.ts`: add the four nullable fields to `SkuForm` and `ProductSku`.
- Modify `frontend/src/features/product/productFormMapper.ts`: preserve the fields when opening an existing product for editing.
- Modify `frontend/src/features/product/editor/productEditorState.ts`: include the fields in blank state, packaging copies, unified-mode comparison, and validation.
- Modify `frontend/src/features/product/editor/steps/ProductPackagingStep.vue`: render product dimensions and capacity in unified mode.
- Modify `frontend/src/features/product/editor/PackagingEditorDialog.vue`: render the same controls for one SKU.
- Modify `frontend/src/features/product/components/ProductPackagingSection.vue`: show physical attributes in unified and per-SKU detail layouts.

### Frontend catalog customization

- Create `frontend/src/features/product/productCatalogColumns.ts`: own column identifiers, labels, canonical order, persistence rules, default-SKU fallback, and value formatting.
- Create `frontend/src/features/product/components/ProductColumnSelector.vue`: accessible anchored checkbox panel with count, minimum enforcement, reset, Escape, and outside-click handling.
- Modify `frontend/src/features/product/components/ProductList.vue`: own selected columns and place `显示字段` in the filter bar.
- Modify `frontend/src/features/product/components/ProductTable.vue`: generate headers, cells, width, loading, and empty state from the same active column definitions.

---

### Task 1: Add The Physical Attribute Migration

**Files:**
- Create: `backend/src/main/resources/db/migration/V9__product_physical_attributes.sql`
- Modify: `backend/src/test/java/com/bebefish/erp/common/persistence/FlywayMigrationTest.java`

**Interfaces:**
- Consumes: existing Flyway V1-V8 schema and `JdbcTemplate` migration test helpers.
- Produces: nullable columns `product_length_cm`, `product_width_cm`, `product_height_cm`, and `capacity_ml` on `product_sku`.

- [ ] **Step 1: Extend the migration test with column and legacy-row assertions**

Add a focused migration test using the existing `jdbc` and insert helpers:

```java
var columns = jdbc.queryForList(
        "select column_name from information_schema.columns "
                + "where table_schema = database() and table_name = 'product_sku'",
        String.class
);
assertThat(columns).contains(
        "product_length_cm",
        "product_width_cm",
        "product_height_cm",
        "capacity_ml"
);

var productId = insertProduct("P900", "ITEM900");
var skuId = insertSku(productId, "SKU900");
var physical = jdbc.queryForMap("""
        select product_length_cm, product_width_cm, product_height_cm, capacity_ml
        from product_sku
        where id = ?
        """, skuId);
assertThat(physical)
        .containsEntry("product_length_cm", null)
        .containsEntry("product_width_cm", null)
        .containsEntry("product_height_cm", null)
        .containsEntry("capacity_ml", null);
```

Add one constraint assertion by inserting a negative dimension and expecting a data-integrity failure:

```java
assertThatThrownBy(() -> jdbc.update(
        "update product_sku set product_length_cm = -1 where id = ?",
        skuId
)).isInstanceOf(DataAccessException.class);
```

Also extend `upgradesRepresentativeLegacyRowsFromV7ToV8WithoutDataLoss` to assert all four new columns are null after migration and change the latest successful version assertion from `8` to `9`. Rename that test to `upgradesRepresentativeLegacyRowsFromV7ToLatestWithoutDataLoss`.

- [ ] **Step 2: Run the migration test and confirm the new assertions fail**

Run:

```bash
cd backend
mvn -Dtest=FlywayMigrationTest test
```

Expected: FAIL because the V9 columns do not exist.

- [ ] **Step 3: Add the V9 migration**

Create the migration with explicit constraints:

```sql
alter table product_sku
    add column product_length_cm decimal(12, 3) null after inner_package_height_cm,
    add column product_width_cm decimal(12, 3) null after product_length_cm,
    add column product_height_cm decimal(12, 3) null after product_width_cm,
    add column capacity_ml decimal(12, 3) null after product_height_cm,
    add constraint ck_product_sku_product_length check (product_length_cm is null or product_length_cm >= 0),
    add constraint ck_product_sku_product_width check (product_width_cm is null or product_width_cm >= 0),
    add constraint ck_product_sku_product_height check (product_height_cm is null or product_height_cm >= 0),
    add constraint ck_product_sku_capacity check (capacity_ml is null or capacity_ml >= 0);
```

- [ ] **Step 4: Re-run the migration test**

Run:

```bash
cd backend
mvn -Dtest=FlywayMigrationTest test
```

Expected: PASS, including the null legacy-row and negative-value checks.

- [ ] **Step 5: Commit the migration**

```bash
git add backend/src/main/resources/db/migration/V9__product_physical_attributes.sql backend/src/test/java/com/bebefish/erp/common/persistence/FlywayMigrationTest.java
git commit -m "feat: add SKU physical attribute columns"
```

---

### Task 2: Round-Trip Physical Fields Through The Backend

**Files:**
- Modify: `backend/src/test/java/com/bebefish/erp/product/api/ProductControllerTest.java`
- Modify: `backend/src/main/java/com/bebefish/erp/product/domain/Packaging.java`
- Modify: `backend/src/main/java/com/bebefish/erp/product/application/PackagingCommand.java`
- Modify: `backend/src/main/java/com/bebefish/erp/product/api/SaveProductRequest.java`
- Modify: `backend/src/main/java/com/bebefish/erp/product/api/ProductResponse.java`
- Modify: `backend/src/main/java/com/bebefish/erp/product/application/ProductService.java`
- Modify: `backend/src/main/java/com/bebefish/erp/product/infrastructure/ProductJpaAdapter.java`
- Modify: backend tests that construct `Packaging` or `PackagingCommand`, found with `rg -n "new Packaging(Command)?\\(" backend/src/test`

**Interfaces:**
- Consumes: database columns from Task 1.
- Produces: request/response properties `productLengthCm`, `productWidthCm`, `productHeightCm`, and `capacityMl`, all represented as nullable `BigDecimal` values.

- [ ] **Step 1: Add controller round-trip and validation tests**

Extend a create/update/get scenario so its SKU payload contains:

```java
sku.put("productLengthCm", new BigDecimal("12.500"));
sku.put("productWidthCm", new BigDecimal("8.250"));
sku.put("productHeightCm", new BigDecimal("20.000"));
sku.put("capacityMl", new BigDecimal("450.000"));
```

Assert the create response, a subsequent GET, and an update response preserve the values:

```java
.andExpect(jsonPath("$.data.skus[0].productLengthCm").value(12.5))
.andExpect(jsonPath("$.data.skus[0].productWidthCm").value(8.25))
.andExpect(jsonPath("$.data.skus[0].productHeightCm").value(20.0))
.andExpect(jsonPath("$.data.skus[0].capacityMl").value(450.0));
```

Add invalid payload cases using the existing `requestBody`, `skuInput`, `mvc`, and `objectMapper` helpers:

```java
@Test
void rejectsInvalidProductPhysicalValues() throws Exception {
    for (var value : List.of("-0.001", "1.0001")) {
        var request = requestBody(null, "INVALID-PHYSICAL-" + value, "无效物理数据", null);
        skuInput(request).put("productLengthCm", value);
        mvc.perform(post("/api/products")
                .header("Authorization", bearer(editToken))
                .contentType(APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }
}
```

- [ ] **Step 2: Run the controller tests and confirm they fail**

Run:

```bash
cd backend
mvn -Dtest=ProductControllerTest test
```

Expected: FAIL because request and response DTOs do not expose the four properties.

- [ ] **Step 3: Extend domain, command, request, and response records in one fixed order**

Insert the four fields after inner-package dimensions and before weights in both `Packaging` and `PackagingCommand`:

```java
BigDecimal innerLengthCm,
BigDecimal innerWidthCm,
BigDecimal innerHeightCm,
BigDecimal productLengthCm,
BigDecimal productWidthCm,
BigDecimal productHeightCm,
BigDecimal capacityMl,
BigDecimal netWeightKg,
```

Add request validation after `innerPackageHeightCm`:

```java
@DecimalMin(value = "0", message = "产品长不能小于 0")
@Digits(integer = 9, fraction = 3, message = "产品长最多允许 9 位整数和 3 位小数")
BigDecimal productLengthCm,
@DecimalMin(value = "0", message = "产品宽不能小于 0")
@Digits(integer = 9, fraction = 3, message = "产品宽最多允许 9 位整数和 3 位小数")
BigDecimal productWidthCm,
@DecimalMin(value = "0", message = "产品高不能小于 0")
@Digits(integer = 9, fraction = 3, message = "产品高最多允许 9 位整数和 3 位小数")
BigDecimal productHeightCm,
@DecimalMin(value = "0", message = "容量不能小于 0")
@Digits(integer = 9, fraction = 3, message = "容量最多允许 9 位整数和 3 位小数")
BigDecimal capacityMl,
```

Map them into `PackagingCommand` in the same order. Add matching fields and accessors to `ProductResponse.SkuResponse`.

- [ ] **Step 4: Extend JDBC write and read mappings**

Add the four columns and bind values in both insert and update SQL. Keep SQL column order aligned with parameter order:

```java
packaging.productLengthCm(),
packaging.productWidthCm(),
packaging.productHeightCm(),
packaging.capacityMl(),
```

Add the four names to every SKU select and construct `Packaging` with:

```java
resultSet.getBigDecimal("product_length_cm"),
resultSet.getBigDecimal("product_width_cm"),
resultSet.getBigDecimal("product_height_cm"),
resultSet.getBigDecimal("capacity_ml"),
```

Update `ProductService` and every backend test fixture constructor with four explicit nulls or values in the fixed field order. Do not introduce a second constructor that could hide field-order mistakes.

- [ ] **Step 5: Re-run focused backend contract tests**

Run:

```bash
cd backend
mvn -Dtest=ProductControllerTest,ProductServiceTest,ProductJpaAdapterQueryCountTest test
```

Expected: PASS. Confirm create, update, get, invalid input, and list query coverage all remain green.

- [ ] **Step 6: Commit the backend round trip**

```bash
git add backend/src/main/java/com/bebefish/erp/product backend/src/test/java/com/bebefish/erp/product
git commit -m "feat: expose SKU physical attributes"
```

---

### Task 3: Include Product Dimensions In Completeness

**Files:**
- Modify: `backend/src/test/java/com/bebefish/erp/product/application/ProductCompletenessCalculatorTest.java`
- Modify: `backend/src/main/java/com/bebefish/erp/product/application/ProductCompletenessCalculator.java`

**Interfaces:**
- Consumes: `Packaging.productLengthCm()`, `productWidthCm()`, `productHeightCm()`, and `capacityMl()` from Task 2.
- Produces: the `产品尺寸` missing label for enabled SKUs lacking any dimension, while the combined packaging-and-dimensions score remains one of the existing five 20-point groups; capacity is ignored.

- [ ] **Step 1: Add focused completeness cases**

Update `completePackaging()` with valid product dimensions and null capacity, then add a helper that replaces the complete SKU's packaging while preserving every other field:

```java
private Product withPackaging(Packaging packaging, String status) {
    var product = completeProduct();
    var original = product.skus().getFirst();
    var sku = new Sku(
            original.id(), original.code(), original.barcode(), original.name(), original.specText(),
            original.specificationValues(), original.salesUnit(), original.defaultSalePrice(),
            original.standardCost(), original.safetyStockQuantity(), packaging,
            original.skuImageFileId(), original.isDefault(), status
    );
    return productWithSkus(product, List.of(sku));
}

@Test
void productDimensionsAreRequiredButCapacityIsOptional() {
    var packaging = completePackaging();
    var withoutCapacity = new Packaging(
            packaging.lengthCm(), packaging.widthCm(), packaging.heightCm(), packaging.volumeCm3(),
            packaging.innerLengthCm(), packaging.innerWidthCm(), packaging.innerHeightCm(),
            new BigDecimal("12"), new BigDecimal("8"), new BigDecimal("20"), null,
            packaging.netWeightKg(), packaging.grossWeightKg(), packaging.gramWeightG(),
            packaging.innerWeightKg(), packaging.method(), packaging.cartonQuantity(),
            packaging.packageImageFileId(), packaging.cartonImageFileId()
    );

    var result = calculator.calculate(withPackaging(withoutCapacity, "enabled"), completeQuotes());

    assertThat(result.missingGroups()).doesNotContain("产品尺寸");
}
```

Add one case for each missing dimension and assert `产品尺寸` is missing and completeness drops by exactly 20 points. Add a case with missing carton/weight data and missing product dimensions; assert both `包装重量` and `产品尺寸` are reported while the score still loses only the single packaging group. For the disabled-SKU case, append `withPackaging(packagingWithNullDimensions, "disabled").skus().getFirst()` to an otherwise complete product and assert the result stays complete.

- [ ] **Step 2: Run the calculator test and confirm dimension cases fail**

Run:

```bash
cd backend
mvn -Dtest=ProductCompletenessCalculatorTest test
```

Expected: FAIL because product dimensions are not inspected and `产品尺寸` is never reported.

- [ ] **Step 3: Add a dimension completeness predicate and decouple scoring from missing-label count**

Keep `packagingComplete` responsible for the existing packaging and weight rules. Add:

```java
private boolean productDimensionsComplete(List<Sku> enabledSkus) {
    return !enabledSkus.isEmpty() && enabledSkus.stream().allMatch(sku -> {
        Packaging packaging = sku.packaging();
        return packaging != null
                && packaging.productLengthCm() != null
                && packaging.productWidthCm() != null
                && packaging.productHeightCm() != null;
    });
}
```

In `calculate`, evaluate named booleans, add both labels, and count five score groups explicitly:

```java
var basicComplete = basicInfoComplete(product);
var skuComplete = skuInfoComplete(enabledSkus);
var purchasingComplete = purchasingComplete(enabledSkus, quotesBySkuId);
var packagingWeightComplete = packagingComplete(enabledSkus);
var dimensionsComplete = productDimensionsComplete(enabledSkus);
var imageComplete = imagesComplete(product, enabledSkus);

addMissing(missingGroups, "基本信息", basicComplete);
addMissing(missingGroups, "SKU 信息", skuComplete);
addMissing(missingGroups, "采购信息", purchasingComplete);
addMissing(missingGroups, "包装重量", packagingWeightComplete);
addMissing(missingGroups, "产品尺寸", dimensionsComplete);
addMissing(missingGroups, "图片资料", imageComplete);

var completedGroups = 0;
if (basicComplete) completedGroups++;
if (skuComplete) completedGroups++;
if (purchasingComplete) completedGroups++;
if (packagingWeightComplete && dimensionsComplete) completedGroups++;
if (imageComplete) completedGroups++;
var percent = completedGroups * GROUP_SCORE;
```

Do not inspect `capacityMl()`. Update the existing fixed-order assertion to include `产品尺寸` between `包装重量` and `图片资料`; its percentage assertion remains based on five groups.

- [ ] **Step 4: Re-run completeness and product service tests**

Run:

```bash
cd backend
mvn -Dtest=ProductCompletenessCalculatorTest,ProductServiceTest test
```

Expected: PASS with capacity absent, FAIL only in the test fixture before it is updated with all three dimensions.

- [ ] **Step 5: Commit completeness behavior**

```bash
git add backend/src/main/java/com/bebefish/erp/product/application/ProductCompletenessCalculator.java backend/src/test/java/com/bebefish/erp/product/application/ProductCompletenessCalculatorTest.java backend/src/test/java/com/bebefish/erp/product/application/ProductServiceTest.java
git commit -m "feat: require SKU product dimensions for completeness"
```

---

### Task 4: Preserve Physical Fields In Frontend State

**Files:**
- Modify: `frontend/src/features/product/types.ts`
- Modify: `frontend/src/features/product/productFormMapper.ts`
- Modify: `frontend/src/features/product/productFormMapper.test.ts`
- Modify: `frontend/src/features/product/productTestFixtures.ts`
- Modify: `frontend/src/features/product/editor/productEditorState.ts`
- Modify: `frontend/src/features/product/editor/productEditorState.test.ts`
- Modify: frontend test fixtures found with `rg -n "packageLengthCm" frontend/src/features/product -g '*.test.ts'`

**Interfaces:**
- Consumes: backend JSON properties from Task 2.
- Produces: nullable number properties `productLengthCm`, `productWidthCm`, `productHeightCm`, `capacityMl` on both `SkuForm` and `ProductSku`; `PackagingForm` includes all four.

- [ ] **Step 1: Add mapper and packaging-mode state tests**

Extend the mapper fixture and assertion:

```ts
productLengthCm: 12.5,
productWidthCm: 8.25,
productHeightCm: 20,
capacityMl: 450
```

```ts
expect(payload.skus[0]).toMatchObject({
  productLengthCm: 12.5,
  productWidthCm: 8.25,
  productHeightCm: 20,
  capacityMl: 450
});
```

Add state tests proving unified mode copies all four values to every SKU and per-SKU mode is detected when only capacity or one product dimension differs.

- [ ] **Step 2: Run focused frontend tests and confirm type/test failures**

Run:

```bash
cd frontend
npm run test:run -- src/features/product/productFormMapper.test.ts src/features/product/editor/productEditorState.test.ts
```

Expected: FAIL because the fields are absent from types and state helpers.

- [ ] **Step 3: Extend TypeScript contracts and state helpers**

Add the properties after inner-package dimensions in `SkuForm` and `ProductSku`:

```ts
productLengthCm: number | null;
productWidthCm: number | null;
productHeightCm: number | null;
capacityMl: number | null;
```

Add them to `PackagingForm`, `packagingFields`, `createBlankSku`, `packagingFromSku`, and `toProductFormPayload`. Keep null as null; do not coerce missing physical data to zero.

Expose one validation limit for all four fields:

```ts
export const MAX_SAFE_DIMENSION = '999999999.999';
```

Use the same `MAX_SAFE_DIMENSION` for capacity because its backend integer/fraction limits match the dimension fields.

- [ ] **Step 4: Add frontend validation assertions**

Extend editor validation tests so `-0.001` and `1000000000` fail for each new field, while `0`, `1.125`, and null pass. Use exact Chinese field labels in expected messages:

```ts
expect(errors).toContain('产品长不能小于 0');
expect(errors).toContain('容量最多允许 9 位整数和 3 位小数');
```

- [ ] **Step 5: Run the focused state and mapper tests**

Run:

```bash
cd frontend
npm run test:run -- src/features/product/productFormMapper.test.ts src/features/product/editor/productEditorState.test.ts
```

Expected: PASS with unified copy, per-SKU difference detection, null preservation, and validation coverage.

- [ ] **Step 6: Commit frontend data support**

```bash
git add frontend/src/features/product/types.ts frontend/src/features/product/productFormMapper.ts frontend/src/features/product/productFormMapper.test.ts frontend/src/features/product/productTestFixtures.ts frontend/src/features/product/editor/productEditorState.ts frontend/src/features/product/editor/productEditorState.test.ts
git commit -m "feat: preserve SKU physical fields in product editor"
```

---

### Task 5: Add Product Dimensions And Capacity To Packaging Editors

**Files:**
- Modify: `frontend/src/features/product/editor/ProductEditorView.test.ts`
- Modify: `frontend/src/features/product/editor/steps/ProductPackagingStep.vue`
- Modify: `frontend/src/features/product/editor/PackagingEditorDialog.vue`

**Interfaces:**
- Consumes: `PackagingForm` and validation limits from Task 4.
- Produces: unified and per-SKU controls with test IDs `packaging-product-length`, `packaging-product-width`, `packaging-product-height`, `packaging-capacity`, plus dialog-prefixed equivalents.

- [ ] **Step 1: Add editor rendering and interaction tests**

In `ProductEditorView.test.ts`, navigate to the packaging step and assert the new controls and units:

```ts
expect(wrapper.get('[data-testid="packaging-product-length-unit"]').text()).toBe('cm');
expect(wrapper.get('[data-testid="packaging-capacity-unit"]').text()).toBe('ml');
expect(wrapper.findAll('[data-testid="packaging-product-dimension-separator"]')).toHaveLength(2);
```

Set values and assert all SKU payloads receive them in unified mode:

```ts
await wrapper.get('[data-testid="packaging-product-length"]').setValue('12.5');
await wrapper.get('[data-testid="packaging-product-width"]').setValue('8.25');
await wrapper.get('[data-testid="packaging-product-height"]').setValue('20');
await wrapper.get('[data-testid="packaging-capacity"]').setValue('450');
expect(editorState.skus.every((sku) => sku.productLengthCm === 12.5)).toBe(true);
```

Open the per-SKU dialog, edit the four dialog fields, save, and assert only the selected SKU changes.

- [ ] **Step 2: Run the editor test and confirm the controls are missing**

Run:

```bash
cd frontend
npm run test:run -- src/features/product/editor/ProductEditorView.test.ts
```

Expected: FAIL because the physical-attribute inputs are not rendered.

- [ ] **Step 3: Add the unified editor group**

Add a `产品尺寸与容量` group to `ProductPackagingStep.vue`. Use the established formula control for dimensions:

```vue
<fieldset>
  <legend>产品尺寸</legend>
  <div class="dimension-formula">
    <label class="pack-field"><span>长</span><span class="pack-unit-input"><input data-testid="packaging-product-length" type="number" min="0" :max="fieldMax('productLengthCm')" step="0.001" /><span data-testid="packaging-product-length-unit" class="pack-unit-suffix">cm</span></span></label>
    <span data-testid="packaging-product-dimension-separator" class="dimension-separator">×</span>
    <label class="pack-field"><span>宽</span><span class="pack-unit-input"><input data-testid="packaging-product-width" type="number" min="0" :max="fieldMax('productWidthCm')" step="0.001" /><span class="pack-unit-suffix">cm</span></span></label>
    <span data-testid="packaging-product-dimension-separator" class="dimension-separator">×</span>
    <label class="pack-field"><span>高</span><span class="pack-unit-input"><input data-testid="packaging-product-height" type="number" min="0" :max="fieldMax('productHeightCm')" step="0.001" /><span class="pack-unit-suffix">cm</span></span></label>
  </div>
</fieldset>
<label class="pack-field"><span>容量</span><span class="pack-unit-input"><input data-testid="packaging-capacity" type="number" min="0" :max="fieldMax('capacityMl')" step="0.001" /><span data-testid="packaging-capacity-unit" class="pack-unit-suffix">ml</span></span></label>
```

Bind each input through the component's existing numeric update path so empty input becomes null.

- [ ] **Step 4: Add matching per-SKU dialog controls**

Add the same group to `PackagingEditorDialog.vue` using test IDs:

```text
packaging-dialog-product-length
packaging-dialog-product-width
packaging-dialog-product-height
packaging-dialog-product-dimension-separator
packaging-dialog-capacity
packaging-dialog-capacity-unit
```

Fit the group inside the existing scrollable body. Do not change the dialog width or sticky footer height.

- [ ] **Step 5: Re-run editor tests and production type checking**

Run:

```bash
cd frontend
npm run test:run -- src/features/product/editor/ProductEditorView.test.ts
npm run build
```

Expected: PASS; Vue type checking confirms every dynamic field key is part of `PackagingForm`.

- [ ] **Step 6: Commit the packaging editor UI**

```bash
git add frontend/src/features/product/editor/ProductEditorView.test.ts frontend/src/features/product/editor/steps/ProductPackagingStep.vue frontend/src/features/product/editor/PackagingEditorDialog.vue
git commit -m "feat: edit SKU dimensions and capacity"
```

---

### Task 6: Show Physical Attributes On Product Detail

**Files:**
- Modify: `frontend/src/features/product/ProductDetailNavigation.test.ts`
- Modify: `frontend/src/features/product/components/ProductPackagingSection.vue`

**Interfaces:**
- Consumes: `ProductSku.productLengthCm`, `productWidthCm`, `productHeightCm`, and `capacityMl` from Task 4 plus existing `formatDimensions` and `formatWithUnit` helpers.
- Produces: product detail labels `产品尺寸` and `容量` in both uniform and per-SKU packaging modes.

- [ ] **Step 1: Add uniform and per-SKU detail assertions**

Extend detail fixtures with distinct values and assert formatted text:

```ts
expect(wrapper.get('[data-testid="product-packaging-section"]').text()).toContain('产品尺寸');
expect(wrapper.get('[data-testid="product-packaging-section"]').text()).toContain('12.5 × 8.25 × 20 cm');
expect(wrapper.get('[data-testid="product-packaging-section"]').text()).toContain('450 ml');
```

For per-SKU mode, assert each `packaging-sku-{id}` article renders its own values. Add a null-capacity case and assert `--`.

- [ ] **Step 2: Run the detail test and confirm the labels are missing**

Run:

```bash
cd frontend
npm run test:run -- src/features/product/ProductDetailNavigation.test.ts
```

Expected: FAIL because detail packaging does not render product dimensions or capacity.

- [ ] **Step 3: Add physical attributes to both detail modes**

In unified mode, add a `产品信息` subsection or fold the fields into `包装规格` without increasing the outer fixed desktop height beyond its content capacity:

```vue
<div class="min-h-10 min-w-0">
  <dt class="text-xs text-[#94a3b8]">产品尺寸</dt>
  <dd class="truncate text-sm font-medium text-[#25314d]">
    {{ formatDimensions(uniformSku.productLengthCm, uniformSku.productWidthCm, uniformSku.productHeightCm) }}
  </dd>
</div>
<div class="min-h-10 min-w-0">
  <dt class="text-xs text-[#94a3b8]">容量</dt>
  <dd class="truncate text-sm font-medium text-[#25314d]">{{ formatWithUnit(uniformSku.capacityMl, 'ml') }}</dd>
</div>
```

Repeat with `sku.*` inside each per-SKU article. If the uniform layout no longer fits `246px`, remove the fixed height and retain only `min-h-[246px]`; do not clip content.

- [ ] **Step 4: Re-run the detail test**

Run:

```bash
cd frontend
npm run test:run -- src/features/product/ProductDetailNavigation.test.ts
```

Expected: PASS for uniform, per-SKU, and missing-value rendering.

- [ ] **Step 5: Commit detail rendering**

```bash
git add frontend/src/features/product/ProductDetailNavigation.test.ts frontend/src/features/product/components/ProductPackagingSection.vue
git commit -m "feat: show SKU physical attributes in product detail"
```

---

### Task 7: Build The Catalog Column Registry And Preference Rules

**Files:**
- Create: `frontend/src/features/product/productCatalogColumns.ts`
- Create: `frontend/src/features/product/productCatalogColumns.test.ts`

**Interfaces:**
- Consumes: `Product` and `ProductSku` from Task 4.
- Produces:
  - `ProductCatalogColumnId`
  - `PRODUCT_CATALOG_STORAGE_KEY`
  - `DEFAULT_PRODUCT_COLUMN_IDS`
  - `MIN_CONFIGURABLE_PRODUCT_COLUMNS`
  - `PRODUCT_CATALOG_COLUMNS`
  - `normalizeProductColumnIds(ids: unknown): ProductCatalogColumnId[]`
  - `loadProductColumnIds(storage?: StorageLike): ProductCatalogColumnId[]`
  - `saveProductColumnIds(ids: ProductCatalogColumnId[], storage?: StorageLike): void`
  - `resetProductColumnIds(storage?: StorageLike): ProductCatalogColumnId[]`
  - `resolveCatalogSku(product: Product): ProductSku | undefined`
  - `StorageLike = Pick<Storage, 'getItem' | 'setItem' | 'removeItem'>`

- [ ] **Step 1: Write registry, persistence, and fallback tests**

Create tests covering default order, unknown identifiers, malformed JSON, below-minimum stored arrays, reset, and SKU fallback:

```ts
expect(DEFAULT_PRODUCT_COLUMN_IDS).toEqual([
  'productInfo',
  'category',
  'brandSupplier',
  'stock',
  'price',
  'completenessStatus'
]);
expect(MIN_CONFIGURABLE_PRODUCT_COLUMNS).toBe(6);

expect(normalizeProductColumnIds(['stock', 'unknown', 'productInfo', 'price', 'category', 'brandSupplier', 'completenessStatus']))
  .toEqual(DEFAULT_PRODUCT_COLUMN_IDS);

expect(resolveCatalogSku(productWithDefault)).toBe(enabledDefaultSku);
expect(resolveCatalogSku(productWithoutEnabledDefault)).toBe(firstEnabledSku);
expect(resolveCatalogSku(productWithoutEnabledSku)).toBe(firstSku);
```

Use a memory storage fixture:

```ts
const storage = {
  values: new Map<string, string>(),
  getItem(key: string) { return this.values.get(key) ?? null; },
  setItem(key: string, value: string) { this.values.set(key, value); },
  removeItem(key: string) { this.values.delete(key); }
};
```

- [ ] **Step 2: Run the new test and confirm the module is missing**

Run:

```bash
cd frontend
npm run test:run -- src/features/product/productCatalogColumns.test.ts
```

Expected: FAIL because `productCatalogColumns.ts` does not exist.

- [ ] **Step 3: Define identifiers and canonical registry**

Use this identifier union and canonical order:

```ts
export type ProductCatalogColumnId =
  | 'productInfo'
  | 'productCode'
  | 'category'
  | 'productType'
  | 'brandSupplier'
  | 'stock'
  | 'safetyStock'
  | 'price'
  | 'skuCount'
  | 'completenessStatus'
  | 'completenessPercent'
  | 'recordStatus'
  | 'updatedAt'
  | 'remark'
  | 'productDimensions'
  | 'capacity'
  | 'packageVolume'
  | 'cartonQuantity'
  | 'packagingMethod'
  | 'grossWeight'
  | 'netWeight'
  | 'gramWeight';

export interface ProductCatalogColumnDefinition {
  id: ProductCatalogColumnId;
  label: string;
  width: number;
  align: 'left' | 'center' | 'right';
}
```

Create `PRODUCT_CATALOG_COLUMNS` in the order above, with Chinese labels from the spec and widths no smaller than the text they display. Keep `操作` out of the registry because it is fixed.

- [ ] **Step 4: Implement preference normalization and persistence**

Use a versioned key and canonical filtering:

```ts
export const PRODUCT_CATALOG_STORAGE_KEY = 'bebefish.product.catalog.columns.v1';
export const MIN_CONFIGURABLE_PRODUCT_COLUMNS = 6;
export type StorageLike = Pick<Storage, 'getItem' | 'setItem' | 'removeItem'>;

export function normalizeProductColumnIds(ids: unknown): ProductCatalogColumnId[] {
  if (!Array.isArray(ids)) return [...DEFAULT_PRODUCT_COLUMN_IDS];
  const requested = new Set(ids.filter((id): id is ProductCatalogColumnId => knownIds.has(id as ProductCatalogColumnId)));
  const normalized = PRODUCT_CATALOG_COLUMNS.map(({ id }) => id).filter((id) => requested.has(id));
  return normalized.length >= MIN_CONFIGURABLE_PRODUCT_COLUMNS
    ? normalized
    : [...DEFAULT_PRODUCT_COLUMN_IDS];
}
```

`loadProductColumnIds` catches `JSON.parse` and storage access errors. `saveProductColumnIds` stores only normalized arrays. `resetProductColumnIds` removes the key and returns a fresh default array.

- [ ] **Step 5: Implement SKU fallback and physical value formatting**

```ts
export function resolveCatalogSku(product: Product) {
  return product.skus.find((sku) => sku.status === 'enabled' && sku.defaultSku)
    ?? product.skus.find((sku) => sku.status === 'enabled')
    ?? product.skus[0];
}

export function formatCatalogDimensions(sku?: ProductSku) {
  if (!sku || [sku.productLengthCm, sku.productWidthCm, sku.productHeightCm].some((value) => value == null)) return '--';
  return `${sku.productLengthCm} × ${sku.productWidthCm} × ${sku.productHeightCm} cm`;
}

export function formatCatalogUnit(value: number | null | undefined, unit: string) {
  return value == null ? '--' : `${value} ${unit}`;
}
```

Add tests for `ml`, `cm³`, `kg`, and `g` output and for incomplete dimensions returning `--`.

- [ ] **Step 6: Run the registry tests**

Run:

```bash
cd frontend
npm run test:run -- src/features/product/productCatalogColumns.test.ts
```

Expected: PASS for canonical ordering, minimum enforcement, invalid storage recovery, reset, fallback, and units.

- [ ] **Step 7: Commit the catalog column model**

```bash
git add frontend/src/features/product/productCatalogColumns.ts frontend/src/features/product/productCatalogColumns.test.ts
git commit -m "feat: define product catalog column preferences"
```

---

### Task 8: Add The Column Selector And Dynamic Product Table

**Files:**
- Create: `frontend/src/features/product/components/ProductColumnSelector.vue`
- Modify: `frontend/src/features/product/components/ProductList.vue`
- Modify: `frontend/src/features/product/components/ProductTable.vue`
- Modify: `frontend/src/features/product/ProductFeature.test.ts`

**Interfaces:**
- Consumes: Task 7 registry and persistence functions.
- Produces:
  - `ProductColumnSelector` props `modelValue: ProductCatalogColumnId[]` and event `update:modelValue`.
  - `ProductTable` prop `columnIds: ProductCatalogColumnId[]`.
  - Fixed operation column appended by `ProductTable`, never passed through the selector.

- [ ] **Step 1: Add feature tests for the selector interaction**

Mount the list/table feature with clean storage and assert defaults:

```ts
expect(wrapper.findAll('thead th')).toHaveLength(7);
expect(wrapper.get('[data-testid="product-column-trigger"]').attributes('aria-expanded')).toBe('false');
await wrapper.get('[data-testid="product-column-trigger"]').trigger('click');
expect(wrapper.get('[data-testid="product-column-panel"]').isVisible()).toBe(true);
expect(wrapper.get('[data-testid="product-column-count"]').text()).toContain('已选 6');
```

Select `产品尺寸` and `容量`, then assert canonical header order, stored identifiers, and row values. Deselect until six configurable columns remain and assert each selected checkbox that would violate the minimum is disabled. Test `恢复默认`, Escape, and an outside click.

- [ ] **Step 2: Add dynamic table alignment tests**

Pass expanded `columnIds` directly to `ProductTable` and assert:

```ts
expect(wrapper.findAll('thead th')).toHaveLength(columnIds.length + 1);
expect(wrapper.get('[data-testid="product-table-loading"]').attributes('colspan')).toBe(String(columnIds.length + 1));
expect(wrapper.get('[data-testid="product-table-empty"]').attributes('colspan')).toBe(String(columnIds.length + 1));
```

Add three products that exercise enabled-default, first-enabled, and first-SKU fallback; assert the derived physical values are rendered from the expected SKU.

- [ ] **Step 3: Run feature tests and confirm the selector and dynamic prop are missing**

Run:

```bash
cd frontend
npm run test:run -- src/features/product/ProductFeature.test.ts
```

Expected: FAIL because the selector does not exist and `ProductTable` has fixed columns.

- [ ] **Step 4: Build the accessible selector panel**

Create `ProductColumnSelector.vue` with `Columns3` and `RotateCcw` lucide icons. The trigger must use:

```vue
<button
  data-testid="product-column-trigger"
  type="button"
  :aria-expanded="open"
  aria-controls="product-column-panel"
  @click="open = !open"
>
  <Columns3 class="h-4 w-4" aria-hidden="true" />
  显示字段
</button>
```

Render one explicit checkbox label per registry definition. Disable a checked box when `modelValue.length === MIN_CONFIGURABLE_PRODUCT_COLUMNS`. Emit a new canonical array after every valid change. Add the visible explanation `至少保留 6 个业务字段，操作列固定显示。`

Register document listeners while open:

```ts
function onKeydown(event: KeyboardEvent) {
  if (event.key === 'Escape') closeAndFocusTrigger();
}

function onPointerDown(event: PointerEvent) {
  if (!root.value?.contains(event.target as Node)) open.value = false;
}
```

Remove listeners in `onBeforeUnmount`.

- [ ] **Step 5: Own and persist selection in ProductList**

Initialize once and persist emitted changes:

```ts
const productColumnIds = ref<ProductCatalogColumnId[]>(loadProductColumnIds());

function updateProductColumns(ids: ProductCatalogColumnId[]) {
  productColumnIds.value = [...ids];
  saveProductColumnIds(productColumnIds.value);
}

function resetProductColumns() {
  productColumnIds.value = resetProductColumnIds();
}
```

Place `ProductColumnSelector` at the right edge of the existing filter bar and pass `:column-ids="productColumnIds"` to `ProductTable`.

- [ ] **Step 6: Convert ProductTable to one column-definition-driven loop**

Resolve active definitions and width:

```ts
const activeColumns = computed(() => PRODUCT_CATALOG_COLUMNS.filter((column) => props.columnIds.includes(column.id)));
const totalColumnCount = computed(() => activeColumns.value.length + 1);
const minimumWidth = computed(() => activeColumns.value.reduce((sum, column) => sum + column.width, 112));
```

Render headers from `activeColumns`, render one body cell per active definition, and append the fixed `查看详情` operation cell. Use a focused cell component or a `switch` helper so header and row iteration share the same definitions. Physical cases must use `resolveCatalogSku`:

```ts
case 'productDimensions': return formatCatalogDimensions(sku);
case 'capacity': return formatCatalogUnit(sku?.capacityMl, 'ml');
case 'packageVolume': return formatCatalogUnit(sku?.packageVolumeCm3, 'cm³');
case 'cartonQuantity': return sku?.cartonQuantity == null ? '--' : `${sku.cartonQuantity} ${sku.salesUnit || '件'}/箱`;
case 'packagingMethod': return sku?.packagingMethod || '--';
case 'grossWeight': return formatCatalogUnit(sku?.grossWeightKg, 'kg');
case 'netWeight': return formatCatalogUnit(sku?.netWeightKg, 'kg');
case 'gramWeight': return formatCatalogUnit(sku?.gramWeightG, 'g');
```

Bind loading and empty rows to `:colspan="totalColumnCount"`. Keep the existing horizontal scroll wrapper and set the table style to `minWidth: `${minimumWidth}px``.

- [ ] **Step 7: Run feature, registry, and build verification**

Run:

```bash
cd frontend
npm run test:run -- src/features/product/ProductFeature.test.ts src/features/product/productCatalogColumns.test.ts
npm run build
```

Expected: PASS with seven default total columns, minimum enforcement, persisted expanded selection, aligned states, and no TypeScript errors.

- [ ] **Step 8: Commit catalog customization**

```bash
git add frontend/src/features/product/components/ProductColumnSelector.vue frontend/src/features/product/components/ProductList.vue frontend/src/features/product/components/ProductTable.vue frontend/src/features/product/ProductFeature.test.ts
git commit -m "feat: customize product catalog columns"
```

---

### Task 9: Complete Regression And Visual Verification

**Files:**
- Modify: `design-qa.md`
- Modify: `frontend/prototype-screenshots/README.md`
- Create: `frontend/prototype-screenshots/products/product-edit-physical-attributes.png`
- Create: `frontend/prototype-screenshots/products/product-detail-physical-attributes.png`
- Create: `frontend/prototype-screenshots/products/product-catalog-columns-expanded.png`

**Interfaces:**
- Consumes: all implementation tasks.
- Produces: final automated verification and desktop visual evidence at 1440 x 1024.

- [ ] **Step 1: Run the full backend suite**

Run:

```bash
cd backend
mvn test
```

Expected: PASS with no Flyway checksum, JDBC parameter-count, completeness, controller, or service failures.

- [ ] **Step 2: Run the full frontend suite and production build**

Run:

```bash
cd frontend
npm run test:run
npm run build
```

Expected: all Vitest tests pass and Vite emits the production build.

- [ ] **Step 3: Verify the editor at 1440 x 1024**

Open an existing product edit route, navigate to `包装与重量`, and verify:

```text
产品尺寸 labels are 长 / 宽 / 高 with two × separators
all three dimension controls show cm suffixes
capacity shows an ml suffix
unified mode updates every SKU
per-SKU dialog updates only the chosen SKU
dialog content does not overlap the sticky footer
```

Capture `frontend/prototype-screenshots/products/product-edit-physical-attributes.png`.

- [ ] **Step 4: Verify product detail at 1440 x 1024**

Open a saved product and confirm product dimensions and capacity appear in both unified and per-SKU layouts, null capacity shows `--`, and no section clips or overlaps. Capture `frontend/prototype-screenshots/products/product-detail-physical-attributes.png`.

- [ ] **Step 5: Verify default and expanded catalog columns at 1440 x 1024**

Open `/products?page=1&size=20`, clear the versioned storage key, reload, and confirm seven total default columns. Open `显示字段`, select all physical columns, and verify:

```text
canonical header order
operation remains the final fixed column
minimum selected checkbox behavior at six configurable fields
horizontal scrolling without compressed or covered text
physical values use the intended default-SKU fallback
Escape and outside-click close the panel
reload restores the expanded selection
恢复默认 returns to seven total columns and removes the storage entry
```

Capture `frontend/prototype-screenshots/products/product-catalog-columns-expanded.png`.

- [ ] **Step 6: Record exact verification results**

Add a dated section to `design-qa.md` containing the backend test count, frontend test count, build result, viewport, routes checked, and the three screenshot paths. Add those paths under the product module in `frontend/prototype-screenshots/README.md`.

- [ ] **Step 7: Review the final diff and commit QA evidence**

Run:

```bash
git status --short
git diff --check
git diff --stat
```

Expected: no whitespace errors; only intended feature and QA files are present in this sequence.

```bash
git add design-qa.md frontend/prototype-screenshots/README.md frontend/prototype-screenshots/products/product-edit-physical-attributes.png frontend/prototype-screenshots/products/product-detail-physical-attributes.png frontend/prototype-screenshots/products/product-catalog-columns-expanded.png
git commit -m "test: verify product physical attributes and columns"
```
