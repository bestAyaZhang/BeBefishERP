# BeBefish ERP 首页与商品管理改版 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将已确认的通栏首页和商品管理原型实现为真实可用的 Vue/Spring Boot 功能，补齐 SKU 安全库存、内盒包装、采购报价、库存聚合和经营统计。

**Architecture:** 后端保留现有分层结构，以 Flyway 增量迁移扩展 `product_sku`，由商品应用服务在单一事务中保存商品、SKU 和供应商报价，并使用查询服务聚合库存、完整度和首页指标。前端将旧的单体原型拆成独立工作台、商品列表、详情和六步编辑器页面，通过现有服务工厂接入真实 API。

**Tech Stack:** Java 21、Spring Boot、JdbcTemplate/JPA、Flyway、MySQL、JUnit 5、Vue 3、TypeScript、Vue Router、Tailwind CSS、Lucide、Vitest。

**Spec:** `docs/superpowers/specs/2026-09-01-home-product-redesign-implementation-design.md`

## Global Constraints

- 应用框架占满浏览器宽度，桌面侧边栏固定约 `244px`，不设置 `1440px` 外层最大宽度。
- 商品查询只保留“货号 / SKU”；分类树只负责筛选，不提供分类修改和删除。
- 商品详情是独立只读页，编辑通过 `/products/:id/edit` 进入六步向导。
- 渠道售价、附件、合规资料和已取消的商品描述/销售属性不进入本轮数据模型。
- 包装模式不落库；统一模式把相同包装字段复制到每个 SKU，逐 SKU 模式分别保存。
- 人工维护字段落库；库存、资料完整度和首页统计实时聚合，不保存统计快照。
- 不增加新的前端状态管理库或图表库；趋势图使用 Vue、CSS 和轻量 SVG/HTML 绘制。
- 所有图标使用现有 `lucide-vue-next`。
- 前端生产构建不得使用 Mock 数据源，空数据时不得填充演示数字。

---

## File Structure

### Backend

- `backend/src/main/resources/db/migration/V8__product_catalog_expansion.sql`：SKU 安全库存和内盒包装字段迁移。
- `backend/src/main/java/com/bebefish/erp/product/domain/{Product,Sku,Packaging}.java`：扩展商品聚合值对象。
- `backend/src/main/java/com/bebefish/erp/product/application/{SaveSkuCommand,PackagingCommand,ProductSupplierQuoteCommand}.java`：扩展商品保存命令。
- `backend/src/main/java/com/bebefish/erp/product/application/ProductSupplierQuoteSynchronizer.java`：在商品事务内校验和同步 SKU 报价。
- `backend/src/main/java/com/bebefish/erp/product/application/ProductCompletenessCalculator.java`：纯函数计算资料完整度。
- `backend/src/main/java/com/bebefish/erp/product/application/ProductCatalogQueryService.java`：批量聚合库存、报价、价格、图片和分类数量。
- `backend/src/main/java/com/bebefish/erp/product/api/{SaveProductRequest,ProductResponse,ProductController}.java`：扩展请求与响应。
- `backend/src/main/java/com/bebefish/erp/dashboard/application/DashboardQueryService.java`：工作台聚合查询。
- `backend/src/main/java/com/bebefish/erp/dashboard/api/{DashboardOverviewResponse,DashboardController}.java`：工作台 API。

### Frontend

- `frontend/src/features/dashboard/{types,dashboardService,httpDashboardService,mockDashboardService}.ts`：工作台服务契约。
- `frontend/src/features/dashboard/components/*.vue`：指标、趋势、库存提醒和最近订单组件。
- `frontend/src/views/WorkbenchView.vue`：真实工作台页面。
- `frontend/src/features/product/types.ts`、`httpProductService.ts`：扩展商品契约。
- `frontend/src/features/product/components/ProductCategoryTree.vue`：只读分类树筛选。
- `frontend/src/features/product/components/ProductTable.vue`：无竖线商品表格和分页。
- `frontend/src/features/product/components/ProductDetailSections.vue`：独立详情页内容。
- `frontend/src/features/product/editor/ProductEditorView.vue`：六步向导容器。
- `frontend/src/features/product/editor/steps/*.vue`：六个步骤组件。
- `frontend/src/features/product/editor/SkuEditorDialog.vue`：SKU 宽弹窗。
- `frontend/src/features/product/editor/PackagingEditorDialog.vue`：逐 SKU 包装宽弹窗。
- `frontend/src/features/product/editor/productEditorState.ts`：向导状态、校验、模式识别和提交映射纯函数。
- `frontend/src/features/product/views/{ProductListView,ProductDetailView}.vue`：新版列表和详情页面。
- `frontend/src/layouts/ErpLayout.vue`、`frontend/src/components/navigation/SidebarNav.vue`：通栏主框架。
- `frontend/src/router/index.ts`：新增、详情和编辑路由。

---

### Task 1: 扩展 SKU 数据库与领域模型

**Files:**
- Create: `backend/src/main/resources/db/migration/V8__product_catalog_expansion.sql`
- Modify: `backend/src/main/java/com/bebefish/erp/product/domain/Packaging.java`
- Modify: `backend/src/main/java/com/bebefish/erp/product/domain/Sku.java`
- Modify: `backend/src/main/java/com/bebefish/erp/product/domain/Product.java`
- Modify: `backend/src/main/java/com/bebefish/erp/product/application/PackagingCommand.java`
- Modify: `backend/src/main/java/com/bebefish/erp/product/application/SaveSkuCommand.java`
- Modify: `backend/src/main/java/com/bebefish/erp/product/application/ProductService.java`
- Modify: `backend/src/main/java/com/bebefish/erp/product/infrastructure/ProductJpaAdapter.java`
- Modify: `backend/src/main/java/com/bebefish/erp/product/api/SaveProductRequest.java`
- Modify: `backend/src/main/java/com/bebefish/erp/product/api/ProductResponse.java`
- Test: `backend/src/test/java/com/bebefish/erp/common/persistence/FlywayMigrationTest.java`
- Test: `backend/src/test/java/com/bebefish/erp/product/application/ProductServiceTest.java`
- Test: `backend/src/test/java/com/bebefish/erp/product/api/ProductControllerTest.java`

