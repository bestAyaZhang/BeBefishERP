# Shipping Figma Flow Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将已确认的发货管理 Figma 设计实现为可用的 ERP 发货列表、全页新建/详情/编辑流程，并把安能电子面单下单直接融合到发货单中。

**Architecture:** 延续现有 Spring Boot + JdbcTemplate 后端和 Vue 3 serviceFactory 前端。发货单持久化收件拆分地址、店铺、多个备货人及可暂存的安能下单草稿；创建和更新由服务端维护发货日期、默认状态和下单人，物流下单只接收发货单版本并读取持久化草稿，避免页面重复提交收件资料。前端改为列表、新建、详情、编辑四个路由页面，智能识别在收件卡片内完成，保存并下单成功后显示结果弹框。

**Tech Stack:** Java 21、Spring Boot 3.3.5、Bean Validation、JdbcTemplate、Flyway、MySQL、Vue 3、TypeScript、Vue Router、Tailwind CSS、lucide-vue-next、Vitest、Vue Test Utils。

**Spec:** `docs/superpowers/specs/2026-09-17-shipping-ledger-design.md`、`docs/superpowers/specs/2026-09-18-shipping-figma-design.md`；实时 Figma 节点 `1313:31754`、`1313:31755`、`1313:31756`、`1314:43250`、`1314:43531`、`1314:43848`、`1377:1863` 及本轮确认内容优先于旧文档中的 7 画板、独立成功页、两列表单和 1320px 高度描述。

## Global Constraints

- 第一版不选择 SKU、不关联库存、不扣减库存；备货清单继续使用最多 10000 字的自由多行文本。
- 发货单和安能一键下单位于同一个新建/编辑页面，不新增独立下单路由。
- 收件信息原文只在浏览器中用于识别，不写入数据库；点击“智能识别”后在当前卡片内填充姓名、电话、省、市、区/县和详细地址。
- 创建时发货日期由服务端取当前日期，状态固定为 `unfinished`，下单人取当前登录人的显示名；三个字段均不出现在新建表单中。
- 编辑时允许人工维护四种业务状态：`unfinished`、`completed`、`out_of_stock`、`partially_shipped`。
- 备货人支持多选，候选人来自在职员工；候选接口只要求 `shipping:view`，不要求发货人员同时拥有 `organization:view`。
- 店铺名称为必选项，候选值来自 `ERP_SHIPPING_SHOP_NAMES` 配置；本地默认值为“贝贝鱼淘宝旗舰店”，页面不得硬编码实际生产店铺列表。
- “下单结果”不是录入字段；安能成功返回后回填物流单号并弹出“返回发货列表 / 继续下单”。
- 已存在非 `rejected` 的安能订单后，后端必须锁定平台、店铺、备货人、收件信息、运费预测及安能下单参数；备货清单、备注和状态仍可更新。
- 未提供运费试算接口，不编造计费公式；运费预测保持可选人工金额，并提示实际费用以安能结算为准。
- 保留 V13 已实现的订单预约、防重复下单、未知结果阻断、测试环境标识和签名协议。
- 不新增前端运行时依赖；复用现有 Tailwind、lucide-vue-next、消息组件、路由守卫和认证会话。
- 当前 `codex/shipping-ledger` 是包含未提交发货代码的普通工作目录；执行时直接在此分支增量修改，不搬移或覆盖现有变更。

## Review Focus

- 智能识别输入含换行、标签和空格时，应提取可识别字段；缺失字段保持用户原值并明确提示人工补充，不能静默清空。
- “保存并一键下单”在保存成功、安能失败时，应保留已创建的发货单并进入可重试的编辑状态，不能重复创建发货单。
- 重复点击、刷新或安能返回未知结果时，应复用 V13 幂等记录并阻止再次提交，不能生成第二个外部订单。
- 已下单发货单的关键字段即使绕过前端直接调用更新 API，也应返回 409；仅修改状态、备货清单或备注应成功。
- 店铺配置为空、没有在职员工或旧数据只有单个 `preparer` 时，页面仍应可打开，旧备货人可回显，必填店铺在保存时给出明确校验错误。

---

### Task 1: 扩展发货数据契约、默认值和持久化

**Files:**
- Create: `backend/src/main/java/com/bebefish/erp/shipping/domain/ShipmentFormInput.java`
- Create: `backend/src/main/java/com/bebefish/erp/shipping/domain/AneOrderDraft.java`
- Create: `backend/src/main/java/com/bebefish/erp/shipping/domain/ShipmentEditPolicy.java`
- Create: `backend/src/main/resources/db/migration/V14__shipping_figma_flow.sql`
- Modify: `backend/src/main/java/com/bebefish/erp/shipping/domain/ShipmentContent.java`
- Modify: `backend/src/main/java/com/bebefish/erp/shipping/domain/ShipmentRepository.java`
- Modify: `backend/src/main/java/com/bebefish/erp/shipping/application/ShipmentService.java`
- Modify: `backend/src/main/java/com/bebefish/erp/shipping/infrastructure/JdbcShipmentRepository.java`
- Modify: `backend/src/main/java/com/bebefish/erp/shipping/api/SaveShipmentRequest.java`
- Modify: `backend/src/main/java/com/bebefish/erp/shipping/api/ShipmentController.java`
- Modify: `backend/src/test/java/com/bebefish/erp/shipping/application/ShipmentServiceTest.java`
- Modify: `backend/src/test/java/com/bebefish/erp/shipping/api/ShipmentControllerTest.java`
- Create: `backend/src/test/java/com/bebefish/erp/shipping/domain/ShipmentEditPolicyTest.java`

