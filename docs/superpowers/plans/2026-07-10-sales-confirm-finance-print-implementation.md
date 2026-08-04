# 销售确认、财务与打印 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 完成销售确认扣库存、应收与多次收款、作废冲回，以及用户选定的 B 版现代清晰 A4 销售送货单打印。

**Architecture:** `SalesConfirmationService` 编排销售、库存和财务模块，并以单个 MySQL 事务保证全成功或全回滚。`finance` 模块独立拥有应收和收款记录；打印使用销售确认时保存的快照，不读取可能已变化的当前产品资料。

**Tech Stack:** Java 21、Spring Boot、JPA、Flyway、MySQL 8、JUnit 5、MockMvc、Vue 3、TypeScript、Tailwind CSS、Vitest、浏览器打印 CSS。

## Global Constraints

- 必须先完成并验收前三份实现计划。
- 销售确认、库存扣减、库存流水、应收和初始收款必须处于同一事务。
- 已确认单据不能直接编辑；作废必须产生反向记录，禁止删除历史流水。
- 收款金额不得超过当前欠款；冲回记录必须关联原收款记录。
- 打印只使用销售快照；已作废单据显示“已作废”水印。
- 每项生产行为必须先有目标失败测试；未经用户明确指令不提交 Git。

---

### Task 1: 财务表、确认字段与应收状态机

**Files:**
- Create: `backend/src/main/resources/db/migration/V5__sales_finance_schema.sql`
- Create: `backend/src/main/java/com/bebefish/erp/finance/domain/Receivable.java`
- Create: `backend/src/main/java/com/bebefish/erp/finance/domain/ReceiptRecord.java`
- Create: `backend/src/main/java/com/bebefish/erp/finance/domain/ReceivableRepository.java`
- Create: `backend/src/main/java/com/bebefish/erp/finance/application/ReceivableService.java`
- Create: `backend/src/main/java/com/bebefish/erp/finance/infrastructure/JpaReceivableRepository.java`
- Create: `backend/src/test/java/com/bebefish/erp/finance/application/ReceivableServiceTest.java`

**Interfaces:**
- Produces: `createForConfirmedSale`、`recordReceipt`、`voidForSale`。
- Produces: 应收状态 `unpaid|partial|paid|voided`。

- [ ] **Step 1: 写应收状态失败测试**

```java
@Test
void createsUnpaidPartialAndPaidReceivables() {
    assertThat(service.createForConfirmedSale(sale("100", "0")).status()).isEqualTo("unpaid");
    assertThat(service.createForConfirmedSale(sale("100", "40")).status()).isEqualTo("partial");
    assertThat(service.createForConfirmedSale(sale("100", "100")).status()).isEqualTo("paid");
}

@Test
void alwaysKeepsReceivableEvenWhenFullyPaid() {
    var receivable = service.createForConfirmedSale(sale("100", "100"));
    assertThat(repository.findById(receivable.id())).isPresent();
    assertThat(repository.receiptsFor(receivable.id())).singleElement()
            .extracting(ReceiptRecord::amount).isEqualTo(new BigDecimal("100.00"));
}
```

- [ ] **Step 2: 运行 RED**

Run: `cd backend && mvn -Dtest=ReceivableServiceTest test`

Expected: FAIL，财务模型不存在。

- [ ] **Step 3: 创建迁移和最小状态机**

```sql
create table receivable (
    id bigint primary key auto_increment,
    receivable_no varchar(50) not null,
    sales_order_id bigint not null,
    customer_id bigint not null,
    amount decimal(18,2) not null,
    received_amount decimal(18,2) not null,
    outstanding_amount decimal(18,2) not null,
    status varchar(20) not null,
    settlement_cycle varchar(20) not null,
    created_at datetime(3) not null,
    updated_at datetime(3) not null,
    unique key uk_receivable_sales (sales_order_id),
    unique key uk_receivable_no (receivable_no)
);

create table receipt_record (
    id bigint primary key auto_increment,
    receipt_no varchar(50) not null,
    receivable_id bigint not null,
    sales_order_id bigint not null,
    customer_id bigint not null,
    record_type varchar(20) not null,
    original_receipt_id bigint null,
    amount decimal(18,2) not null,
    payment_method varchar(20) not null,
    received_on date not null,
    operator_mobile varchar(20) not null,
    remark varchar(500) null,
    created_at datetime(3) not null,
    unique key uk_receipt_no (receipt_no)
);
```

