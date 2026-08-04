# 销售草稿 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 实现可保存、编辑、查询的销售草稿和完整开单页面，但草稿阶段不改变库存或财务数据。

**Architecture:** `sales` 模块拥有销售单和明细，领域计算器统一计算明细金额、折扣、运费、应收和欠款预览。草稿引用客户、仓库和 SKU，同时保存必要的显示快照；确认行为由下一份计划实现。

**Tech Stack:** Java 21、Spring Boot、JPA、Flyway、MySQL 8、JUnit 5、MockMvc、Vue 3、TypeScript、Tailwind CSS、Vitest。

## Global Constraints

- 必须先完成并验收基础资料/产品和库存基础两份计划。
- 草稿只能保存业务输入和计算结果，不扣库存、不写库存流水、不生成应收或收款。
- 金额计算以后端 `BigDecimal` 为最终结果，前端只做即时预览。
- 运输方式、结算周期和收款渠道分别存储，不能混用。
- 未经用户明确指令，不提交或推送 Git。

---

### Task 1: 销售金额领域计算器

**Files:**
- Create: `backend/src/main/java/com/bebefish/erp/sales/domain/SalesLineInput.java`
- Create: `backend/src/main/java/com/bebefish/erp/sales/domain/SalesAmountSummary.java`
- Create: `backend/src/main/java/com/bebefish/erp/sales/domain/SalesAmountCalculator.java`
- Create: `backend/src/test/java/com/bebefish/erp/sales/domain/SalesAmountCalculatorTest.java`

**Interfaces:**
- Produces: `SalesAmountSummary calculate(List<SalesLineInput> lines, BigDecimal shippingFee, BigDecimal receivedAmount)`。

- [x] **Step 1: 写金额与校验失败测试**

```java
@Test
void calculatesDiscountShippingReceivedAndOutstanding() {
    var summary = calculator.calculate(List.of(
            line("1250", "2.20", "0"),
            line("100", "5.00", "10")
    ), new BigDecimal("100"), new BigDecimal("1000"));

    assertThat(summary.goodsAmount()).isEqualByComparingTo("3200.00");
    assertThat(summary.discountAmount()).isEqualByComparingTo("50.00");
    assertThat(summary.totalAmount()).isEqualByComparingTo("3300.00");
    assertThat(summary.outstandingAmount()).isEqualByComparingTo("2300.00");
}

@Test
void rejectsReceivedAmountAboveTotal() {
    assertThatThrownBy(() -> calculator.calculate(
            List.of(line("1", "10", "0")), BigDecimal.ZERO, new BigDecimal("11")
    )).hasMessage("收款金额不能超过应收合计");
}
```

- [x] **Step 2: 运行 RED**

Run: `cd backend && mvn -Dtest=SalesAmountCalculatorTest test`

Expected: FAIL，计算器不存在。

- [x] **Step 3: 实现最小纯函数计算器**

```java
public record SalesLineInput(BigDecimal quantity, BigDecimal unitPrice, BigDecimal discountRate) {}

public record SalesAmountSummary(
        BigDecimal originalAmount,
        BigDecimal discountAmount,
        BigDecimal goodsAmount,
        BigDecimal shippingFee,
        BigDecimal totalAmount,
        BigDecimal receivedAmount,
        BigDecimal outstandingAmount
) {}
```

数量必须大于 0，单价和运费不得小于 0，折扣率范围为 0-100，金额统一四舍五入到 2 位。

- [x] **Step 4: 运行 GREEN**

Run: `cd backend && mvn -Dtest=SalesAmountCalculatorTest test`

Expected: PASS。

### Task 2: 销售草稿数据库与单号生成

