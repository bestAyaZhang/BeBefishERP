# 飞书组织架构同步 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox syntax for tracking.

**Goal:** 用真实数据库支持组织管理，在生产禁用 Mock，并安全地导入飞书部门与员工。

**Architecture:** 保持 api/application/domain/infrastructure 分层；JDBC 执行分页和子树查询。飞书客户端负责分页协议，同步服务负责短事务合并，任务服务负责异步互斥、节流与状态。Vue 服务遵循现有 HTTP 包装。

**Tech Stack:** Java 21, Spring Boot 3.3.5, MySQL 8, Flyway, Vue 3, TypeScript, Vitest.

**Spec:** docs/superpowers/specs/2026-09-05-feishu-organization-sync-design.md

## Global Constraints

- 生产构建默认且只允许 real；真实 API 失败时不返回 Mock 记录。
- 保留 LOCAL 角色、密码、上次登录方式和时间、ERP position_id、人工停用和离职状态。
- 租户必须匹配 FEISHU_ALLOWED_TENANT_KEY；根部门 0 是虚拟全公司。
- 每人独立短事务；缺失用户不得推断离职；分页协议异常必须终止。
- 登录同步异步执行，30 分钟节流，运行中任务复用。
- 不部署、不推送远程；验收使用本地真实 MySQL 和真实 HTTP 数据源。

### Task 1: V11 迁移与组织 REST API

**Files:** Create backend/src/main/resources/db/migration/V11__organization_directory_sync.sql; create backend/src/main/java/com/bebefish/erp/organization/{api/OrganizationController.java,api/OrganizationDtos.java,application/OrganizationService.java,domain/OrganizationRepository.java,infrastructure/JdbcOrganizationRepository.java}; create matching organization tests under backend/src/test/java/com/bebefish/erp/organization/.

**Interfaces:** GET/POST/PUT/PATCH /api/organization/departments, positions, employees plus /all, /summary, /employee-counts; payloads and page result match frontend/src/features/organization/types.ts (page, pageSize, total, records). departmentId/positionId nullable. Employee has optional feishuJobTitle. Require organization:view for reads and organization:manage for writes (verify existing catalog and reuse its established codes if different).

- [x] Add migration integration assertions for manager_employee_id, responsibilities, hire_date, feishu_job_title, status_source; assert active Feishu rows become feishu while disabled rows remain manual. Assert organization:sync assigned only to SUPER_ADMIN. Task table fields/statuses exactly follow spec §4.2.
- [x] Write failing repository/controller tests for empty page, keyword/type/status filters, SQL descendant department filter, page bounds, CRUD, unique employee/code/mobile constraints, parent cycles, occupied department/position disable checks and permission denial. Example: `assertThat(service.listEmployees(query).total()).isEqualTo(0);` against cleared real test tables; create parent/child and verify parent query returns child employee.
- [x] Run `cd backend && mvn -Dtest='*Organization*Test' test` and capture the expected missing API/migration failures.
- [x] Implement typed DTOs, validated service, repository and controller. Use parameterized SQL, count query and LIMIT/OFFSET. Status mutation sets status_source=manual and updates sys_user status in same transaction. Password uses existing encoder and is only changed when explicitly supplied; never return hashes. Validate FK relationships and enabled selectors; permit null department/position. Department cycles rejected before mutation.
- [x] Run focused tests until green; commit Task 1 source/tests/migration only.

### Task 2: 飞书客户端与安全同步、登录任务

**Files:** Modify feishu/FeishuDirectoryClient.java, feishu/infrastructure/HttpFeishuClient.java, identity/application/EmployeeProvisioningService.java, identity repositories as needed, auth/application/FeishuLoginService.java. Create organization/application/FeishuDirectorySyncService.java, organization/application/FeishuDirectorySyncTaskService.java, organization/api/FeishuSyncController.java and focused tests; create typed directory records in feishu/.

