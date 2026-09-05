# Organization And Permission Continuation Handoff

## Objective

Finish the remaining organization-management and permission-management issues, run clean verification, and leave `codex/organization-management` ready for the user's integration choice.

This file is the authoritative continuation context. Do not reconstruct the previous chat history.

## Git State

- Repository: `F:/codex 项目/BeBeFishERP`
- Source worktree: `F:/codex 项目/BeBeFishERP/.worktrees/organization-management`
- Branch: `codex/organization-management`
- Base branch: `main`
- Merge base: `06362834af37b2aabf32c8e2c8b3ee20047a383d`
- Last completed permission-prototype commit before this handoff: `4988efb`

## Completed Work

- Organization-management frontend implementation, routes, navigation, reusable components, mock service, and tests are committed.
- Permission-management Figma specification and execution plan are committed and fully checked.
- The permission prototype has exactly four `1440x1024` deliverables and a complete role/data/member/create-role interaction graph.
- Task 9 scoped review passed with `0 Critical` and `0 Important` findings after restoring the Create Role drawer scrim.
- The four permission exports are stored in `frontend/prototype-screenshots/permissions/`.

## Open Final-Review Findings

The whole-branch review in `.superpowers/sdd/2026-09-04-permission-management-figma-prototype/final-review.md` reported `0 Critical`, `3 Important`, and `1 Minor`.

### Important 1: Figma role-panel placement

`Permission Management / Data Scope` (`600:11953`) and `Permission Management / Member Management` (`622:12746`) place the `280px` role panel on the right at frame-relative `x=1128`. Preserve the shared two-column hierarchy used by the matrix frame: role panel on the left at frame-relative `x=276`, flexible workspace to its right.

### Important 2: Figma member paginator overlap

In member frame `622:12746`, paginator instance `I622:13142;621:13947;615:12508` ends at absolute `y=6428`, while action bar `I622:13142;621:12590` begins at `y=6408`. Remove the `20px` overlap while retaining pinned pagination, fixed actions, and independent table scrolling.

After both Figma fixes, refresh all four local permission PNGs and verify them at original scale. Preserve exactly four deliverables and the protected organization section `499:9745`.

### Important 3: Organization pagination can become stranded

`frontend/src/features/organization/components/OrganizationPagination.vue` renders a clamped current page but compares clicks against the raw parent page. After a mutation reduces the filtered result to fewer pages, the parent can remain on an empty page and clicking displayed page `1` emits nothing.

Fix the source of truth so employee, department, and position lists reconcile to a valid page after save/status/delete/filter result changes. Add focused regression coverage for a parent at page `2` with a reduced total that only has page `1`.

### Minor: Existing disabled relationships appear blank

`frontend/src/features/organization/components/DepartmentFormDrawer.vue` excludes disabled departments and inactive employees from select options, even when they are the current saved parent or manager. Preserve the current relationship as a disabled/read-only option so edit state remains intelligible.

Treat this Minor as part of the single final fix wave when it can be addressed without broadening scope.

## Figma Reference

- File: `BeBefish ERP — Master Data UI`
- File key: `jIz9HNkSoXH63gvTc3yOtj`
- URL: `https://www.figma.com/design/jIz9HNkSoXH63gvTc3yOtj/BeBefish-ERP-%E2%80%94-Master-Data-UI?node-id=572-11100&p=f`
- Page: `03 Master Data` (`1:3`)
- Permission section: `538:10667`
- Matrix: `572:11100`
- Data scope: `600:11953`
- Members: `622:12746`
- Create Role drawer: `626:13258`
- Protected organization section: `499:9745`

Before every Figma `use_figma` call, load `figma:figma-use` in full. Do not create a fifth deliverable frame.

## Verification Baseline

- Clean committed frontend suite: `37` files, `431` tests passed.
- Clean committed frontend build: passed (`vue-tsc` and Vite, `1749` modules transformed).
- Backend focused test: `AuthServiceTest`, `6` tests passed.
- Full backend `mvn test` cannot run without test database variables. `backend/src/test/resources/application-test.yml` requires `ERP_TEST_DB_URL`, `ERP_TEST_DB_USERNAME`, and `ERP_TEST_DB_PASSWORD`; with them unset, Hikari reports `URL must start with 'jdbc'` and subsequent Spring context failures cascade.

After fixes, rerun the full frontend suite and build from a clean checkout. Run backend tests only with valid test-database variables, or clearly report that environmental limitation.

## Repository Boundary

These five pre-existing user-owned changes are unstaged in the source worktree. Do not overwrite, stage, revert, or use them as proof of committed behavior:

- `frontend/src/features/organization/DepartmentPositionManagement.test.ts`
- `frontend/src/features/organization/EmployeeManagementView.test.ts`
- `frontend/src/features/organization/views/DepartmentManagementView.vue`
- `frontend/src/features/organization/views/EmployeeManagementView.vue`
- `frontend/src/features/organization/views/PositionManagementView.vue`

Review and edit committed content carefully around those paths. If the continuation uses a fresh worktree from the branch, it will receive the committed versions and avoid this conflict.

## Required Finish Sequence

1. Use one bounded fix wave for the three Important findings and the related Minor.
2. Commit only intended code/tests, Figma plan evidence, and byte-changed permission PNGs.
3. Run a fresh scoped re-review for the final findings.
4. Run full frontend tests/build and Git scope checks from a clean worktree.
5. Do not merge or push automatically. Present the standard three integration choices after the branch is clean and verified.

## Accepted Scope Limits

The permission deliverable is a Figma-only prototype. It does not implement backend authorization, API enforcement, persistence, real organization synchronization, or production routes. Browser presentation-player pointer/focus behavior is not proven.