**Interfaces:**
- Consumes: `ErpPrincipal.employeeId()`, `ErpPrincipal.displayName()`, `ErpPrincipal.mobile()` and `ErpPrincipal.operatorIdentifier()` from the existing authentication layer.
- Produces: `ShipmentFormInput`, `AneOrderDraft`, expanded `ShipmentContent`, `ShipmentService.create(ShipmentFormInput, String, String)`, and `ShipmentService.update(long, ShipmentFormInput, String, long, String)`.

The persisted/request types are fixed as follows so later frontend and logistics tasks use the same property names:

```java
public record AneOrderDraft(
        String cargoName,
        String packType,
        BigDecimal weight,
        BigDecimal volume,
        Integer pieceAmount,
        Integer productTypeId,
        Integer goodsType,
        Integer payType,
        String logisticsRemark
) {}

public record ShipmentFormInput(
        @Size(max = 100) String platform,
        @NotBlank @Size(max = 200) String shopName,
        @Size(max = 20) List<@NotBlank @Size(max = 100) String> preparers,
        @NotBlank @Size(max = 100) String recipientName,
        @NotBlank @Size(max = 50) String recipientPhone,
        @NotBlank @Size(max = 30) String recipientProvince,
        @NotBlank @Size(max = 30) String recipientCity,
        @NotBlank @Size(max = 30) String recipientCounty,
        @NotBlank @Size(max = 500) String recipientDetailAddress,
        @NotBlank @Size(max = 10000) String preparationContent,
        @Size(max = 5000) String remark,
        @DecimalMin("0") @Digits(integer = 10, fraction = 2) BigDecimal estimatedFreight,
        @NotNull @Valid AneOrderDraft orderDraft
) {}

public record SaveShipmentRequest(
        @NotNull @Valid ShipmentFormInput form,
        @Pattern(regexp = "unfinished|completed|out_of_stock|partially_shipped") String status,
        @Min(0) Long version
) {}
```

`ShipmentContent` returns the normalized form fields plus `shipmentDate`, `status`, `orderer`, `logisticsCompany`, and `trackingNo`. `recipientFullAddress()` concatenates province/city/county/detail for storage and display, while the four source address fields remain separately available to Aneng.

```java
public record ShipmentContent(
        LocalDate shipmentDate,
        String platform,
        String shopName,
        List<String> preparers,
        String recipientName,
        String recipientPhone,
        String recipientProvince,
        String recipientCity,
        String recipientCounty,
        String recipientDetailAddress,
        String preparationContent,
        String remark,
        BigDecimal estimatedFreight,
        AneOrderDraft orderDraft,
        String status,
        String orderer,
        String logisticsCompany,
        String trackingNo
) {}
```

- [ ] **Step 1: Write failing service tests for server-owned values and normalization**

Add focused cases to `ShipmentServiceTest`:

```java
@Test
void createOwnsDateStatusAndOrdererAndNormalizesPreparers() {
    var saved = service.create(form(List.of(" 小周 ", "阿杰", "小周")), "employee:8", "陈小鱼");

    assertThat(saved.content().shipmentDate()).isEqualTo(LocalDate.of(2026, 9, 24));
    assertThat(saved.content().status()).isEqualTo("unfinished");
    assertThat(saved.content().orderer()).isEqualTo("陈小鱼");
    assertThat(saved.content().preparers()).containsExactly("小周", "阿杰");
}

@Test
void updatePreservesServerOwnedDateAndOrderer() {
    var updated = service.update(7L, changedForm(), "completed", 2L, "employee:9");

    assertThat(updated.content().shipmentDate()).isEqualTo(LocalDate.of(2026, 9, 18));
    assertThat(updated.content().orderer()).isEqualTo("陈小鱼");
    assertThat(updated.content().status()).isEqualTo("completed");
}
```

Use a fixed `Clock` in the service test so the date assertion is deterministic.

- [ ] **Step 2: Run the service tests and verify the new contract fails**

Run:

```powershell
cd backend
mvn -Dtest=ShipmentServiceTest test
```

Expected: compilation fails because `ShipmentFormInput`, `AneOrderDraft`, the new service signatures, and expanded `ShipmentContent` do not exist.

- [ ] **Step 3: Add V14 columns and backfill legacy rows**

Create `V14__shipping_figma_flow.sql` with these operations in order:

```sql
alter table shipment
    add column shop_name varchar(200) not null default '' after platform,
    add column recipient_province varchar(30) not null default '' after recipient_phone,
    add column recipient_city varchar(30) not null default '' after recipient_province,
    add column recipient_county varchar(30) not null default '' after recipient_city,
    add column recipient_detail_address varchar(500) not null default '' after recipient_county,
    add column preparers_json json null after preparer,
    add column cargo_name varchar(32) not null default '' after estimated_freight,
    add column pack_type varchar(50) not null default '' after cargo_name,
    add column volume decimal(12,2) null after weight,
    add column piece_amount int null after volume,
    add column product_type_id int null after piece_amount,
    add column goods_type int null after product_type_id,
    add column pay_type int null after goods_type,
    add column logistics_remark varchar(200) not null default '' after pay_type;

update shipment
set recipient_detail_address = recipient_address,
    preparers_json = case when preparer = '' then json_array() else json_array(preparer) end;

alter table shipment modify preparers_json json not null;
```

Keep the legacy `recipient_address` as a denormalized full address and `preparer` as a comma-joined search column. New writes update both compatibility columns and the structured fields.

