# BeBefish ERP 组织架构真实数据与飞书通讯录同步设计

日期：2026-09-05

## 1. 背景

当前组织架构前端已有员工、部门和岗位管理页，但 `organizationService` 固定使用内存 Mock 数据。后端 V10 已建立 `department`、`position`、`employee`、`sys_user` 和 `sys_feishu_identity` 等表，飞书登录也会创建或更新当前登录人，但尚无组织架构 REST API 和全量通讯录同步。

本轮目标是：

- 生产构建只读取真实后端，禁止 Mock 回退。
- 组织架构页面改为读写 MySQL 中的真实部门、岗位和员工数据。
- 从飞书通讯录安全、可重试地导入部门与员工。
- 飞书登录后在后台触发已过期的同步，不阻塞登录。
- 员工管理页提供手动同步和同步结果反馈。
- 不覆盖 ERP 内部角色、权限、密码和人工维护的岗位。

## 2. 方案选择

采用“本地持久化 + 飞书单向同步”方案。

未选择以下方案：

- 页面每次直接查询飞书：页面延迟和可用性受外部 API 影响，也无法稳定关联 ERP 角色、单据和库存数据。
- 仅执行一次导入脚本：无法满足新员工、调部门和离职状态的持续更新。
- 实时双向同步：当前没有将 ERP 组织数据写回飞书的需求，双向同步会增加冲突和误更新风险。

## 3. 整体架构

### 3.1 前端

新增 `httpOrganizationService` 实现现有 `OrganizationService` 接口，`organizationService` 通过现有 `createService` 选择数据源：

- 生产构建默认且只允许 `real`。
- 本地开发和自动化测试可显式使用 `VITE_DATA_SOURCE=mock`。
- 真实 API 失败时显示错误或空态，不返回 Mock 记录。

员工管理页增加“从飞书同步”按钮和同步状态。同步为后台任务，页面启动后轮询任务状态，完成时刷新统计、部门树和员工列表。

### 3.2 后端

新增 `organization` 模块，按现有项目结构分为：

- `api`：部门、岗位、员工和飞书同步接口。
- `application`：查询、维护校验、同步编排和结果统计。
- `domain`：组织实体、查询条件、同步状态与仓储端口。
- `infrastructure`：JDBC 仓储和 MySQL 分页查询。

现有 `EmployeeProvisioningService` 仍负责单个登录人的即时校验和绑定。全量通讯录导入由新的 `FeishuDirectorySyncService` 负责，两者共用同一套安全合并规则，避免并发创建重复员工。

### 3.3 飞书接口

通讯录同步使用应用身份 `tenant_access_token`：

- `GET /open-apis/contact/v3/departments/{department_id}/children`：从根部门 `0` 递归或分页获取子部门。
- `GET /open-apis/contact/v3/users/find_by_department`：分页获取指定部门直属用户。

请求统一使用 `user_id_type=open_id`、`department_id_type=open_department_id`和接口允许的最大 `page_size`。根部门用户与各子部门用户都需查询，一人属于多部门时按 `open_id` 去重。

## 4. 数据库调整

新增 V11 Flyway 迁移。

### 4.1 现有表扩展

`department` 新增：

- `manager_employee_id bigint null`：部门负责人，在员工导入完成后二次回填。

`position` 新增：

- `responsibilities varchar(1000) not null default ''`：ERP 内部岗位职责。

`employee` 新增：

- `hire_date date null`：飞书 `join_time` 或 ERP 人工录入的入职日期。
- `feishu_job_title varchar(100) null`：飞书职务名称快照，不自动创建 ERP 岗位。
- `status_source varchar(20) not null default 'manual'`：记录当前员工状态由 `manual` 还是 `feishu` 维护，防止同步重新启用管理员手动停用的账号。

迁移将现有 `source=feishu and status=active` 的员工标记为 `status_source=feishu`，已停用或离职记录保留 `manual`，避免上线后误恢复账号。管理员通过 ERP 修改员工状态时统一写入 `status_source=manual`。

部门、岗位和员工主表仍不增加租户列。本项目当前为单租户 ERP，租户校验继续使用 `FEISHU_ALLOWED_TENANT_KEY`。

### 4.2 同步任务表

新增 `sys_feishu_directory_sync`：

- `id`、`tenant_key`、`trigger_type`、`status`、`started_by_user_id`。
- `started_at`、`finished_at`。
- `departments_created`、`departments_updated`、`employees_created`、`employees_updated`。
- `records_skipped`、`records_failed`、`warning_message`、`error_message`。

`trigger_type` 为 `login | manual`，`status` 为 `pending | running | success | partial | failed`。任务记录只保留结果和有限长度错误摘要，不记录 access token、手机号或完整飞书响应。

