# Warehouse Canvas evidence

Captured in Codex in-app browser only. Source exports are actual Figma node screenshots. All final source/implementation pairs are 1440 × 1024 at DPR 1. Comparisons have source left and implementation right, 1:1 pixel crops. See the appended Warehouse Canvas section in root design-qa.md for state correspondence, iterations, accepted constraints, tests, and manual results.

## Source node URLs

- Overview: https://www.figma.com/design/jIz9HNkSoXH63gvTc3yOtj?node-id=1187-2
- Draw area: https://www.figma.com/design/jIz9HNkSoXH63gvTc3yOtj?node-id=1190-516
- Add SKU: https://www.figma.com/design/jIz9HNkSoXH63gvTc3yOtj?node-id=1193-39676
- Selected SKU: https://www.figma.com/design/jIz9HNkSoXH63gvTc3yOtj?node-id=1193-40024
- Partial move: https://www.figma.com/design/jIz9HNkSoXH63gvTc3yOtj?node-id=1193-40372
- Warning/delete: https://www.figma.com/design/jIz9HNkSoXH63gvTc3yOtj?node-id=1210-2466

## Console evidence

Read from tab.dev.logs({levels:['error','warn'],limit:100}) after the final workflow. All entries were inspected. No warning or error belongs to the verified 127.0.0.1:5186 application.

The tab contains two historical warnings from the initially inspected unrelated 5173 checkout:

- 2026-09-05T17:15:32.771Z; http://localhost:5173/node_modules/.vite/deps/vue-router.js?v=aba95c50; [Vue Router warn]: Discarded invalid param(s) "pathMatch" when navigating.
- 2026-09-05T17:17:45.393Z; http://127.0.0.1:5173/node_modules/.vite/deps/vue-router.js?v=aba95c50; same router warning.

No errors were returned. These warnings were not suppressed or fixed outside Task 6 scope.

## Pixel manifest

Paths below are relative to this directory. Dimensions are read from actual PNG headers using System.Drawing. Earlier 1425 × 1013 captures and desktop resilience captures exclude native scrollbar strips in the IAB output; final acceptance captures are 1440 × 1024. Historical selected/partial/overview-iteration1 comparisons crop the source and implementation to the same 1425 × 1013 rather than stretching either image.

