# BeBefish ERP 飞书登录与业务角色映射实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在 BeBefish ERP 中交付企业飞书 OAuth 登录、首次扫码自动开户、正式/临时员工登录策略、飞书业务角色映射、数据库会话与审计，以及对应登录和权限管理界面。

**Architecture:** 以后端 MySQL 为员工、账号、RBAC、飞书身份、会话和审计的唯一真实来源；认证应用服务只依赖领域端口，飞书 HTTP 与本地模拟实现位于基础设施层。浏览器通过后端生成的 OAuth `state` Cookie 跳转飞书，回调成功后只携带 60 秒一次性票据回前端，再由前端换取持久 ERP Bearer 会话。

**Tech Stack:** Java 21、Spring Boot 3.3、Spring Security、Spring Data JPA、Flyway、MySQL、JUnit 5、MockMvc、Vue 3、TypeScript 5.7、Vue Router、Vitest、Vue Test Utils、Vite。

**Spec:** `docs/superpowers/specs/2026-09-04-feishu-login-design.md`

## Global Constraints

- 正式员工只允许 `feishu` 登录；临时员工只允许 `password` 登录；短信验证码接口和前端入口删除。
- 首次飞书扫码以 `tenant_key + union_id/open_id` 为主身份，手机号仅用于唯一既有员工合并，不是开户前置条件。
- OAuth 用户 `tenant_key` 不匹配时返回 `FEISHU_TENANT_NOT_ALLOWED`，且不得产生员工、用户、绑定、票据或会话数据。
- `sys_user_role.assignment_source` 只能为 `LOCAL` 或 `FEISHU`；登录同步只替换当前用户的 `FEISHU` 关系并保留 `LOCAL` 关系。
- 自动开户始终分配 `BASIC_EMPLOYEE`；`SUPER_ADMIN`、权限管理员及 `sensitive = true` 的角色不得作为飞书映射目标。
- 飞书角色读取失败时完成登录，但清除本次飞书来源角色并返回稳定警告 `FEISHU_ROLE_SYNC_DEGRADED`。
- 数据库只保存会话令牌、一次性票据的 SHA-256 哈希，不保存其明文，也不长期保存飞书访问令牌或刷新令牌。
- 飞书登录开启时 `ERP_FEISHU_APP_ID`、`ERP_FEISHU_APP_SECRET`、`ERP_FEISHU_REDIRECT_URI`、`ERP_FEISHU_ALLOWED_TENANT_KEY` 必须完整；生产环境禁止模拟飞书服务。
- 真实 App ID、App Secret 和企业标识不得提交；仓库仅在 `.env.example` 记录变量名与安全示例。
- 保持现有产品、组织和权限 UI 行为；不得修改权限设计中列出的五个用户本地组织文件。
- 后端权限管理接口使用 `system:role:view` / `system:role:manage` 做服务端授权，前端隐藏按钮不是安全边界。

---

## File map

- `backend/src/main/resources/db/migration/V10__identity_auth_rbac.sql`：组织、员工、账号、RBAC、飞书身份/映射、会话、票据、审计表及内置权限和角色种子。
- `backend/src/main/java/com/bebefish/erp/identity/**`：员工、账号、飞书身份的领域记录、仓储端口和 JPA 适配器。
- `backend/src/main/java/com/bebefish/erp/auth/**`：登录策略、OAuth 编排、票据、会话、审计及认证 API。
- `backend/src/main/java/com/bebefish/erp/feishu/**`：飞书配置、OAuth/通讯录端口、真实 HTTP 客户端和受 Profile 约束的本地模拟实现。
- `backend/src/main/java/com/bebefish/erp/authorization/**`：数据库 RBAC 仓储、飞书角色同步、权限管理服务与 REST API。
- `frontend/src/services/auth.ts`、`frontend/src/services/authSession.ts`、`frontend/src/types/auth.ts`：飞书状态/票据交换与扩展登录结果。
- `frontend/src/views/LoginView.vue`、`frontend/src/views/FeishuLoginResultView.vue`、`frontend/src/router/index.ts`：正式/临时登录入口和回调结果页。
- `frontend/src/features/permission/httpPermissionService.ts`：现有 `PermissionService` 的生产 HTTP 实现。
- `frontend/src/features/permission/components/FeishuRoleMappingPanel.vue`：飞书角色映射列表、编辑、停用、删除与同步。
- `frontend/src/features/permission/views/PermissionManagementView.vue`：在现有权限管理内编排映射页签，不改变原三个页签行为。

---

### Task 1: 建立身份、RBAC、会话与审计数据库基线

**Files:**
- Create: `backend/src/main/resources/db/migration/V10__identity_auth_rbac.sql`
- Modify: `backend/src/test/java/com/bebefish/erp/common/persistence/FlywayMigrationTest.java`
- Modify: `backend/src/test/java/com/bebefish/erp/common/persistence/MigrationCompatibilityTest.java`

**Interfaces:**
- Consumes: Flyway 现有 `V1` 至 `V9` MySQL 迁移约定与 `datetime(3)` 时间精度。
- Produces: `department`、`position`、`employee`、`sys_user`、`sys_permission`、`sys_role`、`sys_role_permission`、`sys_user_role`、`sys_feishu_identity`、`sys_feishu_role_mapping`、`sys_auth_session`、`sys_login_ticket`、`sys_login_audit`。

- [ ] **Step 1: 写迁移失败测试**

```java
@Test
void migratesIdentityRbacAndAuthTablesWithRequiredSeeds() {
    assertThat(jdbc.queryForObject("select count(*) from employee", Integer.class)).isZero();
    assertThat(jdbc.queryForObject("select count(*) from sys_role where code = 'BASIC_EMPLOYEE'", Integer.class)).isEqualTo(1);
    assertThat(jdbc.queryForObject("select sensitive from sys_role where code = 'SUPER_ADMIN'", Boolean.class)).isTrue();
    assertThat(jdbc.queryForObject("select count(*) from sys_permission where code = 'system:role:manage'", Integer.class)).isEqualTo(1);
}
```

