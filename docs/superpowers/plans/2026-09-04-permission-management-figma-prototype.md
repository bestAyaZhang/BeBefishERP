# BeBefish ERP Permission Management Figma Prototype Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a validated, interactive permission-management prototype to the existing BeBefish ERP Figma workspace, covering permission configuration, data scope, member management, and role creation.

**Architecture:** Reuse the application shell, typography, color variables, and organization-management components already present in `BeBefish ERP — Master Data UI`. Add one named section to the existing `04 Organization` page and build four `1440x1024` desktop frames around a shared role-centered two-column workspace. Keep role selection in a fixed `280px` left panel, switch configuration content through tabs in the right panel, and preserve a fixed bottom action bar.

**Tech Stack:** Figma Design, Figma Plugin API via `use_figma`, existing Figma variables/components, Noto Sans SC, Inter, Lucide-compatible SVG icons.

**Spec:** `docs/superpowers/specs/2026-09-03-permission-management-design.md`

## Global Constraints

- Work in the existing Figma file `BeBefish ERP — Master Data UI`; do not create a second file.
- Resolve the real file key and `04 Organization` page ID before writing; never infer or invent either identifier.
- Target only a `1440x1024` desktop viewport. Mobile and responsive variants are outside this plan.
- Reuse the existing full-width ERP shell: `244px` full-height sidebar, `64px` top toolbar, no outer page margin, and no maximum-width wrapper.
- Add a section named `Permission Management` to `04 Organization`, positioned after the existing organization frames without moving or altering them.
- Use `Noto Sans SC` for Chinese copy and `Inter` for numbers and English. Load fonts before mutating text nodes.
- Reuse existing semantic variables and components whenever their visual contract matches. Create permission-specific components only when reuse would change existing components.
- Use `#25314D` for primary text, `#536DFF` for the brand color, `#F6F7FB` for the page background, white surfaces, slate-200 borders, and `6px` control/panel radii.
- Keep the interface compact and work-focused. Do not introduce decorative gradients, floating section cards, oversized headings, or nested cards.
- Use imported Lucide-compatible SVG icons; do not redraw familiar icons from primitive shapes.
- Every `use_figma` write must return all created or mutated node IDs. Store those IDs in execution notes before the next write.
- Build the outer frame first, then shared workspace components, then one business frame per call group. Take a screenshot after each major frame and repair clipping, overlap, or inconsistent spacing immediately.
- Do not modify Vue, backend, database, or API code as part of this plan.

## File And Artifact Map

- Modify Figma file: `BeBefish ERP — Master Data UI`
- Modify Figma page: `04 Organization`
- Create Figma section: `Permission Management`
- Create frame: `Permission Management / Permission Matrix`
- Create frame: `Permission Management / Data Scope`
- Create frame: `Permission Management / Member Management`
- Create frame: `Permission Management / Create Role Drawer`
- Export: `frontend/prototype-screenshots/permissions/permission-management-matrix.png`
- Export: `frontend/prototype-screenshots/permissions/permission-management-data-scope.png`
- Export: `frontend/prototype-screenshots/permissions/permission-management-members.png`
- Export: `frontend/prototype-screenshots/permissions/permission-management-create-role.png`
- Reference: `docs/superpowers/specs/2026-09-03-permission-management-design.md`
- Reference: `docs/superpowers/specs/2026-09-03-organization-management-design.md`
- Reference Figma section: existing organization employee, department, position, and form frames on `04 Organization`
- Modify during execution: `docs/superpowers/plans/2026-09-04-permission-management-figma-prototype.md` for checkbox tracking and execution notes only

---

### Task 1: Resolve And Audit The Existing Figma Workspace

**Artifacts:**
- Inspect: Figma file `BeBefish ERP — Master Data UI`
- Inspect: page `04 Organization`
- Record: file key, page ID, source frame IDs, component IDs, variable IDs, and verified font styles

**Interfaces:**
- Consumes: current Figma account and the existing organization prototype
- Produces: verified IDs and reusable visual contracts for every later task

- [x] **Step 1: Load the required Figma skills and tool schemas**

Read `figma:figma-use` in full immediately before each `use_figma` call. Also read `figma:figma-generate-design` before the first design-writing call. Discover the schemas for `use_figma`, metadata inspection, screenshots, and design-system search before invoking them.

- [ ] **Step 2: Resolve the existing file without guessing**

Use the authenticated Figma workspace or an existing file URL from the current task context to locate the file whose exact name is `BeBefish ERP — Master Data UI`. Confirm that the editor type is Figma Design and that `04 Organization` exists.

Expected: one verified file key and one page ID for `04 Organization`.

- [x] **Step 3: Inspect the organization source frames**

Read metadata for the employee list, department list, position list, and employee form frames. Record the application-shell instance, sidebar, toolbar, page title, table, tabs, drawer, button, input, select, checkbox, tag, and pagination components that can be reused.

- [x] **Step 4: Inspect variables, text styles, and fonts**