| File | Pixel dimensions |
| --- | --- |
| [comparison-add-sku-drawer-after.png](comparison-add-sku-drawer-after.png) | 1440 × 760 |
| [comparison-add-sku-full-after.png](comparison-add-sku-full-after.png) | 2880 × 1024 |
| [comparison-add-sku-full-before.png](comparison-add-sku-full-before.png) | 2880 × 1024 |
| [comparison-draw-area-draft-after.png](comparison-draw-area-draft-after.png) | 552 × 360 |
| [comparison-draw-area-full-after.png](comparison-draw-area-full-after.png) | 2880 × 1024 |
| [comparison-draw-area-full-before.png](comparison-draw-area-full-before.png) | 2880 × 1024 |
| [comparison-overview-area-sku-after.png](comparison-overview-area-sku-after.png) | 1392 × 224 |
| [comparison-overview-full-after.png](comparison-overview-full-after.png) | 2880 × 1024 |
| [comparison-overview-full-before.png](comparison-overview-full-before.png) | 2880 × 1024 |
| [comparison-overview-full-iteration1.png](comparison-overview-full-iteration1.png) | 2850 × 1013 |
| [comparison-overview-toolbar-after.png](comparison-overview-toolbar-after.png) | 1008 × 90 |
| [comparison-partial-move-arithmetic-after.png](comparison-partial-move-arithmetic-after.png) | 1344 × 544 |
| [comparison-partial-move-full-after.png](comparison-partial-move-full-after.png) | 2880 × 1024 |
| [comparison-partial-move-full-before.png](comparison-partial-move-full-before.png) | 2850 × 1013 |
| [comparison-sku-selected-full-after.png](comparison-sku-selected-full-after.png) | 2880 × 1024 |
| [comparison-sku-selected-full-before.png](comparison-sku-selected-full-before.png) | 2850 × 1013 |
| [comparison-sku-selected-properties-after.png](comparison-sku-selected-properties-after.png) | 720 × 856 |
| [comparison-warning-confirmation-delete.png](comparison-warning-confirmation-delete.png) | 880 × 240 |
| [comparison-warning-full-after.png](comparison-warning-full-after.png) | 2880 × 1024 |
| [comparison-warning-full-before.png](comparison-warning-full-before.png) | 2880 × 1024 |
| [comparison-warning-issues-after.png](comparison-warning-issues-after.png) | 720 × 536 |
| [implementation-add-sku-after.png](implementation-add-sku-after.png) | 1440 × 1024 |
| [implementation-add-sku-before.png](implementation-add-sku-before.png) | 1440 × 1024 |
| [implementation-add-sku-with-d-01.png](implementation-add-sku-with-d-01.png) | 1440 × 1024 |
| [implementation-draw-area-after.png](implementation-draw-area-after.png) | 1440 × 1024 |
| [implementation-draw-area-before.png](implementation-draw-area-before.png) | 1440 × 1024 |
| [implementation-overview-after.png](implementation-overview-after.png) | 1440 × 1024 |
| [implementation-overview-before.png](implementation-overview-before.png) | 1440 × 1024 |
| [implementation-overview-iteration1.png](implementation-overview-iteration1.png) | 1425 × 1013 |
| [implementation-partial-move-after.png](implementation-partial-move-after.png) | 1440 × 1024 |
| [implementation-partial-move-before.png](implementation-partial-move-before.png) | 1425 × 1013 |
| [implementation-sku-selected-after.png](implementation-sku-selected-after.png) | 1440 × 1024 |
| [implementation-sku-selected-before.png](implementation-sku-selected-before.png) | 1425 × 1013 |
| [implementation-warning-after.png](implementation-warning-after.png) | 1440 × 1024 |
| [implementation-warning-before.png](implementation-warning-before.png) | 1440 × 1024 |
| [implementation-warning-delete.png](implementation-warning-delete.png) | 1440 × 1024 |
| [implementation-warning-issues.png](implementation-warning-issues.png) | 1440 × 1024 |
| [interaction-added-250.png](interaction-added-250.png) | 1425 × 1013 |
| [interaction-delete-confirmation.png](interaction-delete-confirmation.png) | 1440 × 1024 |
| [interaction-delete-final-confirmation.png](interaction-delete-final-confirmation.png) | 1440 × 1024 |
| [interaction-desktop-1024.png](interaction-desktop-1024.png) | 1009 × 757 |
| [interaction-desktop-1280.png](interaction-desktop-1280.png) | 1265 × 889 |
| [interaction-invalid-split.png](interaction-invalid-split.png) | 1440 × 1024 |
| [interaction-library-drop.png](interaction-library-drop.png) | 1440 × 1024 |
| [interaction-property-overflow-before.png](interaction-property-overflow-before.png) | 1425 × 1013 |
| [interaction-resized-250.png](interaction-resized-250.png) | 1440 × 1024 |
| [interaction-split-conserved.png](interaction-split-conserved.png) | 1440 × 1024 |
| [interaction-split-preview-250.png](interaction-split-preview-250.png) | 1440 × 1024 |
| [interaction-undo-redo.png](interaction-undo-redo.png) | 1440 × 1024 |
| [interaction-warehouse-query-limitation.png](interaction-warehouse-query-limitation.png) | 1440 × 1024 |
| [interaction-warnings-repaired.png](interaction-warnings-repaired.png) | 1440 × 1024 |
| [interaction-whole-move-250.png](interaction-whole-move-250.png) | 1440 × 1024 |
| [interaction-zoom-pan.png](interaction-zoom-pan.png) | 1440 × 1024 |
| [source-add-sku.png](source-add-sku.png) | 1440 × 1024 |
| [source-draw-area.png](source-draw-area.png) | 1440 × 1024 |
| [source-overview.png](source-overview.png) | 1440 × 1024 |
| [source-partial-move.png](source-partial-move.png) | 1440 × 1024 |
| [source-sku-selected.png](source-sku-selected.png) | 1440 × 1024 |
| [source-warning.png](source-warning.png) | 1440 × 1024 |

Comparison generation: compare.ps1 validates matching crop bounds and joins originals without rescaling. Final full views use x=0,y=0,w=1440,h=1024. Focused crop coordinates are in design-qa.md. Original screenshots are preserved.