同时增加约束测试：重复非空 `sys_user.mobile`、重复 `(tenant_key, union_id)`、同用户同角色同来源、明文重复会话哈希均由唯一索引拒绝；`employment_type`、员工状态、账号状态和 `assignment_source` 的非法值由检查约束拒绝。

- [ ] **Step 2: 运行迁移测试确认失败**

Run: `cd backend && mvn -Dtest=FlywayMigrationTest,MigrationCompatibilityTest test`

Expected: FAIL，缺少 `employee` 或 `sys_role` 表。

- [ ] **Step 3: 编写 V10 迁移和安全种子**

```sql
create table sys_feishu_identity (
    id bigint primary key auto_increment,
    user_id bigint not null,
    tenant_key varchar(128) not null,
    open_id varchar(128) not null,
    union_id varchar(128) null,
    display_name varchar(100) not null,
    avatar_url varchar(1000) null,
    bound_at datetime(3) not null,
    last_verified_at datetime(3) not null,
    unique key uk_feishu_identity_user (user_id),
    unique key uk_feishu_identity_open (tenant_key, open_id),
    unique key uk_feishu_identity_union (tenant_key, union_id),
    constraint fk_feishu_identity_user foreign key (user_id) references sys_user (id)
) engine = InnoDB default charset = utf8mb4 collate = utf8mb4_unicode_ci;
```

`employee.mobile` 和 `sys_user.mobile` 允许为空但非空唯一；`password_hash` 允许为空并由应用层强制临时员工必填。种子包含完整 `PERMISSION_MODULES` 权限编码、`SUPER_ADMIN`、`PERMISSION_ADMIN`、`BASIC_EMPLOYEE`，其中后两项分别标记敏感和非敏感；创建手机号 `13800138000` 的临时系统管理员并使用固定 BCrypt 开发密码哈希，绝不存明文。

- [ ] **Step 4: 运行迁移测试确认通过**

Run: `cd backend && mvn -Dtest=FlywayMigrationTest,MigrationCompatibilityTest test`

Expected: PASS，且 Flyway schema history 最新版本为 `10`。

- [ ] **Step 5: 提交数据库基线**

```bash
git add backend/src/main/resources/db/migration/V10__identity_auth_rbac.sql backend/src/test/java/com/bebefish/erp/common/persistence/FlywayMigrationTest.java backend/src/test/java/com/bebefish/erp/common/persistence/MigrationCompatibilityTest.java
git commit -m "feat: add persistent identity auth and rbac schema"
```

---

### Task 2: 用数据库适配器替换内存账号与 RBAC

**Files:**
- Modify: `backend/src/main/java/com/bebefish/erp/auth/domain/UserAccount.java`
- Modify: `backend/src/main/java/com/bebefish/erp/auth/domain/UserAccountRepository.java`
- Delete: `backend/src/main/java/com/bebefish/erp/auth/infrastructure/InMemoryUserAccountRepository.java`
- Create: `backend/src/main/java/com/bebefish/erp/auth/infrastructure/UserAccountJpaEntity.java`
- Create: `backend/src/main/java/com/bebefish/erp/auth/infrastructure/SpringDataUserAccountRepository.java`
- Create: `backend/src/main/java/com/bebefish/erp/auth/infrastructure/JpaUserAccountRepository.java`
- Modify: `backend/src/main/java/com/bebefish/erp/authorization/domain/Role.java`
- Modify: `backend/src/main/java/com/bebefish/erp/authorization/domain/RoleRepository.java`
- Delete: `backend/src/main/java/com/bebefish/erp/authorization/infrastructure/InMemoryRoleRepository.java`
- Create: `backend/src/main/java/com/bebefish/erp/authorization/infrastructure/JdbcRoleRepository.java`
- Create: `backend/src/test/java/com/bebefish/erp/auth/infrastructure/JpaUserAccountRepositoryTest.java`
- Create: `backend/src/test/java/com/bebefish/erp/authorization/infrastructure/JdbcRoleRepositoryTest.java`
- Modify: `backend/src/test/java/com/bebefish/erp/authorization/application/AuthorizationResolverTest.java`

**Interfaces:**
- Consumes: Task 1 数据表。
- Produces: `UserAccount(long id, long employeeId, String mobile, String passwordHash, EmploymentType employmentType, UserStatus userStatus, EmployeeStatus employeeStatus, String displayName, String avatarUrl)`；`UserAccountRepository.findById(long)`、`findByMobile(String)`、`save(UserAccount)`、`recordLogin(long,String)`；`RoleRepository.findEnabledByMemberKey(String)` 保持兼容并新增按 `userId` 解析路径。

- [ ] **Step 1: 写账号持久化和角色并集失败测试**

```java
@Test
void loadsAccountWithEmployeeLoginPolicy() {
    var account = repository.findByMobile("13900000001").orElseThrow();
    assertThat(account.employmentType()).isEqualTo(EmploymentType.TEMPORARY);
    assertThat(account.userStatus()).isEqualTo(UserStatus.ENABLED);
}

@Test
void returnsUnionOfLocalAndFeishuAssignmentsForUser() {
    assertThat(repository.findEnabledByUserId(userId)).extracting(Role::code)
            .containsExactlyInAnyOrder("BASIC_EMPLOYEE", "PRODUCT_OPERATOR");
}
```

- [ ] **Step 2: 运行仓储测试确认失败**

Run: `cd backend && mvn -Dtest=JpaUserAccountRepositoryTest,JdbcRoleRepositoryTest,AuthorizationResolverTest test`

Expected: FAIL，数据库适配器和新账号字段尚不存在。

- [ ] **Step 3: 实现账号 JPA 映射和 RBAC JDBC 查询**

