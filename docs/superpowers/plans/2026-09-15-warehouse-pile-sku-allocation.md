# Warehouse Pile SKU Allocation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let users with `inventory:edit` assign positive whole-unit quantities from a warehouse's unallocated real stock to a selected planned goods pile without changing warehouse total inventory.

**Architecture:** Add a transactional Spring service and warehouse-scoped POST endpoint that validate the saved completed layout, lock the SKU balance and location rows, upsert the pile allocation, reconcile `UNALLOCATED`, and return the refreshed inventory layout. Extend the Vue inventory service and existing right drawer so permission-gated users can submit the allocation and replace the page's inventory snapshot atomically.

**Tech Stack:** Java 21, Spring Boot 3.3, Spring Security, Spring JDBC, MySQL/H2 tests, Vue 3, TypeScript, Vitest, Vue Test Utils.

**Spec:** `docs/superpowers/specs/2026-09-15-warehouse-pile-sku-allocation-design.md`

## Global Constraints

- Allocation changes location only; `inventory_balance.quantity` and `WarehouseInventoryLayout.totalUnits` must remain unchanged.
- The server requires `inventory:edit`; hiding the frontend action is not the security boundary.
- `units` is an individual-unit positive integer and cannot exceed the current derived unallocated quantity.
- The target pile must exist in a saved warehouse layout whose document has `completed: true`.
- Local mock behavior must preserve the same validation and totals as production.
- Do not add capacity blocking, pile-to-pile moves, removal, bulk allocation, or a location movement audit ledger.

---

### Task 1: Transactional pile allocation domain service

**Files:**
- Create: `backend/src/main/java/com/bebefish/erp/inventory/application/WarehousePileAllocationService.java`
- Create: `backend/src/test/java/com/bebefish/erp/inventory/application/WarehousePileAllocationServiceTest.java`
- Use: `backend/src/main/java/com/bebefish/erp/inventory/application/WarehouseInventoryLayoutQueryService.java`
- Use: `backend/src/main/resources/db/migration/V12__warehouse_layout.sql`
- Use: `backend/src/main/resources/db/migration/V15__inventory_location_balance.sql`

**Interfaces:**
- Consumes: saved `warehouse_layout.layout_json`, authoritative `inventory_balance`, and `inventory_location_balance`.
- Produces: `WarehousePileAllocationService.allocate(long warehouseId, Command command): WarehouseInventoryLayoutView` and `Command(String palletId, long skuId, long units)`.

- [ ] **Step 1: Write failing service tests for first allocation and existing allocation increment**

Create an integration-style Spring test with unique warehouse/SKU ids. Insert a completed layout containing `pallet-c018`, an `inventory_balance` of `100`, and an `UNALLOCATED` location row of `100`. Assert:

```java
var result = service.allocate(warehouseId,
        new WarehousePileAllocationService.Command("pallet-c018", skuId, 24));

assertThat(result.totalUnits()).isEqualByComparingTo("100");
assertThat(result.placedUnits()).isEqualByComparingTo("24");
assertThat(result.unallocatedUnits()).isEqualByComparingTo("76");
assertThat(result.allocations()).anySatisfy(item -> {
    assertThat(item.palletId()).isEqualTo("pallet-c018");
    assertThat(item.skuId()).isEqualTo(skuId);
    assertThat(item.units()).isEqualByComparingTo("24");
});
```

Call the service again with `6` and assert the target row is `30`, the unallocated row is `70`, and `inventory_balance` remains `100`.

- [ ] **Step 2: Run the service test and verify RED**

Run: `mvn -Dtest=WarehousePileAllocationServiceTest test`

Expected: compilation fails because `WarehousePileAllocationService` and `Command` do not exist.

- [ ] **Step 3: Add failing validation tests**

Add parameterized assertions that `units` values `0` and `-1` produce `INVALID_PILE_ALLOCATION`, and tests for:

```java
assertThatThrownBy(() -> service.allocate(warehouseId,
        new WarehousePileAllocationService.Command("pallet-c018", skuId, 101)))
    .isInstanceOfSatisfying(BusinessException.class,
        error -> assertThat(error.code()).isEqualTo("INSUFFICIENT_UNALLOCATED_INVENTORY"));
```