**Files:**
- Create: `backend/src/main/resources/db/migration/V6__sales_draft_schema.sql`
- Create: `backend/src/main/java/com/bebefish/erp/sales/domain/SalesOrder.java`
- Create: `backend/src/main/java/com/bebefish/erp/sales/domain/SalesOrderItem.java`
- Create: `backend/src/main/java/com/bebefish/erp/sales/domain/SalesOrderRepository.java`
- Create: `backend/src/main/java/com/bebefish/erp/sales/domain/SalesOrderNumberGenerator.java`
- Create: `backend/src/main/java/com/bebefish/erp/sales/infrastructure/JdbcSalesOrderRepository.java`
- Create: `backend/src/main/java/com/bebefish/erp/sales/infrastructure/JdbcSalesOrderSequenceRepository.java`
- Create: `backend/src/test/java/com/bebefish/erp/sales/domain/SalesOrderNumberGeneratorTest.java`
- Create: `backend/src/test/java/com/bebefish/erp/sales/infrastructure/SalesOrderRepositoryTest.java`

**Interfaces:**
- Produces: `SOyyyyMMddNNNN` 单号。
- Produces: 销售草稿聚合持久化。

- [x] **Step 1: 写单号失败测试**

```java
@Test
void generatesFourDigitSequencePerBusinessDate() {
    assertThat(generator.next(LocalDate.of(2026, 7, 10))).isEqualTo("SO202607100001");
    assertThat(generator.next(LocalDate.of(2026, 7, 10))).isEqualTo("SO202607100002");
    assertThat(generator.next(LocalDate.of(2026, 7, 11))).isEqualTo("SO202607110001");
}
```

- [x] **Step 2: 运行 RED，创建迁移和数据库序列表**

Run: `cd backend && mvn -Dtest=SalesOrderNumberGeneratorTest test`

迁移创建：

```sql
create table sales_order_sequence (
    business_date date primary key,
    current_value int not null
);

create table sales_order (
    id bigint primary key auto_increment,
    sales_no varchar(50) not null,
    customer_id bigint not null,
    warehouse_id bigint not null,
    sales_date date not null,
    salesperson_mobile varchar(20) not null,
    status varchar(20) not null,
    transport_method varchar(20) not null,
    settlement_cycle varchar(20) not null,
    payment_method varchar(20) null,
    delivery_address varchar(500) null,
    logistics_company varchar(100) null,
    tracking_no varchar(100) null,
    package_note varchar(500) null,
    invoice_required boolean not null default false,
    invoice_status varchar(20) not null,
    goods_amount decimal(18,2) not null,
    discount_amount decimal(18,2) not null,
    shipping_fee decimal(18,2) not null,
    total_amount decimal(18,2) not null,
    received_amount decimal(18,2) not null,
    outstanding_amount decimal(18,2) not null,
    remark varchar(1000) null,
    created_at datetime(3) not null,
    updated_at datetime(3) not null,
    unique key uk_sales_no (sales_no)
);
```

`sales_order_item` 保存 SKU 引用、数量、默认售价、实际售价、折扣、金额和产品/SKU/货号/规格/单位快照；成本字段第一版允许为空，确认时写入。

- [x] **Step 3: 写仓储往返失败测试并实现 JDBC 适配器**

```java
@Test
void savesAndLoadsDraftWithAllDeliveryAndLineFields() {
    var saved = repository.save(draftOrder());
    var loaded = repository.findById(saved.id()).orElseThrow();
    assertThat(loaded.status()).isEqualTo("draft");
    assertThat(loaded.transportMethod()).isEqualTo("delivery");
    assertThat(loaded.items()).hasSize(2);
}
```

Run RED/GREEN: `cd backend && mvn -Dtest=SalesOrderRepositoryTest test`

### Task 3: 销售草稿应用服务与 API

**Files:**
- Create: `backend/src/main/java/com/bebefish/erp/sales/application/SalesDraftService.java`
- Create: `backend/src/main/java/com/bebefish/erp/sales/api/SalesOrderController.java`
- Create: `backend/src/main/java/com/bebefish/erp/sales/api/SaveSalesOrderRequest.java`
- Create: `backend/src/main/java/com/bebefish/erp/sales/api/SalesOrderResponse.java`
- Create: `backend/src/test/java/com/bebefish/erp/sales/application/SalesDraftServiceTest.java`
- Create: `backend/src/test/java/com/bebefish/erp/sales/api/SalesOrderControllerTest.java`

