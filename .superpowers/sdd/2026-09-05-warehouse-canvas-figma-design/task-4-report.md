# Task 4 Report — Product Placement, Editing, And Partial Movement

Date: 2026-09-05
Figma file: `jIz9HNkSoXH63gvTc3yOtj`
Page: `05 Warehouse Canvas MVP` (`1154:38239`)
Section: `02 Product Placement` (`1154:38242`)

## Result

Task 4 created exactly three nonoverlapping `1440×1024` roots in the Product Placement section and no other top-level children:

| Root | ID | Local position | Size |
| --- | --- | ---: | ---: |
| `Warehouse Canvas / Add SKU` | `1193:39676` | `(80, 128)` | `1440×1024` |
| `Warehouse Canvas / SKU Selected` | `1193:40024` | `(1680, 128)` | `1440×1024` |
| `Warehouse Canvas / Partial Move` | `1193:40372` | `(3280, 128)` | `1440×1024` |

All three roots clone the validated Task 3 Overview shell and demo data. No Task 1–3 frame, section, component, component set, variable, or style was modified.

## Add SKU

- Valid B-01 target: `Drop Target / B-01 Valid` (`1193:40747`), with selected live Area instance `1193:39704` and explicit copy `有效目标 B-01 · 松开放置`.
- Real dragged SKU Block: `1193:40750`, linked to `Warehouse/SKU Block` (`1168:38293`) in `State=Selected`.
- Dragged SKU data: `SKU-FISH-500ML-蓝`, `250 个`, `24 个/件`, derived `10 件 + 10 个`, target `B-01`.
- Native live drawer: `Drawer / Add SKU Quantity` (`1193:40767`), linked to `Overlay/Drawer` (`67:459`).
- Real linked controls in the drawer: SKU select `I1193:40767;67:466;1193:40787`, disabled package field `I1193:40767;67:466;1193:40795`, editable units number field `I1193:40767;67:466;1193:40803`, and disabled derived-copy field `I1193:40767;67:466;1193:40811`.
- Actions: secondary cancel `I1193:40767;67:467;1193:40822`; enabled primary `放入 B-01` `I1193:40767;67:467;1193:40832`.
- Calculation: `floor(250 / 24) = 10`, `250 % 24 = 10`, therefore `10 件 + 10 个`. Package and derived values are read-only; units remain authoritative.

## Selected SKU And Whole Move

- Selected live SKU Block: `SKU Block / BL-A Selected` (`1193:40054`), linked to `Warehouse/SKU Block` in `State=Selected`; its component resize handles are visible.
- Live SKU Object Properties instance: `1194:1883`, linked to `Warehouse/Object Properties` (`1171:3600`), `Context=SKU`.
- The inspector retains linked SKU select, units number field, disabled derived field, x/y/width/height fields, secondary whole-move action, primary partial-move action, and danger delete action. No nested instance was detached.
- Explicit invariant card: `Rule / Visual Size Independent` (`1195:1991`) with exact copy `调整大小只改变画布占地，不改变库存`.
- Whole-move after-preview block: `1195:1994`, a real default SKU Block in `B-01`.
- Whole-move ledger: before `A-01 / 150 个 / 6 件 + 6 个`; after `B-01 / 150 个 / 6 件 + 6 个`. Only `areaId` changes.
- The selected and partial-move roots each contain only their intended source block in `State=Selected`; the cloned C-01 search match was returned to `State=Default`.

## Partial Move And Conservation

- Post-split source block: `1193:40402`, a real selected SKU Block with `A-01 / 190 个 / 7 件 + 22 个`.
- New target block: `1196:2003`, a real default SKU Block with `B-01 / 60 个 / 2 件 + 12 个`.
- Native live drawer: `Drawer / Partial Move` (`1196:40438`), linked to `Overlay/Drawer`.
- Real target select: `I1196:40438;67:466;1196:40460`, filled `B-01`.
- Real move-quantity field: `I1196:40438;67:466;1196:40468`, filled `60`, with helper `正整数且小于 250`.
- Before/after preview: `I1196:40438;67:466;1196:40491`.
- Exact visible ledger:

```text
Source block: 深海矿物水 500ml 蓝 · A-01 / 250 个
Move: 60 个
Source after move: 深海矿物水 500ml 蓝 · A-01 / 190 个
New target block: 深海矿物水 500ml 蓝 · B-01 / 60 个
190 + 60 = 250 个
```

