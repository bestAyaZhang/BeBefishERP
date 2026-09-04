# BeBefish ERP 飞书登录与角色映射设计

## 1. 背景与目标

BeBefish ERP 当前使用手机号密码、短信验证码、内存账号和内存会话。组织架构与权限管理前端已经完成，但员工、账号、角色成员关系仍缺少可供真实登录使用的数据库持久化。

本次改造建立企业飞书单点登录闭环，并遵循以下已确认规则：

- 正式员工使用飞书扫码登录。
- 临时员工使用手机号和密码登录。
- 短信验证码登录下线。
- 本企业员工首次扫码时，即使 ERP 中没有手机号或员工记录，也允许自动开户并进入系统。
- 自动开户以飞书企业与用户身份为准，不以手机号为前置条件。
- 飞书业务角色可映射到 ERP 角色；ERP 仍是权限定义的唯一来源。
- 飞书映射角色与 ERP 本地附加角色取并集，高风险角色禁止自动映射。

## 2. 范围

### 2.1 本期包含

- 飞书企业自建应用 OAuth 登录。
- 企业 `tenant_key` 双重校验。
- 首次登录自动创建员工、用户和飞书身份绑定。
- 已有员工按唯一手机号合并绑定。
- 员工飞书资料更新与资料完整状态。
- 飞书业务角色到 ERP 角色的映射与登录时同步。
- 正式员工、临时员工登录策略强制执行。
- 数据库账号、角色关系、会话和登录审计持久化。
- 登录页、飞书回调结果页和权限管理中的飞书角色映射界面。
- 本地模拟飞书身份服务与真实企业应用扫码验收。

### 2.2 本期不包含

- 全量或定时同步所有飞书通讯录成员。
- 飞书消息、审批、日历和任务联动。
- 长期保存飞书 `user_access_token` 或 `refresh_token`。
- 多企业租户接入。
- 内嵌二维码或弹窗授权。
- 移动端专项适配。

## 3. 总体架构

采用“浏览器跳转飞书官方授权页 + 后端接收回调 + 一次性票据换取 ERP 会话”的方式。

### 3.1 登录主流程

1. 前端访问 `GET /api/auth/feishu/authorize`。
2. 后端生成高熵随机 `state`，写入短期、HttpOnly、SameSite Cookie，并跳转到飞书官方授权页。
3. 飞书完成扫码授权后回调 `GET /api/auth/feishu/callback`。
4. 后端校验 `state`，使用授权码从飞书服务端换取当前用户身份。
5. 后端校验用户 `tenant_key` 与允许企业完全一致。
6. 后端查询飞书用户详情与业务角色，执行员工自动开户或身份更新。
7. 后端同步飞书来源角色，保留 ERP 本地来源角色，并计算当前有效权限。
8. 后端生成 60 秒、仅可消费一次的登录票据，重定向到 `/auth/feishu/result?ticket=...`。
9. 前端调用 `POST /api/auth/feishu/exchange` 消费票据，取得 ERP 会话和当前用户信息。

授权码、飞书访问令牌、`App Secret` 和 ERP 会话明文都不得出现在前端持久状态或日志中。URL 中只允许出现短期一次性票据。

### 3.2 回调地址

- 本地：`http://127.0.0.1:5173/api/auth/feishu/callback`
- 生产：环境变量 `ERP_FEISHU_REDIRECT_URI` 指向 ERP 公网 HTTPS 同域下的 `/api/auth/feishu/callback`

本地由 Vite 将 `/api` 代理到后端；生产由同域反向代理转发到后端。

## 4. 企业身份校验

仅“扫码成功”不足以授权自动开户。系统使用两层企业边界：

1. 飞书应用管理后台只向本企业员工开放应用可用范围。
2. 后端使用应用凭证取得企业信息并校验企业 `tenant_key`，同时与 `ERP_FEISHU_ALLOWED_TENANT_KEY` 配置比较。

启动配置校验规则：

- 飞书登录启用时，`App ID`、`App Secret`、回调地址和允许企业标识必须完整。
- 应用凭证查询到的企业标识与允许企业标识不一致时，飞书登录进入不可用状态并记录配置错误。
- OAuth 用户身份中的 `tenant_key` 不一致时返回 `FEISHU_TENANT_NOT_ALLOWED`，不得创建员工、用户、绑定或会话。