**Interfaces:**
- Produces: `Sku.safetyStockQuantity(): BigDecimal`。
- Produces: `Packaging.innerLengthCm()`, `innerWidthCm()`, `innerHeightCm()`, `innerWeightKg()`。
- Produces: `Product.createdAt()` 和 `Product.updatedAt()`，从 `product_spu` 审计列读取。
- Consumes: 现有 `product_sku`、`product_spu` 和图片文件 ID。

- [ ] **Step 1: 写迁移失败测试**

在 `FlywayMigrationTest` 增加：

```java
@Test
void addsSafetyStockAndInnerPackagingColumns() {
    var columns = jdbc.queryForList(
            "select column_name from information_schema.columns "
                    + "where table_schema = database() and table_name = 'product_sku'",
            String.class
    );
    assertThat(columns).contains(
            "safety_stock_quantity", "inner_package_length_cm", "inner_package_width_cm",
            "inner_package_height_cm", "inner_package_weight_kg"
    );
    var productId = insertProduct("P800", "ITEM800");
    var skuId = insertSku(productId, "SKU800");
    assertThat(jdbc.queryForObject(
            "select safety_stock_quantity from product_sku where id = ?",
            BigDecimal.class, skuId
    )).isEqualByComparingTo("0");
}
```

- [ ] **Step 2: 运行迁移测试并确认失败**

Run: `mvn -f backend/pom.xml -Dtest=FlywayMigrationTest#addsSafetyStockAndInnerPackagingColumns test`

Expected: FAIL，缺少 `safety_stock_quantity` 等列。

- [ ] **Step 3: 创建 V8 迁移**

```sql
alter table product_sku
    add column safety_stock_quantity decimal(18, 4) not null default 0 after standard_cost,
    add column inner_package_length_cm decimal(12, 3) null after package_volume_cm3,
    add column inner_package_width_cm decimal(12, 3) null after inner_package_length_cm,
    add column inner_package_height_cm decimal(12, 3) null after inner_package_width_cm,
    add column inner_package_weight_kg decimal(12, 3) null after gram_weight_g,
    add constraint ck_product_sku_safety_stock check (safety_stock_quantity >= 0),
    add constraint ck_product_sku_inner_length check (inner_package_length_cm is null or inner_package_length_cm >= 0),
    add constraint ck_product_sku_inner_width check (inner_package_width_cm is null or inner_package_width_cm >= 0),
    add constraint ck_product_sku_inner_height check (inner_package_height_cm is null or inner_package_height_cm >= 0),
    add constraint ck_product_sku_inner_weight check (inner_package_weight_kg is null or inner_package_weight_kg >= 0);
```

- [ ] **Step 4: 写领域校验失败测试**

在 `ProductServiceTest` 增加安全库存与内盒字段回显测试，并增加负安全库存拒绝测试：

```java
@Test
void keepsSafetyStockAndInnerPackagingData() {
    var result = service.createProduct(simpleProductWithSafetyStock("12.5", "36", "25", "22", "1.1"));
    var sku = result.skus().getFirst();
    assertThat(sku.safetyStockQuantity()).isEqualByComparingTo("12.5");
    assertThat(sku.packaging().innerLengthCm()).isEqualByComparingTo("36");
    assertThat(sku.packaging().innerWeightKg()).isEqualByComparingTo("1.1");
}

@Test
void rejectsNegativeSafetyStock() {
    assertThatThrownBy(() -> service.createProduct(simpleProductWithSafetyStock("-1", "36", "25", "22", "1.1")))
            .isInstanceOf(BusinessException.class)
            .hasMessage("安全库存不能小于 0");
}
```

- [ ] **Step 5: 扩展领域、命令、请求、响应和 JDBC 映射**

将 `Packaging` 与 `PackagingCommand` 扩展为：

```java
public record Packaging(
        BigDecimal lengthCm, BigDecimal widthCm, BigDecimal heightCm, BigDecimal volumeCm3,
        BigDecimal innerLengthCm, BigDecimal innerWidthCm, BigDecimal innerHeightCm,
        BigDecimal netWeightKg, BigDecimal grossWeightKg, BigDecimal gramWeightG,
        BigDecimal innerWeightKg, String method, Integer cartonQuantity,
        Long packageImageFileId, Long cartonImageFileId
) {}
```

`Sku` 在 `standardCost` 后增加 `BigDecimal safetyStockQuantity`；`Product` 增加 `LocalDateTime createdAt` 和 `updatedAt`。更新全部构造、复制方法、SQL 列、参数和 JSON 字段，保持现有字段名称不变，并新增：

```json
{
  "safetyStockQuantity": 12.5,
  "innerPackageLengthCm": 36,
  "innerPackageWidthCm": 25,
  "innerPackageHeightCm": 22,
  "innerPackageWeightKg": 1.1,
  "createdAt": "2026-09-01T10:00:00",
  "updatedAt": "2026-09-01T10:00:00"
}
```

`ProductJpaAdapter.save` 在插入或更新完成后通过 `findById(productId)` 返回聚合，使创建响应也能取得数据库生成的审计时间；`withIdentity` 和 `withStatus` 必须保留已有审计时间。

- [ ] **Step 6: 运行商品与迁移测试**

Run: `mvn -f backend/pom.xml -Dtest=FlywayMigrationTest,ProductServiceTest,ProductControllerTest test`

Expected: PASS。

- [ ] **Step 7: 提交**

```bash
git add backend/src/main/resources/db/migration/V8__product_catalog_expansion.sql backend/src/main/java/com/bebefish/erp/product backend/src/test/java/com/bebefish/erp/common/persistence/FlywayMigrationTest.java backend/src/test/java/com/bebefish/erp/product
git commit -m "feat: extend sku packaging data"
```

---

### Task 2: 在商品事务中同步供应商报价