迁移同时为 `sales_order` 增加 `cost_amount`、`confirmed_at`、`confirmed_by`、`voided_at`、`voided_by`、`void_reason`；为 `sales_order_item` 增加非空确认后成本快照字段。

- [ ] **Step 4: 运行 GREEN**

Run: `cd backend && mvn -Dtest=ReceivableServiceTest test`

Expected: PASS。

### Task 2: 销售确认事务与幂等保护

**Files:**
- Create: `backend/src/main/java/com/bebefish/erp/sales/application/SalesConfirmationService.java`
- Create: `backend/src/test/java/com/bebefish/erp/sales/application/SalesConfirmationServiceTest.java`
- Create: `backend/src/test/java/com/bebefish/erp/sales/application/SalesConfirmationTransactionTest.java`
- Modify: `backend/src/main/java/com/bebefish/erp/sales/api/SalesOrderController.java`
- Modify: `backend/src/test/java/com/bebefish/erp/sales/api/SalesOrderControllerTest.java`

**Interfaces:**
- Consumes: `InventoryService.decrease` 和 `ReceivableService.createForConfirmedSale`。
- Produces: `SalesOrder confirm(long orderId, ErpPrincipal operator)`。
- Produces: `POST /api/sales-orders/{id}/confirm`。

- [ ] **Step 1: 写成功确认失败测试**

```java
@Test
void confirmationDeductsInventorySnapshotsCostAndCreatesReceivableOnce() {
    inventory.seed(1L, 10L, "1250");
    var draft = orders.save(draft(item(10L, "100", "2.20"), received("50")));

    var confirmed = service.confirm(draft.id(), principal("13800138000"));

    assertThat(confirmed.status()).isEqualTo("confirmed");
    assertThat(inventory.balance(1L, 10L)).isEqualByComparingTo("1150");
    assertThat(confirmed.items().getFirst().standardCost()).isEqualByComparingTo("1.25");
    assertThat(receivables.findBySalesOrderId(draft.id()).orElseThrow().status()).isEqualTo("partial");
}
```

- [ ] **Step 2: 写失败回滚和重复确认测试**

```java
@Test
void inventoryFailureRollsBackSalesAndFinance() {
    inventory.seed(1L, 10L, "2");
    var draft = orders.save(draft(item(10L, "5", "2.20")));
    assertThatThrownBy(() -> service.confirm(draft.id(), principal())).hasMessageContaining("库存不足");
    assertThat(orders.findById(draft.id()).orElseThrow().status()).isEqualTo("draft");
    assertThat(receivables.findBySalesOrderId(draft.id())).isEmpty();
    assertThat(inventory.ledger()).isEmpty();
}

@Test
void repeatedConfirmationHasNoSecondSideEffect() {
    var confirmed = service.confirm(seedDraft(), principal());
    assertThatThrownBy(() -> service.confirm(confirmed.id(), principal()))
            .hasMessage("销售单已确认，请勿重复操作");
    assertThat(inventory.ledger()).hasSize(1);
    assertThat(receivables.countBySalesOrderId(confirmed.id())).isEqualTo(1);
}
```

- [ ] **Step 3: 运行 RED**

Run: `cd backend && mvn -Dtest=SalesConfirmationServiceTest,SalesConfirmationTransactionTest test`

Expected: FAIL，确认服务不存在。

- [ ] **Step 4: 实现事务编排**

```java
@Transactional
public SalesOrder confirm(long orderId, ErpPrincipal operator) {
    var order = orders.lockById(orderId).orElseThrow(orderNotFound());
    order.ensureDraft();
    validateReferences(order);
    snapshotCurrentCosts(order);
    inventory.decrease(order.warehouseId(), inventoryChanges(order), source(order), operator.mobile());
    receivables.createForConfirmedSale(order, operator.mobile());
    return orders.save(order.confirm(operator.mobile(), clock.instant()));
}
```

先锁销售单，再由库存服务按升序锁余额；确认时重新计算金额并校验已收不超额。

- [ ] **Step 5: 写 API 测试并实现端点**

```java
mockMvc.perform(post("/api/sales-orders/{id}/confirm", draftId)
        .header("Authorization", bearerToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.status").value("confirmed"));
```

Run RED/GREEN: `cd backend && mvn -Dtest=SalesOrderControllerTest test`

- [ ] **Step 6: 运行后端全量测试**

Run: `cd backend && mvn test`

Expected: PASS。

### Task 3: 后续多次收款与欠款应收 API