Confirm the IDs and current values for brand, surface, text, border, status, spacing, and radius variables. Confirm the exact installed style names for Noto Sans SC and Inter and load those font styles before creating text.

- [x] **Step 5: Capture visual references**

Take screenshots of the employee list and its form/drawer state at original scale. Verify shell dimensions, sidebar width, top-toolbar height, content padding, table density, field height, and drawer width against metadata.

- [x] **Step 6: Record the execution checkpoint**

Add an `Execution Notes` section at the end of this plan containing the resolved Figma URL, file key, page ID, reusable component IDs, and variable/style IDs. Do not continue until all identifiers come from tool output.

---

### Task 2: Build Permission-Specific Components

**Artifacts:**
- Create component: `Permissions/Role List Item`
- Create component: `Permissions/Role Group Header`
- Create component: `Permissions/Workspace Tabs`
- Create component: `Permissions/Permission Checkbox Cell`
- Create component: `Permissions/Data Scope Option`
- Create component: `Permissions/Member Row`
- Create component: `Permissions/Bottom Action Bar`

**Interfaces:**
- Consumes: existing design variables and organization components from Task 1
- Produces: component IDs reused by all four permission-management frames

- [x] **Step 1: Create the role-list item component set**

Build a `256x64` item with role name, member count, status, and an optional system-role tag. Create variants for `Default`, `Hover`, `Selected`, `Disabled`, and `Unsaved`. The selected state uses a subtle brand-blue fill and brand-blue role name; the unsaved state adds a small semantic warning dot without changing row dimensions.

- [x] **Step 2: Create the role-group header**

Build a compact header for `系统角色` and `自定义角色` with group count and disclosure chevron. Create `Expanded` and `Collapsed` variants while keeping the label baseline aligned with the role list.

- [x] **Step 3: Create the workspace-tab component**

Create three fixed-width tabs: `权限配置`, `数据范围`, and `成员管理`. Add `Active`, `Default`, and `Disabled` states with a stable `40px` height and no layout shift between states.

- [x] **Step 4: Create permission-cell states**

Create checkbox-cell variants for `Unchecked`, `Checked`, `Indeterminate`, `Disabled`, and `ReadOnlyChecked`. Use a real checkbox component or component instance for interactive states and a centered em dash only for actions that do not apply.

- [x] **Step 5: Create the data-scope option**

Build a full-width radio row containing title, explanation, and one-line example. Add `Default`, `Hover`, `Selected`, and `Disabled` variants. Keep selection styling restrained: border and pale background only.

- [x] **Step 6: Create the member-row pattern**

Build a compact row with selection checkbox, avatar/name/mobile/employee number, department and position, employment type, other roles, final data scope, and text actions. Add a locked variant for the super administrator member with a lock reason replacing the remove action.

- [x] **Step 7: Create the fixed action bar**

Build a `64px`-high bottom bar with `取消修改` and `保存配置`. Create `Pristine`, `Dirty`, `Saving`, and `Error` variants. In the error state, place a concise retry message left of the actions without changing the bar height.

- [x] **Step 8: Validate the component set**

Place every variant in a specimen row inside the `Permission Management` section. Screenshot at original scale and verify consistent typography, `6px` radii, border color, checkbox sizes, and stable dimensions.

---

### Task 3: Build The Permission Matrix Frame

**Artifacts:**
- Create frame: `Permission Management / Permission Matrix`
- Create instances: shared shell, role list, role summary, tabs, matrix rows, bottom action bar

**Interfaces:**
- Consumes: shared organization shell and permission components
- Produces: the default entry state and primary prototype destination

- [x] **Step 1: Create the desktop frame and shared shell**

Create a `1440x1024` frame in the new section. Reuse the exact sidebar and toolbar from the organization source frame. Set the `组织架构 > 权限管理` navigation item to active and keep all other navigation states unchanged.

- [x] **Step 2: Build the page title area**

At the top of the content region, add title `权限管理`, subtitle `按角色维护功能权限、数据范围与授权成员`, a role-count badge `8 个角色`, and the primary action `新增角色`. Match existing organization title spacing and button height.

- [x] **Step 3: Build the full-height two-column workspace**

Create a workspace that fills the remaining viewport height. Use a `280px` left role panel, `16px` gap, and a flexible right panel. Both panels must be equal height. Give each panel its own vertical scrolling region and reserve `64px` at the bottom of the right panel for the action bar.

- [x] **Step 4: Populate the role panel**

Add a search input `搜索角色`, then two role groups:

```text
系统角色 3
超级管理员 · 2 人 · 系统
组织管理员 · 3 人 · 系统
财务管理员 · 4 人 · 系统

自定义角色 5
商品运营 · 6 人 · 启用
仓库主管 · 5 人 · 启用
采购专员 · 4 人 · 启用
销售专员 · 7 人 · 启用
临时访客 · 1 人 · 已停用
```

Select `超级管理员` in the default frame.

- [x] **Step 5: Build the selected-role summary**