## 5. 员工自动开户与身份合并

### 5.1 身份主键

- 企业边界：`tenant_key`。
- 跨应用稳定用户标识：`union_id`。
- 当前应用用户标识：`open_id`。
- 手机号只用于补充资料与匹配既有员工，不作为飞书登录的必要条件。

### 5.2 首次登录匹配顺序

1. 按 `tenant_key + union_id` 查找已有绑定。
2. 未找到时按 `tenant_key + open_id` 查找已有绑定。
3. 未绑定且飞书返回手机号时，按标准化手机号查找唯一 ERP 员工。
4. 唯一匹配且尚未绑定其他飞书身份时，绑定到该员工。
5. 手机号不存在、未返回或无匹配时，创建新的正式员工与用户。
6. 同手机号匹配多条数据或已绑定其他身份时，拒绝自动合并并写入审计，避免错误合并账号。

### 5.3 自动创建规则

- `employment_type` 固定为 `formal`。
- `status` 初始为 `active`，用户账号初始为 `enabled`。
- 姓名和头像取 OAuth 身份结果；手机号、员工编号和主部门在应用获得相应通讯录只读权限时补齐。
- 飞书未提供员工编号时，由 ERP 序列生成 `FS-XXXXXXXX`，不从手机号或用户标识截取。
- 飞书返回明确主部门且 ERP 已存在对应映射时写入 `department_id`；没有主部门或无法映射时保持为空，不任意选择其他部门。
- 部门、岗位、手机号或员工编号缺少任意必填资料时，将 `profile_complete` 标记为 `false`。
- 自动开户完成后立即写入身份绑定与登录审计。

并发首次登录由数据库唯一约束和事务保证只创建一套员工、用户和身份记录。

## 6. 账号与登录策略

- 正式员工只允许 `feishu` 登录；密码接口必须在服务层拒绝正式员工。
- 临时员工只允许 `password` 登录，不允许创建飞书绑定。
- 短信验证码接口和前端入口移除。
- 停用、离职员工或禁用用户不得登录。
- 本地模拟飞书登录只在 `local` 或 `test` Profile 且显式开启时可用，生产 Profile 启动时禁止开启。
- 本地管理员可以保留为登记明确的临时系统账号；生产应急账号必须纳入审计。

## 7. 角色与权限同步

### 7.1 责任边界

- 飞书负责确定“哪些员工属于哪些业务角色”。
- ERP 负责定义角色对应的菜单、按钮、API 权限和数据范围。
- 映射使用不可变的飞书角色 ID 与 ERP 角色 ID，不依赖可变名称。

### 7.2 角色来源

`sys_user_role.assignment_source` 取值：

- `LOCAL`：ERP 管理员手动分配。
- `FEISHU`：根据飞书业务角色映射自动分配。

登录同步只替换当前用户的 `FEISHU` 来源关系，绝不删除 `LOCAL` 来源关系。最终角色为两类有效角色的并集，再由现有 `AuthorizationResolver` 计算权限和数据范围。

### 7.3 默认与失败策略

- 自动开户用户始终获得系统内置 `BASIC_EMPLOYEE` 基础角色。
- 未配置飞书角色映射时仅保留基础角色和本地角色。
- 飞书业务角色查询失败时允许完成身份登录，但本次不授予飞书来源角色，只加载基础角色和本地角色，并向用户显示降级提示。
- `SUPER_ADMIN`、权限管理员及其他标记为 `sensitive` 的角色禁止出现在飞书映射目标中。
- 每次飞书登录重新读取角色；从飞书业务角色移除后，对应权限在下一次登录时失效。

## 8. 数据模型

### 8.1 `employee`

核心字段：

- `id`
- `employee_no`
- `name`
- `mobile`，允许为空
- `avatar_url`
- `department_id`，允许为空
- `position_id`，允许为空
- `employment_type`：`formal | temporary`
- `status`：`active | disabled | resigned`
- `source`：`manual | feishu`
- `profile_complete`
- `created_at`、`updated_at`

### 8.2 `sys_user`

