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

- [ ] **Step 1: Load the required Figma skills and tool schemas**

Read `figma:figma-use` in full immediately before each `use_figma` call. Also read `figma:figma-generate-design` before the first design-writing call. Discover the schemas for `use_figma`, metadata inspection, screenshots, and design-system search before invoking them.

- [ ] **Step 2: Resolve the existing file without guessing**

Use the authenticated Figma workspace or an existing file URL from the current task context to locate the file whose exact name is `BeBefish ERP — Master Data UI`. Confirm that the editor type is Figma Design and that `04 Organization` exists.

Expected: one verified file key and one page ID for `04 Organization`.

- [ ] **Step 3: Inspect the organization source frames**

Read metadata for the employee list, department list, position list, and employee form frames. Record the application-shell instance, sidebar, toolbar, page title, table, tabs, drawer, button, input, select, checkbox, tag, and pagination components that can be reused.

- [ ] **Step 4: Inspect variables, text styles, and fonts**

Confirm the IDs and current values for brand, surface, text, border, status, spacing, and radius variables. Confirm the exact installed style names for Noto Sans SC and Inter and load those font styles before creating text.

- [ ] **Step 5: Capture visual references**

Take screenshots of the employee list and its form/drawer state at original scale. Verify shell dimensions, sidebar width, top-toolbar height, content padding, table density, field height, and drawer width against metadata.

- [ ] **Step 6: Record the execution checkpoint**

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

- [ ] **Step 1: Create the role-list item component set**

Build a `256x64` item with role name, member count, status, and an optional system-role tag. Create variants for `Default`, `Hover`, `Selected`, `Disabled`, and `Unsaved`. The selected state uses a subtle brand-blue fill and brand-blue role name; the unsaved state adds a small semantic warning dot without changing row dimensions.

- [ ] **Step 2: Create the role-group header**

Build a compact header for `系统角色` and `自定义角色` with group count and disclosure chevron. Create `Expanded` and `Collapsed` variants while keeping the label baseline aligned with the role list.

- [ ] **Step 3: Create the workspace-tab component**

Create three fixed-width tabs: `权限配置`, `数据范围`, and `成员管理`. Add `Active`, `Default`, and `Disabled` states with a stable `40px` height and no layout shift between states.

- [ ] **Step 4: Create permission-cell states**

Create checkbox-cell variants for `Unchecked`, `Checked`, `Indeterminate`, `Disabled`, and `ReadOnlyChecked`. Use a real checkbox component or component instance for interactive states and a centered em dash only for actions that do not apply.

- [ ] **Step 5: Create the data-scope option**

Build a full-width radio row containing title, explanation, and one-line example. Add `Default`, `Hover`, `Selected`, and `Disabled` variants. Keep selection styling restrained: border and pale background only.

- [ ] **Step 6: Create the member-row pattern**

Build a compact row with selection checkbox, avatar/name/mobile/employee number, department and position, employment type, other roles, final data scope, and text actions. Add a locked variant for the super administrator member with a lock reason replacing the remove action.

- [ ] **Step 7: Create the fixed action bar**

Build a `64px`-high bottom bar with `取消修改` and `保存配置`. Create `Pristine`, `Dirty`, `Saving`, and `Error` variants. In the error state, place a concise retry message left of the actions without changing the bar height.

- [ ] **Step 8: Validate the component set**

Place every variant in a specimen row inside the `Permission Management` section. Screenshot at original scale and verify consistent typography, `6px` radii, border color, checkbox sizes, and stable dimensions.

---

### Task 3: Build The Permission Matrix Frame

**Artifacts:**
- Create frame: `Permission Management / Permission Matrix`
- Create instances: shared shell, role list, role summary, tabs, matrix rows, bottom action bar

**Interfaces:**
- Consumes: shared organization shell and permission components
- Produces: the default entry state and primary prototype destination

- [ ] **Step 1: Create the desktop frame and shared shell**