Show `超级管理员`, code `SUPER_ADMIN`, status `启用`, members `2`, and `最近更新：系统内置`. Replace edit actions with a read-only notice `系统角色不可编辑`.

- [x] **Step 6: Build the permission matrix**

Use columns `模块`, `权限说明`, `查看`, `新增`, `编辑`, `删除`, `审核`, and `导出`. Populate module rows for 工作台、商品、分类、客户、供应商、仓库、库存、销售、财务、组织架构. Display the super administrator as read-only checked for supported actions and disabled em dashes for unsupported actions. Include one indeterminate group-control example in a compact matrix legend above the table.

- [x] **Step 7: Add the pristine action bar**

Place the `Pristine` bottom action bar instance. Both actions are disabled because the selected system role is read-only.

- [x] **Step 8: Validate the matrix frame**

Take a screenshot and inspect metadata. Verify: frame is exactly `1440x1024`; sidebar is `244px`; role panel is `280px`; headers remain visible; no permission label or checkbox is clipped; table rows remain distinguishable without vertical grid lines; bottom bar does not overlap the final row.

---

### Task 4: Build The Data Scope Frame

**Artifacts:**
- Create frame: `Permission Management / Data Scope`
- Create instances: shared shell, selected custom role, data-scope options, scope summary, dirty action bar

**Interfaces:**
- Consumes: Task 3 frame structure and reusable permission components
- Produces: data-scope configuration state for a custom role

- [x] **Step 1: Duplicate only the shared frame structure**

Duplicate the matrix frame into the same section, rename it `Permission Management / Data Scope`, select `商品运营`, and activate the `数据范围` tab. Detach no reusable instances.

- [x] **Step 2: Update the custom-role summary**

Show role code `PRODUCT_OPERATOR`, status `启用`, members `6`, and `最近更新：Aya Zhang · 2026-09-03`. Provide text actions `编辑角色`, `复制角色`, and `停用` with the destructive action visually separated.

- [x] **Step 3: Add the multi-role scope notice**

Add an informational strip: `员工拥有多个角色时，最终数据范围按最宽授权计算。` Use the existing subtle information treatment without creating a decorative card.

- [x] **Step 4: Build four scope options**

Display these radio choices in one vertical list:

```text
全公司：可访问公司内所有部门和员工数据；示例：总部管理角色。
本部门及下级：可访问所在部门及全部下级部门；示例：业务中心负责人。
本部门：仅可访问所在部门；示例：部门主管。
仅本人：仅可访问本人创建或负责的数据；示例：一线业务员工。
```

Select `本部门及下级`.

- [x] **Step 5: Build the organization-range summary**

On the right side of the scope content, show `当前可访问范围` with `产品中心及 3 个下级组织`, then a compact tree containing 产品中心、产品研发部、产品设计部、归档项目组. This summary is read-only.

- [x] **Step 6: Add the dirty action state**

Use the `Dirty` bottom action bar variant, enabling both cancel and save. Add a small unsaved dot to the selected role item.

- [x] **Step 7: Validate the data-scope frame**

Screenshot at original scale. Verify radio rows align, option descriptions wrap without clipping, the tree remains inside its column, and the fixed action bar leaves the entire last option visible.

---

### Task 5: Build The Member Management Frame

**Artifacts:**
- Create frame: `Permission Management / Member Management`
- Create instances: filters, member table, bulk actions, pagination, add-member control

**Interfaces:**
- Consumes: Task 3 frame structure and member-row component
- Produces: complete member authorization state

- [ ] **Step 1: Create the member frame**

Duplicate the shared frame, rename it `Permission Management / Member Management`, select `商品运营`, and activate the `成员管理` tab.

- [ ] **Step 2: Build the member toolbar**

Add one search input with placeholder `姓名 / 手机号 / 工号`, a department select defaulting to `全部部门`, a secondary `批量移除` action disabled until selection, and a primary `添加成员` action.

- [ ] **Step 3: Build the member table**

Use columns `员工信息`, `部门 / 岗位`, `用工类型`, `其他角色`, `最终数据范围`, and `操作`. Populate six rows using the organization prototype's existing employee identities where possible. Use text actions, not unlabeled icon-only actions.

- [ ] **Step 4: Represent multi-role results clearly**

At least two rows must contain other-role tags and final ranges wider than the current role. Add a tooltip note on `最终数据范围` explaining that the widest active role scope wins.

- [ ] **Step 5: Add locked-member behavior**

Include one super-administrator member row in a locked state. Replace the remove action with `不可移除` and a visible reason `系统角色成员`.

- [ ] **Step 6: Add pagination and empty-state readiness**

Place compact pagination at the table bottom with `共 6 人`, previous/next controls, page `1`, and `20 条/页`. Create an unused component variant for the filtered-empty state with action `清除筛选`, but keep the populated state in the final frame.

- [ ] **Step 7: Validate the member frame**

Screenshot and inspect metadata. Verify columns do not overlap, names and roles truncate gracefully, text actions remain readable, pagination stays pinned beneath the table, and the right workspace does not create horizontal overflow.

---