```java
public interface UserAccountRepository {
    Optional<UserAccount> findById(long id);
    Optional<UserAccount> findByMobile(String mobile);
    UserAccount save(UserAccount account);
    void recordLogin(long userId, String loginMethod);
}

public interface RoleRepository {
    List<Role> findEnabledByUserId(long userId);
    List<Role> findEnabledByMemberKey(String memberKey);
    List<PermissionDefinition> findAllPermissions();
}
```

`JdbcRoleRepository` 一次加载用户两种来源的启用角色和权限；`Role` 增加 `id`、`sensitive`，继续强制 `SUPER_ADMIN` 为系统、启用、公司范围角色。`AuthorizationResolver` 新增 `resolve(long userId)` 并保留现有手机号入口直到认证调用全部迁移完成。

- [ ] **Step 4: 运行仓储和授权测试确认通过**

Run: `cd backend && mvn -Dtest=JpaUserAccountRepositoryTest,JdbcRoleRepositoryTest,AuthorizationResolverTest test`

Expected: PASS。

- [ ] **Step 5: 提交持久账号与 RBAC**

```bash
git add backend/src/main/java/com/bebefish/erp/auth backend/src/main/java/com/bebefish/erp/authorization backend/src/test/java/com/bebefish/erp/auth/infrastructure backend/src/test/java/com/bebefish/erp/authorization
git commit -m "feat: persist user accounts and authorization"
```

---

### Task 3: 实现哈希会话、一次性票据和登录审计

**Files:**
- Modify: `backend/src/main/java/com/bebefish/erp/auth/domain/AuthenticatedUser.java`
- Modify: `backend/src/main/java/com/bebefish/erp/auth/domain/TokenIssuer.java`
- Delete: `backend/src/main/java/com/bebefish/erp/auth/infrastructure/InMemoryTokenIssuer.java`
- Create: `backend/src/main/java/com/bebefish/erp/auth/domain/SessionRepository.java`
- Create: `backend/src/main/java/com/bebefish/erp/auth/domain/LoginAuditRepository.java`
- Create: `backend/src/main/java/com/bebefish/erp/auth/application/LoginTicketService.java`
- Create: `backend/src/main/java/com/bebefish/erp/auth/infrastructure/Sha256TokenHasher.java`
- Create: `backend/src/main/java/com/bebefish/erp/auth/infrastructure/JdbcSessionRepository.java`
- Create: `backend/src/main/java/com/bebefish/erp/auth/infrastructure/JdbcLoginTicketRepository.java`
- Create: `backend/src/main/java/com/bebefish/erp/auth/infrastructure/JdbcLoginAuditRepository.java`
- Create: `backend/src/test/java/com/bebefish/erp/auth/infrastructure/JdbcSessionRepositoryTest.java`
- Create: `backend/src/test/java/com/bebefish/erp/auth/application/LoginTicketServiceTest.java`

**Interfaces:**
- Consumes: Task 1 会话、票据、审计表和 Task 2 账号/RBAC。
- Produces: `SessionRepository.issue(AuthenticatedUser,String,Duration)`、`resolve(String)`、`revoke(String)`；`LoginTicketService.issue(long,List<String>)`、`consume(String)`；`LoginAuditRepository.record(LoginAuditEvent)`。

- [ ] **Step 1: 写过期、撤销、重复消费与明文泄露失败测试**

```java
@Test
void ticketCanOnlyBeConsumedOnce() {
    var ticket = service.issue(userId, List.of("FEISHU_ROLE_SYNC_DEGRADED"));
    assertThat(service.consume(ticket).userId()).isEqualTo(userId);
    assertThatThrownBy(() -> service.consume(ticket))
            .isInstanceOf(AuthException.class)
            .extracting("code").isEqualTo("FEISHU_CALLBACK_EXPIRED");
    assertThat(jdbc.queryForObject("select count(*) from sys_login_ticket where ticket_hash = ?", Integer.class, ticket)).isZero();
}
```

使用可变 `Clock` 验证 60 秒票据和配置会话 TTL；断言撤销后、过期后 `resolve` 返回 `UNAUTHORIZED`，数据库中原始令牌和票据均查不到。

- [ ] **Step 2: 运行会话测试确认失败**

Run: `cd backend && mvn -Dtest=JdbcSessionRepositoryTest,LoginTicketServiceTest test`

Expected: FAIL，持久实现尚不存在。

- [ ] **Step 3: 实现安全随机值、SHA-256 哈希和原子消费**

```java
public String hash(String raw) {
    return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
            .digest(raw.getBytes(StandardCharsets.UTF_8)));
}
```

使用 `SecureRandom` 生成 32 字节 URL-safe 无填充令牌。票据消费使用单事务 `select ... for update`，只有 `consumed_at is null and expires_at > now` 才更新并返回用户；会话解析只返回未撤销、未过期记录，再实时加载用户与授权，确保停用用户立即失效。

- [ ] **Step 4: 运行会话测试确认通过**

Run: `cd backend && mvn -Dtest=JdbcSessionRepositoryTest,LoginTicketServiceTest test`

Expected: PASS。

- [ ] **Step 5: 提交持久会话、票据和审计**

```bash
git add backend/src/main/java/com/bebefish/erp/auth backend/src/test/java/com/bebefish/erp/auth
git commit -m "feat: persist auth sessions tickets and audits"
```

---

### Task 4: 建立飞书配置、企业校验、HTTP 客户端和本地模拟服务