**Files:**
- Create: `backend/src/main/java/com/bebefish/erp/finance/api/ReceivableController.java`
- Create: `backend/src/main/java/com/bebefish/erp/finance/api/ReceiptRecordController.java`
- Create: `backend/src/main/java/com/bebefish/erp/finance/api/RecordReceiptRequest.java`
- Create: `backend/src/test/java/com/bebefish/erp/finance/application/ReceiptServiceTest.java`
- Create: `backend/src/test/java/com/bebefish/erp/finance/api/ReceivableControllerTest.java`

**Interfaces:**
- Produces: `POST /api/receivables/{id}/receipts`。
- Produces: `GET /api/receivables`、`GET /api/receivables/{id}`、`GET /api/receipt-records`。

- [ ] **Step 1: 写多次与超额收款失败测试**

```java
@Test
void recordsMultipleReceiptsAndMarksPaid() {
    var receivable = seedReceivable("100");
    service.recordReceipt(receivable.id(), receipt("40", "wechat"), operator());
    var result = service.recordReceipt(receivable.id(), receipt("60", "bank"), operator());
    assertThat(result.receivedAmount()).isEqualByComparingTo("100");
    assertThat(result.outstandingAmount()).isZero();
    assertThat(result.status()).isEqualTo("paid");
    assertThat(repository.receiptsFor(receivable.id())).hasSize(2);
}

@Test
void rejectsReceiptAboveOutstandingAmount() {
    var receivable = seedReceivable("100", "80");
    assertThatThrownBy(() -> service.recordReceipt(receivable.id(), receipt("21", "cash"), operator()))
            .hasMessage("收款金额超过剩余欠款");
}
```

- [ ] **Step 2: 运行 RED，随后实现带锁收款事务**

Run: `cd backend && mvn -Dtest=ReceiptServiceTest test`

锁定应收记录，校验状态和余额，新增收款后重算应收与销售单 `received_amount/outstanding_amount`。

- [ ] **Step 3: 写 API 测试并实现查询与收款端点**

Run RED/GREEN: `cd backend && mvn -Dtest=ReceivableControllerTest test`

应收列表支持客户、状态、结算周期、销售单号和日期筛选；收款列表支持客户、渠道、经办人和日期筛选。

- [ ] **Step 4: 运行全量测试**

Run: `cd backend && mvn test`

Expected: PASS。

### Task 4: 销售作废、库存反向和财务冲回

**Files:**
- Create: `backend/src/main/java/com/bebefish/erp/sales/application/SalesVoidService.java`
- Create: `backend/src/test/java/com/bebefish/erp/sales/application/SalesVoidServiceTest.java`
- Modify: `backend/src/main/java/com/bebefish/erp/sales/api/SalesOrderController.java`
- Modify: `backend/src/test/java/com/bebefish/erp/sales/api/SalesOrderControllerTest.java`

**Interfaces:**
- Consumes: `InventoryService.increase` 和 `ReceivableService.voidForSale`。
- Produces: `POST /api/sales-orders/{id}/void`。

- [ ] **Step 1: 写作废失败测试**

```java
@Test
void voidRestoresInventoryAndCreatesReceiptReversals() {
    var confirmed = confirmedSale(total("100"), received("40"));
    service.voidOrder(confirmed.id(), "客户取消", operator());
    assertThat(inventory.balance(1L, 10L)).isEqualByComparingTo("1250");
    assertThat(inventory.ledger()).extracting(InventoryLedgerEntry::sourceType)
            .contains("sales", "sales_void");
    assertThat(receivables.bySale(confirmed.id()).status()).isEqualTo("voided");
    assertThat(receivables.receipts(confirmed.id())).extracting(ReceiptRecord::recordType)
            .containsExactly("receipt", "reversal");
}

@Test
void draftOrVoidedOrderCannotBeVoided() {
    assertThatThrownBy(() -> service.voidOrder(seedDraft().id(), "错误", operator()))
            .hasMessage("只有已确认销售单可以作废");
}
```

- [ ] **Step 2: 运行 RED 并实现单事务作废**

Run: `cd backend && mvn -Dtest=SalesVoidServiceTest test`

作废顺序：锁销售单、检查状态、增加库存和写反向流水、冲回应收和有效收款、更新销售状态。任何步骤失败全部回滚。

- [ ] **Step 3: 写 API 测试并实现二次确认端点**

请求体必须包含非空 `reason`。Run RED/GREEN: `cd backend && mvn -Dtest=SalesOrderControllerTest test`。

- [ ] **Step 4: 运行全量测试**