### Task 6: Build The Create Role Drawer Frame

**Artifacts:**
- Create frame: `Permission Management / Create Role Drawer`
- Create overlay: role-creation drawer and page scrim

**Interfaces:**
- Consumes: permission matrix frame and existing organization drawer/form components
- Produces: role-creation overlay state linked from the primary action

- [ ] **Step 1: Duplicate the default matrix state**

Duplicate `Permission Management / Permission Matrix`, rename the copy `Permission Management / Create Role Drawer`, and keep the underlying page content in the same state and position.

- [ ] **Step 2: Add the overlay scrim and drawer**

Add a neutral scrim over the application content while preserving visible context. Reuse the organization form drawer width and structure. Anchor the drawer to the right edge at full viewport height, with header and footer fixed and form content scrollable.

- [ ] **Step 3: Build the drawer header**

Use title `新增角色`, subtitle `创建角色后继续配置权限和成员`, and a close icon button with tooltip `关闭`.

- [ ] **Step 4: Build the role form**

Add these fields:

```text
角色名称*：商品运营
角色编码*：PRODUCT_OPERATOR
角色说明：负责商品资料维护与商品运营数据查看
复制权限来源：不复制
```

Use a text input for name, uppercase code input with helper text `仅支持大写字母、数字和下划线`, multiline textarea for description, and the shared select component for copy source. Add an inline uniqueness-success state `角色编码可用`.

- [ ] **Step 5: Build the drawer footer**

Add `取消` and primary `创建角色`. Include helper copy above the footer: `复制权限仅包含功能权限和数据范围，不复制成员。`

- [ ] **Step 6: Validate the drawer frame**

Screenshot at original scale. Verify the drawer matches organization drawer dimensions, all labels and helper text fit, the select style matches the project standard, footer actions remain visible, and the scrim does not obscure the drawer.

---

### Task 7: Connect The Interactive Prototype

**Artifacts:**
- Modify: the four permission-management frames
- Create: tab, add-role, close, cancel, and create-success prototype connections

**Interfaces:**
- Consumes: completed frame IDs from Tasks 3-6
- Produces: a navigable desktop prototype with a stable start point

- [ ] **Step 1: Set the prototype start point**

Set `Permission Management / Permission Matrix` as the flow starting frame named `权限管理`.

- [ ] **Step 2: Connect the three tabs**

Connect `权限配置`, `数据范围`, and `成员管理` to their corresponding frames using instant navigation so the stable shell does not animate or shift.

- [ ] **Step 3: Connect role creation**

Connect `新增角色` from each main frame to `Permission Management / Create Role Drawer` using an overlay-style transition. Connect close and cancel back to the matrix frame.

- [ ] **Step 4: Connect create success**

Connect `创建角色` to `Permission Management / Permission Matrix`. The destination must show `商品运营` selected and the permission tab editable; if that state cannot coexist with the read-only default without ambiguity, add it as a component-state change inside the matrix frame rather than creating a fifth deliverable frame.

- [ ] **Step 5: Add interaction notes for non-navigating behavior**

Use concise Figma annotations for automatic view-permission selection, clearing dependent permissions when view is unchecked, unsaved-change confirmation, duplicate-member skipping, and widest-scope calculation. Keep annotations outside the visible application viewport.

- [ ] **Step 6: Walk the prototype end to end**

Start at the matrix frame, visit both tabs, open and close the role drawer, reopen it, and submit the role. Confirm every hotspot lands on the intended frame and no dead-end blocks returning to the main state.

---

### Task 8: Perform Visual QA And Export Named Artifacts

**Artifacts:**
- Validate: all four Figma frames and prototype connections
- Export: four named PNG files under `frontend/prototype-screenshots/permissions/`

**Interfaces:**
- Consumes: final Figma frames and approved design spec
- Produces: inspectable design evidence and a final Figma URL

- [ ] **Step 1: Run metadata validation**

Verify every deliverable frame is exactly `1440x1024`, all four live in `Permission Management`, and their names exactly match the artifact map. Confirm the shared shell and permission-specific parts are component instances rather than duplicated detached groups.

- [ ] **Step 2: Run typography and token validation**

Confirm all Chinese copy uses the verified Noto Sans SC style, numbers/English use Inter where appropriate, letter spacing is `0`, and visible fills/strokes/radii come from approved variables or matching existing organization components.

- [ ] **Step 3: Run visual collision checks**

Inspect screenshots at original scale for overlapping text, clipped table headers, hidden final rows, action bars covering content, inconsistent select styling, horizontal overflow, and mismatched panel heights. Repair every discovered issue and capture again.

- [ ] **Step 4: Check permission-state completeness**

Confirm the delivered prototype visibly demonstrates selected, unselected, indeterminate, disabled, and read-only permission states; all four data scopes; ordinary and locked members; pristine and dirty bottom bars; and the create-role form.

- [ ] **Step 5: Export the four prototype images**

Export each deliverable frame at `1x` PNG to:

```text
frontend/prototype-screenshots/permissions/permission-management-matrix.png
frontend/prototype-screenshots/permissions/permission-management-data-scope.png
frontend/prototype-screenshots/permissions/permission-management-members.png
frontend/prototype-screenshots/permissions/permission-management-create-role.png
```

Do not overwrite screenshots belonging to product or organization modules.

- [ ] **Step 6: Verify exported artifacts locally**

Open all four PNGs and confirm they are readable, nonblank, correctly framed, and free of browser/editor chrome. Verify the names and directory match the artifact map exactly.

- [ ] **Step 7: Update execution notes**

Record the final Figma URL, section ID, four frame IDs, prototype flow start ID, export paths, and any deliberate deviations from the spec. The expected deviation count is zero.

- [ ] **Step 8: Commit the completed prototype artifacts**

Stage only this plan's checkbox updates and the four exported permission screenshots. Leave unrelated organization layout changes untouched.

```powershell
git add -- 'docs/superpowers/plans/2026-09-04-permission-management-figma-prototype.md' 'frontend/prototype-screenshots/permissions'
git diff --cached --check
git commit -m "design: add permission management prototype"
```

Expected: one commit containing the tracked execution record and four named prototype screenshots, with no unrelated Vue or test files.

---

### Task 9: Final Review Against The Approved Spec

**Artifacts:**
- Review: Figma prototype, exported screenshots, plan execution notes, and Git diff

**Interfaces:**
- Consumes: all outputs from Tasks 1-8
- Produces: final evidence that the approved prototype is complete

- [ ] **Step 1: Compare each frame with the approved specification**

Check every requirement under 授权模型、页面布局、原型画面、交互与状态、视觉规范、原型验收. Record a pass/fail result for each section in the execution notes.

- [ ] **Step 2: Check repository scope**

```powershell
git status --short
git show --stat --oneline HEAD
```

Expected: the permission prototype commit contains only the plan record and permission screenshot directory. Existing unrelated organization layout edits may remain unstaged and must not be reverted.

- [ ] **Step 3: Deliver the prototype**

Provide the user with the Figma prototype URL, the four local screenshot links, a short list of covered interactions, and any validation limitation. Do not claim backend authorization or persistence because those are explicitly outside this prototype.

## Execution Notes

### Task 1: Existing Figma Workspace Audit

- Status: `DONE_WITH_CONCERNS`.
- Authenticated file: `BeBefish ERP — Master Data UI` (`figma`, file key `jIz9HNkSoXH63gvTc3yOtj`). Browser-resolved URL: `https://www.figma.com/design/jIz9HNkSoXH63gvTc3yOtj/BeBefish-ERP-%E2%80%94-Master-Data-UI?node-id=1-3&p=f&t=kkv9k4jGUtUCoSWB-0`.
- Hierarchy concern: the file has pages `01 Foundations` (`0:1`), `02 Components` (`1:2`), and `03 Master Data` (`1:3`). `04 Organization` is a section (`499:9745`) on `03 Master Data`, not a page. Task 1 Step 2 remains unchecked because the required page does not exist under the approved name/type.
- Organization source frames: employee list `499:9746`; employee list, Product Center state `499:9909`; department list `499:10072`; position list `499:10235`; employee form/drawer state `499:10398`.
- Shell and header instances: sidebar `499:9747` -> `Navigation/Sidebar` component `28:81`; top toolbar `499:9877` -> `Header/Top Bar` component `31:186`; employee page header `500:10541`, department page header `502:10565`, and position page header `504:10577` -> `Header/Page Header` set `33:234`, `Action=Shown` variant `33:208`.
- Source pattern IDs: employee workspace `500:10554`, filters `500:10624`, keyword input `500:10625`, employment select `500:10627`, status select `500:10630`, query button `500:10633`, reset button `500:10635`, table `500:10646`, selection control `500:10676`, status chip `500:10638`, and pagination `500:10841`; department workspace `502:10578`, table `503:10588`, and pagination `503:10767`; position workspace `504:10578`, table `505:10600`, and pagination `505:10679`; employee drawer `506:10894`, field input sample `506:10909`, cancel button `506:10991`, and save button `506:10993`.
- Reusable local component IDs: `Button/Command` set `58:285`; `Field/Text` set `60:322`; `Field/Search` set `60:456`; `Field/Select` set `61:352`; `Tag/Status` set `64:348`; `Pagination` `66:441`; `__Table/Selection Control` set `106:658`; `Table/Header Cell` set `101:623`; `Table/Data Cell` set `103:614`; `Table/Row` set `106:761`; `Table/Container` `107:654`; `Overlay/Drawer` `67:459`. No local tab component was found, and the organization tables, fields, tags, buttons, pagination, and drawer are mostly raw frames rather than instances.
- Semantic variables: Brand `VariableID:6:5` (`#536DFF`) and `VariableID:6:6` (`#465EEA`); surfaces `VariableID:6:7` (`#F6F7FB`), `VariableID:6:8` (`#FFFFFF`), and `VariableID:6:9` (`#F8FAFC`); text `VariableID:6:10` (`#25314D`), `VariableID:6:11` (`#64748B`), and `VariableID:6:12` (`#94A3B8`); border `VariableID:6:13` (`#E2E8F0`); status `VariableID:6:14` through `VariableID:6:17`; spacing `VariableID:6:18` through `VariableID:6:25` (`4`, `8`, `12`, `16`, `20`, `24`, `32`, `40`); radii `VariableID:6:26` (`6`) and `VariableID:6:27` (`8`).
- Text style IDs: page title `S:5fe7bb5c2575a078964587ca4defc176ebaafd97,`; section title `S:b78418d75a93de672fe41d4221b9d7760362ca7d,`; card title `S:eac6a5041e269484a2847f3cc92ba876b6c108be,`; body `S:b803ede944e73a4a4d38d0a6c5998ebb5ad7e06b,`; body strong `S:f2fc909c143c7c88e9cf769d60bae9fb62988c0a,`; label `S:aaffcae05e4a0a2e32b0ba92a8dd8741150a1893,`; caption `S:ee2b232ff33b617b92fd1ad16368ab221946d71c,`; table number `S:d89771bae53e5f7313faf2d296585702f4b228a4,`.
- Font checks: successfully loaded `Noto Sans SC` styles `Regular`, `Medium`, and `Bold`, plus `Inter` style `Semi Bold`. Letter spacing is `0px` for every local text style. The organization section currently uses Noto Sans SC only; the Inter table-number style has zero uses there.
- Original-scale screenshots were captured for employee list `499:9746` and employee form `499:10398`; both rendered at their natural `1440x1024`. Metadata and visual checks confirm sidebar `244px`, top toolbar `64px`, content padding `32px`, employee rows `72px`, form controls `40px`, and drawer `720px` wide. The rendered references were nonblank and showed no clipping, overlap, or browser/editor chrome.
- Design-system search returned no published library matches for the combined button/input/select/checkbox/tag/pagination/tab/drawer query; reusable IDs above were therefore verified directly from the local `02 Components` page through the Figma Plugin API.