**Files:**
- Modify: `backend/src/main/resources/application.yml`
- Create: `backend/src/main/java/com/bebefish/erp/feishu/FeishuProperties.java`
- Create: `backend/src/main/java/com/bebefish/erp/feishu/FeishuOAuthClient.java`
- Create: `backend/src/main/java/com/bebefish/erp/feishu/FeishuDirectoryClient.java`
- Create: `backend/src/main/java/com/bebefish/erp/feishu/FeishuOAuthIdentity.java`
- Create: `backend/src/main/java/com/bebefish/erp/feishu/FeishuEmployeeProfile.java`
- Create: `backend/src/main/java/com/bebefish/erp/feishu/FeishuBusinessRole.java`
- Create: `backend/src/main/java/com/bebefish/erp/feishu/FeishuAvailability.java`
- Create: `backend/src/main/java/com/bebefish/erp/feishu/FeishuConfigurationValidator.java`
- Create: `backend/src/main/java/com/bebefish/erp/feishu/infrastructure/HttpFeishuClient.java`
- Create: `backend/src/main/java/com/bebefish/erp/feishu/infrastructure/MockFeishuClient.java`
- Create: `backend/src/test/java/com/bebefish/erp/feishu/FeishuConfigurationValidatorTest.java`
- Create: `backend/src/test/java/com/bebefish/erp/feishu/infrastructure/HttpFeishuClientTest.java`

**Interfaces:**
- Consumes: 环境变量 `ERP_FEISHU_*` 和 Spring Profile。
- Produces: `FeishuOAuthClient.authorizationUri(String)`、`exchangeCode(String)`；`FeishuDirectoryClient.currentTenantKey()`、`employeeProfile(String)`、`businessRoles(String)`；不含任何长期令牌存储。

- [ ] **Step 1: 写配置矩阵、租户不一致和有限超时失败测试**

```java
@Test
void productionRejectsEnabledMockClient() {
    var properties = enabledProperties(true);
    assertThatThrownBy(() -> validator.validate(properties, Set.of("prod"), client))
            .hasMessageContaining("生产环境禁止启用飞书模拟服务");
}

@Test
void appTenantMustMatchAllowedTenant() {
    when(client.currentTenantKey()).thenReturn("tenant-other");
    assertThat(validator.validate(properties, Set.of("test"), client).available()).isFalse();
}
```

HTTP 测试使用本地 `MockWebServer` 等价测试服务验证授权码交换、OAuth 用户、通讯录资料、业务角色 JSON 映射，以及连接/读取超时均有限。

- [ ] **Step 2: 运行飞书配置和客户端测试确认失败**

Run: `cd backend && mvn -Dtest=FeishuConfigurationValidatorTest,HttpFeishuClientTest test`

Expected: FAIL，端口和配置类尚不存在。

- [ ] **Step 3: 实现配置绑定、启动校验、真实 HTTP 和 Profile 限定模拟实现**

```yaml
erp:
  feishu:
    enabled: ${ERP_FEISHU_ENABLED:false}
    app-id: ${ERP_FEISHU_APP_ID:}
    app-secret: ${ERP_FEISHU_APP_SECRET:}
    redirect-uri: ${ERP_FEISHU_REDIRECT_URI:}
    allowed-tenant-key: ${ERP_FEISHU_ALLOWED_TENANT_KEY:}
    mock-enabled: ${ERP_FEISHU_MOCK_ENABLED:false}
  auth:
    session-ttl: ${ERP_AUTH_SESSION_TTL:PT12H}
    ticket-ttl: ${ERP_AUTH_TICKET_TTL:PT60S}
```

真实客户端用 JDK `HttpClient`，连接超时 3 秒、请求超时 5 秒，日志只记录错误类别和飞书 request id。模拟客户端只在 `local`/`test` 且显式开启时注册，识别 `mock-no-mobile`、`mock-existing-mobile`、`mock-role-degraded` 等授权码场景。

- [ ] **Step 4: 运行飞书配置和客户端测试确认通过**

Run: `cd backend && mvn -Dtest=FeishuConfigurationValidatorTest,HttpFeishuClientTest test`

Expected: PASS。

- [ ] **Step 5: 提交飞书基础设施**

```bash
git add backend/src/main/resources/application.yml backend/src/main/java/com/bebefish/erp/feishu backend/src/test/java/com/bebefish/erp/feishu
git commit -m "feat: add feishu oauth and directory clients"
```

---

### Task 5: 实现首次扫码开户、身份合并和飞书角色同步

**Files:**
- Create: `backend/src/main/java/com/bebefish/erp/identity/domain/Employee.java`
- Create: `backend/src/main/java/com/bebefish/erp/identity/domain/FeishuIdentity.java`
- Create: `backend/src/main/java/com/bebefish/erp/identity/domain/EmployeeRepository.java`
- Create: `backend/src/main/java/com/bebefish/erp/identity/domain/FeishuIdentityRepository.java`
- Create: `backend/src/main/java/com/bebefish/erp/identity/application/EmployeeProvisioningService.java`
- Create: `backend/src/main/java/com/bebefish/erp/identity/infrastructure/JdbcEmployeeRepository.java`
- Create: `backend/src/main/java/com/bebefish/erp/identity/infrastructure/JdbcFeishuIdentityRepository.java`
- Create: `backend/src/main/java/com/bebefish/erp/authorization/application/FeishuRoleSyncService.java`
- Create: `backend/src/main/java/com/bebefish/erp/authorization/domain/FeishuRoleMappingRepository.java`
- Create: `backend/src/main/java/com/bebefish/erp/authorization/infrastructure/JdbcFeishuRoleMappingRepository.java`
- Create: `backend/src/test/java/com/bebefish/erp/identity/application/EmployeeProvisioningServiceTest.java`
- Create: `backend/src/test/java/com/bebefish/erp/identity/application/EmployeeProvisioningConcurrencyTest.java`
- Create: `backend/src/test/java/com/bebefish/erp/authorization/application/FeishuRoleSyncServiceTest.java`

**Interfaces:**
- Consumes: Task 2 账号/RBAC、Task 4 飞书身份和资料。
- Produces: `ProvisionedUser provision(FeishuOAuthIdentity, FeishuEmployeeProfile)`；`List<String> sync(long userId,String tenantKey,List<FeishuBusinessRole>)`，返回警告码集合。