**Files:**
- Create: `backend/src/main/java/com/bebefish/erp/product/application/ProductSupplierQuoteCommand.java`
- Create: `backend/src/main/java/com/bebefish/erp/product/application/ProductSupplierQuoteSynchronizer.java`
- Modify: `backend/src/main/java/com/bebefish/erp/product/application/SaveSkuCommand.java`
- Modify: `backend/src/main/java/com/bebefish/erp/product/application/ProductService.java`
- Modify: `backend/src/main/java/com/bebefish/erp/product/domain/SupplierQuoteRepository.java`
- Modify: `backend/src/main/java/com/bebefish/erp/product/infrastructure/SupplierQuoteJpaAdapter.java`
- Modify: `backend/src/main/java/com/bebefish/erp/product/api/SaveProductRequest.java`
- Test: `backend/src/test/java/com/bebefish/erp/product/application/ProductServiceTest.java`
- Test: `backend/src/test/java/com/bebefish/erp/product/api/ProductControllerTest.java`

**Interfaces:**
- Produces: `ProductSupplierQuoteCommand(Long id, Long supplierId, String supplierItemNo, BigDecimal purchasePrice, BigDecimal minPurchaseQuantity, boolean defaultQuote, String status)`。
- Produces: `ProductSupplierQuoteSynchronizer.synchronize(long skuId, List<ProductSupplierQuoteCommand> commands)`。
- Produces: `SupplierQuoteRepository.deleteBySkuIds(Collection<Long>)` 和 `deleteBySkuIdExcept(long, Collection<Long>)`。
- Contract: `supplierQuotes == null` 表示旧客户端不修改报价；空数组表示删除该 SKU 的全部报价。

- [ ] **Step 1: 写商品与报价原子保存失败测试**

在 `ProductControllerTest` 的创建请求中加入：

```java
sku.put("supplierQuotes", List.of(Map.of(
        "supplierId", supplierId,
        "supplierItemNo", "SUP-PUMP-01",
        "purchasePrice", "61.20",
        "minPurchaseQuantity", "12",
        "defaultQuote", true,
        "status", "enabled"
)));
```

执行创建请求，读取返回的 SKU ID，然后直接断言数据库中的报价：

```java
var skuId = objectMapper.readTree(response).path("data").path("skus").get(0).path("id").asLong();
var quote = jdbc.queryForMap(
        "select supplier_item_no, purchase_price, is_default from sku_supplier_quote where sku_id = ?",
        skuId
);
assertThat(quote.get("supplier_item_no")).isEqualTo("SUP-PUMP-01");
assertThat(quote.get("purchase_price").toString()).startsWith("61.20");
assertThat(quote.get("is_default")).isEqualTo(true);
```

再添加一个无效供应商 ID 的请求，断言 HTTP 失败且 `product_spu` 中没有该货号。

- [ ] **Step 2: 运行控制器测试并确认失败**

Run: `mvn -f backend/pom.xml -Dtest=ProductControllerTest test`

Expected: FAIL，商品请求尚不接受并保存 `supplierQuotes`。

- [ ] **Step 3: 添加报价命令和同步器**

```java
public record ProductSupplierQuoteCommand(
        Long id,
        Long supplierId,
        String supplierItemNo,
        BigDecimal purchasePrice,
        BigDecimal minPurchaseQuantity,
        boolean defaultQuote,
        String status
) {}
```

同步器在同一 SKU 内校验供应商不重复、最多一个启用默认报价、价格非负、起订量大于零；先清理已删除报价，再保存新增和更新报价。所有方法由 `ProductService` 的 `@Transactional` 创建/更新流程调用。

- [ ] **Step 4: 扩展请求和商品保存流程**

给 `SaveSkuCommand` 增加可空 `List<ProductSupplierQuoteCommand> supplierQuotes`。`SaveProductRequest.SkuInput` 保留 `null` 与空数组的区别。`ProductService` 先根据已有 SKU 与准备保存的 SKU 计算被删除 SKU ID 并删除其报价，再保存商品聚合，最后按 SKU 顺序同步非空报价命令。

- [ ] **Step 5: 扩展报价仓储删除操作**

```java
void deleteBySkuIds(Collection<Long> skuIds);
void deleteBySkuIdExcept(long skuId, Collection<Long> retainedQuoteIds);
```

JDBC 实现必须使用参数占位符生成 `IN` 列表；空集合直接返回，不拼接无效 SQL。

- [ ] **Step 6: 运行商品与报价测试**

Run: `mvn -f backend/pom.xml -Dtest=ProductServiceTest,SupplierQuoteServiceTest,ProductControllerTest,SupplierQuoteControllerTest test`

Expected: PASS；旧的独立报价接口继续可用。

- [ ] **Step 7: 提交**

```bash
git add backend/src/main/java/com/bebefish/erp/product backend/src/test/java/com/bebefish/erp/product
git commit -m "feat: save supplier quotes with products"
```

---

### Task 3: 提供商品列表与详情聚合读模型

**Files:**
- Create: `backend/src/main/java/com/bebefish/erp/product/application/ProductCompletenessCalculator.java`
- Create: `backend/src/main/java/com/bebefish/erp/product/application/ProductCatalogMetrics.java`
- Create: `backend/src/main/java/com/bebefish/erp/product/application/ProductCatalogQueryService.java`
- Modify: `backend/src/main/java/com/bebefish/erp/product/domain/ProductRepository.java`
- Modify: `backend/src/main/java/com/bebefish/erp/product/infrastructure/ProductJpaAdapter.java`
- Modify: `backend/src/main/java/com/bebefish/erp/product/api/ProductResponse.java`
- Modify: `backend/src/main/java/com/bebefish/erp/product/api/ProductController.java`
- Test: `backend/src/test/java/com/bebefish/erp/product/application/ProductCompletenessCalculatorTest.java`
- Test: `backend/src/test/java/com/bebefish/erp/product/api/ProductControllerTest.java`

