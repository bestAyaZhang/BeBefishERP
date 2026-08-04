# Product Prototype Alignment Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Render the live product master-data workflow in the approved `WorkspacePrototype.vue` visual language while retaining the current Product Service API.

**Architecture:** Keep routing and the ERP shell unchanged. Move product-page presentation into focused product feature components: an overview strip, prototype-style filters and product rows, and a right-side detail drawer. `ProductService` remains the only product data dependency; static presentation values must not become business data.

**Tech Stack:** Vue 3, TypeScript, Tailwind CSS, Vitest, Vue Test Utils.

## Global Constraints

- Write a failing Vitest test before each behavior change.
- Use existing `ProductService` types and REST endpoints; do not change backend behavior.
- Keep the existing ERP left navigation as the only application sidebar.
- Do not run `git add`, `git commit`, or `git push` without explicit user instruction.

---

### Task 1: Prototype-Style Product Overview and List

**Files:**
- Modify: `frontend/src/features/product/components/ProductList.vue`
- Modify: `frontend/src/features/product/ProductFeature.test.ts`

**Interfaces:**
- Consumes: `ProductService.listProducts(query): Promise<PageResult<Product>>`
- Produces: `select(product: Product)` event and prototype-style SPU list with expandable SKU rows.

- [ ] **Step 1: Write the failing test**

```ts
it('shows the approved product overview labels and opens a selected product', async () => {
  const wrapper = mount(ProductList, { props: { service } });
  await flushPromises();
  expect(wrapper.text()).toContain('SKU 主数据');
  await wrapper.get('[data-testid="product-row-8"]').trigger('click');
  expect(wrapper.emitted('select')?.[0]).toEqual([expect.objectContaining({ id: 8 })]);
});
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd frontend && npm run test:run -- src/features/product/ProductFeature.test.ts`

Expected: FAIL because the prototype overview label and `select` event do not exist.

- [ ] **Step 3: Implement the visual structure**

Use the existing prototype's stat-strip density, status badges, filter placement, and row hierarchy. Bind product code, item number, name, brand, SKU count, status, and SKU values from `Product` instead of static prototype records. Keep list actions as icon or terse text controls.

- [ ] **Step 4: Run the focused test**

Run: `cd frontend && npm run test:run -- src/features/product/ProductFeature.test.ts`

Expected: PASS.

### Task 2: Product Detail Drawer and Form Transition

**Files:**
- Modify: `frontend/src/features/product/views/ProductListView.vue`
- Create: `frontend/src/features/product/components/ProductDetailDrawer.vue`
- Modify: `frontend/src/features/product/ProductFeature.test.ts`

**Interfaces:**
- Consumes: `Product` selected by `ProductList`.
- Produces: a right-side detail drawer and transitions to `ProductForm` for create/edit.

- [ ] **Step 1: Write the failing test**

```ts
it('shows selected product details in a right drawer', async () => {
  const wrapper = mount(ProductListView, { global: { provide: { productService: service } } });
  await flushPromises();
  await wrapper.get('[data-testid="product-row-8"]').trigger('click');
  expect(wrapper.get('[data-testid="product-detail-drawer"]').text()).toContain('玻璃杯');
});
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd frontend && npm run test:run -- src/features/product/ProductFeature.test.ts`

Expected: FAIL because no drawer exists.

- [ ] **Step 3: Implement drawer and transitions**

Render main product information, SKU list, packaging values and supplier quote placeholder in the drawer. The drawer's edit action opens the existing form with the selected product; close restores the list without losing filters.

- [ ] **Step 4: Run full verification**

Run: `cd frontend && npm run test:run && VITE_DATA_SOURCE=real npm run build`

Expected: PASS.

## Self-Review

- Scope is limited to matching the approved product prototype rather than redesigning other ERP modules.
- Product fields and API contracts stay in the existing `ProductService` boundary.
- No static demo product values are used as saved business data.