- [ ] **Step 1: 写匹配顺序、冲突、状态和角色来源失败测试**

```java
@Test
void createsFormalEmployeeWithoutMobileAndAssignsBasicRole() {
    var result = service.provision(identity("tenant-a", "open-1", "union-1"), profile(null));
    assertThat(result.account().employmentType()).isEqualTo(EmploymentType.FORMAL);
    assertThat(result.employee().employeeNo()).matches("FS-[A-Z0-9]{8}");
    assertThat(result.employee().profileComplete()).isFalse();
    assertThat(roleAssignments.findByUserId(result.account().id()))
            .anyMatch(row -> row.roleCode().equals("BASIC_EMPLOYEE") && row.source().equals("FEISHU"));
}
```

分别覆盖 union id、open id、唯一标准化手机号、手机号多条冲突、已绑定其他身份、临时员工拒绝绑定、禁用/离职拒绝、资料刷新、部门映射缺失和 10 路并发只生成一个用户。

- [ ] **Step 2: 运行开户和角色同步测试确认失败**

Run: `cd backend && mvn -Dtest=EmployeeProvisioningServiceTest,EmployeeProvisioningConcurrencyTest,FeishuRoleSyncServiceTest test`

Expected: FAIL，开户与同步服务尚不存在。

- [ ] **Step 3: 实现事务匹配、唯一约束重试和来源隔离同步**

```java
@Transactional
public ProvisionedUser provision(FeishuOAuthIdentity oauth, FeishuEmployeeProfile profile) {
    requireAllowedTenant(oauth.tenantKey());
    return identities.find(oauth.tenantKey(), oauth.unionId(), oauth.openId())
            .map(existing -> updateExisting(existing, oauth, profile))
            .orElseGet(() -> mergeUniqueMobileOrCreate(oauth, profile));
}
```

手机号标准化为中国大陆 11 位格式或原始 E.164；并发唯一冲突捕获后重新按飞书稳定身份读取。角色同步先解析启用映射，再单事务删除该用户旧 `FEISHU` 关系并插入 `BASIC_EMPLOYEE + mapped roles`；目录异常时改为仅基础角色并返回 `FEISHU_ROLE_SYNC_DEGRADED`，`LOCAL` 行不参与删除。

- [ ] **Step 4: 运行开户和角色同步测试确认通过**

Run: `cd backend && mvn -Dtest=EmployeeProvisioningServiceTest,EmployeeProvisioningConcurrencyTest,FeishuRoleSyncServiceTest test`

Expected: PASS。

- [ ] **Step 5: 提交开户与角色同步**

```bash
git add backend/src/main/java/com/bebefish/erp/identity backend/src/main/java/com/bebefish/erp/authorization backend/src/test/java/com/bebefish/erp/identity backend/src/test/java/com/bebefish/erp/authorization
git commit -m "feat: provision feishu employees and sync roles"
```

---

### Task 6: 完成密码策略和飞书 OAuth API 闭环

**Files:**
- Modify: `backend/src/main/java/com/bebefish/erp/auth/application/AuthService.java`
- Modify: `backend/src/main/java/com/bebefish/erp/auth/application/LoginResult.java`
- Modify: `backend/src/main/java/com/bebefish/erp/auth/api/AuthController.java`
- Create: `backend/src/main/java/com/bebefish/erp/auth/application/FeishuLoginService.java`
- Create: `backend/src/main/java/com/bebefish/erp/auth/application/OAuthStateService.java`
- Create: `backend/src/main/java/com/bebefish/erp/auth/api/FeishuAuthController.java`
- Create: `backend/src/main/java/com/bebefish/erp/auth/api/FeishuExchangeRequest.java`
- Delete: `backend/src/main/java/com/bebefish/erp/auth/application/SendSmsCodeCommand.java`
- Delete: `backend/src/main/java/com/bebefish/erp/auth/application/SmsLoginCommand.java`
- Delete: `backend/src/main/java/com/bebefish/erp/auth/domain/SmsCodeStore.java`
- Delete: `backend/src/main/java/com/bebefish/erp/auth/infrastructure/InMemorySmsCodeStore.java`
- Modify: `backend/src/main/java/com/bebefish/erp/common/config/SecurityConfig.java`
- Modify: `backend/src/main/java/com/bebefish/erp/common/security/BearerTokenAuthenticationFilter.java`
- Modify: `backend/src/test/java/com/bebefish/erp/auth/application/AuthServiceTest.java`
- Modify: `backend/src/test/java/com/bebefish/erp/auth/api/AuthControllerTest.java`
- Create: `backend/src/test/java/com/bebefish/erp/auth/api/FeishuAuthControllerTest.java`
- Create: `backend/src/test/java/com/bebefish/erp/auth/application/FeishuLoginServiceTest.java`

**Interfaces:**
- Consumes: Tasks 3–5 票据、会话、飞书客户端、开户和角色同步。
- Produces: 设计第 9.2 节全部认证 API；`LoginResult(accessToken,employeeId,mobile,displayName,avatarUrl,roles,permissions,loginMethod,warnings)`。

- [ ] **Step 1: 写 API 安全失败测试**

```java
mockMvc.perform(get("/api/auth/feishu/authorize"))
        .andExpect(status().isFound())
        .andExpect(cookie().httpOnly("erp_feishu_state", true))
        .andExpect(cookie().sameSite("erp_feishu_state", "Lax"))
        .andExpect(header().string("Location", containsString("state=")));
```

覆盖正确、缺失、不一致、过期、重复 state；非本企业无副作用；callback URL 只含一次性 ticket；ticket 过期/重复；正式员工密码拒绝、临时员工密码成功、临时员工飞书拒绝、停用/离职拒绝；短信两个旧路由均为 404。