**Interfaces:**
- Produces: create/update/delete/get/list 草稿和销售单。
- Produces: `GET/POST /api/sales-orders`、`GET/PUT/DELETE /api/sales-orders/{id}`。

- [x] **Step 1: 写默认客户信息和快照失败测试**

```java
@Test
void createsDraftUsingCustomerAndDefaultWarehouseValues() {
    var draft = service.create(commandWithCustomerAndSku(1L, 10L));
    assertThat(draft.status()).isEqualTo("draft");
    assertThat(draft.transportMethod()).isEqualTo("delivery");
    assertThat(draft.settlementCycle()).isEqualTo("monthly");
    assertThat(draft.items().getFirst().itemNoSnapshot()).isEqualTo("EW43245");
    assertThat(inventoryRepository.ledger()).isEmpty();
}

@Test
void onlyDraftMayBeEditedOrDeleted() {
    repository.save(confirmedOrder());
    assertThatThrownBy(() -> service.update(1L, command())).hasMessage("已确认销售单不能直接修改");
    assertThatThrownBy(() -> service.delete(1L)).hasMessage("已确认销售单不能删除");
}
```

- [x] **Step 2: 运行 RED 并实现草稿服务**

Run: `cd backend && mvn -Dtest=SalesDraftServiceTest test`

服务校验客户、仓库、SKU 存在且启用，按 SKU 当前默认售价填充初始单价，但允许请求覆盖实际售价。保存时调用金额计算器重新计算。

- [x] **Step 3: 写 API 失败测试并实现控制器**

```java
mockMvc.perform(post("/api/sales-orders")
        .header("Authorization", bearerToken)
        .contentType(APPLICATION_JSON)
        .content(validDraftJson()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.status").value("draft"))
        .andExpect(jsonPath("$.data.outstandingAmount").value(2250.00));
```

Run RED/GREEN: `cd backend && mvn -Dtest=SalesOrderControllerTest test`

列表支持销售单号、客户、仓库、状态和日期范围筛选。

- [x] **Step 4: 验证后端**

Run: `cd backend && mvn test`

Expected: PASS，草稿测试中库存和财务表均无新增记录。

### Task 4: 销售前端 Service 与金额预览

**Files:**
- Create: `frontend/src/features/sales/types.ts`
- Create: `frontend/src/features/sales/salesService.ts`
- Create: `frontend/src/features/sales/mockSalesService.ts`
- Create: `frontend/src/features/sales/httpSalesService.ts`
- Create: `frontend/src/features/sales/salesAmount.ts`
- Create: `frontend/src/features/sales/salesAmount.test.ts`

**Interfaces:**
- Produces: `SalesDraftInput`、`SalesOrderDetail`、`SalesAmountPreview`。
- Consumes: 产品 SKU 搜索、客户、仓库和库存余额 API。

- [x] **Step 1: 写金额预览失败测试**

```typescript
it('previews discount shipping received and outstanding amounts', () => {
  expect(calculateSalesPreview({
    lines: [{ quantity: '100', unitPrice: '5.00', discountRate: '10' }],
    shippingFee: '100',
    receivedAmount: '200'
  })).toEqual({
    goodsAmount: '450.00', discountAmount: '50.00', totalAmount: '550.00', outstandingAmount: '350.00'
  });
});
```

- [x] **Step 2: 运行 RED 并实现纯函数**

Run: `cd frontend && npm run test:run -- src/features/sales/salesAmount.test.ts`

前端使用字符串金额和定点十进制辅助函数，不用二进制浮点直接累计。后端仍是最终权威。

- [x] **Step 3: 运行 GREEN**

Run: `cd frontend && npm run test:run -- src/features/sales/salesAmount.test.ts`

Expected: PASS。

### Task 5: 销售开单页面