- [ ] **Step 4: Implement normalized domain types and server-owned create/update values**

Inject the existing `Clock.systemDefaultZone()` bean from `SecurityConfig` into `ShipmentService`. Normalize text with `strip()`, remove blank preparers, preserve first occurrence order, cap the normalized list at 20, and use the display name fallback below in the controller:

```java
private static String operatorDisplayName(ErpPrincipal principal) {
    if (principal.displayName() != null && !principal.displayName().isBlank()) return principal.displayName().strip();
    if (principal.mobile() != null && !principal.mobile().isBlank()) return principal.mobile().strip();
    return "员工#" + principal.employeeId();
}
```

`create` must set `LocalDate.now(clock)`, `unfinished`, and the display name. `update` must copy the existing date and orderer and require a nonblank valid status.

- [ ] **Step 5: Update Jdbc mapping and optimistic update rules**

Map every V14 column, serialize `preparers_json` with the existing Jackson `ObjectMapper`, and keep parameterized SQL. Add `ShipmentRepository.hasNonRejectedLogisticsOrder(long id)`. `ShipmentEditPolicy.assertAllowed(previous, next, ordered)` compares platform, shop, preparers, all recipient fields, estimated freight and every `AneOrderDraft` field when `ordered=true`; it permits changes only to status, preparation content and remark.

Pin the rule with these domain tests:

```java
@Test
void orderedShipmentRejectsRecipientOrOrderFieldChanges() {
    assertThatThrownBy(() -> policy.assertAllowed(saved, changedRecipient, true))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("已下单");
}

@Test
void orderedShipmentAllowsPreparationRemarkAndStatusChanges() {
    assertThatCode(() -> policy.assertAllowed(saved, changedProgressOnly, true)).doesNotThrowAnyException();
}
```

Keep the update statement’s version predicate and existing non-rejected-order equality predicate as the transaction-level race guard; include `preparers_json` in that predicate. This makes the API enforce the locked edit state even if the UI is bypassed.

- [ ] **Step 6: Update controller contract tests**

Add request/response assertions to `ShipmentControllerTest`:

```java
mockMvc.perform(post("/api/shipments")
        .with(auth("shipping:create", "陈小鱼"))
        .contentType(APPLICATION_JSON)
        .content(objectMapper.writeValueAsString(Map.of("form", validFormJson()))))
    .andExpect(status().isOk())
    .andExpect(jsonPath("$.data.content.status").value("unfinished"))
    .andExpect(jsonPath("$.data.content.orderer").value("陈小鱼"))
    .andExpect(jsonPath("$.data.content.preparers[0]").value("小周"));
```

Also assert 400 for blank shop, more than 20 preparers, blank recipient region, and update without `status` or `version`.

- [ ] **Step 7: Run focused backend tests**

Run:

```powershell
cd backend
mvn -Dtest=ShipmentServiceTest,ShipmentEditPolicyTest,ShipmentControllerTest test
```

Expected: all focused tests pass.

- [ ] **Step 8: Commit the data-contract task**

```powershell
git add backend/src/main/java/com/bebefish/erp/shipping backend/src/main/resources/db/migration/V14__shipping_figma_flow.sql backend/src/test/java/com/bebefish/erp/shipping
git commit -m "feat: expand shipping form data model"
```

### Task 2: Add list summary and permission-safe form options

**Files:**
- Create: `backend/src/main/java/com/bebefish/erp/shipping/domain/ShipmentSummary.java`
- Create: `backend/src/main/java/com/bebefish/erp/shipping/application/ShippingFormOptionsService.java`
- Create: `backend/src/main/java/com/bebefish/erp/shipping/infrastructure/JdbcShippingFormOptionsRepository.java`
- Create: `backend/src/main/java/com/bebefish/erp/shipping/api/ShippingFormOptions.java`
- Create: `backend/src/main/java/com/bebefish/erp/shipping/application/ShippingProperties.java`
- Create: `backend/src/test/java/com/bebefish/erp/shipping/application/ShippingFormOptionsServiceTest.java`
- Modify: `backend/src/main/java/com/bebefish/erp/shipping/domain/ShipmentRepository.java`
- Modify: `backend/src/main/java/com/bebefish/erp/shipping/infrastructure/JdbcShipmentRepository.java`
- Modify: `backend/src/main/java/com/bebefish/erp/shipping/application/ShipmentService.java`
- Modify: `backend/src/main/java/com/bebefish/erp/shipping/api/ShipmentController.java`
- Modify: `backend/src/main/resources/application.yml`
- Modify: `.env.example`
- Modify: `backend/src/test/java/com/bebefish/erp/shipping/api/ShipmentControllerTest.java`

**Interfaces:**
- Consumes: the `employee` table from V10 and shipment status/date columns from V12.
- Produces: `GET /api/shipments/summary?date=YYYY-MM-DD`, `GET /api/shipments/form-options`, `ShipmentSummary(todayCount, unfinishedCount, completedCount, outOfStockCount, partiallyShippedCount)`, and `ShippingFormOptions(shopNames, preparers)` where each preparer is `{ employeeId, employeeName }`.

- [ ] **Step 1: Write failing summary and options tests**

Add controller expectations:

```java
mockMvc.perform(get("/api/shipments/summary").param("date", "2026-09-24").with(auth("shipping:view")))
    .andExpect(status().isOk())
    .andExpect(jsonPath("$.data.todayCount").value(7))
    .andExpect(jsonPath("$.data.outOfStockCount").value(1))
    .andExpect(jsonPath("$.data.partiallyShippedCount").value(1));

mockMvc.perform(get("/api/shipments/form-options").with(auth("shipping:view")))
    .andExpect(status().isOk())
    .andExpect(jsonPath("$.data.shopNames[0]").value("贝贝鱼淘宝旗舰店"))
    .andExpect(jsonPath("$.data.preparers[0].employeeName").value("小周"));
```