### Task 2: Permission-Specific Components

- Status: `DONE_WITH_CONCERNS`.
- Hierarchy: created sibling section `Permission Management` (`538:10667`) on `03 Master Data` (`1:3`) at `27680,0`, sized `4200x3000`. The existing `04 Organization` section (`499:9745`) remains at `22720,0`, sized `4800x2368`.
- Specimen groups: Roles & Tabs `538:10671` (`1900x800`); Permissions & Scope `538:10673` (`2060x800`); Members & Actions `538:10675` (`4040x1780`).
- `Permissions/Role List Item` set `539:10722`: `State=Default` `539:10667`, `State=Hover` `539:10678`, `State=Selected` `539:10689`, `State=Disabled` `539:10700`, and `State=Unsaved` `539:10711`; every variant is `256x64`.
- `Permissions/Role Group Header` set `541:10695`: `Group=系统角色, State=Expanded` `541:10667`, `Group=系统角色, State=Collapsed` `541:10674`, `Group=自定义角色, State=Expanded` `541:10681`, and `Group=自定义角色, State=Collapsed` `541:10688`; every variant is `256x36`.
- `Permissions/Workspace Tabs` set `542:10711`: `权限配置` Active/Default/Disabled `542:10675`, `542:10679`, `542:10683`; `数据范围` Active/Default/Disabled `542:10687`, `542:10691`, `542:10695`; `成员管理` Active/Default/Disabled `542:10699`, `542:10703`, `542:10707`; every variant is `120x40`.
- `Permissions/Permission Checkbox Cell` set `543:10702`: `State=Unchecked` `543:10675`, `State=Checked` `543:10678`, `State=Indeterminate` `543:10684`, `State=Disabled` `543:10691`, `State=ReadOnlyChecked` `543:10694`, and `State=NotApplicable` `543:10700`; every variant is `64x40`. `NotApplicable` is the explicit centered-em-dash state requested for actions that do not apply.
- `Permissions/Data Scope Option` set `544:10714`: `State=Default` `544:10682`, `State=Hover` `544:10690`, `State=Selected` `544:10698`, and `State=Disabled` `544:10706`; every variant is `760x96`.
- `Permissions/Member Row` set `546:10724`: `State=Default` `546:10682` and `State=Locked` `546:10703`; both are `836x80`. The locked super-administrator row replaces removal with `系统角色成员`.
- `Permissions/Bottom Action Bar` set `548:10758`: `State=Pristine` `548:10682`, `State=Dirty` `548:10704`, `State=Saving` `548:10726`, and `State=Error` `548:10742`; every variant is `836x64`. Its actions are instances from `Button/Command` set `58:285`, and the error message occupies a stable left slot.
- Reused tokens: Brand `VariableID:6:5` and `VariableID:6:6`; surfaces `VariableID:6:8` and `VariableID:6:9`; text `VariableID:6:10` through `VariableID:6:12`; border `VariableID:6:13`; status `VariableID:6:14` through `VariableID:6:17`; spacing `VariableID:6:18` through `VariableID:6:25`; radius `VariableID:6:26`. Selection and restrained brand surfaces bind directly to approved `Color/Brand/Primary` (`VariableID:6:5`) at paint opacity `0.08`; no Task 2 color variable remains.
- Reused text styles: page title, section title, card title, body, body strong, label, caption, and table number IDs recorded in Task 1. Final typography audit covered 100 text nodes and found no Chinese font mismatch or ASCII-only font mismatch; Noto Sans SC and Inter styles were loaded before every text mutation.
- Reused components: checkbox variants from set `106:658`, disclosure chevrons `23:48` and `23:51`, and command buttons from set `58:285`. All seven checkbox instances audit at `20x20`.
- Original-scale specimen screenshots: Roles & Tabs `538:10671` rendered `1900x800`; Permissions & Scope `538:10673` rendered `2060x800`; Members & Actions `538:10675` rendered `4040x1780`. The whole-section capture for `538:10667` reports natural geometry `4200x3000`; the PNG is `4280x3080` because Figma includes the section label and outline in the render.
- Final Plugin API audit status: `PASS`. It confirmed all seven component-set names, IDs, variant counts and dimensions, 20px checkbox instances, zero font-rule mismatches, zero placeholders, intact specimen bounds, and unchanged source-section geometry. Visual inspection found no clipping or incoherent overlap.