**Interfaces:**
- Produces: `ProductCompletenessCalculator.calculate(Product, Map<Long,List<SupplierQuote>>): ProductCompleteness`。
- Produces: `ProductCatalogQueryService.load(List<Product>): Map<Long, ProductCatalogMetrics>`。
- Produces: `ProductCatalogQueryService.categoryCounts(): Map<Long, Long>`。
- Produces endpoint: `GET /api/products/category-counts`。
- Product response additions: `totalStock`, `totalSafetyStock`, `defaultSalePrice`, `completenessPercent`, `completenessStatus`; each SKU adds `stockQuantity` and `supplierQuotes`。

- [ ] **Step 1: 写完整度纯函数失败测试**

```java
@Test
void scoresFiveEqualCompletenessGroups() {
    var empty = calculator.calculate(productWithoutImagesOrQuotes(), Map.of());
    assertThat(empty.percent()).isEqualTo(40);
    assertThat(empty.missingGroups()).containsExactly("采购信息", "包装重量", "图片资料");

    var complete = calculator.calculate(completeProduct(), completeQuotes());
    assertThat(complete.percent()).isEqualTo(100);
    assertThat(complete.status()).isEqualTo("complete");
}
```

- [ ] **Step 2: 运行测试并确认失败**

Run: `mvn -f backend/pom.xml -Dtest=ProductCompletenessCalculatorTest test`

Expected: FAIL，计算器不存在。

- [ ] **Step 3: 实现五组等权完整度计算**

```java
public record ProductCompleteness(int percent, String status, List<String> missingGroups) {}
```

五组固定为基本信息、SKU 信息、采购信息、包装重量、图片资料，每组 `20` 分；`percent == 100` 时状态为 `complete`，否则为 `incomplete`。

- [ ] **Step 4: 写聚合响应失败测试**

在 `ProductControllerTest` 插入两个仓库库存余额和安全库存后断言：

```java
.andExpect(jsonPath("$.data.records[0].totalStock").value(18.5))
.andExpect(jsonPath("$.data.records[0].totalSafetyStock").value(12.5))
.andExpect(jsonPath("$.data.records[0].skus[0].stockQuantity").value(18.5))
.andExpect(jsonPath("$.data.records[0].completenessPercent").isNumber());
```

增加父分类与子分类数据，使用父分类 `categoryId` 查询时断言子分类商品被返回；调用 `/api/products/category-counts` 时父分类数量包含后代商品。

- [ ] **Step 5: 实现批量商品指标查询和后代分类筛选**

`ProductCatalogQueryService.load` 必须以产品 ID 集合批量查询库存、报价和默认价格，禁止在控制器中按商品逐条查询。`ProductJpaAdapter.findAll` 使用递归 CTE 获取选中分类及后代分类：

```sql
with recursive selected_categories as (
  select id from product_category where id = ?
  union all
  select child.id from product_category child
  join selected_categories parent on child.parent_id = parent.id
)
```

- [ ] **Step 6: 扩展 API 响应**

`ProductController.list` 先取得一页 `Product`，再批量加载 `ProductCatalogMetrics`；`get` 使用同一读模型并返回 SKU 报价、库存和审计时间。图片 URL 继续由 `FileAccessUrlResolver` 解析。

- [ ] **Step 7: 运行商品读模型测试**

Run: `mvn -f backend/pom.xml -Dtest=ProductCompletenessCalculatorTest,ProductControllerTest test`

Expected: PASS。

- [ ] **Step 8: 提交**

```bash
git add backend/src/main/java/com/bebefish/erp/product backend/src/test/java/com/bebefish/erp/product
git commit -m "feat: add product catalog read model"
```

---

### Task 4: 添加工作台聚合接口

**Files:**
- Create: `backend/src/main/java/com/bebefish/erp/dashboard/application/DashboardOverview.java`
- Create: `backend/src/main/java/com/bebefish/erp/dashboard/application/DashboardQueryService.java`
- Create: `backend/src/main/java/com/bebefish/erp/dashboard/api/DashboardOverviewResponse.java`
- Create: `backend/src/main/java/com/bebefish/erp/dashboard/api/DashboardController.java`
- Test: `backend/src/test/java/com/bebefish/erp/dashboard/api/DashboardControllerTest.java`

**Interfaces:**
- Produces endpoint: `GET /api/dashboard/overview?period=week|month|year`。
- Produces response sections: `summary`, `salesTrend`, `stockAlerts`, `recentOrders`。
- Period contract: week=`今天及前 6 天`，month=`当月自然日`，year=`当年按月聚合`。

- [ ] **Step 1: 写工作台接口失败测试**

```java
@Test
void returnsRealOverviewMetrics() throws Exception {
    insertConfirmedOrder("2026-09-01", "2434.23", "340.00");
    insertDraftOrder("2026-09-01");
    insertStockAlert("SKU-DASH", "2", "10");

    mvc.perform(get("/api/dashboard/overview").param("period", "week")
                    .header("Authorization", bearer(viewToken)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.summary.salesAmount").value(2434.23))
            .andExpect(jsonPath("$.data.summary.draftOrderCount").value(1))
            .andExpect(jsonPath("$.data.stockAlerts[0].shortageQuantity").value(8));
}
```

再增加 `period=quarter` 返回 `400 VALIDATION_FAILED` 的测试。

- [ ] **Step 2: 运行测试并确认失败**

Run: `mvn -f backend/pom.xml -Dtest=DashboardControllerTest test`

Expected: FAIL，控制器不存在。

- [ ] **Step 3: 实现查询记录和 SQL 聚合**

```java
public record DashboardOverview(
        Summary summary,
        List<SalesTrendPoint> salesTrend,
        List<StockAlert> stockAlerts,
        List<RecentOrder> recentOrders
) {}
```

销售额、订单数和未收金额只统计 `confirmed`；草稿单独计数，`void` 不进入经营统计。库存预警按全部仓库余额合计后与 SKU 安全库存比较，先显示缺口最大的 8 条。最近订单按业务日期和 ID 倒序取 6 条。

- [ ] **Step 4: 实现 API 与参数校验**

