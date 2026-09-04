# Organization And Permission Final Fix Wave Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Close the three Important and one Minor findings from the organization/permission handoff without touching the five protected user-owned source-worktree files.

**Architecture:** Keep pagination reconciliation inside the shared `OrganizationPagination` controlled component so all three protected list views receive the same valid page event. Keep relationship visibility inside `DepartmentFormDrawer` by admitting only the currently saved disabled/inactive records as disabled options. In Figma, restore the existing shared two-column order and make the populated member table a header + independently scrolling rows viewport + pinned pagination composition inside the fixed workspace.

**Tech Stack:** Vue 3, TypeScript, Vitest, Vue Test Utils, Figma Plugin API

**Spec:** `docs/handovers/2026-09-04-organization-permission-continuation.md`

## Global Constraints

- Do not edit, stage, or revert the five protected files listed in the handoff.
- Keep exactly four `1440x1024` permission deliverables and do not modify organization section `499:9745`.
- The permission work remains a Figma-only prototype; do not add production permission routes or backend enforcement.
- Do not merge or push.

---

### Task 1: Reconcile Stranded Organization Pagination

**Files:**
- Modify: `frontend/src/features/organization/components/OrganizationPagination.vue`
- Test: `frontend/src/features/organization/OrganizationComponents.test.ts`

**Interfaces:**
- Consumes: controlled props `page`, `pageSize`, and `total`.
- Produces: `page(validPage: number)` when the parent page falls outside the current page count.

- [x] **Step 1: Write the failing regression test**

```ts
it('reconciles a parent page after the result shrinks to an earlier last page', async () => {
  const wrapper = mount(OrganizationPagination, { props: { page: 2, pageSize: 10, total: 9 } });
  await nextTick();
  expect(wrapper.emitted('page')).toEqual([[1]]);
});
```

- [x] **Step 2: Run the focused test and verify RED**

Run: `cd frontend && npm run test:run -- src/features/organization/OrganizationComponents.test.ts`

Expected: FAIL because no `page` event is emitted for the clamped display page.

- [x] **Step 3: Implement the minimal controlled-state reconciliation**

Import `watch` and add:

```ts
watch([() => props.page, pageCount], ([requestedPage, validPageCount]) => {
  const validPage = Math.min(Math.max(requestedPage, 1), validPageCount);
  if (validPage !== requestedPage) emit('page', validPage);
}, { immediate: true });
```

Keep user-click validation, but compare a clicked target with the raw `props.page` so the displayed valid page can always repair stale parent state.

- [x] **Step 4: Run the focused test and verify GREEN**

Run: `cd frontend && npm run test:run -- src/features/organization/OrganizationComponents.test.ts`

Expected: PASS with the new regression and all existing component tests.

### Task 2: Preserve Existing Disabled Department Relationships

**Files:**
- Modify: `frontend/src/features/organization/components/DepartmentFormDrawer.vue`
- Test: `frontend/src/features/organization/OrganizationComponents.test.ts`

**Interfaces:**
- Consumes: the edited department's saved `parentId` and `managerEmployeeId`.
- Produces: normal enabled/active options plus a disabled option for each saved relationship that would otherwise be filtered out.

- [x] **Step 1: Write the failing drawer regression test**

Mount edit mode with a disabled parent and a resigned manager, then assert both saved IDs remain selected, their labels are visible, and their matching `<option>` elements have `disabled` set.

```ts
expect((wrapper.get('[data-testid="department-parent"]').element as HTMLSelectElement).value).toBe('4');
expect(wrapper.get('[data-testid="department-parent"] option[value="4"]').attributes('disabled')).toBeDefined();
expect((wrapper.get('[data-testid="department-manager"]').element as HTMLSelectElement).value).toBe('9');
expect(wrapper.get('[data-testid="department-manager"] option[value="9"]').attributes('disabled')).toBeDefined();
```

- [x] **Step 2: Run the focused test and verify RED**

Run: `cd frontend && npm run test:run -- src/features/organization/OrganizationComponents.test.ts`

Expected: FAIL because the saved records are absent from both selects.

- [x] **Step 3: Implement current-record-aware option lists**