The options service test supplies active, disabled, and resigned employees and asserts only active names remain, sorted by employee id, with duplicate/blank configured shop names removed.

- [ ] **Step 2: Run tests and verify endpoint absence**

Run:

```powershell
cd backend
mvn -Dtest=ShipmentControllerTest,ShippingFormOptionsServiceTest test
```

Expected: tests fail because the summary and form-options APIs are not implemented.

- [ ] **Step 3: Implement one-query summary and narrow employee options**

Use conditional aggregation for the selected date:

```sql
select count(*) as today_count,
       sum(status = 'unfinished') as unfinished_count,
       sum(status = 'completed') as completed_count,
       sum(status = 'out_of_stock') as out_of_stock_count,
       sum(status = 'partially_shipped') as partially_shipped_count
from shipment
where shipment_date = :date
```

`JdbcShippingFormOptionsRepository` must query only `id,name` from `employee where status='active' order by id`; do not expose phone, employment type, department, or login fields. Protect both endpoints with `shipping:view`.

- [ ] **Step 4: Bind configurable shop names**

Add this configuration shape:

```yaml
erp:
  shipping:
    shop-names: ${ERP_SHIPPING_SHOP_NAMES:贝贝鱼淘宝旗舰店}
```

Add `ERP_SHIPPING_SHOP_NAMES=贝贝鱼淘宝旗舰店` to `.env.example` with a comment that multiple values are comma-separated. Normalize the bound list in `ShippingFormOptionsService` and return an empty list when the setting is blank.

- [ ] **Step 5: Run focused tests**

Run:

```powershell
cd backend
mvn -Dtest=ShipmentControllerTest,ShippingFormOptionsServiceTest test
```

Expected: all focused tests pass, and an account with only `shipping:view` can load options.

- [ ] **Step 6: Commit summary and options**

```powershell
git add .env.example backend/src/main/java/com/bebefish/erp/shipping backend/src/main/resources/application.yml backend/src/test/java/com/bebefish/erp/shipping
git commit -m "feat: add shipping summary and form options"
```

### Task 3: Refactor Aneng submission to use the saved shipment draft

**Files:**
- Modify: `backend/src/main/java/com/bebefish/erp/shipping/logistics/PlaceAneOrderRequest.java`
- Modify: `backend/src/main/java/com/bebefish/erp/shipping/logistics/AneOrderService.java`
- Modify: `backend/src/main/java/com/bebefish/erp/shipping/logistics/AneOrderStore.java`
- Modify: `backend/src/main/java/com/bebefish/erp/shipping/api/LogisticsOrderController.java`
- Modify: `backend/src/test/java/com/bebefish/erp/shipping/logistics/AneOrderServiceTest.java`
- Modify: `backend/src/test/java/com/bebefish/erp/shipping/logistics/AneOrderStoreTest.java`
- Modify: `backend/src/test/java/com/bebefish/erp/shipping/api/ShipmentControllerTest.java`

**Interfaces:**
- Consumes: persisted `ShipmentContent` and `AneOrderDraft` from Task 1.
- Produces: `PlaceAneOrderRequest(@NotNull @Min(0) Long version)`; `POST /api/shipments/{id}/logistics-order` no longer accepts address or cargo data from the browser.

- [ ] **Step 1: Write failing tests for persisted-data submission**

In `AneOrderServiceTest`, save a shipment whose address and draft are distinctive, call `place(id, new PlaceAneOrderRequest(version), operator)`, and assert the Aneng client receives:

```java
assertThat(params).containsEntry("receiveMan", "林女士")
    .containsEntry("receivePhone", "13800006028")
    .containsEntry("toProvinceName", "浙江省")
    .containsEntry("toCityName", "杭州市")
    .containsEntry("toCountyName", "余杭区")
    .containsEntry("toAddress", "示例路18号2栋101室")
    .containsEntry("cargoName", "水族用品")
    .containsEntry("pieceAmount", 2);
```

Add one case with a missing volume that expects `VALIDATION_FAILED` and no client call. Keep the existing repeat-click and unknown-outcome tests, changing only their request construction to version-only.

- [ ] **Step 2: Run logistics tests and verify the old payload contract fails**

Run:

```powershell
cd backend
mvn -Dtest=AneOrderServiceTest,AneOrderStoreTest test
```

Expected: compilation/test failures because `PlaceAneOrderRequest` still owns the duplicated order fields.

- [ ] **Step 3: Move validation and request mapping to persisted content**

Replace the request with the version-only record. In `AneOrderService.place`, validate every required Aneng field from `shipment.content().orderDraft()` immediately before `store.claim`; retain allowed-value checks for product type `{95,24,23,524,270,546}`, delivery type `{180,179,285}`, and payment type `{102,103,104}`.

Build `toProvinceName`, `toCityName`, `toCountyName`, `toAddress`, receiver identity, weight, volume, pieces, cargo, packaging and logistics remark exclusively from the claimed shipment snapshot. Do not accept browser overrides.

- [ ] **Step 4: Preserve idempotency and lock semantics in the store**

Change `AneOrderStore.claim` to receive `(shipmentId, expectedVersion, operator)`. Generate `request_snapshot` from the persisted shipment content, preserve the existing row reservation and unknown-result behavior, and keep successful tracking-number backfill in the same transaction as the stored result.