控制器使用 `@PreAuthorize("isAuthenticated()")`，将领域记录映射为 JSON。数据库无数据时返回零值与空数组。

- [ ] **Step 5: 运行工作台与相关回归测试**

Run: `mvn -f backend/pom.xml -Dtest=DashboardControllerTest,SalesOrderControllerTest,InventoryQueryControllerTest test`

Expected: PASS。

- [ ] **Step 6: 提交**

```bash
git add backend/src/main/java/com/bebefish/erp/dashboard backend/src/test/java/com/bebefish/erp/dashboard
git commit -m "feat: add dashboard overview api"
```

---

### Task 5: 对齐前端服务契约与路由

**Files:**
- Create: `frontend/src/features/dashboard/types.ts`
- Create: `frontend/src/features/dashboard/dashboardService.ts`
- Create: `frontend/src/features/dashboard/httpDashboardService.ts`
- Create: `frontend/src/features/dashboard/mockDashboardService.ts`
- Create: `frontend/src/features/dashboard/httpDashboardService.test.ts`
- Modify: `frontend/src/features/product/types.ts`
- Modify: `frontend/src/features/product/httpProductService.ts`
- Modify: `frontend/src/features/product/mockProductService.ts`
- Modify: `frontend/src/features/product/ProductFeature.test.ts`
- Modify: `frontend/src/router/index.ts`
- Modify: `frontend/src/router/index.test.ts`

**Interfaces:**
- Produces: `DashboardService.getOverview(period: DashboardPeriod): Promise<DashboardOverview>`。
- Produces: `ProductService.getCategoryCounts(): Promise<Record<number, number>>`。
- Produces: `ProductFormPayload.skus[].supplierQuotes`、安全库存和内盒包装字段。
- Routes: `product-new`、`product-detail`、`product-edit`。

- [ ] **Step 1: 写 HTTP 契约失败测试**

```ts
it('requests dashboard overview for the selected period', async () => {
  mockFetchApi({ summary: {}, salesTrend: [], stockAlerts: [], recentOrders: [] });
  await httpDashboardService.getOverview('month');
  expect(fetch).toHaveBeenCalledWith('/api/dashboard/overview?period=month', expect.any(Object));
});

it('serializes sku purchasing and packaging fields', async () => {
  await httpProductService.createProduct(productPayloadWithQuotes);
  const body = JSON.parse(String(fetchMock.mock.calls[0][1]?.body));
  expect(body.skus[0].safetyStockQuantity).toBe(12);
  expect(body.skus[0].supplierQuotes[0].supplierId).toBe(3);
});
```

- [ ] **Step 2: 运行前端服务测试并确认失败**

Run: `npm --prefix frontend run test:run -- httpDashboardService.test.ts ProductFeature.test.ts`

Expected: FAIL，工作台服务和新字段不存在。

- [ ] **Step 3: 定义 TypeScript 契约和真实/Mock 服务**

`DashboardOverview` 精确对应后端四个区块；商品类型新增：

```ts
export interface ProductSupplierQuoteInput {
  id?: number;
  supplierId: number;
  supplierItemNo: string;
  purchasePrice: number;
  minPurchaseQuantity: number;
  defaultQuote: boolean;
  status: RecordStatus;
}
```

`SkuForm` 增加 `safetyStockQuantity`、四个内盒字段、`stockQuantity` 和 `supplierQuotes`；`Product` 增加聚合与审计字段。Mock 服务返回结构正确的空/示例记录，不在生产路径使用。

- [ ] **Step 4: 写路由失败测试**

```ts
expect(router.resolve('/products/new').name).toBe('product-new');
expect(router.resolve('/products/12/edit').name).toBe('product-edit');
expect(router.resolve('/products/12').name).toBe('product-detail');
```

- [ ] **Step 5: 更新路由**

`/workbench` 改为 `WorkbenchView.vue`；新增和编辑都懒加载 `ProductEditorView.vue`。移除 `ownsPrototypeHeader`，保留现有登录守卫。

- [ ] **Step 6: 运行服务和路由测试**

Run: `npm --prefix frontend run test:run -- httpDashboardService.test.ts ProductFeature.test.ts router/index.test.ts`

Expected: PASS。

- [ ] **Step 7: 提交**

```bash
git add frontend/src/features/dashboard frontend/src/features/product frontend/src/router
git commit -m "feat: define dashboard and catalog frontend contracts"
```

---

### Task 6: 改造通栏主框架

**Files:**
- Modify: `frontend/src/layouts/ErpLayout.vue`
- Modify: `frontend/src/components/navigation/SidebarNav.vue`
- Create: `frontend/src/layouts/ErpLayout.test.ts`
- Modify: `frontend/src/components/navigation/SidebarNav.test.ts`
- Modify: `frontend/src/assets/main.css`

**Interfaces:**
- Produces: `[data-testid="erp-shell"]`，桌面网格 `244px minmax(0,1fr)`。
- Produces: `[data-testid="erp-topbar"]` 固定 `64px` 内容高度。
- Consumes: 现有导航权限目录和登录用户会话。

- [ ] **Step 1: 写布局失败测试**

```ts
expect(wrapper.get('[data-testid="erp-shell"]').classes()).toContain('min-h-screen');
expect(wrapper.get('[data-testid="erp-layout-grid"]').classes().join(' ')).toContain('lg:grid-cols-[244px_minmax(0,1fr)]');
expect(wrapper.find('.max-w-[1440px]').exists()).toBe(false);
expect(wrapper.get('[data-testid="erp-sidebar"]').classes()).not.toContain('rounded-[24px]');
```

- [ ] **Step 2: 运行布局测试并确认失败**

Run: `npm --prefix frontend run test:run -- ErpLayout.test.ts SidebarNav.test.ts`

Expected: FAIL，当前框架仍有外围内边距、最大宽度和圆角侧栏。

- [ ] **Step 3: 实现通栏框架**

