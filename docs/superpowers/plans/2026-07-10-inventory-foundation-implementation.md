# 库存基础 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 建立多仓 SKU 库存余额、库存初始化/调整和不可修改的库存流水，为销售确认提供可并发校验的库存服务。

**Architecture:** `inventory` 模块拥有库存余额与流水，其他业务模块只能调用 `InventoryService`，不能直接修改库存表。库存调整和余额/流水更新使用同一 MySQL 事务，行锁按 SKU ID 排序获取以降低死锁风险。

**Tech Stack:** Java 21、Spring Boot、Spring Data JPA、Flyway、MySQL 8、JUnit 5、MockMvc、Vue 3、TypeScript、Vitest。

## Global Constraints

- 必须先完成并验收 `2026-07-10-masterdata-product-implementation.md`。
- 严格 RED → GREEN → REFACTOR；每个库存行为先观察目标失败测试。
- 第一版不允许负库存，不允许直接编辑 `inventory_balance`。
- 金额和数量使用 `BigDecimal`；库存数量保留 4 位小数。
- 未经用户明确指令，不执行任何 Git 提交或推送。

## 当前执行状态

本计划实际接续执行时，项目已有 V1、V2、V3 迁移，因此库存迁移顺延为 V4、V5，禁止修改已执行迁移。

| 任务 | 状态 | 结果 |
| --- | --- | --- |
| Task 1 | 已完成 | V4 库存余额/流水表、SKU 唯一余额、升序行锁查询 |
| Task 2 | 已完成 | 增加、扣减、反向、有符号调整、库存不足、多明细原子性、真实 MySQL 并发 |
| Task 3 | 已完成 | V5 调整单草稿、确认、作废、重复确认保护和 REST API |
| Task 4 | 已完成 | 库存余额和库存流水只读查询 API |
| Task 5 | 已完成 | Vue 余额、流水、调整页面，Mock/API 双数据源和路由接入 |
| Task 6 | 已完成 | MySQL 全量后端回归、前端全量回归/构建和页面验收均已通过 |

当前库存基础第一版只落 SKU 库存；物料库存会在采购/组装阶段按同一库存服务扩展，避免本轮提前引入未验收的物料逻辑。

---

### Task 1: 库存数据库迁移与仓储锁接口

**Files:**
- Create: `backend/src/main/resources/db/migration/V4__inventory_schema.sql`
- Create: `backend/src/main/java/com/bebefish/erp/inventory/domain/InventoryBalance.java`
- Create: `backend/src/main/java/com/bebefish/erp/inventory/domain/InventoryLedgerEntry.java`
- Create: `backend/src/main/java/com/bebefish/erp/inventory/domain/InventoryRepository.java`
- Create: `backend/src/main/java/com/bebefish/erp/inventory/infrastructure/InventoryBalanceJpaEntity.java`
- Create: `backend/src/main/java/com/bebefish/erp/inventory/infrastructure/InventoryLedgerJpaEntity.java`
- Create: `backend/src/main/java/com/bebefish/erp/inventory/infrastructure/JpaInventoryRepository.java`
- Create: `backend/src/test/java/com/bebefish/erp/inventory/infrastructure/InventoryRepositoryTest.java`

**Interfaces:**
- Produces: `lockBalances(long warehouseId, List<Long> sortedSkuIds)`。
- Produces: `saveBalance` 和 `appendLedger`。

- [x] **Step 1: 写迁移与行锁失败测试**

```java
@DataJpaTest
@ActiveProfiles("test")
class InventoryRepositoryTest {
    @Autowired InventoryRepository repository;

    @Test
    void createsOneBalancePerWarehouseAndSku() {
        repository.saveBalance(new InventoryBalance(null, 1L, 10L, new BigDecimal("12.0000"), 0L));
        assertThatThrownBy(() -> repository.saveBalance(
                new InventoryBalance(null, 1L, 10L, BigDecimal.ONE, 0L)
        )).isInstanceOf(DataIntegrityViolationException.class);
    }
}
```