**Interfaces:** directory client returns complete department/user collections through paginated endpoints; task service `startManual(long userId)`, `triggerAfterLogin(long userId)`, `latest()`, `get(long id)`. REST POST /api/organization/feishu-syncs returns 202 ApiResponse task (id/status/counters/messages), GET latest nullable and GET id. Use organization:sync to start, organization:view to read (adapt to verified catalog).

- [x] Add HTTP fixture tests for root children and root users, multiple pages, missing/repeated page token, null fields and primary department order. Add merge tests for union/open conflict, normalized unique mobile binding, duplicate import, employee_no collision/stable fallback, status precedence and preservation of passwords/LOCAL roles/position. Example: sync same profile twice then assert one identity and employee, stable FS-U- hash and unchanged password.
- [x] Run focused Maven tests and observe intended failures.
- [x] Implement maximum page sizes supported by official endpoint; validate every response and prevent token loops. Upsert departments parent-first using FS-D- stable hash, never create root. Deduplicate open_id across departments. Resolve primary department using order.is_primary_dept before first imported department.
- [x] Share identity conflict and merge rules between login provisioning and directory sync. Match tenant+union then tenant+open, reject disagreement, then normalized unique unbound mobile. Preserve manual nonactive state; Feishu negative state disables account. Map employee_type=1 formal, others temporary; store job title/join date. Each employee commit independent; counter failure messages must be sanitized. Backfill department managers after users.
- [x] Test task concurrency using controlled executor; two starts return same pending ID; successful/partial result throttles login for 30 minutes; login returns without executing queued sync; executor/API failure persists failed. Test stale pending/running recovery on startup.
- [x] Implement durable task transitions and single-instance mutex with database task check. Inject executor, do not self-invoke @Async. Add login hook that never propagates sync failure into login. Log task ID/counts/request ID only, no response body or credentials.
- [x] Run focused tests until green and commit Task 2.

### Task 3: 前端真实服务与同步反馈

**Files:** Create frontend/src/features/organization/httpOrganizationService.ts and test. Modify organizationService.ts, types.ts, employee views/components/tests, mockOrganizationService.ts only for interface compatibility; inspect all service exports and vite.config.ts for production Mock leaks.

**Interfaces:** Existing OrganizationService methods unchanged; add startFeishuSync/getLatestFeishuSync/getFeishuSync with Task 2 task shape; nullable organization IDs and feishuJobTitle fallback. HTTP client propagates errors.

- [x] Add failing contract tests asserting routes, filters, mutation payloads, nullable latest and propagation of failed fetch. Add factory production real/mock rejection test. Example: mock fetch rejects; `await expect(service.listEmployees({page:1,size:20})).rejects.toThrow()`.
- [x] Run `cd frontend && npm run test:run -- src/features/organization` for red evidence.
- [x] Implement typed HTTP calls with existing request helper and createService factory. Audit remaining exported mock-only services so production never silently serves fixture data; use existing real services when available and explicit unsupported failure where no endpoint exists.
- [x] Add view tests for organization:sync permission, initial running-task resume, duplicate-click prevention, terminal success/partial/failed messages, polling cleanup and refresh. Implement button + task status using existing Message API; keep filters/list on failure. Poll until terminal and cancel on unmount; show ERP position then Feishu job title then 未分配.
- [x] Run focused frontend tests and typecheck; commit Task 3.

### Task 4: 全量验收与评审

**Files:** Update this plan progress and create docs/superpowers/plans/2026-09-05-feishu-organization-sync-validation.md.

- [x] Run backend full Maven suite with local MySQL test configuration; record executed/skipped totals, fix regressions with failing tests.
- [x] Run frontend full Vitest and `npm run build`; run production build with VITE_DATA_SOURCE=mock and confirm rejection.
- [x] Package/start backend with dedicated local MySQL database; start frontend real source. Use browser to log in with local test account, inspect employees/departments/positions, empty state and CRUD, manual sync unavailable/error feedback if actual Feishu credentials are absent. Never fabricate external live-sync success.
- [x] Review entire diff against spec and security requirements, resolve important findings, rerun changed-area checks, record limitations and commands. Keep branch local.