- Arithmetic: `250 - 60 = 190`; `190 + 60 = 250`; conservation passed.
- Valid-state final action: `I1196:40438;67:467;1196:40489`, live primary/default button labeled `确认移动 60 个`.
- Compact invalid-state panel: `I1196:40438;67:466;1196:40503`.
- Invalid examples are explicit: `0`, `-10`, `1.5`, and `≥ 250`.
- Invalid-state final action: `I1196:40438;67:466;1196:40525`, live primary/disabled button labeled `确认移动`.
- The visible rule states that only a positive integer smaller than source inventory enables confirmation.

## Before / After Ledger

| Item | Before Task 4 | After Task 4 | Delta |
| --- | ---: | ---: | ---: |
| Children in `02 Product Placement` | 0 | 3 | +3 required roots |
| `1440×1024` Task 4 roots | 0 | 3 | +3 |
| Component sets in file | 40 | 40 | 0 |
| Components on MVP page | 0 | 0 | 0 |
| Variable collections | 4 | 4 | 0 |
| Variables | 33 | 33 | 0 |
| Text styles | 8 | 8 | 0 |
| Paint / effect / grid styles | 0 / 0 / 0 | 0 / 0 / 0 | 0 / 0 / 0 |

## Verification Evidence

### Automated audits

- Product Placement child count is exactly 3; names, positions, sizes, and parent ID all match the brief.
- Pairwise root overlap is false for all three pairs.
- All required Add SKU, resize, whole-move, partial-ledger, conservation, and invalid-state strings were found as exact visible text values.
- The three roots contain 209 live instances, including 31 Warehouse instances, 16 real field/select/text controls, and 8 real Button/Command instances.
- Missing component masters: 0. No instance was detached.
- Valid confirmation is `State=Default`; invalid confirmation is `State=Disabled`.
- Rendered font families are exactly `Noto Sans SC` and `Inter`; font offenders: 0; `figma.hasMissingFont=false`.
- Excluded positive concepts found: 0 for 3D, stacking, racks/shelves, utilization, load bearing, CAD, outbound allocation, stocktake, batches, or expiry. Existing cloned copy says only that layout does not calculate capacity.
- Local counts remain 4 collections, 33 variables, 8 text styles, and zero paint/effect/grid styles; the MVP page still contains zero components/component sets.

### Screenshots inspected

| Evidence | Node | Render / natural size | Result |
| --- | --- | --- | --- |
| Add SKU full | `1193:39676` | `1440×1024` / `1440×1024` | Passed target emphasis, dragged real SKU, units/package fields, and visible actions |
| B-01 drop close | `1193:40747` | `340×400` / `340×400` | Passed valid-target border, instruction, card identity, and selected handles |
| Add SKU drawer close | `1193:40767` | `760×788` / `760×788` including effects | Passed field hierarchy, read-only styling, calculation, and enabled primary action |
| SKU Selected full | `1193:40024` | `1440×1024` / `1440×1024` | Passed selected handles, invariant card, whole-move preview, and inspector containment |
| SKU inspector close | `1194:1883` | `360×856` / `360×856` | Passed live controls, values, action hierarchy, and no overflow |
| Partial Move full | `1193:40372` | `1440×1024` / `1440×1024` | Passed real source/target blocks, drawer, exact math, validation examples, and action states |
| Partial Move drawer close | `1196:40438` | `760×788` / `760×788` including effects | Passed exact ledger, compact invalid states, disabled and enabled actions, and no clipping |

Transient local renders used for inspection are under `C:\Users\张振亚\AppData\Local\Temp\warehouse-task4-*.png`; they are QA evidence only and are not repository artifacts.

## Plan Tracking And Commit

- Checked only Task 4 Steps 1–8.
- Added one Task 4 execution-note paragraph.
- Left Task 5 unchanged.
- Commit message: `docs: track warehouse canvas Figma task 4`.
- Commit SHA is returned in the Task 4 handoff; it is not embedded here because the SHA is content-addressed from this report itself.

## Self-review

- Scope is exactly the three Task 4 roots; no extra top-level frame was added.
- Units are authoritative everywhere; package conversion fields are disabled/read-only.
- Add SKU proves a real linked SKU moves into a valid B-01 target and then receives `250` units.
- Selected state proves visual resizing and inventory are independent.
- Whole movement preserves `150 个` and `6 件 + 6 个` while changing only `A-01` to `B-01`.
- Partial movement proves `250 - 60 = 190` and `190 + 60 = 250` with real post-split blocks.
- Invalid zero, negative, decimal, and at-least-source values are explicit and paired with a real disabled final action.
- No detached instances, missing masters, missing fonts, new masters, new variables, new styles, or prohibited feature concepts were introduced.

## Concerns