- [ ] **Step 2: 运行认证 API 测试确认失败**

Run: `cd backend && mvn -Dtest=AuthServiceTest,AuthControllerTest,FeishuAuthControllerTest,FeishuLoginServiceTest test`

Expected: FAIL，飞书端点不存在且短信端点仍存在。

- [ ] **Step 3: 实现 state Cookie、回调编排、票据交换和登录审计**

```java
@GetMapping("/callback")
public void callback(@RequestParam String code, @RequestParam String state,
                     @CookieValue(name = STATE_COOKIE, required = false) String cookie,
                     HttpServletRequest request, HttpServletResponse response) {
    var ticket = loginService.callback(code, stateService.consume(state, cookie), clientContext(request));
    response.sendRedirect(frontendResultUri(ticket));
}
```

state 采用 32 字节随机值、Cookie Max-Age 300 秒、HttpOnly、SameSite=Lax，生产 HTTPS 时 Secure。回调顺序必须是 state → code → OAuth tenant → app tenant → 开户 → 角色同步 → ticket；每个成功/失败路径写去敏审计。`SecurityConfig` 仅公开 status/authorize/callback/exchange/password，权限 API继续认证。

- [ ] **Step 4: 运行认证 API 测试确认通过**

Run: `cd backend && mvn -Dtest=AuthServiceTest,AuthControllerTest,FeishuAuthControllerTest,FeishuLoginServiceTest,BearerTokenAuthenticationFilterTest test`

Expected: PASS。

- [ ] **Step 5: 提交认证闭环**

```bash
git add backend/src/main/java/com/bebefish/erp/auth backend/src/main/java/com/bebefish/erp/common backend/src/test/java/com/bebefish/erp/auth backend/src/test/java/com/bebefish/erp/common/security
git commit -m "feat: complete feishu oauth login flow"
```

---

### Task 7: 实现真实权限管理和飞书角色映射 REST API

**Files:**
- Create: `backend/src/main/java/com/bebefish/erp/authorization/application/PermissionManagementService.java`
- Create: `backend/src/main/java/com/bebefish/erp/authorization/application/FeishuRoleMappingService.java`
- Create: `backend/src/main/java/com/bebefish/erp/authorization/api/PermissionController.java`
- Create: `backend/src/main/java/com/bebefish/erp/authorization/api/FeishuRoleMappingController.java`
- Create: `backend/src/main/java/com/bebefish/erp/authorization/api/PermissionDtos.java`
- Create: `backend/src/test/java/com/bebefish/erp/authorization/api/PermissionControllerTest.java`
- Create: `backend/src/test/java/com/bebefish/erp/authorization/api/FeishuRoleMappingControllerTest.java`
- Create: `backend/src/test/java/com/bebefish/erp/authorization/application/PermissionManagementServiceTest.java`

**Interfaces:**
- Consumes: Task 2 RBAC 数据库、Task 4 飞书角色目录、Task 5 映射仓储。
- Produces: 角色列表/详情/创建/编辑/状态/配置/成员查询/成员分配/移除 API，以及设计第 9.2 节五个飞书映射 API。

- [ ] **Step 1: 写权限鉴权、系统保护和敏感映射失败测试**

```java
mockMvc.perform(put("/api/permissions/feishu-role-mappings/fs-finance")
        .with(authentication(permissionAdmin()))
        .contentType(APPLICATION_JSON)
        .content("{\"feishuRoleName\":\"财务\",\"erpRoleId\":" + superAdminId + ",\"enabled\":true}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("SENSITIVE_ROLE_MAPPING_FORBIDDEN"));
```

覆盖 `system:role:view` 与 `system:role:manage`、角色编码唯一、系统角色不可改、权限依赖、成员重复跳过、超级管理员不可移除、映射 CRUD、同步成员数/错误时间和无权限飞书角色读取失败。

- [ ] **Step 2: 运行权限 API 测试确认失败**

Run: `cd backend && mvn -Dtest=PermissionControllerTest,FeishuRoleMappingControllerTest,PermissionManagementServiceTest test`

Expected: FAIL，控制器和服务尚不存在。

- [ ] **Step 3: 实现事务服务、稳定 DTO 和方法级鉴权**

```java
@PutMapping("/feishu-role-mappings/{feishuRoleId}")
@PreAuthorize("hasAuthority('system:role:manage')")
public ApiResponse<FeishuRoleMappingResponse> save(
        @PathVariable String feishuRoleId,
        @Valid @RequestBody SaveFeishuRoleMappingRequest request) {
    return ApiResponse.success(service.save(feishuRoleId, request));
}
```

API 字段与前端 `PermissionService` 类型一一对应，时间统一返回 ISO-8601。候选映射角色查询必须附带 `sensitive=false and status='enabled'`，保存时再次按 ID 校验，避免伪造请求绕过列表过滤。

- [ ] **Step 4: 运行权限 API 测试确认通过**

Run: `cd backend && mvn -Dtest=PermissionControllerTest,FeishuRoleMappingControllerTest,PermissionManagementServiceTest test`

Expected: PASS。

- [ ] **Step 5: 提交权限和映射 API**

```bash
git add backend/src/main/java/com/bebefish/erp/authorization backend/src/test/java/com/bebefish/erp/authorization
git commit -m "feat: expose persistent permission and feishu mapping APIs"
```

---

### Task 8: 改造前端登录页并新增飞书回调结果页

**Files:**
- Modify: `frontend/src/types/auth.ts`
- Modify: `frontend/src/services/auth.ts`
- Modify: `frontend/src/services/authSession.ts`
- Modify: `frontend/src/views/LoginView.vue`
- Create: `frontend/src/views/FeishuLoginResultView.vue`
- Modify: `frontend/src/router/index.ts`
- Modify: `frontend/src/views/LoginView.test.ts`
- Create: `frontend/src/views/FeishuLoginResultView.test.ts`
- Modify: `frontend/src/services/auth.test.ts`
- Modify: `frontend/src/router/index.test.ts`