Include a department when it is enabled or equals `form.parentId`; include an employee when active or equals `form.managerEmployeeId`. Bind `:disabled` only for the inactive saved option, while leaving create-mode filtering and subtree exclusion unchanged.

- [x] **Step 4: Run the focused test and verify GREEN**

Run: `cd frontend && npm run test:run -- src/features/organization/OrganizationComponents.test.ts`

Expected: PASS and no Vue warnings.

### Task 3: Repair Figma Hierarchy And Member Table Geometry

**Files:**
- Modify: Figma file `jIz9HNkSoXH63gvTc3yOtj`, page `1:3`
- Modify: `docs/superpowers/plans/2026-09-04-permission-management-figma-prototype.md`
- Modify: `frontend/prototype-screenshots/permissions/permission-management-matrix.png`
- Modify: `frontend/prototype-screenshots/permissions/permission-management-data-scope.png`
- Modify: `frontend/prototype-screenshots/permissions/permission-management-members.png`
- Modify: `frontend/prototype-screenshots/permissions/permission-management-create-role.png`

**Interfaces:**
- Consumes: role-panel instances `741:18928` and `741:19031`, populated member-table component `614:12373`, and member workspace component `621:12538`.
- Produces: role panel at frame-relative `x=0` (`x=276` relative to each deliverable), main workspace at `x=296`, and a populated member table whose pinned paginator ends before the action bar at member-workspace `y=720`.

- [x] **Step 1: Restore role-panel-first child order**

For workspace rows `600:11958` and `622:12751`, move the role-panel instance to child index 0. Validate that auto-layout places role panel at `x=0` and main workspace at `x=296`.

- [x] **Step 2: Add an independent rows viewport to the populated member table**

In component `614:12373`, create `Member Rows Scroll Viewport` at child index 1, width `836`, height `352`, `clipsContent=true`, and `overflowDirection='VERTICAL'`. Reparent the six member rows and six separators in their existing order, resetting row-relative `y` values to `0..383`. Keep the header fixed before the viewport and pagination fixed after it; resize the component to `836x456`.

- [x] **Step 3: Fit the table into the member workspace**

Resize source instance `621:13947` to `836x456`, filling the member-management region from `y=96` to `y=552` while the fixed action bar remains at workspace `y=720`.

- [x] **Step 4: Validate Figma structure and visuals**

Verify exactly four direct `1440x1024` deliverables under `538:10667`, protected section `499:9745` unchanged, role panels at frame-relative `x=276`, member paginator ending at or before action-bar start, and no overlap/cropping at original scale.

- [x] **Step 5: Refresh all four PNG exports**

Export nodes `572:11100`, `600:11953`, `622:12746`, and `626:13258` at 1x to their existing local filenames. Confirm all four are `1440x1024` and record the geometry/readback evidence in the existing Figma plan.

### Task 4: Independent Review And Clean Verification

**Files:**
- Create: `.superpowers/sdd/2026-09-04-organization-permission-final-fix-wave/final-review.md`

**Interfaces:**
- Consumes: the complete bounded fix-wave diff and live Figma readback.
- Produces: severity-counted review verdict plus clean test/build evidence.

- [ ] **Step 1: Commit only intended fix-wave files**

Stage the two unprotected Vue files, `OrganizationComponents.test.ts`, the final-fix plan/evidence, and only byte-changed permission PNGs. Confirm none of the five protected paths is staged.

- [ ] **Step 2: Request an independent read-only review**

Give the reviewer the handoff, plan, commit range, Figma IDs, and explicit instruction to report Critical/Important/Minor findings without mutating the checkout.

- [ ] **Step 3: Address all Critical and Important review findings**

Use a new RED/GREEN cycle for code changes and revalidate targeted Figma geometry for design changes. Re-request review until Critical and Important counts are zero.

- [ ] **Step 4: Verify from a clean archive/worktree**

Run full frontend `npm run test:run` and `npm run build` against the committed tree in a disposable clean directory. Run `mvn -Dtest=AuthServiceTest test`; run full `mvn test` only when all three required DB environment variables are set, otherwise report the known environment limitation.

- [ ] **Step 5: Run final Git scope checks**

Confirm the working tree is clean, the commit contains only intended paths, all four PNGs are `1440x1024`, no fifth permission deliverable exists, and no merge/push occurred.