- `id`
- `employee_id`，唯一
- `mobile`，允许为空；非空时唯一
- `password_hash`，正式员工允许为空，临时员工必须有值
- `status`：`enabled | disabled`
- `last_login_method`
- `last_login_at`
- `created_at`、`updated_at`

### 8.3 `sys_feishu_identity`

- `id`
- `user_id`，唯一
- `tenant_key`
- `open_id`
- `union_id`
- `display_name`
- `avatar_url`
- `bound_at`
- `last_verified_at`

唯一约束：`tenant_key + open_id`、`tenant_key + union_id`。

### 8.4 RBAC 持久化

- `sys_permission`
- `sys_role`
- `sys_role_permission`
- `sys_user_role`
- `sys_feishu_role_mapping`

`sys_feishu_role_mapping` 保存企业、飞书角色 ID、名称快照、ERP 角色 ID、启用状态、最后同步时间与最近错误。现有内存 `RoleRepository` 迁移为数据库适配器，领域接口保持稳定。

### 8.5 会话与审计

`sys_auth_session`：

- 用户 ID、令牌哈希、登录方式、创建时间、过期时间、撤销时间。

`sys_login_ticket`：

- 票据哈希、用户 ID、提示信息、创建时间、过期时间、消费时间。

`sys_login_audit`：

- 用户 ID（可空）、身份方式、结果、错误码、企业标识、IP、User-Agent、发生时间。

数据库不保存会话、票据或飞书访问令牌的明文。

## 9. 后端组件

### 9.1 领域端口

- `FeishuOAuthClient`：生成授权地址、交换授权码、读取 OAuth 用户身份。
- `FeishuDirectoryClient`：读取企业信息、用户通讯录资料和业务角色。
- `FeishuIdentityRepository`：身份查找与绑定。
- `EmployeeProvisioningService`：匹配、合并或创建员工与用户。
- `FeishuRoleSyncService`：读取角色、应用映射并替换飞书来源角色。
- `LoginTicketService`：签发与消费一次性票据。
- `SessionRepository`：会话签发、解析与撤销。
- `LoginAuditRepository`：记录认证事件。

飞书 SDK 或 HTTP 细节只能存在于基础设施层，应用服务只依赖以上端口。

### 9.2 API

- `GET /api/auth/feishu/status`：返回飞书登录是否可用，不暴露敏感配置。
- `GET /api/auth/feishu/authorize`：设置 state Cookie 并 302 到飞书。
- `GET /api/auth/feishu/callback`：处理飞书回调并跳转前端结果页。
- `POST /api/auth/feishu/exchange`：消费一次性票据并返回 `LoginResult`。
- `POST /api/auth/login/password`：仅临时员工。
- `POST /api/auth/logout`、`GET /api/auth/me`：保持现有契约。
- 删除 `/api/auth/sms-code` 与 `/api/auth/login/sms`。

飞书角色映射 API 位于权限管理边界：

- `GET /api/permissions/feishu-roles`
- `GET /api/permissions/feishu-role-mappings`
- `PUT /api/permissions/feishu-role-mappings/{feishuRoleId}`
- `DELETE /api/permissions/feishu-role-mappings/{feishuRoleId}`
- `POST /api/permissions/feishu-role-mappings/sync`

这些接口要求权限管理权限，并在后端禁止敏感目标角色。

`LoginResult` 在现有字段基础上增加 `employeeId`、`displayName`、`avatarUrl` 和 `warnings`。角色接口降级时返回稳定警告码 `FEISHU_ROLE_SYNC_DEGRADED`，前端显示提示但仍完成登录。

真实数据模式下，现有权限管理页面必须改用 `HttpPermissionService`，不得继续以浏览器内 Mock 角色作为飞书映射目标。后端同时提供角色列表、创建、编辑、状态变更、权限配置、成员查询、成员分配和移除 API；现有 `PermissionService` 接口保持不变，通过服务工厂在 Mock 与 HTTP 实现间切换。

## 10. 前端体验

### 10.1 登录页

- 保持当前桌面双栏风格。
- `飞书扫码登录` 为正式员工主入口。
- `临时员工登录` 为次入口，展开手机号与密码。
- 移除短信验证码入口。
- 跳转前显示“正在前往飞书”，并禁用重复操作。