Update the store test so two claims for the same shipment return the existing order and so a version mismatch fails before the external client is called.

- [ ] **Step 5: Run focused logistics and API tests**

Run:

```powershell
cd backend
mvn -Dtest=AneOrderServiceTest,AneOrderStoreTest,ShipmentControllerTest test
```

Expected: all focused tests pass.

- [ ] **Step 6: Commit the integrated order contract**

```powershell
git add backend/src/main/java/com/bebefish/erp/shipping/logistics backend/src/test/java/com/bebefish/erp/shipping
git commit -m "refactor: submit aneng order from saved shipment"
```

### Task 4: Add frontend contracts, recipient recognition and multi-select controls

**Files:**
- Create: `frontend/src/features/shipping/shippingRecipientParser.ts`
- Create: `frontend/src/features/shipping/shippingRecipientParser.test.ts`
- Create: `frontend/src/features/shipping/RecipientRecognitionCard.vue`
- Create: `frontend/src/features/shipping/RecipientRecognitionCard.test.ts`
- Create: `frontend/src/features/shipping/PreparerMultiSelect.vue`
- Create: `frontend/src/features/shipping/PreparerMultiSelect.test.ts`
- Modify: `frontend/src/features/shipping/types.ts`
- Modify: `frontend/src/features/shipping/shippingService.ts`
- Modify: `frontend/src/features/shipping/httpShippingService.ts`
- Modify: `frontend/src/features/shipping/httpShippingService.test.ts`
- Modify: `frontend/src/features/shipping/mockShippingService.ts`

**Interfaces:**
- Consumes: `parseAddress(value: string)` from `frontend/src/features/masterdata/addressParser.ts` and the Task 1/2 backend contracts.
- Produces: `parseRecipientText(raw: string): ParsedRecipient`, `RecipientRecognitionCard` v-model bindings, `PreparerMultiSelect` v-model, and expanded `ShippingService` methods.

Use these frontend contracts:

```ts
export interface AneOrderDraft {
  cargoName: string;
  packType: string;
  weight: number | null;
  volume: number | null;
  pieceAmount: number | null;
  productTypeId: number | null;
  goodsType: number | null;
  payType: number | null;
  logisticsRemark: string;
}

export interface ShipmentFormInput {
  platform: string;
  shopName: string;
  preparers: string[];
  recipientName: string;
  recipientPhone: string;
  recipientProvince: string;
  recipientCity: string;
  recipientCounty: string;
  recipientDetailAddress: string;
  preparationContent: string;
  remark: string;
  estimatedFreight: number | null;
  orderDraft: AneOrderDraft;
}

export interface ShipmentContent extends ShipmentFormInput {
  shipmentDate: string;
  status: ShipmentStatus;
  orderer: string;
  logisticsCompany: string;
  trackingNo: string;
}

export interface ShipmentSummary {
  todayCount: number;
  unfinishedCount: number;
  completedCount: number;
  outOfStockCount: number;
  partiallyShippedCount: number;
}

export interface ShippingFormOptions {
  shopNames: string[];
  preparers: Array<{ employeeId: number; employeeName: string }>;
}
```

`ShippingService` adds `summary(date): Promise<ShipmentSummary>`, `formOptions(): Promise<ShippingFormOptions>`, changes `create(form: ShipmentFormInput)`, `update(id, form, status, version)`, and changes `placeLogisticsOrder(id, { version })`. Replace `emptyShipment()` with `emptyShipmentForm(): ShipmentFormInput`, and add `recipientFullAddress(content)` for list/detail display.

The blank form defaults are `packType='纸箱'`, `pieceAmount=1`, `productTypeId=524` (MiNi 电商小件), `goodsType=180` (送货，不含上楼), and `payType=104` (月结). If the options API returns exactly one shop, preselect it; with zero or multiple shops, require the user to choose.

- [ ] **Step 1: Write parser tests for plain and labeled recipient text**

```ts
it('parses a plain recipient line', () => {
  expect(parseRecipientText('林女士 13800006028 浙江省杭州市余杭区 示例路18号2栋101室')).toEqual({
    recipientName: '林女士', recipientPhone: '13800006028', recipientProvince: '浙江省',
    recipientCity: '杭州市', recipientCounty: '余杭区', recipientDetailAddress: '示例路18号2栋101室'
  });
});

it('uses labels across lines and leaves missing values empty', () => {
  expect(parseRecipientText('收件人：王先生\n电话：13900001086\n地址：江苏省苏州市吴中区')).toMatchObject({
    recipientName: '王先生', recipientPhone: '13900001086', recipientDetailAddress: ''
  });
});
```

- [ ] **Step 2: Run parser tests and verify the module is absent**

Run:

```powershell
cd frontend
npm run test:run -- src/features/shipping/shippingRecipientParser.test.ts
```

Expected: FAIL because the parser module does not exist.

- [ ] **Step 3: Implement recognition without destructive blanking**

Reuse the region tree through `parseAddress`; extract labeled name/phone first, then fall back to the non-address, non-phone prefix. `RecipientRecognitionCard` emits only nonblank parsed values into the current form, so an unrecognized field keeps its manually entered value. It displays “已识别，请核对” when all six fields are present and “已识别部分信息，请补充标红字段” otherwise. It must not navigate, save, or call logistics APIs.

- [ ] **Step 4: Write and implement multi-select behavior**

