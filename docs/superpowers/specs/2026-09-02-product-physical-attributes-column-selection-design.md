# SKU Physical Attributes And Product Column Selection Design

Date: 2026-09-02

## Context

The product catalog currently exposes packaging dimensions and weights at SKU level, but it has no distinct product dimensions or capacity. The catalog table also has a fixed seven-column layout. Users need to record product length, width, height, and capacity in the packaging workflow and choose additional catalog fields without reducing the table below its current information density.

## Goals

- Persist product length, width, height, and capacity for each SKU.
- Maintain those values in both unified and per-SKU packaging modes.
- Require product dimensions for completeness while keeping capacity optional.
- Show the new values in product details.
- Let users choose catalog columns while keeping at least seven visible columns including the fixed action column.
- Keep existing products and API clients compatible with the migration.

## Non-Goals

- No product-level duplicate of the SKU physical attributes.
- No automatic copy from outer-carton or inner-package dimensions.
- No server-side user preference storage or cross-device synchronization.
- No drag-and-drop column ordering.
- No mobile-specific catalog or editor layout work.

## Chosen Architecture

Extend the existing SKU packaging model. A separate physical-attributes table would add joins and lifecycle rules without a current reuse case, while JSON storage would weaken validation and queryability. The existing `Packaging`, `PackagingCommand`, SKU request/response, and persistence mapping already own related physical data and are the narrowest consistent boundary.

## Data Model

Add nullable columns to `product_sku` in Flyway migration `V9`:

| Column | SQL type | Unit | Constraint |
| --- | --- | --- | --- |
| `product_length_cm` | `decimal(12,3)` | cm | null or >= 0 |
| `product_width_cm` | `decimal(12,3)` | cm | null or >= 0 |
| `product_height_cm` | `decimal(12,3)` | cm | null or >= 0 |
| `capacity_ml` | `decimal(12,3)` | ml | null or >= 0 |

Existing rows remain null. No backfill derives product dimensions from packaging dimensions.

## Backend Contract

Extend these existing boundaries with `productLengthCm`, `productWidthCm`, `productHeightCm`, and `capacityMl`:

- SKU save request and Jakarta validation
- `PackagingCommand`
- `Packaging`
- SKU persistence insert, update, and result mapping
- SKU API response
- test fixtures and repository/controller contract tests

Validation uses at most nine integer digits and three fractional digits, matching other dimension and weight inputs. Negative values return normal validation errors.

## Completeness

Every enabled SKU must have all three product dimension values to satisfy packaging completeness. Missing any of length, width, or height adds `产品尺寸` to the product's missing groups. Capacity remains optional and never affects completeness.

Disabled SKUs continue to be ignored by completeness calculation. Existing products will become incomplete until their enabled SKUs receive product dimensions; this is an accepted migration effect.

## Editor Experience

The `包装与重量` step gains a `产品尺寸与容量` group:

- Product dimensions render as `长 [value cm] × 宽 [value cm] × 高 [value cm]`.
- Capacity renders as a numeric input with an `ml` suffix.
- Values participate in `全部 SKU 统一` and `按 SKU 单独维护` exactly like other packaging fields.
- The per-SKU packaging dialog exposes the same fields.
- Fields accept null or nonnegative decimals with up to three fractional digits.

The fixed desktop stage and dialog geometry remain unchanged. Internal content may scroll when required; controls must not overlap the sticky dialog footer.

## Detail Experience

The packaging section displays product dimensions and capacity alongside carton, inner-package, and weight information. Unified packaging mode shows one shared value. Per-SKU mode shows each SKU's values. Missing values render as `--`.

## Catalog Column Selection

Add a `显示字段` button to the product filter bar. It opens an accessible anchored panel containing checkboxes, a selected-column count, and `恢复默认`.

### Default Columns

1. 商品信息
2. 分类
3. 品牌 / 供应商
4. 库存
5. 价格
6. 资料状态
7. 操作

The operation column is fixed and is not configurable. At least six configurable business columns must remain selected, preserving at least seven total visible columns. When the lower bound is reached, selected checkboxes that would violate it are disabled and the panel explains the constraint.

### Optional Columns

- 商品编码
- 商品类型
- 安全库存
- SKU 数量
- 资料完整度
- 启停状态
- 更新时间
- 备注
- 产品尺寸
- 容量
- 箱规体积
- 装箱数量
- 内盒包装
- 毛重
- 净重
- 克重

SKU-derived optional columns use the enabled default SKU. If no enabled default exists, they use the first enabled SKU, then the first SKU as a final fallback. Missing values render as `--`. Dimensions render with `×` and units; numeric physical values always include their units.

Column order follows one canonical order regardless of selection sequence. Wider selections increase the table's minimum width and use the existing horizontal scroll container rather than compressing text.

## Preference Persistence

Persist selected column identifiers in browser `localStorage` under a versioned key. On load:

1. Parse only known column identifiers.
2. Add the fixed action column implicitly.
3. Reject malformed or below-minimum preferences and restore defaults.
4. `恢复默认` removes the stored preference and restores the seven default columns.

This preference is UI-only and does not alter backend product data.

## Accessibility And Interaction

- `显示字段` reports expanded state and controls the panel.
- The panel is keyboard reachable, supports Escape dismissal, and closes on outside click.
- Checkboxes have explicit field labels and disabled-state explanations.
- Table headers and cells are generated from the same active-column definition to prevent misalignment.
- Loading and empty rows use the active column count for `colspan`.

## Error Handling

- Invalid physical values are blocked in frontend validation and validated again by the backend.
- A failed product save retains the entered values and shows the existing save error feedback.
- Invalid stored column preferences silently restore defaults; product loading is unaffected.
- Missing SKU-derived values never fail catalog rendering.

## Testing

### Backend

- Flyway migration adds four nullable constrained columns.
- Repository round-trip covers all four values and null legacy rows.
- Controller create/update/get round-trips the fields.
- Negative and over-precision values are rejected.
- Completeness requires all three dimensions for enabled SKUs and ignores capacity.

### Frontend

- Form mapper and editor state preserve the four fields.
- Unified mode copies values to all SKUs; per-SKU mode preserves differences.
- Editor validation covers negative and over-precision input.
- Product detail renders values and units.
- Column selector defaults to seven columns, enforces the minimum, restores defaults, and recovers from invalid storage.
- SKU-derived columns follow the default-SKU fallback rule.
- Headers, rows, loading, and empty states stay aligned for different column selections.

### Visual Verification

- Desktop editor and per-SKU packaging dialog at 1440 x 1024.
- Product catalog at 1440 x 1024 with default and expanded column selections.
- Confirm no clipping, overlapping controls, or unreadable horizontal compression.

## Delivery Sequence

1. Add migration and backend domain/API/persistence support.
2. Add completeness behavior.
3. Extend frontend types, mappers, editor state, and validation.
4. Add editor and detail UI.
5. Add catalog column selection and preference persistence.
6. Run backend tests, frontend tests, production build, migration verification, and browser visual checks.
