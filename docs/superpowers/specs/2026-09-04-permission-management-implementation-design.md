# BeBefish ERP 权限管理实现设计

日期：2026-09-04

## 1. 背景与目标

现有权限管理已经完成 Figma 原型和最终布局修复，但仓库内尚无生产路由、Vue 页面或可复用的授权领域模型。组织架构前端与演示登录账号仍使用内存数据，后端也没有员工、部门或岗位表。

本轮目标是在不提前引入不完整组织持久化的前提下，交付可操作、可测试的权限管理前端，并在后端建立可供登录和后续业务接口复用的 RBAC 授权核心。

## 2. 已批准范围

- 新增 `/organization/permissions` 生产前端路由和“组织架构 / 权限管理”导航项。
- 实现角色列表、权限矩阵、数据范围、成员管理、新增角色、编辑角色、复制角色、停用角色和未保存变更保护。
- 权限管理页面通过 `PermissionService` 接口读取和修改数据；本轮默认实现为可变的内存 Mock，并复用现有组织模块中的员工与部门数据。
- 后端新增独立的 RBAC 授权领域模型、角色仓储接口、权限解析器和内存实现。
- 登录发放令牌时由授权解析器计算有效角色和权限，不再要求调用方直接拼装最终权限集合。
- 系统角色、自定义角色、停用角色、功能权限并集和最宽数据范围均有自动化测试。

## 3. 本轮不包含

- MySQL 权限表、Flyway 权限迁移和权限管理 REST API。
- 后端组织架构、员工、部门或岗位持久化。
- 飞书组织、飞书角色或 OAuth 同步。
- 员工级权限例外、字段级权限、条件表达式和审批流权限。
- 移动端专用权限管理布局。

这些能力将在后端员工身份和组织接口落地后统一接入，避免当前阶段用无外键的员工编号或重复账号表形成第二套身份源。

## 4. 授权语义

- 权限授予角色，员工可拥有多个角色。
- 有效功能权限是所有启用角色权限的并集。
- 有效数据范围按 `SELF < DEPARTMENT < DEPARTMENT_AND_DESCENDANTS < COMPANY` 取最宽值。
- 停用角色不参与任何有效权限或数据范围计算。
- `SUPER_ADMIN` 是不可修改、不可停用的系统角色，始终拥有完整权限目录和 `COMPANY` 数据范围。
- 其他系统角色不可编辑角色元数据、权限和范围；自定义角色可以编辑、复制和停用。
- 复制角色只复制功能权限和数据范围，不复制成员。
- 任一模块勾选新增、编辑、删除、审核或导出时必须同时拥有查看权限；移除查看权限时同步移除该模块其他操作。
- 添加重复成员时跳过已有绑定，并返回新增数与跳过数。

## 5. 前端架构

新增 `frontend/src/features/permission` 垂直模块：

- `types.ts`：角色、权限目录、数据范围、成员、分页、查询和保存载荷。
- `permissionRules.ts`：权限依赖、模块全选、最终数据范围和编辑能力等纯函数。
- `permissionService.ts`：页面依赖的服务接口与默认服务导出。
- `mockPermissionService.ts`：角色、权限、范围和成员的可变内存实现。
- `views/PermissionManagementView.vue`：角色选择、三个页签、脏状态、保存和抽屉编排。
- `components/RoleListPanel.vue`：角色搜索、分组、选中和筛选外提示。
- `components/PermissionMatrix.vue`：模块权限矩阵和查看权限依赖。
- `components/DataScopePanel.vue`：四级单选范围与组织摘要。
- `components/RoleMembersPanel.vue`：成员筛选、分页、添加和批量移除。
- `components/RoleFormDrawer.vue`：新增、编辑和复制角色。
- `components/MemberSelectionDrawer.vue`：员工筛选和批量添加。

页面组件只编排视图状态，业务规则放在纯函数或服务中。组件通过 props 和 emits 接收状态，避免直接引用全局单例，从而可以用真实行为测试。

## 6. 前端数据与交互

### 6.1 角色与权限目录

初始角色与 Figma 保持一致：三个系统角色、五个自定义角色。权限目录按工作台、商品、分类、客户、供应商、仓库、库存、销售、财务和组织架构分组，操作列固定为查看、新增、编辑、删除、审核和导出；不适用操作以 `supportedActions` 明确标记。

进入页面默认选中超级管理员并展示只读完整权限。自定义角色修改后标记脏状态；切换角色、切换页签或关闭抽屉前先确认是否放弃修改。保存失败保留本地编辑状态并允许重试。