Test selection toggling, duplicate prevention, Escape close, outside-click close, and legacy selected names missing from the current active-employee options. The control renders selected names as removable chips and exposes a button/listbox with `aria-expanded`, `role="listbox"`, and `aria-multiselectable="true"`.

- [ ] **Step 5: Update service contract tests and mock persistence**

Assert exact calls:

```ts
expect(fetcher.mock.calls).toContainEqual(['/api/shipments/summary?date=2026-09-24']);
expect(fetcher.mock.calls).toContainEqual(['/api/shipments/form-options']);
expect(fetcher).toHaveBeenCalledWith('/api/shipments/8/logistics-order', expect.objectContaining({
  method: 'POST', body: JSON.stringify({ version: 3 })
}));
```

Update mock creation to set client-local current date, `unfinished`, and current display name, and persist all new structured fields. Keep mock logistics unavailable so development mode never suggests that an external order was created.

- [ ] **Step 6: Run focused frontend tests**

Run:

```powershell
cd frontend
npm run test:run -- src/features/shipping/shippingRecipientParser.test.ts src/features/shipping/RecipientRecognitionCard.test.ts src/features/shipping/PreparerMultiSelect.test.ts src/features/shipping/httpShippingService.test.ts
```

Expected: all focused tests pass.

- [ ] **Step 7: Commit frontend foundations**

```powershell
git add frontend/src/features/shipping
git commit -m "feat: add shipping form foundations"
```

### Task 5: Implement the Figma list and routed detail page

**Files:**
- Modify: `frontend/src/features/shipping/ShipmentListView.vue`
- Modify: `frontend/src/features/shipping/ShipmentListView.test.ts`
- Create: `frontend/src/features/shipping/ShipmentDetailView.vue`
- Create: `frontend/src/features/shipping/ShipmentDetailView.test.ts`
- Modify: `frontend/src/router/index.ts`
- Modify: `frontend/src/AppRouting.test.ts`
- Modify: `frontend/src/layouts/ErpLayout.vue`

**Interfaces:**
- Consumes: `ShippingService.summary`, `list`, `get`, `getLogisticsOrder`, the current permission list, and the final Figma list/detail nodes.
- Produces: routes `shipping-list` and `shipping-detail`; list/detail page navigation used by Task 6. The existing editor dialog remains available until Task 6 replaces create/edit together, so this task ends with a working intermediate application.

- [ ] **Step 1: Replace dialog-flow tests with route-flow tests**

Update `ShipmentListView.test.ts` to mount with a memory router and assert:

```ts
expect(wrapper.get('[data-testid="shipment-summary-today"]').text()).toContain('7 单');
await wrapper.get('[data-testid="shipment-detail-8"]').trigger('click');
expect(router.currentRoute.value).toMatchObject({ name: 'shipping-detail', params: { id: '8' } });
```

Keep coverage for today, all-incomplete, explicit date/status/platform filters, pagination, loading failure/retry, permission-hidden create action, multiline preparation summary, and empty results.

- [ ] **Step 2: Run list tests and verify dialog behavior conflicts**

Run:

```powershell
cd frontend
npm run test:run -- src/features/shipping/ShipmentListView.test.ts
```

Expected: the summary assertion and detail-route assertion fail because the current view has no summary cards and opens `ShipmentDetailDialog`.

- [ ] **Step 3: Implement the Figma list layout**

Match node `1313:31754`: title/action row, four summary cards, one filter card, and a single full-width table. Load summary and list independently so a summary error does not erase list results. Mask phones in the table, combine platform/date under the shipment number, show recipient address, preparation/remark summary, logistics/tracking, weight/estimate, state badge, preparer/orderer, and row actions.

The “今天” button sets both dates to `todayDate()`. “全部未完成” clears both dates and sets `incompleteOnly=true`. The reset button returns to today, page 1 and cleared keyword/status/platform.

- [ ] **Step 4: Write and implement the detail page test**

Test loading, not-found error/retry, read-only recipient and preparation cards, record metadata, current logistics state, tracking number, and return-to-list navigation. Task 6 adds the edit action together with the real edit route so this intermediate page never points to a missing route.

Match node `1313:31756`: wide left content cards and narrower right status/order cards on desktop, collapsing to one column below `lg`. Show the shop in record information and show multiple preparers joined by `、`.

- [ ] **Step 5: Add route and header definitions**

Keep the existing list route and add the detail route after it:

```ts
{ path: 'shipping/list', name: 'shipping-list', component: () => import('../features/shipping/ShipmentListView.vue'), meta: { requiredPermission: 'shipping:view' } },
{ path: 'shipping/:id', name: 'shipping-detail', component: () => import('../features/shipping/ShipmentDetailView.vue'), meta: { requiredPermission: 'shipping:view' } },
```

Add the Chinese detail breadcrumb mapping in `ErpLayout.vue`, and extend `AppRouting.test.ts` to assert the detail route renders inside the authenticated ERP shell and redirects without `shipping:view`.

- [ ] **Step 6: Run routed page tests**

Run:

```powershell
cd frontend
npm run test:run -- src/features/shipping/ShipmentListView.test.ts src/features/shipping/ShipmentDetailView.test.ts src/AppRouting.test.ts
```

Expected: all routed list/detail tests pass.

- [ ] **Step 7: Commit routed list and detail pages**

```powershell
git add frontend/src/features/shipping/ShipmentListView.vue frontend/src/features/shipping/ShipmentListView.test.ts frontend/src/features/shipping/ShipmentDetailView.vue frontend/src/features/shipping/ShipmentDetailView.test.ts frontend/src/router/index.ts frontend/src/AppRouting.test.ts frontend/src/layouts/ErpLayout.vue
git commit -m "feat: add routed shipping list and detail pages"
```