将最外层改为无外围 padding 的全屏网格；侧栏桌面固定、主内容 `min-w-0`；顶部工具栏以细底边分隔；主内容统一 `p-4 lg:p-6`。移动端保留遮罩抽屉，桌面侧栏不使用卡片圆角和阴影。

- [ ] **Step 4: 对齐面包屑和新路由标题**

增加 `product-new`、`product-edit`、`workbench` 的中文/英文标题映射；详情页面包屑返回商品列表，顶部搜索框保持紧凑。

- [ ] **Step 5: 运行布局与路由回归测试**

Run: `npm --prefix frontend run test:run -- ErpLayout.test.ts SidebarNav.test.ts router/index.test.ts AppRouting.test.ts`

Expected: PASS。

- [ ] **Step 6: 提交**

```bash
git add frontend/src/layouts frontend/src/components/navigation frontend/src/assets/main.css frontend/src/router
git commit -m "feat: make erp shell full width"
```

---

### Task 7: 实现真实工作台首页

**Files:**
- Create: `frontend/src/features/dashboard/components/DashboardSummaryGrid.vue`
- Create: `frontend/src/features/dashboard/components/SalesTrendPanel.vue`
- Create: `frontend/src/features/dashboard/components/StockAlertList.vue`
- Create: `frontend/src/features/dashboard/components/RecentOrderList.vue`
- Modify: `frontend/src/views/WorkbenchView.vue`
- Modify: `frontend/src/views/WorkbenchView.test.ts`

**Interfaces:**
- Consumes: `dashboardService.getOverview(period)`。
- Produces: 周/月/年分段选择、局部错误重试、真实统计、趋势、库存提醒和最近订单。

- [ ] **Step 1: 写工作台页面失败测试**

```ts
it('renders api metrics and reloads a selected period', async () => {
  service.getOverview.mockResolvedValue(overviewFixture);
  const wrapper = mountWorkbench(service);
  await flushPromises();
  expect(wrapper.get('[data-testid="summary-salesAmount"]').text()).toContain('2,434.23');
  await wrapper.get('[data-testid="period-year"]').trigger('click');
  expect(service.getOverview).toHaveBeenLastCalledWith('year');
});

it('shows empty panels without demo values', async () => {
  service.getOverview.mockResolvedValue(emptyOverview);
  const wrapper = mountWorkbench(service);
  await flushPromises();
  expect(wrapper.text()).toContain('暂无销售趋势');
  expect(wrapper.text()).not.toContain('639');
});
```

- [ ] **Step 2: 运行页面测试并确认失败**

Run: `npm --prefix frontend run test:run -- WorkbenchView.test.ts`

Expected: FAIL，当前页面是硬编码零值。

- [ ] **Step 3: 实现页面状态和数据加载**

`WorkbenchView` 管理 `period`、`loading`、`error` 和 `overview`。周期切换只重新请求工作台接口；请求序号防止慢响应覆盖最新选择。

- [ ] **Step 4: 实现工作台组件**

摘要卡保持紧凑；趋势面板使用同一坐标区展示销售额柱形和订单数折线；库存提醒和最近订单使用密集列表。组件只格式化接收到的数据，不自行请求 API。

- [ ] **Step 5: 实现局部加载、空态和错误重试**

接口失败显示页内错误条和“重新加载”；空数组分别显示“暂无销售趋势”“暂无库存预警”“暂无最近订单”。不得写入固定业务数字。

- [ ] **Step 6: 运行工作台测试**

Run: `npm --prefix frontend run test:run -- WorkbenchView.test.ts httpDashboardService.test.ts`

Expected: PASS。

- [ ] **Step 7: 提交**

```bash
git add frontend/src/features/dashboard frontend/src/views/WorkbenchView.vue frontend/src/views/WorkbenchView.test.ts
git commit -m "feat: build live operations dashboard"
```

---

### Task 8: 实现分类树与商品列表

**Files:**
- Create: `frontend/src/features/product/components/ProductCategoryTree.vue`
- Create: `frontend/src/features/product/components/ProductTable.vue`
- Create: `frontend/src/features/product/components/ProductPagination.vue`
- Modify: `frontend/src/features/product/views/ProductListView.vue`
- Replace: `frontend/src/features/product/components/ProductList.vue`
- Modify: `frontend/src/features/product/ProductFeature.test.ts`

**Interfaces:**
- Consumes: `masterdataService.listCategories`、`productService.getCategoryCounts`、`productService.listProducts`。
- Produces route query: `categoryId`, `keyword`, `page`, `size`。
- Emits: `select-category`, `search`, `reset`, `change-page`, `change-size`, `open-product`, `create-product`。

- [ ] **Step 1: 写分类树和列表失败测试**

```ts
it('filters products from the category tree and resets the page', async () => {
  const wrapper = mountProductList({ routeQuery: { page: '3' } });
  await flushPromises();
  await wrapper.get('[data-testid="category-node-12"]').trigger('click');
  expect(router.push).toHaveBeenCalledWith(expect.objectContaining({
    query: expect.objectContaining({ categoryId: '12', page: '1' })
  }));
});

it('keeps only item number or sku as the query field', () => {
  const wrapper = mountProductList();
  expect(wrapper.find('[data-testid="product-keyword"]').exists()).toBe(true);
  expect(wrapper.find('[data-testid="product-filter-brand-button"]').exists()).toBe(false);
  expect(wrapper.find('[data-testid="product-filter-supplier-button"]').exists()).toBe(false);
});
```

再断言表格没有批量复选框、没有修改/删除图标，存在文字“查看详情”和分页器。

- [ ] **Step 2: 运行商品列表测试并确认失败**

Run: `npm --prefix frontend run test:run -- ProductFeature.test.ts`

Expected: FAIL，当前列表仍有多筛选项且没有左侧树。

- [ ] **Step 3: 实现只读分类树**

将扁平分类按 `parentId` 转为树，支持搜索时保留匹配节点及祖先。树中显示后端数量；不显示分类修改、删除和新增按钮。点击父分类将其 ID 作为服务端后代筛选条件。

- [ ] **Step 4: 实现商品表格**