None blocking. The first connector call failed at transport before execution and caused no mutation. One attempted native-drawer internal resize fix failed atomically because nested instance transforms cannot be overridden; the drawer was restored to its native `720×760` geometry, and final screenshots show all content and actions intact. A first custom-text pass resolved the wrong fallback style; it was corrected to the exact existing `Typography/Body Strong` and `Typography/Caption` styles before final screenshots and audits.

## Fix Round 1 — Canonical SKU Identity

The Important review finding is resolved. Every Task 4 representation of `SKU-FISH-500ML-蓝` now uses the existing authoritative product name `深海矿物水 500ml 蓝`; the obsolete Task 4 name `蓝莓气泡饮 500ml` has been removed.

### Mutated nodes

- Dragged SKU Block `1193:40750`: product name changed only; code `SKU-FISH-500ML-蓝`, `250 个`, `10 件 + 10 个`, target `B-01`, `State=Selected`, and the `Warehouse/SKU Block` link are unchanged.
- Add identity select `I1193:40767;67:466;1193:40787`: value is now `SKU-FISH-500ML-蓝 · 深海矿物水 500ml 蓝`.
- Add drawer `1193:40767`: description now exposes the full canonical code/name pair and target B-01; root geometry remains `720×760` at `(720,264)`.
- Partial source `1193:40402`: product name changed only; `A-01 / 190 个 / 7 件 + 22 个`, `State=Selected`, and its live component link are unchanged.
- Partial target `1196:2003`: product name changed only; `B-01 / 60 个 / 2 件 + 12 个`, `State=Default`, and its live component link are unchanged.
- Partial drawer `1196:40438`: description now exposes the full canonical code/name pair and source A-01; root geometry remains `720×760` at `(720,264)`.
- Partial ledger lines `I1196:40438;67:466;1196:40493`, `I1196:40438;67:466;1196:40497`, and `I1196:40438;67:466;1196:40499` now include `深海矿物水 500ml 蓝` while preserving `250`, `190`, `60`, A-01, and B-01.

### Fresh scan and invariant evidence

- `蓝莓气泡饮` visible-text occurrences across all three Task 4 roots: 0.
- `蓝莓气泡饮` instance-property occurrences across all three Task 4 roots: 0.
- `Warehouse/SKU Block` instances carrying `SKU-FISH-500ML-蓝`: 9; canonical-name matches: 9/9.
- Canonical visible identity surfaces include the Add drawer description, Add identity select, and Partial drawer description.
- Partial ledger remains `250 - 60 = 190` and `190 + 60 = 250 个`.
- Derived copies remain correct: `250 / 24 = 10 件 + 10 个`; `190 / 24 = 7 件 + 22 个`; `60 / 24 = 2 件 + 12 个`.
- Three root IDs, names, parents, positions, and `1440×1024` sizes are unchanged.
- Live instances: 209; missing component masters: 0.
- Fonts remain exactly `Noto Sans SC` and `Inter`; offenders: 0; `figma.hasMissingFont=false`.

### Fresh screenshots inspected

| Evidence | Node | Render / natural size | Result |
| --- | --- | --- | --- |
| Add SKU full, fix round 1 | `1193:39676` | `1440×1024` / `1440×1024` | Canonical name visible in dragged block and drawer header; no geometry or quantity regression |
| Add drawer close, fix round 1 | `1193:40767` | `760×788` / `760×788` including effects | Full code/name pair visible in header; field, package, derived copy, and actions remain intact |
| SKU Selected full, fix round 1 | `1193:40024` | `1440×1024` / `1440×1024` | Existing canonical selected/whole-move identities remain unchanged and readable |
| SKU inspector close, fix round 1 | `1194:1883` | `360×856` / `360×856` | Canonical helper, units, package copy, geometry fields, and actions remain intact |
| Partial Move full, fix round 1 | `1193:40372` | `1440×1024` / `1440×1024` | Canonical source/target identities and ledger fit without clipping |
| Partial drawer close, fix round 1 | `1196:40438` | `760×788` / `760×788` including effects | Header and before/after ledger show one canonical identity; validation and action states remain intact |

### Fix self-review

- Scope was limited to identity text/properties on the specified Task 4 instances and ledger lines.
- SKU code, units, units per case, derived calculations, areas, visual geometry, state variants, real instance links, and root IDs were not changed.
- No hidden or visible `蓝莓气泡饮` occurrence remains in the three Task 4 roots.
- No new nodes, variables, styles, effects, components, or component sets were created.
- Fix commit message: `docs: fix warehouse canvas SKU identity`.