### Task 3: Permission Matrix Frame

- Status: `DONE`.
- Deliverable frame: `Permission Management / Permission Matrix` (`572:11100`) in `Permission Management` (`538:10667`) on `03 Master Data` (`1:3`). The frame is exactly `1440x1024` at `80,3000` within the section.
- Reusable workspace: `Permissions/Main Workspace` component `562:10728` (`836x784`) with final-frame instance `574:11274`. Its default composition is `SuperAdminReadOnly`; Task 7 may convert it to a component set or add the `NewRoleEditable` state without creating a fifth deliverable frame. Supporting row component: `Permissions/Matrix Row` `561:10698` (`836x40`).
- Shared shell instances: sidebar `572:11101` -> `Navigation/Sidebar` `28:81`; topbar `572:11231` -> `Header/Top Bar` `31:186`; page header `574:11259` -> `Header/Page Header` `33:208`. The permission label is a local override on active sidebar item `I572:11101;29:91`; no shared shell component was mutated.
- Layout readback: sidebar `244x1024`; topbar `1196x64`; content `1196x960` with `32px` padding and `16px` vertical gap; workspace row `1132x784`; role panel `280x784`; inter-panel gap `16px`; workspace `836x784`; right action bar `836x64`. Role scroll region `575:11694` and matrix scroll region `I574:11274;562:10731` both use vertical overflow.
- Role nodes: group headers `576:11691` and `576:11731`; role instances `576:11698`, `576:11709`, `576:11720`, `576:11738`, `576:11749`, `576:11760`, `576:11771`, and `576:11782`. `超级管理员` is selected and the disabled `临时访客` uses the Disabled variant.
- Matrix readback: ten row instances from reusable row component `561:10698`, each `836x40`; 60 permission cells total, comprising 47 `ReadOnlyChecked` and 13 `NotApplicable` states. The pristine bottom action instance resolves to `548:10682`; the indeterminate legend uses component `543:10684` via source instance `565:10747`.
- Token readback: page fill `VariableID:6:7`; panel fill `VariableID:6:8`; border `VariableID:6:13`; spacing `VariableID:6:19`, `VariableID:6:20`, `VariableID:6:21`, and `VariableID:6:24`; radius `VariableID:6:26`. Typography audit found only Noto Sans SC and Inter, zero negative letter spacing, all required strings present, and zero remaining placeholders.
- Durable original-scale screenshot: `.superpowers/sdd/2026-09-04-permission-management-figma-prototype/task-3-permission-matrix.png`, `1440x1024`, `162474` bytes, SHA-256 `cedc7819b117643cd83d93e2f2dd3524c3faf60e082216833428598faeabf5a4`. Visual inspection confirmed readable selected-role content, distinct border-only rows, no clipped permission labels or checkboxes, and no final-row/action-bar overlap.
- Source preservation: `04 Organization` (`499:9745`) remains at `22720,0`, `4800x2368`, with primary source frame `499:9746` still `1440x1024`; the validation baseline matched exactly.
- Concern: the shared sidebar has no dedicated permission item, so the approved local-instance override repurposes its active navigation label while preserving the shell geometry and main-component ancestry.

#### Fix Round 1