列固定为商品、分类、品牌/供应商、库存、价格、资料状态、业务状态、操作。使用水平分隔和行悬停，不使用竖向边框。图片有固定 `44px` 方形尺寸；缺图使用 `ImageOff`。

- [ ] **Step 5: 实现路由查询和服务端分页**

从路由恢复筛选；回车搜索和分类变化写回查询参数；监听查询参数加载商品。请求失败保留筛选和分页并显示重试。

- [ ] **Step 6: 运行商品列表测试**

Run: `npm --prefix frontend run test:run -- ProductFeature.test.ts ProductDetailNavigation.test.ts`

Expected: PASS。

- [ ] **Step 7: 提交**

```bash
git add frontend/src/features/product/components frontend/src/features/product/views/ProductListView.vue frontend/src/features/product/ProductFeature.test.ts frontend/src/features/product/ProductDetailNavigation.test.ts
git commit -m "feat: rebuild product catalog list"
```

---

### Task 9: 实现独立只读商品详情页

**Files:**
- Create: `frontend/src/features/product/components/ProductOverviewSection.vue`
- Create: `frontend/src/features/product/components/ProductSkuSection.vue`
- Create: `frontend/src/features/product/components/ProductProcurementSection.vue`
- Create: `frontend/src/features/product/components/ProductPackagingSection.vue`
- Create: `frontend/src/features/product/components/ProductImagesSection.vue`
- Create: `frontend/src/features/product/components/ProductAuditSection.vue`
- Modify: `frontend/src/features/product/views/ProductDetailView.vue`
- Modify: `frontend/src/features/product/ProductDetailNavigation.test.ts`
- Delete after replacement: `frontend/src/features/product/components/ProductDetailDrawer.vue`

**Interfaces:**
- Consumes: `productService.getProduct(id)`。
- Produces: `edit` 导航到 `{ name: 'product-edit', params: { id } }`。
- Packaging display contract: 所有 SKU 包装字段相同则显示统一摘要，否则显示逐 SKU 表格。

- [ ] **Step 1: 写详情页失败测试**

```ts
it('renders read-only sku images and sku item numbers', async () => {
  const wrapper = mountDetail(productFixture);
  await flushPromises();
  expect(wrapper.get('[data-testid="sku-image-21"]').attributes('src')).toContain('sku-21.png');
  expect(wrapper.get('[data-testid="sku-item-number-21"]').text()).toBe('BBF-PUMP-021-WH');
  expect(wrapper.find('input').exists()).toBe(false);
});

it('navigates to the edit route from the top right action', async () => {
  await wrapper.get('[data-testid="edit-product"]').trigger('click');
  expect(router.push).toHaveBeenCalledWith({ name: 'product-edit', params: { id: 8 } });
});
```

再断言页面不存在附件、合规资料、商品描述、销售属性和抽屉遮罩。

- [ ] **Step 2: 运行详情测试并确认失败**

Run: `npm --prefix frontend run test:run -- ProductDetailNavigation.test.ts`

Expected: FAIL，当前页面仍复用抽屉和内联编辑。

- [ ] **Step 3: 拆分只读详情区块**

每个区块只接收 `Product` 或相应 SKU 数据，不直接调用服务。表格使用稳定列宽和横向滚动；图片固定宽高；空值显示 `--`。

- [ ] **Step 4: 实现包装模式识别**

使用纯函数比较所有 SKU 的外箱、内盒、重量、装箱和图片文件 ID。完全一致时显示一组统一数据并列出适用 SKU；任一字段不同则显示逐 SKU 表格。

- [ ] **Step 5: 替换详情视图并删除抽屉**

详情加载失败显示重试；“返回商品列表”和“编辑商品”均使用路由。删除 `ProductDetailDrawer.vue` 前先确认没有剩余 import。

- [ ] **Step 6: 运行详情和路由测试**

Run: `npm --prefix frontend run test:run -- ProductDetailNavigation.test.ts router/index.test.ts AppRouting.test.ts`

Expected: PASS。

- [ ] **Step 7: 提交**

```bash
git add frontend/src/features/product frontend/src/router
git commit -m "feat: add read only product detail page"
```

---

### Task 10: 实现六步商品向导、SKU 与包装维护

**Files:**
- Create: `frontend/src/features/product/editor/productEditorState.ts`
- Create: `frontend/src/features/product/editor/productEditorState.test.ts`
- Create: `frontend/src/features/product/editor/ProductEditorView.vue`
- Create: `frontend/src/features/product/editor/ProductEditorView.test.ts`
- Create: `frontend/src/features/product/editor/SkuEditorDialog.vue`
- Create: `frontend/src/features/product/editor/PackagingEditorDialog.vue`
- Create: `frontend/src/features/product/editor/steps/ProductBasicStep.vue`
- Create: `frontend/src/features/product/editor/steps/ProductSkuStep.vue`
- Create: `frontend/src/features/product/editor/steps/ProductProcurementStep.vue`
- Create: `frontend/src/features/product/editor/steps/ProductPackagingStep.vue`
- Create: `frontend/src/features/product/editor/steps/ProductImagesStep.vue`
- Create: `frontend/src/features/product/editor/steps/ProductConfirmStep.vue`
- Modify: `frontend/src/features/product/components/ProductForm.vue`
- Modify: `frontend/src/features/product/views/ProductListView.vue`

**Interfaces:**
- Produces: `createEditorState(product?: Product): ProductEditorState`。
- Produces: `validateStep(state, step): Record<string,string>`。
- Produces: `detectPackagingMode(skus): 'unified' | 'perSku'`。
- Produces: `applyUnifiedPackaging(skus, packaging): SkuForm[]`。
- Produces: `toProductPayload(state): ProductFormPayload`。

- [ ] **Step 1: 写向导状态纯函数失败测试**