### 10.2 回调结果页

页面按顺序展示：

1. 正在验证企业身份。
2. 正在同步员工资料与角色。
3. 登录成功并进入工作台。

失败时保留可执行动作：重新扫码或返回临时员工登录。

### 10.3 权限管理

权限管理增加“飞书角色映射”标签页，展示：

- 飞书角色名称。
- 对应 ERP 角色。
- 映射状态。
- 当前成员数。
- 最近同步时间。
- 最近同步异常。
- 编辑、停用或删除映射操作。

敏感 ERP 角色不出现在可选项中。

## 11. 错误处理

统一业务错误码包括：

- `FEISHU_NOT_CONFIGURED`
- `FEISHU_STATE_INVALID`
- `FEISHU_CALLBACK_EXPIRED`
- `FEISHU_TENANT_NOT_ALLOWED`
- `FEISHU_IDENTITY_CONFLICT`
- `FEISHU_USER_UNAVAILABLE`
- `FEISHU_ROLE_SYNC_DEGRADED`
- `USER_DISABLED`
- `LOGIN_FAILED`

用户提示不得包含飞书原始令牌、应用凭证、数据库键或调用堆栈。外部网络失败使用有限超时，不在回调请求中进行无限重试。

## 12. 配置

- `ERP_FEISHU_ENABLED`
- `ERP_FEISHU_APP_ID`
- `ERP_FEISHU_APP_SECRET`
- `ERP_FEISHU_REDIRECT_URI`
- `ERP_FEISHU_ALLOWED_TENANT_KEY`
- `ERP_FEISHU_MOCK_ENABLED`
- `ERP_AUTH_SESSION_TTL`
- `ERP_AUTH_TICKET_TTL`

`App Secret` 仅通过后端环境变量或正式密钥管理服务注入。仓库只提交 `.env.example` 中的变量名，不提交真实值。

飞书应用需要申请并由企业管理员批准：登录用户基本信息、手机号只读、通讯录用户与部门只读、业务角色及角色成员只读权限。手机号、部门等扩展权限缺失时可以自动开户，但对应资料保持待完善；业务角色权限缺失时按角色同步降级策略处理。

## 13. 测试与验收

### 13.1 后端自动化测试

- 授权地址与 state Cookie。
- 正确、错误、过期和重复 state。
- 非本企业用户拒绝且无副作用。
- 无手机号首次登录自动开户。
- 唯一手机号已有员工自动绑定。
- 手机号冲突拒绝自动合并。
- 并发首次登录只创建一个账号。
- 正式员工密码登录被拒绝。
- 临时员工飞书登录被拒绝。
- 停用或离职状态拒绝登录。
- 飞书角色映射、移除与本地角色保留。
- 角色接口失败时安全降级。
- 一次性票据过期与重复消费。
- 会话哈希、过期和撤销。

### 13.2 前端自动化测试

- 正式与临时登录入口切换。
- 飞书登录可用和不可用状态。
- 跳转防重复点击。
- 回调处理中、成功、降级和失败状态。
- 成功后保存 ERP 会话并跳转原目标页。
- 飞书角色映射列表、编辑、删除和敏感角色过滤。

### 13.3 最终验收

- 前端全量测试与构建通过。
- 后端单元、API 和数据库迁移测试通过。
- 使用本地模拟飞书服务验证完整流程。
- 使用真实企业应用完成一次扫码验收。
- 验证自动开户员工可进入工作台但没有未授权业务权限。
- 验证飞书角色变化在下一次登录后反映到 ERP 权限。
- 验证非本企业飞书账号无法创建任何数据。

## 14. 迁移与发布顺序

1. 合并权限管理完成版作为开发基线。
2. 执行数据库迁移并种子化基础角色、权限和本地管理员。
3. 上线持久账号、RBAC、会话与审计，但保持飞书登录关闭。
4. 配置飞书应用权限、可用范围、回调地址和企业标识。
5. 在测试环境启用飞书登录并完成真实扫码验收。
6. 上线登录页改版和角色映射管理。
7. 生产启用飞书登录，监控回调错误、自动开户与角色同步审计。