- [x] **Step 2: 运行 RED**

Run: `cd backend && mvn -Dtest=InventoryRepositoryTest test`

Expected: FAIL，库存表和仓储不存在。

- [x] **Step 3: 创建库存表和 JPA 适配器**

```sql
create table inventory_balance (
    id bigint primary key auto_increment,
    warehouse_id bigint not null,
    sku_id bigint not null,
    quantity decimal(18,4) not null default 0,
    version_no bigint not null default 0,
    created_at datetime(3) not null,
    updated_at datetime(3) not null,
    unique key uk_inventory_warehouse_sku (warehouse_id, sku_id)
);

create table inventory_ledger (
    id bigint primary key auto_increment,
    warehouse_id bigint not null,
    sku_id bigint not null,
    direction varchar(10) not null,
    quantity decimal(18,4) not null,
    before_quantity decimal(18,4) not null,
    after_quantity decimal(18,4) not null,
    source_type varchar(30) not null,
    source_id bigint not null,
    source_no varchar(50) not null,
    occurred_at datetime(3) not null,
    operator_mobile varchar(20) not null
);
```

锁查询使用 `PESSIMISTIC_WRITE`，调用方必须传入升序 SKU ID。

- [x] **Step 4: 运行 GREEN**

Run: `cd backend && mvn -Dtest=InventoryRepositoryTest test`

Expected: PASS。

### Task 2: 统一库存增加、扣减和反向服务

**Files:**
- Create: `backend/src/main/java/com/bebefish/erp/inventory/application/InventoryService.java`
- Create: `backend/src/main/java/com/bebefish/erp/inventory/application/InventoryChange.java`
- Create: `backend/src/test/java/com/bebefish/erp/inventory/application/InventoryServiceTest.java`
- Create: `backend/src/test/java/com/bebefish/erp/inventory/application/InventoryConcurrencyTest.java`

**Interfaces:**
- Produces: `increase(warehouseId, changes, source, operator)`。
- Produces: `decrease(warehouseId, changes, source, operator)`。
- Produces: `reverse(warehouseId, changes, source, operator)`。

- [x] **Step 1: 写增加、扣减与不足失败测试**

```java
@Test
void decreaseWritesBalanceAndLedgerTogether() {
    repository.seed(1L, 10L, new BigDecimal("12"));
    service.decrease(1L, List.of(change(10L, "5")), source("sales", 99L, "SO001"), "13800138000");
    assertThat(repository.balance(1L, 10L)).isEqualByComparingTo("7");
    assertThat(repository.ledger()).singleElement().satisfies(entry -> {
        assertThat(entry.beforeQuantity()).isEqualByComparingTo("12");
        assertThat(entry.afterQuantity()).isEqualByComparingTo("7");
    });
}

@Test
void insufficientInventoryChangesNothing() {
    repository.seed(1L, 10L, new BigDecimal("3"));
    assertThatThrownBy(() -> service.decrease(1L, List.of(change(10L, "5")), source(), "13800138000"))
            .hasMessageContaining("库存不足");
    assertThat(repository.balance(1L, 10L)).isEqualByComparingTo("3");
    assertThat(repository.ledger()).isEmpty();
}
```

- [x] **Step 2: 运行 RED 并实现最小事务服务**

Run: `cd backend && mvn -Dtest=InventoryServiceTest test`

服务校验所有数量大于 0，先锁定并验证全部明细，再统一写余额和流水；任一明细不足抛出 `INVENTORY_NOT_ENOUGH`。

- [x] **Step 3: 写并发失败测试**

```java
@Test
void concurrentDecreasesCannotCreateNegativeInventory() throws Exception {
    seedBalance("10");
    var results = runConcurrently(
            () -> service.decrease(1L, List.of(change(10L, "7")), source("sales", 1L, "SO1"), "u1"),
            () -> service.decrease(1L, List.of(change(10L, "7")), source("sales", 2L, "SO2"), "u2")
    );
    assertThat(results.successCount()).isEqualTo(1);
    assertThat(currentBalance()).isEqualByComparingTo("3");
}
```