### 4.3 权限

新增 `organization:sync` 权限，用于手动发起飞书通讯录同步。迁移同时将该权限赋给 `SUPER_ADMIN`，不自动赋给普通员工。

## 5. 数据归属与映射

### 5.1 飞书主数据

对已绑定飞书身份的员工，以下字段每次同步都由飞书更新：

- 姓名、手机号、头像。
- 飞书工号（非空且无唯一性冲突时）。
- 主部门、飞书职务快照、入职日期。
- 飞书用户激活、冻结、退出和离职状态。

员工类型映射：飞书正式员工映射为 `formal`，实习、外包、劳务、顾问和自定义非正式类型映射为 `temporary`。

状态映射：

- `is_resigned=true` 或 `is_exited=true` -> `employee.status=resigned`、`sys_user.status=disabled`。
- `is_frozen=true`、`is_unjoin=true` 或 `is_activated=false` -> `employee.status=disabled`、`sys_user.status=disabled`。
- 其余有效成员 -> `employee.status=active`。只有当前 `status_source=feishu` 或新创建员工时，同步才可重新启用账号；`status_source=manual` 的停用或离职状态保持不变。

### 5.2 ERP 主数据

以下数据不被通讯录同步覆盖：

- `sys_user_role` 中所有 `LOCAL` 角色，包括超级管理员。
- 密码哈希、上次登录方式和登录时间。
- ERP `position_id` 和岗位职责。
- ERP 手工创建的员工记录，除非通过唯一手机号安全绑定到同一飞书身份。

员工列表的岗位展示优先使用 ERP 岗位；未分配 ERP 岗位时显示 `feishu_job_title`；两者都为空时显示“未分配”。

## 6. 安全合并规则

每个飞书用户按以下顺序查找 ERP 记录：

1. `tenant_key + union_id`。
2. `tenant_key + open_id`。
3. 身份尚未存在时，使用规范化手机号查找唯一员工，且该员工尚未绑定其他飞书身份。
4. 无匹配时创建新员工、`sys_user` 和 `sys_feishu_identity`。

手机号匹配多条、员工已绑定其他身份、`union_id` 和 `open_id` 指向不同用户时，当前记录记为失败并继续同步其他员工，禁止自动合并。

工号优先使用飞书 `employee_no`。飞书未提供工号或工号已被其他员工占用时，使用 `FS-U-` 加 `open_id` 的稳定哈希生成不变工号，不使用每次变化的 UUID。飞书部门编码同样使用 `FS-D-` 加 `open_department_id` 的稳定哈希。

## 7. 同步流程

1. 校验飞书功能已启用且租户与 `FEISHU_ALLOWED_TENANT_KEY` 一致。
2. 获取全部飞书部门，先按层级顺序 upsert 部门。飞书根部门 `0` 映射为 ERP 的虚拟“全公司”筛选，不创建额外部门记录。
3. 对根部门和每个实际部门分页获取直属成员，按 `open_id` 去重。
4. 使用飞书人员排序中的主部门标识确定主部门；无标识时使用首个已导入部门。
5. 逐人在独立短事务中执行安全合并，一条失败不回滚整批导入。
6. 员工全部导入后，根据飞书部门负责人 `open_id` 回填 `manager_employee_id`。
7. 写入同步统计和状态，页面刷新真实数据。

只有飞书明确返回用户离职或退出状态时才更新为 `resigned`。同步结果中缺失某个员工不触发离职，因为缺失可能由通讯录权限范围、分页失败或临时 API 故障导致。

## 8. 触发和并发控制

### 8.1 登录触发

飞书 OAuth 登录完成当前用户创建或更新后，检查最近一次成功或部分成功的全量同步：

- 从未同步或已超过 30 分钟：异步发起新任务。
- 30 分钟内已同步：跳过。
- 已有 `pending` 或 `running` 任务：复用当前任务，不重复发起。

登录成功不等待全量同步完成。全量同步失败不使登录失败，但会在同步记录中保留错误。

### 8.2 手动触发

拥有 `organization:sync` 权限的用户可在员工管理页手动同步。已有任务运行时，接口返回现有任务，不并发启动第二个任务。

后端使用应用级互斥锁防止单实例并发。任务表作为数据库级事实来源；未来扩展多实例时，将互斥锁替换为数据库锁或分布式锁。

## 9. REST API

现有 `OrganizationService` 所有方法都有真实接口：

### 9.1 部门

- `GET /api/organization/departments`：分页查询。
- `GET /api/organization/departments/all`：部门树和表单选项。
- `GET /api/organization/departments/employee-counts`：各部门员工数。
- `POST /api/organization/departments`、`PUT /api/organization/departments/{id}`。
- `PATCH /api/organization/departments/{id}/status`。