Create a `1440x1024` frame in the new section. Reuse the exact sidebar and toolbar from the organization source frame. Set the `组织架构 > 权限管理` navigation item to active and keep all other navigation states unchanged.

- [ ] **Step 2: Build the page title area**

At the top of the content region, add title `权限管理`, subtitle `按角色维护功能权限、数据范围与授权成员`, a role-count badge `8 个角色`, and the primary action `新增角色`. Match existing organization title spacing and button height.

- [ ] **Step 3: Build the full-height two-column workspace**

Create a workspace that fills the remaining viewport height. Use a `280px` left role panel, `16px` gap, and a flexible right panel. Both panels must be equal height. Give each panel its own vertical scrolling region and reserve `64px` at the bottom of the right panel for the action bar.

- [ ] **Step 4: Populate the role panel**

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

- [ ] **Step 5: Build the selected-role summary**

Show `超级管理员`, code `SUPER_ADMIN`, status `启用`, members `2`, and `最近更新：系统内置`. Replace edit actions with a read-only notice `系统角色不可编辑`.

- [ ] **Step 6: Build the permission matrix**

Use columns `模块`, `权限说明`, `查看`, `新增`, `编辑`, `删除`, `审核`, and `导出`. Populate module rows for 工作台、商品、分类、客户、供应商、仓库、库存、销售、财务、组织架构. Display the super administrator as read-only checked for supported actions and disabled em dashes for unsupported actions. Include one indeterminate group-control example in a compact matrix legend above the table.

- [ ] **Step 7: Add the pristine action bar**

Place the `Pristine` bottom action bar instance. Both actions are disabled because the selected system role is read-only.

- [ ] **Step 8: Validate the matrix frame**

Take a screenshot and inspect metadata. Verify: frame is exactly `1440x1024`; sidebar is `244px`; role panel is `280px`; headers remain visible; no permission label or checkbox is clipped; table rows remain distinguishable without vertical grid lines; bottom bar does not overlap the final row.

---

### Task 4: Build The Data Scope Frame

**Artifacts:**
- Create frame: `Permission Management / Data Scope`
- Create instances: shared shell, selected custom role, data-scope options, scope summary, dirty action bar

**Interfaces:**
- Consumes: Task 3 frame structure and reusable permission components
- Produces: data-scope configuration state for a custom role

- [ ] **Step 1: Duplicate only the shared frame structure**

Duplicate the matrix frame into the same section, rename it `Permission Management / Data Scope`, select `商品运营`, and activate the `数据范围` tab. Detach no reusable instances.

- [ ] **Step 2: Update the custom-role summary**

Show role code `PRODUCT_OPERATOR`, status `启用`, members `6`, and `最近更新：Aya Zhang · 2026-09-03`. Provide text actions `编辑角色`, `复制角色`, and `停用` with the destructive action visually separated.

- [ ] **Step 3: Add the multi-role scope notice**

Add an informational strip: `员工拥有多个角色时，最终数据范围按最宽授权计算。` Use the existing subtle information treatment without creating a decorative card.

- [ ] **Step 4: Build four scope options**

Display these radio choices in one vertical list:

```text
全公司：可访问公司内所有部门和员工数据；示例：总部管理角色。
本部门及下级：可访问所在部门及全部下级部门；示例：业务中心负责人。
本部门：仅可访问所在部门；示例：部门主管。
仅本人：仅可访问本人创建或负责的数据；示例：一线业务员工。
```

Select `本部门及下级`.

- [ ] **Step 5: Build the organization-range summary**

On the right side of the scope content, show `当前可访问范围` with `产品中心及 3 个下级组织`, then a compact tree containing 产品中心、产品研发部、产品设计部、归档项目组. This summary is read-only.

- [ ] **Step 6: Add the dirty action state**

Use the `Dirty` bottom action bar variant, enabling both cancel and save. Add a small unsaved dot to the selected role item.

- [ ] **Step 7: Validate the data-scope frame**

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