- [x] **Step 4: 运行 RED/GREEN 和全量测试**

Run RED before lock implementation: `cd backend && mvn -Dtest=InventoryConcurrencyTest test`

Expected RED: 两次扣减都成功或余额错误。实现锁顺序后再次运行，Expected GREEN: 一次成功、一次库存不足、余额为 3。

### Task 3: 库存调整单状态流

**Files:**
- Create: `backend/src/main/resources/db/migration/V5__stock_adjustment_schema.sql`
- Create: `backend/src/main/java/com/bebefish/erp/inventory/domain/StockAdjustment.java`
- Create: `backend/src/main/java/com/bebefish/erp/inventory/domain/StockAdjustmentRepository.java`
- Create: `backend/src/main/java/com/bebefish/erp/inventory/application/StockAdjustmentService.java`
- Create: `backend/src/main/java/com/bebefish/erp/inventory/api/StockAdjustmentController.java`
- Create: `backend/src/main/java/com/bebefish/erp/inventory/infrastructure/JpaStockAdjustmentRepository.java`
- Create: `backend/src/test/java/com/bebefish/erp/inventory/application/StockAdjustmentServiceTest.java`
- Create: `backend/src/test/java/com/bebefish/erp/inventory/api/StockAdjustmentControllerTest.java`

**Interfaces:**
- Produces: 草稿 create/update/delete、confirm、void。
- Produces: `/api/inventory/adjustments` REST API。

- [x] **Step 1: 写确认和作废失败测试**

```java
@Test
void confirmingAdjustmentAppliesEachDeltaOnce() {
    var draft = service.create(command(item(10L, "12"), item(11L, "-2")));
    service.confirm(draft.id(), "13800138000");
    assertThat(inventory.balance(1L, 10L)).isEqualByComparingTo("12");
    assertThat(inventory.balance(1L, 11L)).isEqualByComparingTo("8");
    assertThatThrownBy(() -> service.confirm(draft.id(), "13800138000"))
            .hasMessage("库存调整单已确认，请勿重复操作");
}

@Test
void voidingConfirmedAdjustmentCreatesReverseLedger() {
    var confirmed = confirmedAdjustment("12");
    service.voidAdjustment(confirmed.id(), "录入错误", "13800138000");
    assertThat(inventory.balance(1L, 10L)).isZero();
    assertThat(inventory.ledger()).extracting(InventoryLedgerEntry::sourceType)
            .containsExactly("adjustment", "adjustment_void");
}
```

- [x] **Step 2: 运行 RED，创建调整表并实现状态流**

Run: `cd backend && mvn -Dtest=StockAdjustmentServiceTest test`

`V5__stock_adjustment_schema.sql` 创建 `stock_adjustment` 与 `stock_adjustment_item`。状态只允许 `draft -> confirmed -> voided`；草稿删除允许物理删除，确认后的记录禁止删除。已经执行过的 V1-V3 迁移禁止修改。

- [x] **Step 3: 写 API 测试并实现控制器**

```java
mockMvc.perform(post("/api/inventory/adjustments/{id}/confirm", id)
        .header("Authorization", bearerToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.status").value("confirmed"));
```

Run RED/GREEN: `cd backend && mvn -Dtest=StockAdjustmentControllerTest test`

- [x] **Step 4: 验证 Task 3**

Run: `cd backend && mvn test`

Expected: PASS。

### Task 4: 库存余额与流水查询 API

**Files:**
- Create: `backend/src/main/java/com/bebefish/erp/inventory/api/InventoryQueryController.java`
- Create: `backend/src/main/java/com/bebefish/erp/inventory/application/InventoryQueryService.java`
- Create: `backend/src/test/java/com/bebefish/erp/inventory/api/InventoryQueryControllerTest.java`