Run: `cd backend && mvn test`

Expected: PASS。

### Task 5: 打印数据 API 与历史快照

**Files:**
- Create: `backend/src/main/java/com/bebefish/erp/sales/application/SalesPrintService.java`
- Create: `backend/src/main/java/com/bebefish/erp/sales/api/SalesPrintResponse.java`
- Create: `backend/src/test/java/com/bebefish/erp/sales/application/SalesPrintServiceTest.java`
- Modify: `backend/src/main/java/com/bebefish/erp/sales/api/SalesOrderController.java`

**Interfaces:**
- Produces: `GET /api/sales-orders/{id}/print`。
- Produces: B 版模板所需全部字段和金额大写文本。

- [ ] **Step 1: 写快照与作废水印失败测试**

```java
@Test
void printUsesConfirmedSnapshotsAfterProductChanges() {
    var order = confirmedOrderWithSnapshot("高脚红酒杯", "2.20", "透明 / 350ml");
    productGateway.renameAndReprice(10L, "新名称", "9.99");
    var print = service.getPrintData(order.id());
    assertThat(print.items().getFirst().productName()).isEqualTo("高脚红酒杯");
    assertThat(print.items().getFirst().unitPrice()).isEqualByComparingTo("2.20");
}

@Test
void voidedOrderPrintDataContainsWatermark() {
    assertThat(service.getPrintData(voidedOrder().id()).watermark()).isEqualTo("已作废");
}
```

- [ ] **Step 2: 运行 RED 并实现打印 DTO**

Run: `cd backend && mvn -Dtest=SalesPrintServiceTest test`

打印 DTO 包含公司、客户、收货、仓库、经办人、运输、结算、收款/开票状态、明细快照、包装说明、运费、应收、已收、欠款和金额大写。

- [ ] **Step 3: 运行 GREEN**

Run: `cd backend && mvn -Dtest=SalesPrintServiceTest test`

Expected: PASS。

### Task 6: 财务前端页面和销售确认/作废交互

**Files:**
- Create: `frontend/src/features/finance/types.ts`
- Create: `frontend/src/features/finance/financeService.ts`
- Create: `frontend/src/features/finance/mockFinanceService.ts`
- Create: `frontend/src/features/finance/httpFinanceService.ts`
- Create: `frontend/src/features/finance/views/ReceivableListView.vue`
- Create: `frontend/src/features/finance/views/ReceiptRecordListView.vue`
- Create: `frontend/src/features/finance/components/RecordReceiptDialog.vue`
- Create: `frontend/src/features/finance/FinanceFeature.test.ts`
- Modify: `frontend/src/features/sales/views/SalesOrderDetailView.vue`
- Modify: `frontend/src/features/sales/SalesDraftFeature.test.ts`
- Modify: `frontend/src/router/index.ts`

**Interfaces:**
- Consumes: Task 2-4 API。
- Produces: 确认、作废、收款和财务列表用户流程。

- [ ] **Step 1: 写确认与失败状态测试**

```typescript
it('confirms a draft once and refreshes inventory and finance status', async () => {
  const wrapper = mountSalesDetail(draftOrder);
  await wrapper.get('[data-testid="confirm-sales-order"]').trigger('click');
  await wrapper.get('[data-testid="confirm-dialog-submit"]').trigger('click');
  expect(fakeSalesService.confirm).toHaveBeenCalledWith(draftOrder.id);
  expect(wrapper.text()).toContain('已确认');
});

it('keeps draft visible when backend reports insufficient inventory', async () => {
  fakeSalesService.confirm.mockRejectedValue(new Error('库存不足：EW43245-CL'));
  const wrapper = mountSalesDetail(draftOrder);
  await confirm(wrapper);
  expect(wrapper.text()).toContain('库存不足：EW43245-CL');
  expect(wrapper.text()).toContain('草稿');
});
```

- [ ] **Step 2: 运行 RED 并实现确认/作废交互**

Run: `cd frontend && npm run test:run -- src/features/sales/SalesDraftFeature.test.ts`

确认、作废均使用二次确认对话框；请求进行中禁用按钮，成功后重新读取详情，失败时保留页面状态。

- [ ] **Step 3: 写多次收款页面测试**