**Interfaces:**
- Consumes: Task 6 `status`、`authorize`、`exchange` 和扩展 `LoginResult`。
- Produces: `getFeishuStatus(): Promise<FeishuLoginStatus>`、`exchangeFeishuTicket(ticket:string): Promise<LoginResult>`；公共路由 `/auth/feishu/result`。

- [ ] **Step 1: 写正式/临时入口和回调状态失败测试**

```ts
it('prevents duplicate feishu redirects', async () => {
  const wrapper = mount(LoginView, loginHarness());
  await flushPromises();
  await wrapper.get('[data-testid="feishu-login"]').trigger('click');
  await wrapper.get('[data-testid="feishu-login"]').trigger('click');
  expect(assignLocation).toHaveBeenCalledTimes(1);
  expect(wrapper.get('[data-testid="feishu-login"]').attributes('disabled')).toBeDefined();
});
```

覆盖可用/不可用状态、“正在前往飞书”、展开临时员工表单、无短信控件、密码成功；回调页处理中三段文案、成功保存会话并跳原目标、降级警告、失败重新扫码/返回临时登录、缺票据错误。

- [ ] **Step 2: 运行前端认证测试确认失败**

Run: `cd frontend && npm run test:run -- src/services/auth.test.ts src/views/LoginView.test.ts src/views/FeishuLoginResultView.test.ts src/router/index.test.ts`

Expected: FAIL，飞书按钮仍为预留且结果页不存在。

- [ ] **Step 3: 实现服务、会话类型、登录 UI 和回调路由**

```ts
export interface LoginResult {
  accessToken: string;
  employeeId: number;
  mobile: string | null;
  displayName: string;
  avatarUrl: string | null;
  roles: string[];
  permissions: string[];
  loginMethod: 'password' | 'feishu';
  warnings: string[];
}
```

保留现有桌面双栏和角色展示；飞书为默认主按钮，临时登录用折叠区域。回调页只从 URL 读取 ticket 并立即 `history.replaceState` 清理，票据不写 localStorage；仅 ERP access token 和用户快照沿用现有存储方式。

- [ ] **Step 4: 运行前端认证测试确认通过**

Run: `cd frontend && npm run test:run -- src/services/auth.test.ts src/views/LoginView.test.ts src/views/FeishuLoginResultView.test.ts src/router/index.test.ts`

Expected: PASS。

- [ ] **Step 5: 提交前端登录体验**

```bash
git add frontend/src/types/auth.ts frontend/src/services/auth.ts frontend/src/services/authSession.ts frontend/src/views frontend/src/router/index.ts frontend/src/router/index.test.ts
git commit -m "feat: add feishu login and callback experience"
```

---

### Task 9: 接入真实权限服务并添加飞书角色映射页签

**Files:**
- Modify: `frontend/src/features/permission/types.ts`
- Modify: `frontend/src/features/permission/permissionService.ts`
- Create: `frontend/src/features/permission/httpPermissionService.ts`
- Create: `frontend/src/features/permission/httpPermissionService.test.ts`
- Create: `frontend/src/features/permission/components/FeishuRoleMappingPanel.vue`
- Modify: `frontend/src/features/permission/views/PermissionManagementView.vue`
- Modify: `frontend/src/features/permission/PermissionManagementView.test.ts`
- Modify: `frontend/src/services/serviceFactory.test.ts`

**Interfaces:**
- Consumes: Task 7 权限和映射 API，现有 `PermissionService` 接口与 `createService` 工厂。
- Produces: `FeishuRoleMapping`、`FeishuRoleOption`、`SaveFeishuRoleMappingPayload` 类型；`PermissionService` 增加 `listFeishuRoles`、`listFeishuRoleMappings`、`saveFeishuRoleMapping`、`deleteFeishuRoleMapping`、`syncFeishuRoleMappings`。

- [ ] **Step 1: 写 HTTP 契约、服务选择和映射交互失败测试**

```ts
it('never presents sensitive roles as mapping targets', async () => {
  const wrapper = mount(FeishuRoleMappingPanel, mappingHarness({
    roles: [role('SUPER_ADMIN', true), role('PRODUCT_OPERATOR', false)]
  }));
  await wrapper.get('[data-testid="edit-feishu-mapping"]').trigger('click');
  expect(wrapper.text()).toContain('商品运营');
  expect(wrapper.text()).not.toContain('超级管理员');
});
```

覆盖真实模式选 `HttpPermissionService`、全部 REST method/path/body、加载/空/错误、编辑、停用、删除确认、手动同步、成员数/最近同步/最近异常，以及现有权限三个页签回归。

- [ ] **Step 2: 运行权限前端测试确认失败**

Run: `cd frontend && npm run test:run -- src/features/permission/httpPermissionService.test.ts src/features/permission/PermissionManagementView.test.ts src/services/serviceFactory.test.ts`

Expected: FAIL，真实服务和映射页签尚不存在。

- [ ] **Step 3: 实现 HTTP 适配器、工厂选择和第四页签**

```ts
export const permissionService = createService(
  () => createMockPermissionService(organizationService),
  () => createHttpPermissionService()
);
```

映射面板按飞书角色一行展示名称、ERP 角色、状态、成员数、最近同步、最近异常；敏感角色既由后端剔除也在组件中防御过滤。映射页签拥有独立加载/保存状态，不复用角色配置脏状态，避免破坏原页面切换保护。

- [ ] **Step 4: 运行权限前端测试确认通过**

Run: `cd frontend && npm run test:run -- src/features/permission/httpPermissionService.test.ts src/features/permission/PermissionManagementView.test.ts src/features/permission/PermissionComponents.test.ts src/services/serviceFactory.test.ts`

Expected: PASS。

