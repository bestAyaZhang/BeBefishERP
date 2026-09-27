# Warehouse pile SKU allocation design

**Date:** 2026-09-15
**Status:** Approved direction — transactional pile allocation (Approach A)

## Goal

Allow a user with `inventory:edit` permission to assign real, currently unallocated warehouse stock to a selected planned goods pile from the warehouse detail drawer. The operation changes only the stock location. It must not increase or decrease the warehouse's authoritative inventory balance.

## Product rules

1. The feature is available only in the completed warehouse layout detail view and only when a goods pile is selected.
2. Users with `inventory:view` can inspect pile stock but cannot see or invoke the add-SKU action.
3. Users with `inventory:edit` can assign a positive whole number of units from the warehouse's unallocated stock to the selected pile.
4. A SKU is selectable only when its unallocated quantity is greater than zero.
5. The assigned quantity cannot exceed the current unallocated quantity. Warehouse total stock remains unchanged.
6. Adding a SKU already present in the target pile increases that pile/SKU allocation. Adding to an empty pile creates its first allocation.
7. Capacity percentage remains informational and does not block allocation until a reliable capacity model exists.

## Chosen approach

Add a dedicated transactional pile-allocation endpoint to the existing warehouse inventory-layout API. The server is the authority for permission, layout validity, pile existence, current stock availability, and concurrent writes. The response is the refreshed `WarehouseInventoryLayoutView`, allowing the frontend to update the left pile list and right detail drawer atomically.

Alternatives rejected:

- Reusing stock adjustments would incorrectly model a location change as a warehouse quantity change.
- Frontend-only allocation would be lost after refresh and cannot protect against concurrent over-allocation.

## API contract

### Read

The existing endpoint remains unchanged:

`GET /api/warehouses/{warehouseId}/inventory-layout`

### Allocate SKU to pile

`POST /api/warehouses/{warehouseId}/inventory-layout/pile-allocations`

Required authority: `inventory:edit`

Request:

```json
{
  "palletId": "pallet-c018",
  "skuId": 101,
  "units": 24
}
```

Validation:

- `warehouseId` references an accessible warehouse.
- The saved warehouse layout exists, has `completed: true`, and contains `palletId` in `palletGroups`.
- `skuId` has a positive authoritative balance in the warehouse.
- `units` is a positive integer and does not exceed the current unallocated units for the SKU.

Success returns the same shape as the read endpoint: the refreshed `WarehouseInventoryLayoutView`.

Errors:

- `400 INVALID_PILE_ALLOCATION` for missing identifiers, fractional units, or non-positive units.
- `404 WAREHOUSE_NOT_FOUND`, `LAYOUT_NOT_FOUND`, `PALLET_NOT_FOUND`, or `SKU_BALANCE_NOT_FOUND` when a referenced resource does not exist.
- `409 PLANNING_REQUIRED` when the warehouse layout is not completed.
- `409 INSUFFICIENT_UNALLOCATED_INVENTORY` when another operation has consumed the available units or the request exceeds availability.
- `403` is produced by Spring Security when `inventory:edit` is absent.

## Backend design

Add an allocation command/service beside the existing inventory-layout query service. It performs the following work in one database transaction:

1. Read and validate the saved completed layout. Locate the target pile in `palletGroups`.
2. Derive the pile's ordinary zone from the saved geometry by testing the pile center against zones without a special `kind`. Store `zone_id = null` when the pile is outside an ordinary zone.
3. Lock the warehouse/SKU row in `inventory_balance` with `FOR UPDATE`.
4. Lock the warehouse/SKU rows in `inventory_location_balance`, excluding the synthetic `UNALLOCATED` location when calculating placed stock.
5. Calculate `available = authoritative balance - placed stock` and reject the request when `units > available`.
6. Insert or increment the `(warehouse_id, pallet_id, sku_id)` target allocation and increment its version.
7. Reconcile the `UNALLOCATED` row to the remaining available quantity so the location-balance table preserves the same total as `inventory_balance`.
8. Re-run the existing inventory-layout query and return the refreshed view.

The transaction and row locks prevent two concurrent requests from allocating the same remaining stock. The existing assembler continues to derive unallocated inventory from authoritative balance minus placed allocations, so the read contract stays backward compatible.

No schema migration is required because `inventory_location_balance` already has the required unique key and version field.

## Frontend design

### Permission boundary

`WarehouseCanvasView` derives `canEditInventory` from `currentUser.permissions.includes('inventory:edit')` and passes it to `WarehouseInventoryDrawer`. The hidden UI is only a convenience; the server remains the security boundary.

### Drawer interaction

When a pile is selected:

- An editable user sees a primary `添加 SKU` action in the right drawer.
- A view-only user sees the existing read-only drawer without the action.
- Selecting `添加 SKU` reveals a compact form in the same drawer instead of opening a second modal.
- The SKU selector uses only `UNALLOCATED` allocations from the current inventory-layout response.
- The form shows SKU name/code, packaging conversion, and `可分配 N 个`.
- Quantity is entered in individual units, must be a positive integer, and is capped at the current available quantity.
- If nothing is unallocated, the action is disabled with `暂无待分配库存`.

On submit, the drawer enters a busy state and prevents duplicate submission. A successful response replaces `actualInventory` in `WarehouseCanvasView`, which immediately refreshes:

- the left pile SKU count and actual quantity;
- the selected pile's right-drawer SKU list and summary;
- all unallocated quantities.

The form closes after success and a brief success message is shown. On failure, the form remains open and displays the server message. For an availability conflict, the page reloads inventory data before asking the user to confirm a new quantity.

## Service boundary

Extend the frontend `WarehouseInventoryService` with:

```ts
allocateToPile(
  warehouseId: number,
  input: { palletId: string; skuId: number; units: number },
): Promise<WarehouseInventoryLayout>
```

The production implementation calls the new POST endpoint. The local mock keeps an in-memory allocation state and applies the same invariants so development behavior matches production semantics.

## Testing

### Backend

- Controller authorization: `inventory:view` alone receives 403; `inventory:edit` succeeds.
- Reject fractional, zero, negative, and over-available quantities.
- Reject missing/uncompleted layouts and unknown piles.
- Allocate the first SKU into an empty pile.
- Increment an existing pile/SKU allocation.
- Preserve authoritative warehouse inventory totals and reconcile unallocated stock.
- Simulate competing allocation attempts and verify total placed stock never exceeds the warehouse balance.
- Verify ordinary zone derivation and the `null` fallback.

### Frontend

- Hide the add action without `inventory:edit`.
- Show it for editable users only when a pile is selected.
- List only SKUs with unallocated stock.
- Validate whole-unit quantity and the available maximum before submission.
- Disable duplicate submission while saving.
- Replace the inventory layout after success so both sidebar and drawer update.
- Preserve the form and show an actionable message on server failure.
- Cover the mock and HTTP service contracts.

## Out of scope

- Increasing or decreasing warehouse inventory.
- Moving stock directly between two piles, removing stock from a pile, or bulk allocation.
- Capacity-based hard limits.
- A separate location-movement audit ledger. This can be added later without changing the allocation contract.