```typescript
it('records a partial receipt and updates outstanding amount', async () => {
  const wrapper = mount(ReceivableListView, { global: { provide: { financeService: fakeService } } });
  await wrapper.get('[data-testid="record-receipt-1"]').trigger('click');
  await wrapper.get('[data-testid="receipt-amount"]').setValue('40');
  await wrapper.get('[data-testid="receipt-method-bank"]').trigger('click');
  await wrapper.get('[data-testid="save-receipt"]').trigger('click');
  expect(fakeService.recordReceipt).toHaveBeenCalledWith(1, expect.objectContaining({ amount: '40', paymentMethod: 'bank' }));
});
```

- [ ] **Step 4: 实现应收、收款页面并运行 GREEN**

Run: `cd frontend && npm run test:run && npm run build`

Expected: PASS。

### Task 7: B 版现代清晰 A4 打印页面

**Files:**
- Create: `frontend/src/features/sales/views/SalesOrderPrintView.vue`
- Create: `frontend/src/features/sales/components/SalesPrintDocument.vue`
- Create: `frontend/src/features/sales/sales-print.css`
- Create: `frontend/src/features/sales/SalesPrintDocument.test.ts`
- Modify: `frontend/src/router/index.ts`

**Interfaces:**
- Consumes: `GET /api/sales-orders/{id}/print`。
- Produces: `/sales-orders/:id/print` A4 页面和 `window.print()`。

- [ ] **Step 1: 写打印字段失败测试**

```typescript
it('renders the selected modern print layout with all business fields', () => {
  const wrapper = mount(SalesPrintDocument, { props: { order: printFixture } });
  for (const text of ['出库送货单', 'SO202607100001', '杭州万象家居', '送货上门', '月结',
    'EW43245', '透明 / 350ml', '应收合计', '本次已收', '尚欠金额', '库管员', '客户签字']) {
    expect(wrapper.text()).toContain(text);
  }
});

it('renders void watermark only for voided order', () => {
  const wrapper = mount(SalesPrintDocument, { props: { order: { ...printFixture, status: 'voided' } } });
  expect(wrapper.get('[data-testid="void-watermark"]').text()).toBe('已作废');
});
```

- [ ] **Step 2: 运行 RED 并实现 B 版结构**

Run: `cd frontend && npm run test:run -- src/features/sales/SalesPrintDocument.test.ts`

实现已批准的 B 版：顶部公司/标题与单号；客户和发货双栏；运输/结算/收款/开票标签；商品表格；交付包装和金额汇总；已收/欠款/大写金额；三方签字栏。

- [ ] **Step 3: 实现打印 CSS**

```css
@page { size: A4 portrait; margin: 12mm; }
@media print {
  .print-toolbar { display: none !important; }
  .sales-print-document { width: auto; min-height: auto; box-shadow: none; }
  thead { display: table-header-group; }
  tr { break-inside: avoid; }
  .print-summary { break-inside: avoid; }
}
```

明细跨页时重复表头，金额与签字区避免拆页。打印路由不渲染 ERP 侧边栏。

- [ ] **Step 4: 运行 GREEN、构建和浏览器打印预览**

Run: `cd frontend && npm run test:run && npm run build`

Expected: PASS。浏览器分别验证 3 行、25 行和已作废订单的 A4 预览，无截断、重叠或空白固定行。

### Task 8: 第四批端到端验收门槛

**Files:**
- Modify: `README.md`
- Modify: `docs/01-requirements.md`
- Modify: `docs/03-database-design.md`
- Modify: `docs/04-development-guide.md`
- Modify: `docs/06-phased-development-plan.md`

- [ ] **Step 1: 运行真实 MySQL 后端全量测试**

Run: `cd backend && mvn test`

Expected: PASS，包括事务回滚、重复确认、并发库存、超额收款和作废冲回测试。

- [ ] **Step 2: 运行前端全量测试和生产构建**

Run: `cd frontend && npm run test:run && VITE_DATA_SOURCE=real npm run build`

Expected: PASS。

- [ ] **Step 3: 真实业务闭环验收**

建立客户、仓库和产品；初始化库存；创建部分付款销售草稿；确认后核对库存和应收；分两次收款至结清；打印 B 版；再创建一张销售单并作废，核对反向库存、应收作废和收款冲回。

- [ ] **Step 4: 核对审计和零半成功状态**

人工触发库存不足、重复确认、超额收款和作废失败，确认没有库存或财务半成功记录，错误信息为稳定中文业务错误。

- [ ] **Step 5: 更新全部文档并等待最终验收**

同步当前实现、数据库表、接口和测试命令。展示完整 `git diff` 与测试结果，不提交 Git；等待用户明确决定提交、继续优化或进入下一业务阶段。