### 9.2 岗位

- `GET /api/organization/positions`、`GET /api/organization/positions/all`。
- `POST /api/organization/positions`、`PUT /api/organization/positions/{id}`。
- `PATCH /api/organization/positions/{id}/status`。

### 9.3 员工

- `GET /api/organization/employees`、`GET /api/organization/employees/all`。
- `GET /api/organization/employees/summary`。
- `GET /api/organization/employees/{id}`。
- `POST /api/organization/employees`、`PUT /api/organization/employees/{id}`。
- `PATCH /api/organization/employees/{id}/status`。

分页、筛选、排序和部门子树查询在 SQL 层执行，不把全部员工加载到 JVM 内存后再分页。

### 9.4 同步

- `POST /api/organization/feishu-syncs`：发起同步，返回 `202 Accepted` 和任务 ID。
- `GET /api/organization/feishu-syncs/latest`：获取最近一次同步。
- `GET /api/organization/feishu-syncs/{id}`：获取指定任务进度与结果。

所有接口使用项目现有统一响应包装和错误码，列表空数据返回空数组，不返回示例数据。

## 10. 前端交互

- 进入员工管理页时并行读取部门、岗位、统计和员工页。
- 同步按钮只向拥有 `organization:sync` 权限的用户显示。
- 同步中按钮显示加载状态并禁止重复点击。
- 全部成功时顶部 Message 显示新增和更新数量。
- 部分成功时显示警告 Message，列表仍刷新已成功数据。
- 失败时显示错误 Message，保留当前列表和筛选条件。
- 未分配部门或岗位的飞书员工可正常展示，前端类型中 `departmentId` 和 `positionId` 调整为可空。

## 11. 错误处理与可观测性

- 飞书 token 获取、权限不足或 API 超时：任务标记为 `failed`，不修改未处理数据。
- 个别员工冲突：任务标记为 `partial`，记录失败数和可诊断的非敏感摘要。
- 页码返回 `has_more=true` 但无 `page_token`：视为协议错误并终止当次同步，禁止无限循环。
- 应用日志记录 sync ID、耗时、处理数和飞书 `X-Request-Id`，不记录 token 和完整响应体。
- 同步任务进程异常终止后，下次启动将超过时限的 `pending/running` 记录收敛为 `failed`，允许重试。

## 12. 飞书权限前置条件

飞书自建应用需要启用应用身份的通讯录读取权限，至少覆盖：

- 通讯录基本信息或以应用身份读取通讯录。
- 部门基础信息和部门组织架构。
- 用户基本信息、用户组织架构、手机号、工号和受雇信息。

应用的通讯录数据权限范围需设置为全部员工。若仅授权部分部门，系统只导入实际可见范围，不会将范围外的 ERP 员工标记为离职。

## 13. 测试策略

### 13.1 后端

- 部门、岗位和员工列表的筛选、分页、子树查询和空数据。
- 新增、编辑、状态变更的唯一性、占用关系和权限校验。
- 飞书部门和员工 API 的多页分页、根部门、多部门去重和主部门选择。
- 重复执行同一批数据时不创建重复员工、账号或身份。
- 手机号、工号和身份冲突时仅跳过当前记录。
- 飞书离职状态禁用账号，部分同步缺失不误禁用。
- `LOCAL` 角色、密码和 ERP 岗位在同步后保持不变。
- 登录触发不阻塞 OAuth 回调，30 分钟节流和运行中去重有效。

### 13.2 前端

- 生产环境选择真实 `httpOrganizationService`，显式配置 Mock 时构建或启动失败。
- 组织页面的列表、统计、新增、编辑和状态变更均调用真实接口。
- 同步按钮权限、防重复点击、轮询结束、成功/部分成功/失败 Message 正确。
- 可空部门、可空 ERP 岗位和飞书职务快照展示正确。

### 13.3 验收

- 运行后端全量测试、前端全量 Vitest 和生产构建。
- 在真实 MySQL 测试迁移和接口。
- 在本地真实数据源模式下验证员工、部门和岗位页面。
- 部署前备份生产数据库、JAR、前端产物和环境配置。
- 部署后由超级管理员登录或手动触发首次同步，核对飞书与 ERP 员工数、部门数和冲突记录。

## 14. 交付顺序

1. 以测试固定组织 REST 接口契约与数据库迁移。
2. 实现真实部门、岗位和员工查询及维护。
3. 扩展飞书客户端的部门、员工分页能力。
4. 实现幂等的通讯录同步、任务状态和登录后台触发。
5. 实现 `httpOrganizationService` 、手动同步和 Message 反馈。
6. 执行全量测试、生产构建、真实 API 验收和部署前检查。