Also assert `PLANNING_REQUIRED` for `completed:false`, `PALLET_NOT_FOUND` for an unknown pile, and `SKU_BALANCE_NOT_FOUND` when no positive warehouse/SKU balance exists.

- [ ] **Step 4: Implement the minimal transactional service**

Create:

```java
@Service
public class WarehousePileAllocationService {
    public record Command(String palletId, long skuId, long units) {}

    private final NamedParameterJdbcTemplate jdbc;
    private final WarehouseInventoryLayoutQueryService query;

    @Transactional
    public WarehouseInventoryLayoutView allocate(long warehouseId, Command command) {
        validateCommand(command);
        var target = loadCompletedPile(warehouseId, command.palletId());
        var balance = lockBalance(warehouseId, command.skuId());
        var placed = lockPlacedLocations(warehouseId, command.skuId());
        var available = balance.subtract(placed);
        if (BigDecimal.valueOf(command.units()).compareTo(available) > 0) {
            throw conflict("INSUFFICIENT_UNALLOCATED_INVENTORY", "库存已变化，请重新确认可分配数量");
        }
        upsertTarget(warehouseId, target.zoneId(), command);
        reconcileUnallocated(warehouseId, command.skuId(), available.subtract(BigDecimal.valueOf(command.units())));
        return query.get(warehouseId);
    }
}
```

`loadCompletedPile` must parse `layout_json`, require `completed:true`, find an exact `palletGroups[].id`, and derive the first ordinary zone whose rectangle contains the pile center. A zone is ordinary when `kind` is missing/null. Return `zoneId = null` when no ordinary zone contains the center.

Use `SELECT quantity FROM inventory_balance WHERE warehouse_id=:warehouseId AND sku_id=:skuId AND quantity>0 FOR UPDATE`. Lock all location rows for the same warehouse/SKU with `SELECT pallet_id, quantity ... FOR UPDATE`, sum only rows whose `pallet_id <> 'UNALLOCATED'`, then use MySQL/H2-compatible update-then-insert logic for target and unallocated rows. Increment `version_no` and set `updated_at=CURRENT_TIMESTAMP` on updates.

- [ ] **Step 5: Run the service tests and verify GREEN**

Run: `mvn -Dtest=WarehousePileAllocationServiceTest test`

Expected: all service cases pass; no warehouse balance changes.

- [ ] **Step 6: Add and run a concurrent availability test**

From two executor threads, attempt allocations that together exceed the same SKU balance. Wait for both futures and assert exactly one succeeds, the other has `INSUFFICIENT_UNALLOCATED_INVENTORY`, and the final sum of non-`UNALLOCATED` location rows is not greater than `inventory_balance.quantity`.

Run: `mvn -Dtest=WarehousePileAllocationServiceTest test`

Expected: PASS.

- [ ] **Step 7: Commit Task 1**

```bash
git add backend/src/main/java/com/bebefish/erp/inventory/application/WarehousePileAllocationService.java backend/src/test/java/com/bebefish/erp/inventory/application/WarehousePileAllocationServiceTest.java
git commit -m "feat: allocate warehouse stock to planned piles"
```

---

### Task 2: Secured warehouse pile-allocation endpoint

**Files:**
- Create: `backend/src/main/java/com/bebefish/erp/inventory/api/AllocatePileInventoryRequest.java`
- Modify: `backend/src/main/java/com/bebefish/erp/inventory/api/WarehouseInventoryLayoutController.java`
- Create: `backend/src/test/java/com/bebefish/erp/inventory/api/WarehouseInventoryLayoutControllerTest.java`

**Interfaces:**
- Consumes: `WarehousePileAllocationService.allocate(long, Command)` from Task 1.
- Produces: `POST /api/warehouses/{warehouseId}/inventory-layout/pile-allocations`, request `{ palletId, skuId, units }`, response `ApiResponse<WarehouseInventoryLayoutView>`.

- [ ] **Step 1: Write failing controller authorization and contract tests**

Create a `@SpringBootTest`, `@AutoConfigureMockMvc`, `@ActiveProfiles("test")` test. Seed a completed layout, balance, and unallocated row. Verify a token containing only `inventory:view` receives 403:

```java
mvc.perform(post("/api/warehouses/{id}/inventory-layout/pile-allocations", warehouseId)
        .header("Authorization", bearer(token("inventory:view")))
        .contentType(APPLICATION_JSON)
        .content("""{"palletId":"pallet-c018","skuId":%d,"units":24}""".formatted(skuId)))
    .andExpect(status().isForbidden());
```