- Status: `DONE`. Replaced the relabeled shared-sidebar instance with permission-specific derived component `584:11811` and final instance `584:11997`; nested organization group `583:11775` now shows `员工列表`, `部门管理`, `岗位管理`, and active `权限管理`. Shared component `28:81` remains unchanged.
- Removed action-cell fills and strokes on `561:10703`, `561:10709`, `561:10715`, `561:10721`, `561:10727`, and `561:10733`; row `561:10698` keeps only its 1px bottom separator. Audit: zero vertical rail cells, `47` read-only checks, and `13` visible not-applicable em dashes.
- Created body-only scroll region `585:11913`; outer matrix region `562:10731` is non-scrolling, and header `566:10752` is its sibling outside the vertical-scrolling body `567:10752`.
- Re-exported the exact `1440x1024` durable PNG, `162166` bytes, SHA-256 `e5d95023202bbbe66be844ea90b0e75541288fa6921ae86791f24274dd69266c`. Visual inspection passed with no vertical matrix rails, clipping, or overlap.
- Source section `499:9745` and source frame `499:9746` retain their baseline geometry. Task 3 remains `SuperAdminReadOnly`; Task 7 retains ownership of `NewRoleEditable`.

#### Fix Round 2

- Status: `DONE`. Added collapsed inventory-group instance `594:11914` -> `27:22` to derived sidebar component `584:11811`; final-frame path is `I584:11997;594:11914`. Shared sidebar component `28:81` remains untouched.
- Final top-level order is `工作台`, `商品管理`, `分类管理`, `客户管理`, `供应商管理`, `仓库管理`, `库存管理`, `组织架构`, `财务管理`. `组织架构` remains expanded with `员工列表`, `部门管理`, `岗位管理`, and active `权限管理`; `财务管理` remains collapsed.
- Main Navigation content ends at local `y=790`; pinned user section `I584:11997;584:11828` begins at `y=936`, leaving `146px` clearance and ending exactly at the `1024px` sidebar boundary. Readback found no direct-entry overlaps.
- The accepted matrix was not changed: `562:10731` remains non-scrolling, body `585:11913` remains the only vertical scroll region, and all eight cells of row `561:10698` still have zero stroke paints.
- Re-exported the exact `1440x1024` durable PNG, `164252` bytes, SHA-256 `f4782643f05089523b670ef8620db1c819930abf2a37e04d14744073267f49f3`. Visual inspection confirmed the full sidebar, matrix, and pinned footer are visible without clipping or overlap.
- Source section `499:9745`, source frame `499:9746`, and shared component `28:81` retain their prior geometry and content. Task 3 remains `SuperAdminReadOnly`; Task 7 retains ownership of `NewRoleEditable`.

### Task 4: Data Scope Frame

- Status: `DONE`.
- Deliverable frame: `Permission Management / Data Scope` (`600:11953`) in `Permission Management` (`538:10667`) on `03 Master Data` (`1:3`), exactly `1440x1024` at `80,4240`.
- Reusable workspace: `Permissions/Main Workspace / Data Scope` component `604:12647` (`836x784`) with final instance `600:11974`. The duplicated frame retains sidebar `600:11954` -> `584:11811`, topbar `600:11955` -> `31:186`, and page header `600:11957` -> `33:208`.
- Selected custom role: `600:11969` -> `State=Unsaved` component `539:10711`, with `商品运营`, `6`, `启用`, brand-selected surface, and visible unsaved dot `I600:11969;539:10712`. The prior `超级管理员` row `600:11965` is `State=Default`.
- Workspace state: summary `I600:11974;604:12648`, active data-scope tab `I600:11974;604:12665` -> `542:10687`, four option instances from set `544:10714`, selected `本部门及下级` instance `I600:11974;604:12737` -> `544:10698`, organization tree `I600:11974;604:12765`, and Dirty action bar `I600:11974;604:12693` -> `548:10704`.
- Layout readback: sidebar `244x1024`; topbar `1196x64`; content `1196x960`; role panel `280x784`; workspace gap `16`; workspace `836x784`; summary/tabs/content/action heights `120/48/552/64`; scope columns `804x408` with `520px` options, `16px` gap, and `268px` summary. Option rows are `520x96` at `y=0/104/208/312`.
- Final Plugin API audit returned `PASS`: every required string is present, Noto Sans SC/Inter checks passed with zero negative letter spacing, all role rows/tabs/scope options/actions retain component ancestry, and the final selected surface uses approved `Color/Brand/Primary` through background `I600:11974;610:12372` at node opacity `0.08`.
- Durable original-scale screenshot: `.superpowers/sdd/2026-09-04-permission-management-figma-prototype/task-4-data-scope.png`, `1440x1024`, `147753` bytes, SHA-256 `32deb3d569a77b0a48327a64a6147723b6f7a0d7c8c6f564e07e6ce3fa6d1863`. Visual inspection confirmed aligned radios, unclipped descriptions, contained tree content, visible last option, and clear separation above the fixed action bar.
- Source preservation: organization section `499:9745` remains `22720,0`, `4800x2368`, with its five original children; source frame `499:9746` remains `1440x1024`; Task 3 frame `572:11100` remains `80,3000`, `1440x1024`.
- Concern: Figma normalizes paint-level opacity on nested instances, so the selected option keeps the reusable radio-row instance for content and uses an auto-layout wrapper plus a variable-bound background layer at node opacity `0.08`; no repeated role, tab, scope-option, or action structure is detached.