- [ ] **Step 5: 提交真实权限服务和映射界面**

```bash
git add frontend/src/features/permission frontend/src/services/serviceFactory.test.ts
git commit -m "feat: manage feishu role mappings in permissions"
```

---

### Task 10: 本地模拟闭环、配置文档和全量验收

**Files:**
- Create: `.env.example`
- Modify: `README.md`
- Create: `backend/src/test/java/com/bebefish/erp/auth/api/FeishuLoginFlowIntegrationTest.java`
- Create: `docs/feishu-enterprise-acceptance.md`

**Interfaces:**
- Consumes: Tasks 1–9 完整系统。
- Produces: 无真实密钥的本地端到端测试，以及真实企业扫码验收所需最少配置清单。

- [ ] **Step 1: 写本地模拟完整流程失败测试**

```java
@Test
void mockOauthFlowCreatesEmployeeMapsRolesAndAuthenticatesMe() throws Exception {
    var ticket = completeMockCallback("mock-no-mobile", "tenant-test");
    var login = exchange(ticket);
    assertThat(login.loginMethod()).isEqualTo("feishu");
    assertThat(login.roles()).contains("BASIC_EMPLOYEE", "PRODUCT_OPERATOR");
    assertThat(me(login.accessToken()).employeeId()).isEqualTo(login.employeeId());
}
```

同一集成测试验证非本企业无任何新增数据、角色从模拟服务移除后下次登录权限失效、本地角色保留、降级 warning 和注销后会话撤销。

- [ ] **Step 2: 运行集成测试确认失败或暴露最后契约差异**

Run: `cd backend && mvn -Dtest=FeishuLoginFlowIntegrationTest test`

Expected: 首次运行在尚未接通的契约处 FAIL；修正只限跨模块 DTO、事务或测试装配，不扩展设计范围。

- [ ] **Step 3: 完成安全配置样例和真实企业验收说明**

```dotenv
ERP_FEISHU_ENABLED=false
ERP_FEISHU_APP_ID=
ERP_FEISHU_APP_SECRET=
ERP_FEISHU_REDIRECT_URI=http://127.0.0.1:5173/api/auth/feishu/callback
ERP_FEISHU_ALLOWED_TENANT_KEY=
ERP_FEISHU_MOCK_ENABLED=false
ERP_AUTH_SESSION_TTL=PT12H
ERP_AUTH_TICKET_TTL=PT60S
```

`docs/feishu-enterprise-acceptance.md` 只要求管理员：创建企业自建应用、添加本地/生产回调、限定应用可用范围、审批基本信息/手机号/通讯录部门/业务角色只读权限、把四项值注入部署环境、启动后检查 status、完成一次本企业扫码和一次外企业拒绝测试；明确 App Secret 不粘贴到对话或提交仓库。

- [ ] **Step 4: 修正集成契约并运行全部验证**

Run: `cd backend && mvn test`

Expected: PASS（需要 `ERP_TEST_DB_URL`、`ERP_TEST_DB_USERNAME`、`ERP_TEST_DB_PASSWORD` 指向隔离 MySQL 测试库）。

Run: `cd frontend && npm run test:run`

Expected: PASS。

Run: `cd frontend && npm run build`

Expected: PASS，`vue-tsc --noEmit` 与 Vite production build 均成功。

Run: `git grep -n -E 'cli_[A-Za-z0-9]|ERP_FEISHU_APP_SECRET=.+|user_access_token|refresh_token' -- . ':!docs/superpowers/specs/*' ':!docs/superpowers/plans/*'`

Expected: 不返回真实凭据或持久令牌；只允许接口字段名和安全文档说明命中。

- [ ] **Step 5: 提交集成验收材料**

```bash
git add .env.example README.md backend/src/test/java/com/bebefish/erp/auth/api/FeishuLoginFlowIntegrationTest.java docs/feishu-enterprise-acceptance.md
git commit -m "test: verify feishu login integration"
```

---

### Task 11: 独立代码审查与发布前收口

**Files:**
- Modify: only files implicated by verified review findings

**Interfaces:**
- Consumes: 完整实现提交序列和设计文档。
- Produces: 设计符合性审查、数据安全审查、测试证据和干净工作树。

- [ ] **Step 1: 调用 `superpowers:requesting-code-review` 做独立审查**

审查范围从 `ec40cf9` 到当前 HEAD，重点检查：tenant 校验是否先于写入、state/ticket 是否抗重放、事务并发、敏感角色后端拒绝、LOCAL/FEISHU 来源隔离、会话哈希与撤销、Profile 模拟隔离、前端 ticket 清理，以及旧短信接口是否彻底移除。

- [ ] **Step 2: 对每个有效发现先补失败测试**

Run: 后端发现执行 `cd backend && mvn test`；前端发现执行 `cd frontend && npm run test:run`。

Expected: 新测试在修复前准确 FAIL，并能证明该发现不是纯样式偏好。

- [ ] **Step 3: 实现最小修复并运行定向测试**

修复只改审查已验证问题；不在收口阶段重构无关产品、组织或权限 UI。

Run: 与 Step 2 相同的定向命令。

Expected: PASS。

- [ ] **Step 4: 使用 `superpowers:verification-before-completion` 重跑最终证据**

Run: `cd backend && mvn test`

Run: `cd frontend && npm run test:run`

Run: `cd frontend && npm run build`

Run: `git status --short --branch`

Expected: 三个验证命令退出码为 0；工作树除预期审查修复外无未提交变更。

- [ ] **Step 5: 提交审查修复（仅在存在有效发现时）**

```bash
git add backend/src frontend/src .env.example README.md docs/feishu-enterprise-acceptance.md
git commit -m "fix: address feishu login review findings"
```

若无有效发现，不创建空提交；最终报告列出测试数量、构建结果、审查结论，以及真实扫码仍需的最少外部配置步骤。