Verify `inventory:edit` returns 200 and response paths `$.data.totalUnits=100`, `$.data.placedUnits=24`, and the target allocation contains 24 units. Verify `units:1.5`, `units:0`, blank `palletId`, and missing `skuId` return 400.

- [ ] **Step 2: Run the controller test and verify RED**

Run: `mvn -Dtest=WarehouseInventoryLayoutControllerTest test`

Expected: POST route is not mapped or request type does not exist.

- [ ] **Step 3: Implement request validation and the secured route**

Create:

```java
public record AllocatePileInventoryRequest(
        @NotBlank String palletId,
        @Positive long skuId,
        @Positive long units
) {
    WarehousePileAllocationService.Command toCommand() {
        return new WarehousePileAllocationService.Command(palletId.trim(), skuId, units);
    }
}
```

Add the service dependency to `WarehouseInventoryLayoutController` and add:

```java
@PostMapping("/pile-allocations")
@PreAuthorize("hasAuthority('inventory:edit')")
public ApiResponse<WarehouseInventoryLayoutView> allocate(
        @PathVariable long warehouseId,
        @Valid @RequestBody AllocatePileInventoryRequest request
) {
    return ApiResponse.success(allocationService.allocate(warehouseId, request.toCommand()));
}
```

- [ ] **Step 4: Run inventory backend tests and verify GREEN**

Run: `mvn -Dtest=WarehousePileAllocationServiceTest,WarehouseInventoryLayoutControllerTest,WarehouseInventoryLayoutAssemblerTest test`

Expected: all selected backend tests pass.

- [ ] **Step 5: Commit Task 2**

```bash
git add backend/src/main/java/com/bebefish/erp/inventory/api/AllocatePileInventoryRequest.java backend/src/main/java/com/bebefish/erp/inventory/api/WarehouseInventoryLayoutController.java backend/src/test/java/com/bebefish/erp/inventory/api/WarehouseInventoryLayoutControllerTest.java
git commit -m "feat: expose secured pile inventory allocation"
```

---

### Task 3: Frontend inventory allocation service contract

**Files:**
- Modify: `frontend/src/features/inventory/warehouseCanvas/warehouseInventoryService.ts`
- Modify: `frontend/src/features/inventory/warehouseCanvas/warehouseInventoryService.test.ts`

**Interfaces:**
- Consumes: Task 2 POST endpoint.
- Produces: `WarehousePileAllocationInput` and `WarehouseInventoryService.allocateToPile(warehouseId, input): Promise<WarehouseInventoryLayout>`.

- [ ] **Step 1: Write failing HTTP contract test**

Add:

```ts
it('allocates unassigned units to a pile through the warehouse endpoint', async () => {
  const layout = { warehouseId: 8, totalUnits: 100, skuCount: 1, placedUnits: 24, unallocatedUnits: 76, updatedAt: null, allocations: [] }
  request.mockResolvedValue(layout)

  await httpWarehouseInventoryService.allocateToPile(8, { palletId: 'pallet-c018', skuId: 101, units: 24 })

  expect(request).toHaveBeenCalledWith(
    '/api/warehouses/8/inventory-layout/pile-allocations',
    { method: 'POST', body: JSON.stringify({ palletId: 'pallet-c018', skuId: 101, units: 24 }) },
  )
})
```

- [ ] **Step 2: Run the service test and verify RED**

Run: `npm run test:run -- src/features/inventory/warehouseCanvas/warehouseInventoryService.test.ts`

Expected: FAIL because `allocateToPile` does not exist.

- [ ] **Step 3: Add a failing stateful mock test**

Create one mock service instance, load warehouse 8, allocate 24 units of SKU 104 to `pallet-a03`, and load again. Assert total remains `990`, placed increases by 24, unallocated decreases by 24, and `pallet-a03` contains SKU 104 with 24 units. Then assert zero, fractional, and over-available inputs reject without mutation.

- [ ] **Step 4: Implement production and local mock contracts**

Add:

```ts
export type WarehousePileAllocationInput = {
  palletId: string
  skuId: number
  units: number
}

export type WarehouseInventoryService = {
  load: (warehouseId: number) => Promise<WarehouseInventoryLayout>
  allocateToPile: (warehouseId: number, input: WarehousePileAllocationInput) => Promise<WarehouseInventoryLayout>
}
```

The HTTP implementation sends the POST request exactly as asserted. The mock stores layouts in a `Map<number, WarehouseInventoryLayout>`, clones results on read/write, validates a positive integer and available `UNALLOCATED` units, decrements/removes the unallocated allocation, increments/creates the target allocation, and recomputes `placedUnits`, `unallocatedUnits`, `skuCount`, and `updatedAt` without changing `totalUnits`.

- [ ] **Step 5: Run the service test and verify GREEN**

Run: `npm run test:run -- src/features/inventory/warehouseCanvas/warehouseInventoryService.test.ts`

Expected: PASS.

- [ ] **Step 6: Commit Task 3**

```bash
git add frontend/src/features/inventory/warehouseCanvas/warehouseInventoryService.ts frontend/src/features/inventory/warehouseCanvas/warehouseInventoryService.test.ts
git commit -m "feat: add pile allocation inventory client"
```

---

### Task 4: Permission-gated add-SKU form in the right drawer

**Files:**
- Modify: `frontend/src/features/inventory/warehouseCanvas/components/WarehouseInventoryDrawer.vue`
- Modify: `frontend/src/features/inventory/warehouseCanvas/components/WarehouseInventoryDrawer.test.ts`

**Interfaces:**
- Consumes: `inventory`, selected `pallet`, and `canEdit` from the page.
- Produces: `allocate` event payload `{ palletId: string; skuId: number; units: number }`; props `canEdit: boolean`, `saving: boolean`, and `allocationError: string`.

- [ ] **Step 1: Write failing permission visibility tests**

Mount a selected pile twice. With `canEdit:false`, assert `[data-testid="inventory-add-sku"]` does not exist. With `canEdit:true`, assert it exists. With a selected zone instead of a pile, assert it does not exist even when editable.

- [ ] **Step 2: Run drawer tests and verify RED**

Run: `npm run test:run -- src/features/inventory/warehouseCanvas/components/WarehouseInventoryDrawer.test.ts`

Expected: FAIL because the props and add action do not exist.

- [ ] **Step 3: Write failing form behavior tests**

For editable selected `pallet-a03`, click `[data-testid="inventory-add-sku"]`. Assert the select lists SKU 104 from `UNALLOCATED`, the available copy is `可分配 72 个`, and packaging is read-only. Set quantity to 24 and submit; assert:

```ts
expect(wrapper.emitted('allocate')?.[0]).toEqual([{
  palletId: 'pallet-a03', skuId: 104, units: 24,
}])
```

Assert quantity values `0`, `-1`, `1.5`, `73`, and empty disable submission and show the whole-unit/maximum validation message. Assert `saving:true` disables all inputs and the submit button. Assert `allocationError` remains visible without closing the form. Assert no unallocated rows disables the action with `暂无待分配库存`.

- [ ] **Step 4: Implement the inline drawer form**

Add the new props/events and compute options from:

```ts
const unallocated = computed(() => props.inventory?.allocations
  .filter(item => item.palletId === 'UNALLOCATED' && item.units > 0) ?? [])
```

Render `添加 SKU` only for `pallet && canEdit`. The inline form stays within `.inventory-drawer`, uses individual units, shows `unitsPerCase` as read-only information, and emits only when `Number.isInteger(units) && units > 0 && units <= selectedAvailable`.

- [ ] **Step 5: Run drawer tests and verify GREEN**

Run: `npm run test:run -- src/features/inventory/warehouseCanvas/components/WarehouseInventoryDrawer.test.ts`

Expected: PASS.

- [ ] **Step 6: Commit Task 4**

```bash
git add frontend/src/features/inventory/warehouseCanvas/components/WarehouseInventoryDrawer.vue frontend/src/features/inventory/warehouseCanvas/components/WarehouseInventoryDrawer.test.ts
git commit -m "feat: add SKU allocation form to pile drawer"
```

---

### Task 5: Warehouse page permission and live inventory synchronization

**Files:**
- Modify: `frontend/src/features/inventory/views/WarehouseCanvasView.vue`
- Modify: `frontend/src/features/inventory/WarehouseCanvasView.test.ts`