**Interfaces:**
- Produces: `GET /api/inventory/balances` 和 `GET /api/inventory/ledger`。
- Produces: 销售计划使用的 `getAvailableQuantity(warehouseId, skuId)`。

- [x] **Step 1: 写分页筛选失败测试**

```java
mockMvc.perform(get("/api/inventory/balances")
        .header("Authorization", bearerToken)
        .param("warehouseId", "1")
        .param("keyword", "EW43245"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.items[0].skuCode").value("EW43245-CL"));
```

- [x] **Step 2: 运行 RED，随后实现只读查询**

Run RED/GREEN: `cd backend && mvn -Dtest=InventoryQueryControllerTest test`

余额支持仓库、分类、SKU 状态和关键字筛选；流水支持日期、方向、来源类型、仓库和 SKU 筛选。

### Task 5: 库存前端页面

**Files:**
- Create: `frontend/src/features/inventory/types.ts`
- Create: `frontend/src/features/inventory/inventoryService.ts`
- Create: `frontend/src/features/inventory/mockInventoryService.ts`
- Create: `frontend/src/features/inventory/httpInventoryService.ts`
- Create: `frontend/src/features/inventory/views/InventoryBalanceView.vue`
- Create: `frontend/src/features/inventory/views/InventoryLedgerView.vue`
- Create: `frontend/src/features/inventory/views/StockAdjustmentView.vue`
- Create: `frontend/src/features/inventory/InventoryFeature.test.ts`
- Modify: `frontend/src/router/index.ts`

**Interfaces:**
- Consumes: Task 3-4 API。
- Produces: 销售开单可复用的库存展示类型。

- [x] **Step 1: 写页面失败测试**

```typescript
it('creates and confirms an initial stock adjustment', async () => {
  const wrapper = mount(StockAdjustmentView, { global: { provide: { inventoryService: fakeService } } });
  await wrapper.get('[data-testid="add-adjustment-item"]').trigger('click');
  await selectSku(wrapper, 'EW43245-CL');
  await wrapper.get('[data-testid="quantity-delta-0"]').setValue('1250');
  await wrapper.get('[data-testid="save-adjustment"]').trigger('click');
  await wrapper.get('[data-testid="confirm-adjustment"]').trigger('click');
  expect(fakeService.confirmAdjustment).toHaveBeenCalledOnce();
});
```

- [x] **Step 2: 运行 RED 并实现页面/Service**

Run: `cd frontend && npm run test:run -- src/features/inventory/InventoryFeature.test.ts`

余额页显示仓库、SKU、货号、产品、规格和数量；流水页显示前后数量和来源单号；调整页仅草稿可编辑。

- [x] **Step 3: 运行 GREEN 与构建**

Run: `cd frontend && npm run test:run && npm run build`

Expected: PASS。

### Task 6: 第二批验收门槛

**Files:**
- Modify: `docs/03-database-design.md`
- Modify: `docs/04-development-guide.md`
- Modify: `docs/06-phased-development-plan.md`

- [x] **Step 1: 运行真实 MySQL 后端全量测试**

库存定向回归和全量回归均使用本机 MySQL 8、Flyway V5 完成。全量回归使用独立测试库
`bebefish_erp_test`，避免测试数据写入业务库。

Run: `cd backend && mvn test`

Expected: PASS，包括并发扣减测试。

- [x] **Step 2: 运行前端全量测试与构建**

Run: `cd frontend && npm run test:run && VITE_DATA_SOURCE=real npm run build`

Expected: PASS。

- [x] **Step 3: 浏览器验收**

已在本地页面验收余额、流水、调整三个路由；仓库下拉由基础资料接口加载真实仓库 ID，库存查询不再使用写死的 1/2。
库存初始化、负数调整、并发扣减、确认/作废和重复确认由后端集成测试覆盖，避免在业务库执行破坏性演示操作。

- [x] **Step 4: 更新文档并停在用户验收点**

已同步数据库设计、开发指南和阶段计划；不提交 Git，停在用户验收点，下一阶段再进入采购入库或销售草稿计划。