### 6.2 数据范围

角色只保存一个数据范围。页面展示四个范围说明和当前组织摘要；成员列表根据其所有启用角色计算最终范围。后端授权核心和前端 Mock 必须共享相同的范围宽度顺序。

### 6.3 成员管理

成员列表支持姓名、手机号、工号和部门筛选，固定分页并在数据变化后回到有效页。添加成员复用现有组织 Mock 的员工标识和部门关系；重复成员被跳过并汇总提示。批量移除只作用于可移除成员，锁定成员不进入可选集合。

### 6.4 角色表单

角色编码保存前转成大写，只允许大写字母、数字和下划线，并在角色集合内唯一。新增成功后选中新角色并进入权限配置页签。复制来源为空时创建空权限角色；选择来源时复制权限和范围。

## 7. 后端架构

新增 `backend/src/main/java/com/bebefish/erp/authorization` 模块：

- `domain/PermissionDefinition`：权限编码、模块和操作。
- `domain/Role`：角色身份、系统标记、状态、数据范围、权限编码和成员标识。
- `domain/DataScope`：四级数据范围及宽度顺序。
- `domain/RoleRepository`：按成员读取角色和读取完整权限目录。
- `application/AuthorizationResolver`：计算成员的有效角色、权限和数据范围。
- `application/ResolvedAuthorization`：不可变解析结果。
- `infrastructure/InMemoryRoleRepository`：演示账号和测试使用的种子角色。

`AuthService` 保留账号凭据校验职责，但登录成功后调用 `AuthorizationResolver`，以手机号作为当前内存阶段的成员标识，生成最终角色和权限后再交给 `TokenIssuer`。`UserAccount` 不再保存最终权限列表，避免账号记录和角色记录双写。令牌仍保留当次登录解析出的快照；角色变化后重新登录生效，实时会话刷新不在本轮范围内。

权限管理入口需要 `system:role:view`；可编辑操作需要 `system:role:manage`。现有开发管理员同时拥有这两个权限。后续真实权限管理 API 必须在控制器重复校验，不依赖前端隐藏按钮。

## 8. 错误处理与安全边界

- 服务错误转为用户可读消息，不清空筛选、脏状态或抽屉输入。
- 系统角色保护、唯一编码、权限依赖和重复成员判断都在 Service 或后端领域层再次执行，不能只依赖控件禁用。
- 前端导航和操作可见性只提供体验控制，不作为安全边界。
- 成员列表不展示密码或登录凭据；权限变更不写入浏览器存储。
- 本轮内存实现只用于当前开发阶段，生产持久化切换必须通过同一接口替换，不能绕过领域规则。

## 9. 测试与验收

前端使用 Vitest 和 Vue Test Utils，至少覆盖：

- 权限依赖、模块全选和数据范围合并纯函数。
- 角色创建、复制、编辑、停用和系统角色保护。
- 重复成员跳过、批量移除、最终范围计算和分页回收。
- 路由、导航权限、默认只读状态、三个页签、保存失败保留编辑以及未保存离开确认。
- 新增角色和添加成员抽屉的关键表单规则。

后端使用 JUnit 5 和 AssertJ，至少覆盖：

- 多角色功能权限并集与最宽数据范围。
- 停用角色排除、超级管理员完整权限和无角色默认结果。
- 登录使用解析后的角色与权限发放令牌。
- 开发管理员拥有权限管理查看和管理权限。

最终验收运行权限相关测试、完整前端测试、前端生产构建和不依赖数据库的后端定向测试。完整后端测试仅在 `ERP_TEST_DB_URL`、`ERP_TEST_DB_USERNAME` 和 `ERP_TEST_DB_PASSWORD` 可用时运行，否则明确记录环境限制。

## 10. 仓库边界

以下五个用户本地文件不得修改、暂存、回退或用作新实现证据：

- `frontend/src/features/organization/DepartmentPositionManagement.test.ts`
- `frontend/src/features/organization/EmployeeManagementView.test.ts`
- `frontend/src/features/organization/views/DepartmentManagementView.vue`
- `frontend/src/features/organization/views/EmployeeManagementView.vue`
- `frontend/src/features/organization/views/PositionManagementView.vue`

实现可读取这些文件理解现有接口，但新增权限测试和页面必须放在独立文件中。完成后提交所有预期改动，进行独立复审和干净工作树验证；不自动合并或推送。