**Interfaces:**
- Consumes: `currentUser`, `WarehouseInventoryService.allocateToPile`, and the drawer `allocate` event from Tasks 3–4.
- Produces: permission-gated drawer props, allocation orchestration, refreshed `actualInventory`, and user feedback.

- [ ] **Step 1: Write failing page permission test**

Set `currentUser.value` to a session with `inventory:view` only, mount a completed warehouse, select `pallet-c018`, and assert the add action is absent. Repeat with `['inventory:view', 'inventory:edit']` and assert the action is visible. Restore the previous global session in `afterEach`.

- [ ] **Step 2: Write failing successful allocation integration test**

Mount the completed warehouse with a saved layout fixture containing both `pallet-c018` and the empty `pallet-a03`. Provide a service whose `load` returns the initial inventory layout and whose `allocateToPile` resolves to an updated inventory layout where `pallet-a03` contains 24 units of SKU 104. Select `pallet-a03`, open the form, submit 24, and assert:

```ts
expect(allocateToPile).toHaveBeenCalledWith(8, {
  palletId: 'pallet-a03', skuId: 104, units: 24,
})
expect(wrapper.get('[data-testid="inventory-pile-pallet-a03"]').text()).toContain('1种 SKU · 24个')
expect(wrapper.get('[data-testid="inventory-detail-title"]').text()).toContain('A03')
expect(wrapper.get('[data-testid="inventory-detail-sku"]').text()).toContain('24 个')
```

- [ ] **Step 3: Run the page test and verify RED**

Run: `npm run test:run -- src/features/inventory/WarehouseCanvasView.test.ts`

Expected: FAIL because permission and allocation orchestration are missing.

- [ ] **Step 4: Implement page orchestration**

Import `currentUser` and add:

```ts
const canEditInventory = computed(() => currentUser.value?.permissions.includes('inventory:edit') ?? false)
const inventoryAllocationSaving = ref(false)
const inventoryAllocationError = ref('')

async function allocateInventoryToPile(input: WarehousePileAllocationInput) {
  if (!selectedWarehouseId.value || !canEditInventory.value || inventoryAllocationSaving.value) return
  inventoryAllocationSaving.value = true
  inventoryAllocationError.value = ''
  try {
    actualInventory.value = await inventoryReader.allocateToPile(selectedWarehouseId.value, input)
    notice.value = `已向货物堆分配 ${input.units} 个库存`
  } catch (cause) {
    inventoryAllocationError.value = cause instanceof Error ? cause.message : 'SKU 分配失败'
    if (inventoryAllocationError.value.includes('库存已变化')) await loadActualInventory()
  } finally {
    inventoryAllocationSaving.value = false
  }
}
```

Pass `:can-edit="canEditInventory"`, `:saving="inventoryAllocationSaving"`, and `:allocation-error="inventoryAllocationError"` to the drawer and handle `@allocate="allocateInventoryToPile"`. Clear allocation error when the drawer closes or a different pile/warehouse is selected.

- [ ] **Step 5: Run focused frontend tests and verify GREEN**

Run:

```bash
npm run test:run -- src/features/inventory/warehouseCanvas/warehouseInventoryService.test.ts src/features/inventory/warehouseCanvas/components/WarehouseInventoryDrawer.test.ts src/features/inventory/warehouseCanvas/components/WarehouseInventorySidebar.test.ts src/features/inventory/WarehouseCanvasView.test.ts
```

Expected: all focused frontend tests pass.

- [ ] **Step 6: Run full backend, frontend, and build verification**

Run from `backend`:

```bash
mvn test
```

Run from `frontend`:

```bash
npm run test:run
npm run build
```

Expected: zero failing backend tests, zero failing frontend tests, and a successful type-check/Vite production build.

- [ ] **Step 7: Perform browser acceptance**

With local mock data and an authenticated user containing `inventory:edit`, open a completed warehouse, select an empty pile, allocate 24 units, and verify the right drawer and left pile card both show the new SKU/quantity. Repeat with a view-only session and verify the add action is absent. Check the browser console for errors.

- [ ] **Step 8: Commit Task 5**

```bash
git add frontend/src/features/inventory/views/WarehouseCanvasView.vue frontend/src/features/inventory/WarehouseCanvasView.test.ts
git commit -m "feat: connect pile allocation to warehouse details"
```