```ts
it('copies unified packaging to every sku without sharing object references', () => {
  const result = applyUnifiedPackaging(twoSkus, unifiedPackaging);
  expect(result.map((sku) => sku.innerPackageLengthCm)).toEqual([36, 36]);
  expect(result[0]).not.toBe(result[1]);
});

it('detects per sku mode when one weight differs', () => {
  expect(detectPackagingMode([
    sku({ grossWeightKg: 3.1 }),
    sku({ grossWeightKg: 3.35 })
  ])).toBe('perSku');
});

it('maps supplier quotes and uploaded image ids into the final payload', () => {
  const payload = toProductPayload(completeEditorState);
  expect(payload.skus[0].supplierQuotes[0].supplierId).toBe(4);
  expect(payload.skus[0].cartonImageFileId).toBe(91);
});
```

- [ ] **Step 2: 运行纯函数测试并确认失败**

Run: `npm --prefix frontend run test:run -- productEditorState.test.ts`

Expected: FAIL，状态模块不存在。

- [ ] **Step 3: 实现状态、步骤校验和提交映射**

状态使用普通响应式对象，不引入 Pinia。每步返回字段错误映射；最终提交依次执行六步校验并定位第一个错误步骤。包装模式只存在于编辑状态，不出现在 API payload。

- [ ] **Step 4: 写编辑器交互失败测试**

```ts
it('moves through six steps and submits a complete product', async () => {
  const wrapper = mountEditor({ routeName: 'product-new' });
  await fillBasicStep(wrapper);
  await wrapper.get('[data-testid="next-step"]').trigger('click');
  expect(wrapper.get('[data-testid="editor-step-title"]').text()).toContain('SKU 信息');
  await completeRemainingSteps(wrapper);
  await wrapper.get('[data-testid="submit-product"]').trigger('click');
  expect(productService.createProduct).toHaveBeenCalledWith(expect.objectContaining({ itemNo: 'BBF-021' }));
});
```

增加：编辑路由预填、SKU 新增/编辑弹窗、逐 SKU 包装弹窗底部不遮挡内容、图片上传状态、离开未保存确认、保存失败保留当前步骤。

- [ ] **Step 5: 实现六个步骤组件**

步骤组件通过 `v-model` 或显式更新事件编辑局部状态；下拉框统一使用现有视觉规范，图片只使用上传组件。采购步骤按 SKU 分组维护报价，不显示渠道售价。

- [ ] **Step 6: 实现 SKU 与包装宽弹窗**

弹窗桌面宽度使用 `min(960px, calc(100vw - 48px))`，主体 `max-height: calc(100vh - 180px)` 并滚动，底部操作栏在弹窗流内固定且预留内容底部空间。包装弹窗只写回当前 SKU；取消不修改向导状态。

- [ ] **Step 7: 实现创建、编辑、保存和离开保护**

新增调用 `createProduct`，编辑先 `getProduct` 再调用 `updateProduct`。保存成功跳转详情；路由守卫只在状态相对初始快照变化时提示。

- [ ] **Step 8: 迁移并收缩旧 ProductForm**

将可复用上传逻辑提取到编辑器组件；旧 `ProductForm.vue` 不再被路由使用。确认无引用后删除或保留为薄兼容包装，禁止继续保留两套独立表单逻辑。

- [ ] **Step 9: 运行向导和商品全量测试**

Run: `npm --prefix frontend run test:run -- productEditorState.test.ts ProductEditorView.test.ts ProductFeature.test.ts ProductDetailNavigation.test.ts`

Expected: PASS。

- [ ] **Step 10: 提交**

```bash
git add frontend/src/features/product frontend/src/router
git commit -m "feat: add guided product editor"
```

---

### Task 11: 全量联调、视觉检查与旧原型清理

**Files:**
- Modify/Delete as proven unused: `frontend/src/views/WorkspacePrototype.vue`
- Modify/Delete as proven unused: `frontend/src/views/WorkspacePrototype.test.ts`
- Modify: `frontend/src/AppRouting.test.ts`
- Modify: `README.md` only if startup commands or routes are outdated.

**Interfaces:**
- Consumes: Tasks 1-10 的数据库、API、路由和页面。
- Produces: 可启动的真实数据端到端流程以及最终验证记录。

- [ ] **Step 1: 运行后端全量测试**

Run: `mvn -f backend/pom.xml test`

Expected: `BUILD SUCCESS`，无失败或跳过的关键测试。

- [ ] **Step 2: 运行前端全量测试和构建**

Run: `npm --prefix frontend run test:run`

Expected: 所有 Vitest 测试通过。

Run: `npm --prefix frontend run build`

Expected: TypeScript 与 Vite 构建成功。

- [ ] **Step 3: 启动真实环境**

Run backend: `mvn -f backend/pom.xml spring-boot:run`

Run frontend: `npm --prefix frontend run dev -- --host 127.0.0.1`

Expected: 后端健康启动，前端输出可访问本地 URL；端口占用时使用 Vite 自动选择的新端口。

- [ ] **Step 4: 走通真实商品流程**

使用浏览器完成：新增商品、两个 SKU、供应商采购报价、统一包装保存、编辑为逐 SKU 包装、上传四类图片、详情回显、分类筛选、货号/SKU 搜索和分页。刷新页面后数据仍存在。

- [ ] **Step 5: 检查目标视口**

分别检查 `1440x1024`、`1280x900` 和 `1024x768`：主框架无外围留白；侧栏通栏；表格列不遮挡；详情无重叠；SKU/包装弹窗输入框可见；弹窗底栏不覆盖最后一行；趋势图非空时有实际像素内容。

- [ ] **Step 6: 清理旧原型入口**

使用 `rg "WorkspacePrototype|ProductDetailDrawer|ProductForm" frontend/src` 确认引用。只删除已经完全无引用的旧组件和测试；保留仍被其他模块使用的共享逻辑。

- [ ] **Step 7: 再次运行最终验证**

Run: `mvn -f backend/pom.xml test`

Run: `npm --prefix frontend run test:run`

Run: `npm --prefix frontend run build`

Expected: 三条命令全部成功，`git diff --check` 无输出。

- [ ] **Step 8: 提交**

```bash
git add backend frontend README.md
git commit -m "feat: complete homepage and product redesign"
```