**Files:**
- Create: `frontend/src/features/sales/SalesCreateView.vue`
- Create: `frontend/src/features/sales/SalesOrdersView.vue`
- Create: `frontend/src/features/sales/SalesOrderDetailView.vue`
- Create: `frontend/src/features/sales/SalesCreateView.test.ts`
- Create: `frontend/src/features/sales/SalesOrdersView.test.ts`
- Create: `frontend/src/features/sales/SalesOrderDetailView.test.ts`
- Modify: `frontend/src/features/sales/httpSalesOrderService.ts`
- Modify: `frontend/src/router/index.ts`

**Interfaces:**
- Consumes: Task 3 API 和第一、二批的客户/仓库/SKU/库存查询。
- Produces: 下一计划使用的确认和打印入口位置。

- [x] **Step 1: 写客户默认值和物流字段失败测试**

```typescript
it('loads customer delivery and settlement defaults', async () => {
  const wrapper = mount(SalesOrderCreateView, { global: { provide: { salesService: fakeService } } });
  await selectCustomer(wrapper, 1);
  expect(wrapper.get('[data-testid="transport-method"]').element.value).toBe('delivery');
  expect(wrapper.get('[data-testid="settlement-cycle"]').element.value).toBe('monthly');
  expect(wrapper.get('[data-testid="delivery-address"]').element.value).toContain('江南大道88号');
});

it('shows logistics fields only for freight or express', async () => {
  const wrapper = mountCreateView();
  await wrapper.get('[data-testid="transport-express"]').trigger('click');
  expect(wrapper.find('[data-testid="logistics-company"]').exists()).toBe(true);
  expect(wrapper.find('[data-testid="tracking-no"]').exists()).toBe(true);
});
```

- [x] **Step 2: 运行 RED 并实现主信息区域**

Run: `cd frontend && npm run test:run -- src/features/sales/SalesDraftFeature.test.ts`

- [x] **Step 3: 写 SKU 搜索、改价和保存失败测试**

```typescript
it('adds SKU, shows warehouse stock, allows price override and saves draft', async () => {
  const wrapper = mountCreateView();
  await searchAndSelectSku(wrapper, 'EW43245');
  expect(wrapper.text()).toContain('可用库存 1250');
  await wrapper.get('[data-testid="line-0-quantity"]').setValue('100');
  await wrapper.get('[data-testid="line-0-unit-price"]').setValue('2.10');
  await wrapper.get('[data-testid="save-draft"]').trigger('click');
  expect(fakeService.createDraft).toHaveBeenCalledWith(expect.objectContaining({
    items: [expect.objectContaining({ quantity: '100', unitPrice: '2.10' })]
  }));
});
```

- [x] **Step 4: 实现明细、金额区、列表和详情**

页面使用紧凑业务布局，不在卡片中嵌套卡片。列表显示单号、日期、客户、仓库、金额、已收、欠款、状态和经办人。草稿详情提供编辑和删除，确认按钮先占位为禁用并标注由下一批启用。

- [x] **Step 5: 运行 GREEN、全量测试和构建**

Run: `cd frontend && npm run test:run && npm run build`

Expected: PASS。

### Task 6: 第三批验收门槛

**Files:**
- Modify: `docs/04-development-guide.md`
- Modify: `docs/06-phased-development-plan.md`

- [x] **Step 1: 运行前后端全量测试**

Run: `cd backend && mvn test`

Run: `cd frontend && npm run test:run && VITE_DATA_SOURCE=real npm run build`

Expected: 全部 PASS。

- [ ] **Step 2: 真实 API 浏览器验收**

验证散客和普通客户开单、默认仓库、SKU 搜索、库存展示、手工改价、折扣、运费、自提/送货/托运/快递、日/月/季/年结、未付/部分/全额收款预览、草稿刷新保留、草稿编辑和删除。

- [x] **Step 3: 验证草稿零副作用**

保存和编辑草稿前后对比库存余额、库存流水、应收和收款记录，必须完全不变。

- [x] **Step 4: 更新文档并等待用户验收**

展示测试和页面结果，不提交 Git；用户验收后进入销售确认与财务打印计划。