### Task 6: Build the combined shipment form and one-click order flow

**Files:**
- Create: `frontend/src/features/shipping/ShipmentFormView.vue`
- Create: `frontend/src/features/shipping/ShipmentFormView.test.ts`
- Create: `frontend/src/features/shipping/ShipmentSuccessDialog.vue`
- Create: `frontend/src/features/shipping/ShipmentSuccessDialog.test.ts`
- Modify: `frontend/src/features/shipping/ShipmentListView.vue`
- Modify: `frontend/src/features/shipping/ShipmentDetailView.vue`
- Modify: `frontend/src/features/shipping/ShipmentDetailView.test.ts`
- Delete: `frontend/src/features/shipping/ShipmentEditorDialog.vue`
- Delete: `frontend/src/features/shipping/ShipmentDetailDialog.vue`
- Delete: `frontend/src/features/shipping/ShipmentLogisticsPanel.vue`
- Delete: `frontend/src/features/shipping/ShipmentLogisticsPanel.test.ts`
- Modify: `frontend/src/features/shipping/ShipmentListView.test.ts`
- Modify: `frontend/src/router/index.ts`
- Modify: `frontend/src/AppRouting.test.ts`
- Modify: `frontend/src/layouts/ErpLayout.vue`

**Interfaces:**
- Consumes: `RecipientRecognitionCard`, `PreparerMultiSelect`, all Task 4 service methods, `shipping-list`/`shipping-detail` from Task 5, and Figma nodes `1313:31755`, `1314:43250`, `1314:43531`, `1377:1863`.
- Produces: `shipping-new`/`shipping-edit`, one `ShipmentFormView` for create/pre-order edit/post-order edit, and a result-only `ShipmentSuccessDialog`.

- [ ] **Step 1: Write failing create-form tests**

Cover defaults, hidden server fields, option loading, recognition, multiline preparation, save-only and validation:

```ts
expect(wrapper.find('[data-testid="shipment-date"]').exists()).toBe(false);
expect(wrapper.find('[data-testid="shipment-orderer"]').exists()).toBe(false);
expect(wrapper.find('[data-testid="shipment-status"]').exists()).toBe(false);

await wrapper.get('[data-testid="recipient-raw"]').setValue('林女士 13800006028 浙江省杭州市余杭区 示例路18号2栋101室');
await wrapper.get('[data-testid="recipient-recognize"]').trigger('click');
expect((wrapper.get('[data-testid="recipient-county"]').element as HTMLInputElement).value).toBe('余杭区');

await wrapper.get('[data-testid="shipment-save-only"]').trigger('click');
expect(service.create).toHaveBeenCalledWith(expect.objectContaining({
  shopName: '贝贝鱼淘宝旗舰店', preparers: ['小周', '阿杰'], preparationContent: expect.stringContaining('\n')
}));
expect(router.currentRoute.value.name).toBe('shipping-detail');
```

Save-only validates shipment fields but allows an incomplete `orderDraft`. Save-and-order additionally requires every Aneng field.

- [ ] **Step 2: Run the form test and verify the view is absent**

Run:

```powershell
cd frontend
npm run test:run -- src/features/shipping/ShipmentFormView.test.ts
```

Expected: FAIL because `ShipmentFormView.vue` does not exist.

- [ ] **Step 3: Implement the full-width Figma form**

Match node `1313:31755` with these full-width sections:

1. 发货单信息：平台、必选店铺、多选备货人。
2. 收件人信息：原文输入与同行智能识别按钮；姓名、电话、省、市、区/县、详细地址在当前卡片内填充。
3. 备货清单：大尺寸多行文本和备注，容纳多 SKU/数量描述但不连接 SKU。
4. 安能一键下单：货物名称、包装方式、重量、体积、件数、物流公司只读说明、物流产品、送货方式、付款方式、物流备注和可选运费预测。
5. 底部操作栏：取消、仅保存发货单/仅保存修改、保存并一键下单。

Use responsive grids that collapse below `lg`; do not reproduce Figma’s fixed pixel canvas or add a nested page scrollbar. Reuse the app shell header/sidebar instead of duplicating them inside the page.

Add `shipping-new` before the dynamic routes and `shipping-edit` before `shipping-detail`:

```ts
{ path: 'shipping/new', name: 'shipping-new', component: () => import('../features/shipping/ShipmentFormView.vue'), meta: { requiredPermission: 'shipping:create' } },
{ path: 'shipping/:id/edit', name: 'shipping-edit', component: () => import('../features/shipping/ShipmentFormView.vue'), meta: { requiredPermission: 'shipping:edit' } },
```

Change the list’s new/edit actions to these routes, add their Chinese breadcrumb mappings, and extend `AppRouting.test.ts` with permission checks for both routes.
Add the detail page’s permission-aware “编辑发货单 / 编辑并下单” action at the same time; it routes to `shipping-edit` and never opens a separate logistics page.

- [ ] **Step 4: Test and implement save-then-order orchestration**

Add three cases:

```ts
it('creates once, submits the returned version, and opens success result', async () => {
  service.create.mockResolvedValue(shipment({ id: 18, version: 0 }));
  service.placeLogisticsOrder.mockResolvedValue(order({ trackingNo: 'ANE202609180018', state: 'succeeded' }));
  await clickSaveAndOrder();
  expect(service.create).toHaveBeenCalledTimes(1);
  expect(service.placeLogisticsOrder).toHaveBeenCalledWith(18, { version: 0 });
  expect(wrapper.get('[role="dialog"]').text()).toContain('ANE202609180018');
});

it('keeps the saved shipment when external ordering fails', async () => {
  service.create.mockResolvedValue(shipment({ id: 18, version: 0 }));
  service.placeLogisticsOrder.mockRejectedValue(new Error('安能暂时不可用'));
  await clickSaveAndOrder();
  expect(service.create).toHaveBeenCalledTimes(1);
  expect(router.currentRoute.value).toMatchObject({ name: 'shipping-edit', params: { id: '18' } });
  expect(wrapper.get('[role="alert"]').text()).toContain('发货单已保存');
});

it('disables the action while a save or order request is active', async () => {
  service.create.mockReturnValue(new Promise(() => undefined));
  await clickSaveAndOrderWithoutWaiting();
  expect(wrapper.get('[data-testid="shipment-save-and-order"]').attributes('disabled')).toBeDefined();
});
```

When an order returns `processing` or `unknown`, do not show the success modal; show the stored state and direct the user to the detail page to refresh. A rejected order remains retryable from edit.

- [ ] **Step 5: Test and implement edit and post-order locks**

Before order, load the full form and expose status plus both save actions. After any logistics state other than `rejected`, disable platform, shop, preparers, all recipient fields, estimate and every Aneng field; keep only status, preparation content, and remark enabled. Assert that the update payload retains locked values loaded from the server and that no separate order page opens.

The edit save call is exact:

```ts
await shippingService.update(shipment.id, form, selectedStatus, shipment.version);
```

After update, use the returned version for the order call to avoid a stale-version 409.

- [ ] **Step 6: Implement and test the success modal actions**

Match node `1377:1863`. The modal contains no editable result field. “返回发货列表” routes to `shipping-list`; “继续下单” routes to `shipping-new` and resets the form through a new route instance. Focus the first action on open, trap Tab within the modal, close neither on backdrop nor Escape while the result is being acknowledged, and restore navigation through one of the two explicit actions.

- [ ] **Step 7: Remove obsolete dialog components and references**

Delete the three old dialog/panel components and panel test after `rg` confirms only the new routed flow remains. Keep any useful status labels or formatting helpers by moving them to `types.ts` before deletion.

- [ ] **Step 8: Run all shipping frontend tests**

Run:

```powershell
cd frontend
npm run test:run -- src/features/shipping
```

Expected: all shipping tests pass, including create, edit, locked edit, recognition, save-only, save-and-order, order failure, unknown state, and success actions.

- [ ] **Step 9: Commit the combined form flow**

```powershell
git add frontend/src/features/shipping
git commit -m "feat: integrate shipment form and aneng ordering"
```

### Task 7: Verify migrations, application behavior and Figma fidelity

**Files:**
- Modify: `README.md`
- Modify: `docs/shipping-ane-setup.md`
- Modify: `docs/superpowers/specs/2026-09-18-shipping-figma-design.md`

**Interfaces:**
- Consumes: all tasks above and the live Figma nodes named in the plan header.
- Produces: documented configuration and recorded verification evidence; no new runtime API.

- [ ] **Step 1: Run the complete backend suite**

Run:

```powershell
cd backend
mvn test
```

Expected: all backend tests pass. If `ERP_TEST_DB` is configured, include repository/migration tests against MySQL; if it is absent, report that database migration execution remains an environment limitation rather than claiming it passed.

- [ ] **Step 2: Run the complete frontend suite and production build**

Run:

```powershell
cd frontend
npm run test:run
npm run build
```

Expected: all Vitest tests pass; `vue-tsc --noEmit` and Vite production build succeed.

- [ ] **Step 3: Perform browser acceptance on the real routes**

Start the project using its documented local commands and verify at desktop and a narrow viewport:

1. `/shipping/list` matches Figma list structure and has no clipped table actions.
2. `/shipping/new` keeps recognition inline, accepts at least five preparation lines, and has no overlapping cards or unexpected outer frame.
3. Save-only opens `/shipping/{id}` with date/status/orderer defaults returned by the server.
4. Save-and-order in an unconfigured or test environment shows the correct environment state and never claims real shipment creation.
5. A succeeded stub/test order shows the result modal and both actions navigate correctly.
6. `/shipping/{id}/edit` locks recipient/order fields after ordering while status, preparation and remark remain editable.

Capture screenshots for list, new, detail, pre-order edit, post-order edit and success modal; compare spacing, hierarchy, labels, disabled states and responsive overflow with the live Figma nodes.

- [ ] **Step 4: Update operational documentation**

Document `ERP_SHIPPING_SHOP_NAMES`, server-owned defaults, the version-only order endpoint, save-before-order failure behavior, test-environment labeling, and the fact that V1 has no SKU/inventory integration. Correct the stale Figma spec to six top-level screens plus the success modal component and the final full-width form layout.

- [ ] **Step 5: Review the branch diff and run sensitive-data checks**

Run:

```powershell
git diff --check
rg -n "customerCode|customerPass|appKey|secret" .env.example README.md docs backend/src/main/resources frontend/src --glob '!**/target/**' --glob '!**/dist/**'
git status --short
```

Expected: no whitespace errors, no real Aneng credentials, and only intended shipping/config/documentation files changed beyond the already present branch work.

- [ ] **Step 6: Commit verification documentation**

```powershell
git add README.md docs/shipping-ane-setup.md docs/superpowers/specs/2026-09-18-shipping-figma-design.md
git commit -m "docs: finalize shipping workflow setup"
```
