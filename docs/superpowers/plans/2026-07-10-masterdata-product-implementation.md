# 基础资料与产品 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 建立真实可持久化的分类、客户、供应商、仓库、产品、SKU、多规格组合、多供应商采购报价和图片上传能力。

**Architecture:** 后端按 `masterdata`、`product`、`file` 模块组织，业务规则放在 domain/application，JPA 与 REST 分别作为基础设施和 API 适配器。前端按 feature 拆分，通过统一 Service 接口切换 Mock 与真实 API，并用 Vue Router 替换单文件页面切换。

**Tech Stack:** Java 21、Spring Boot 3.3.5、Spring Data JPA、Flyway、MySQL 8、JUnit 5、MockMvc、Vue 3、Vue Router、TypeScript、Tailwind CSS、Vitest、Vue Test Utils。

## Global Constraints

- 严格执行 RED → GREEN → REFACTOR；没有观察到目标失败测试前，不得编写对应生产代码。
- MySQL 集成测试要求可用的 MySQL 8 测试库；当前机器未检测到 MySQL 客户端或 Docker，执行数据库任务前必须先准备测试库。
- 测试库连接通过 `ERP_TEST_DB_URL`、`ERP_TEST_DB_USERNAME`、`ERP_TEST_DB_PASSWORD` 注入，不把密码写入仓库。
- 前端通过 `VITE_DATA_SOURCE=mock|real` 切换数据源；生产构建仅允许 `real`。
- 所有业务 API 必须携带 Bearer Token；后端权限校验不能只依赖前端菜单。
- 金额使用 `BigDecimal`/`decimal`；尺寸、重量和数量不得使用 `double`。
- 未经用户明确指令，不执行 `git add`、`git commit` 或 `git push`；每个任务末尾只运行验证并等待验收。

---

### Task 1: MySQL、Flyway 与公共业务错误基线

**Files:**
- Modify: `backend/pom.xml`
- Create: `backend/src/main/resources/application.yml`
- Create: `backend/src/test/resources/application-test.yml`
- Create: `backend/src/main/resources/db/migration/V1__masterdata_product_schema.sql`
- Create: `backend/src/main/java/com/bebefish/erp/common/api/BusinessException.java`
- Modify: `backend/src/main/java/com/bebefish/erp/common/api/GlobalExceptionHandler.java`
- Create: `backend/src/test/java/com/bebefish/erp/common/persistence/FlywayMigrationTest.java`
- Create: `backend/src/test/java/com/bebefish/erp/common/api/GlobalExceptionHandlerTest.java`

**Interfaces:**
- Produces: `BusinessException(String code, HttpStatus status, String message)`。
- Produces: Flyway 管理的基础资料、产品、SKU、规格、报价和文件表。

- [ ] **Step 1: 仅添加测试与持久化依赖，使测试可编译**

在 `pom.xml` 增加 `spring-boot-starter-data-jpa`、`flyway-core`、`flyway-mysql`、`mysql-connector-j`，并保留现有测试依赖。此步骤只建立测试运行能力，不实现业务行为。

- [ ] **Step 2: 写数据库迁移失败测试**

```java
@SpringBootTest
@ActiveProfiles("test")
class FlywayMigrationTest {
    @Autowired JdbcTemplate jdbc;

    @Test
    void createsMasterdataAndProductTables() {
        var tables = jdbc.queryForList(
                "select table_name from information_schema.tables where table_schema = database()",
                String.class
        );
        assertThat(tables).contains(
                "product_category", "customer", "supplier", "warehouse",
                "product_spu", "product_sku", "product_spec", "product_spec_value",
                "product_sku_spec_value", "sku_supplier_quote", "file_asset"
        );
    }
}
```

- [ ] **Step 3: 运行测试并确认因表不存在而失败**

先在当前 shell 中设置 `ERP_TEST_DB_URL`、`ERP_TEST_DB_USERNAME`、`ERP_TEST_DB_PASSWORD`，然后运行：

```bash
cd backend
mvn -Dtest=FlywayMigrationTest test
```

Expected: FAIL，断言缺少 `product_category` 等表；若连接失败，先修复 MySQL 测试环境，不能把连接错误当作 RED。

- [ ] **Step 4: 编写迁移和测试配置**

`V1__masterdata_product_schema.sql` 必须创建设计文档第 12 节列出的 11 张表，包含 `bigint` 主键、业务唯一索引、审计字段和以下约束：

```sql
create table product_category (
    id bigint primary key auto_increment,
    category_code varchar(50) not null,
    category_name varchar(100) not null,
    parent_id bigint null,
    level_no int not null default 1,
    sort_order int not null default 0,
    status varchar(20) not null,
    remark varchar(500) null,
    created_at datetime(3) not null,
    updated_at datetime(3) not null,
    unique key uk_category_code (category_code),
    unique key uk_category_name (category_name)
);

create table warehouse (
    id bigint primary key auto_increment,
    warehouse_no varchar(50) not null,
    warehouse_name varchar(100) not null,
    address varchar(500) null,
    is_default boolean not null default false,
    status varchar(20) not null,
    remark varchar(500) null,
    created_at datetime(3) not null,
    updated_at datetime(3) not null,
    unique key uk_warehouse_no (warehouse_no),
    unique key uk_warehouse_name (warehouse_name)
);
```

其余表字段必须与已批准设计文档第 6、7、12 节一致，外键仅阻止误删，不使用级联删除历史数据。

- [ ] **Step 5: 写业务异常处理失败测试**

```java
@WebMvcTest(controllers = ExceptionProbeController.class)
@Import(GlobalExceptionHandler.class)
class GlobalExceptionHandlerTest {
    @Autowired MockMvc mvc;

    @Test
    void rendersStableBusinessErrorCode() throws Exception {
        mvc.perform(get("/test/business-error"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_ITEM_NO"))
                .andExpect(jsonPath("$.message").value("货号已存在"));
    }
}
```

- [ ] **Step 6: 运行异常测试并确认失败，然后实现最小异常映射**

Run: `cd backend && mvn -Dtest=GlobalExceptionHandlerTest test`

Expected RED: `BusinessException` 或处理器分支不存在。

实现：

```java
public final class BusinessException extends RuntimeException {
    private final String code;
    private final HttpStatus status;

    public BusinessException(String code, HttpStatus status, String message) {
        super(message);
        this.code = code;
        this.status = status;
    }

    public String code() { return code; }
    public HttpStatus status() { return status; }
}
```

在 `GlobalExceptionHandler` 中返回 `exception.status()` 和 `ApiResponse.failure(exception.code(), exception.getMessage(), null)`。

- [ ] **Step 7: 验证 Task 1**

Run: `cd backend && mvn test`

Expected: 现有登录测试、迁移测试和异常测试全部 PASS。

### Task 2: Bearer Token 认证与业务 API 保护

**Files:**
- Create: `backend/src/main/java/com/bebefish/erp/common/security/BearerTokenAuthenticationFilter.java`
- Create: `backend/src/main/java/com/bebefish/erp/common/security/ErpPrincipal.java`
- Modify: `backend/src/main/java/com/bebefish/erp/common/config/SecurityConfig.java`
- Modify: `backend/src/main/java/com/bebefish/erp/auth/infrastructure/InMemoryUserAccountRepository.java`
- Create: `backend/src/test/java/com/bebefish/erp/common/security/BearerTokenAuthenticationFilterTest.java`

**Interfaces:**
- Consumes: `TokenIssuer.resolve(String)`。
- Produces: `ErpPrincipal(mobile, roles, permissions)`，供方法级权限校验使用。
- Produces: 权限编码 `masterdata:view|edit`、`product:view|edit`、`inventory:view|adjust`、`sales:view|create|confirm|void|print`、`finance:view|receipt`。

- [ ] **Step 1: 写未登录与有效令牌测试**

```java
@SpringBootTest
@AutoConfigureMockMvc
class BearerTokenAuthenticationFilterTest {
    @Autowired MockMvc mvc;

    @Test
    void rejectsBusinessApiWithoutToken() throws Exception {
        mvc.perform(get("/api/categories"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void leavesAuthLoginApiPublic() throws Exception {
        mvc.perform(post("/api/auth/login/password")
                .contentType(APPLICATION_JSON)
                .content("{\"mobile\":\"13800138000\",\"password\":\"Admin@123456\"}"))
                .andExpect(status().isOk());
    }
}
```

- [ ] **Step 2: 运行并确认未登录业务 API 没有返回统一 401**

Run: `cd backend && mvn -Dtest=BearerTokenAuthenticationFilterTest test`

Expected: FAIL，当前 `SecurityConfig` 对所有请求 `permitAll()`。

- [ ] **Step 3: 实现过滤器和安全配置**

```java
public record ErpPrincipal(String mobile, List<String> roles, List<String> permissions) {}
```

过滤器从 `Authorization: Bearer <token>` 解析令牌，调用 `TokenIssuer.resolve`，把 `ErpPrincipal` 和权限转换成 Spring Security `Authentication`。启用 `@EnableMethodSecurity`，业务控制器使用 `@PreAuthorize` 校验对应权限。`/api/auth/login/**`、`/api/auth/sms-code` 保持公开；其余 `/api/**` 必须认证。认证入口点统一返回：

```json
{"code":"UNAUTHORIZED","message":"未登录","data":null}
```

测试类中增加一个带 `@PreAuthorize("hasAuthority('product:view')")` 的静态 probe controller，并分别验证无权限令牌返回 403、具备 `product:view` 的令牌返回 200。默认开发管理员补齐本计划列出的全部权限，避免登录后看得到菜单却调用不了 API。

- [ ] **Step 4: 验证 Task 2**

Run: `cd backend && mvn test`

Expected: 全部 PASS，登录 API 不受影响。

### Task 3: 分类管理纵向切片

**Files:**
- Create: `backend/src/main/java/com/bebefish/erp/masterdata/domain/Category.java`
- Create: `backend/src/main/java/com/bebefish/erp/masterdata/domain/CategoryRepository.java`
- Create: `backend/src/main/java/com/bebefish/erp/masterdata/application/CategoryService.java`
- Create: `backend/src/main/java/com/bebefish/erp/masterdata/api/CategoryController.java`
- Create: `backend/src/main/java/com/bebefish/erp/masterdata/api/SaveCategoryRequest.java`
- Create: `backend/src/main/java/com/bebefish/erp/masterdata/infrastructure/CategoryJpaEntity.java`
- Create: `backend/src/main/java/com/bebefish/erp/masterdata/infrastructure/SpringDataCategoryRepository.java`
- Create: `backend/src/main/java/com/bebefish/erp/masterdata/infrastructure/JpaCategoryRepository.java`
- Create: `backend/src/test/java/com/bebefish/erp/masterdata/application/CategoryServiceTest.java`
- Create: `backend/src/test/java/com/bebefish/erp/masterdata/api/CategoryControllerTest.java`

**Interfaces:**
- Produces: `CategoryService.create`, `update`, `changeStatus`, `list`。
- Produces: `GET/POST /api/categories`、`GET/PUT /api/categories/{id}`。

- [ ] **Step 1: 写分类领域失败测试**

```java
@Test
void createsOnlyLevelOneCategoryInVersionOne() {
    var result = service.create(new SaveCategoryCommand("GLASS", "玻璃杯", 10, "杯具"));
    assertThat(result.level()).isEqualTo(1);
    assertThat(result.parentId()).isNull();
}

@Test
void rejectsDuplicateCategoryName() {
    repository.save(category("GLASS", "玻璃杯"));
    assertThatThrownBy(() -> service.create(new SaveCategoryCommand("BEER", "玻璃杯", 20, null)))
            .isInstanceOf(BusinessException.class)
            .hasMessage("分类名称已存在");
}
```

- [ ] **Step 2: 运行 RED**

Run: `cd backend && mvn -Dtest=CategoryServiceTest test`

Expected: FAIL，分类服务和命令不存在。

- [ ] **Step 3: 实现最小领域与应用服务**

```java
public record Category(
        Long id, String code, String name, Long parentId, int level,
        int sortOrder, String status, String remark
) {}

public interface CategoryRepository {
    boolean existsByCode(String code, Long excludedId);
    boolean existsByName(String name, Long excludedId);
    Category save(Category category);
    Optional<Category> findById(long id);
    Page<Category> findAll(String keyword, String status, Pageable pageable);
}
```

第一版服务强制 `parentId=null`、`level=1`；重复编码返回 `DUPLICATE_CATEGORY_CODE`，重复名称返回 `DUPLICATE_CATEGORY_NAME`。

- [ ] **Step 4: 写并运行 API 失败测试**

```java
mockMvc.perform(post("/api/categories")
        .header("Authorization", bearerToken)
        .contentType(APPLICATION_JSON)
        .content("{\"categoryCode\":\"GLASS\",\"categoryName\":\"玻璃杯\",\"sortOrder\":10}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.categoryName").value("玻璃杯"));
```

Run: `cd backend && mvn -Dtest=CategoryControllerTest test`

Expected: FAIL，控制器不存在。

- [ ] **Step 5: 实现 API 与 JPA 适配器并验证**

分页参数统一为 `page`、`size`、`keyword`、`status`。运行 `cd backend && mvn test`，Expected: PASS。

### Task 4: 客户与供应商管理纵向切片

**Files:**
- Create: `backend/src/main/java/com/bebefish/erp/masterdata/domain/Customer.java`
- Create: `backend/src/main/java/com/bebefish/erp/masterdata/domain/Supplier.java`
- Create: `backend/src/main/java/com/bebefish/erp/masterdata/domain/CustomerRepository.java`
- Create: `backend/src/main/java/com/bebefish/erp/masterdata/domain/SupplierRepository.java`
- Create: `backend/src/main/java/com/bebefish/erp/masterdata/application/CustomerService.java`
- Create: `backend/src/main/java/com/bebefish/erp/masterdata/application/SupplierService.java`
- Create: `backend/src/main/java/com/bebefish/erp/masterdata/api/CustomerController.java`
- Create: `backend/src/main/java/com/bebefish/erp/masterdata/api/SupplierController.java`
- Create: `backend/src/main/java/com/bebefish/erp/masterdata/infrastructure/CustomerJpaAdapter.java`
- Create: `backend/src/main/java/com/bebefish/erp/masterdata/infrastructure/SupplierJpaAdapter.java`
- Create: `backend/src/test/java/com/bebefish/erp/masterdata/application/CustomerServiceTest.java`
- Create: `backend/src/test/java/com/bebefish/erp/masterdata/application/SupplierServiceTest.java`

**Interfaces:**
- Produces: 客户与供应商 CRUD、启停和分页查询 API。
- Produces: 客户默认 `transportMethod` 和 `settlementCycle`，供销售计划消费。

- [ ] **Step 1: 写客户和供应商失败测试**

```java
@Test
void createsCustomerWithDefaultDeliveryPreferences() {
    var customer = service.create(new SaveCustomerCommand(
            "C0001", "杭州万象家居", "张经理", "13800138000",
            "浙江省", "杭州市", "滨江区", "江南大道88号", "delivery", "monthly", null
    ));
    assertThat(customer.transportMethod()).isEqualTo("delivery");
    assertThat(customer.settlementCycle()).isEqualTo("monthly");
}

@Test
void supplierNumberMustBeUnique() {
    repository.save(supplier("S0001", "义乌玻璃厂"));
    assertThatThrownBy(() -> service.create(command("S0001", "宁波玻璃厂")))
            .hasMessage("供应商编号已存在");
}
```

- [ ] **Step 2: 运行 RED，按最小接口实现，再运行 GREEN**

Run RED: `cd backend && mvn -Dtest=CustomerServiceTest,SupplierServiceTest test`

实现的枚举值只接受：

```java
Set<String> TRANSPORT_METHODS = Set.of("pickup", "delivery", "freight", "express");
Set<String> SETTLEMENT_CYCLES = Set.of("daily", "monthly", "quarterly", "yearly");
```

Run GREEN: `cd backend && mvn -Dtest=CustomerServiceTest,SupplierServiceTest test`

- [ ] **Step 3: 添加 API、JPA 适配器和散客初始化迁移**

散客使用固定客户编号 `WALK_IN`，可编辑联系方式但不可删除或停用。API 与分类使用相同分页响应。

- [ ] **Step 4: 验证 Task 4**

Run: `cd backend && mvn test`

Expected: PASS。

### Task 5: 多仓库与唯一默认仓库

**Files:**
- Create: `backend/src/main/java/com/bebefish/erp/masterdata/domain/Warehouse.java`
- Create: `backend/src/main/java/com/bebefish/erp/masterdata/domain/WarehouseRepository.java`
- Create: `backend/src/main/java/com/bebefish/erp/masterdata/application/WarehouseService.java`
- Create: `backend/src/main/java/com/bebefish/erp/masterdata/api/WarehouseController.java`
- Create: `backend/src/main/java/com/bebefish/erp/masterdata/infrastructure/WarehouseJpaAdapter.java`
- Create: `backend/src/test/java/com/bebefish/erp/masterdata/application/WarehouseServiceTest.java`

**Interfaces:**
- Produces: `WarehouseService.getDefaultWarehouse()`。
- Produces: `GET/POST /api/warehouses`、`GET/PUT /api/warehouses/{id}`。

- [ ] **Step 1: 写默认仓库失败测试**

```java
@Test
void settingNewDefaultClearsPreviousDefaultInOneTransaction() {
    var first = repository.save(warehouse("WH01", true));
    var second = repository.save(warehouse("WH02", false));

    service.setDefault(second.id());

    assertThat(repository.findById(first.id()).orElseThrow().isDefault()).isFalse();
    assertThat(repository.findById(second.id()).orElseThrow().isDefault()).isTrue();
}
```

- [ ] **Step 2: 运行 RED 并实现事务服务**

Run: `cd backend && mvn -Dtest=WarehouseServiceTest test`

`setDefault` 使用 `@Transactional`，先清除旧默认，再设置新默认；停用默认仓库前必须先指定另一个默认仓库，否则抛出 `DEFAULT_WAREHOUSE_REQUIRED`。

- [ ] **Step 3: 运行 GREEN 与全量测试**

Run: `cd backend && mvn test`

Expected: PASS。

### Task 6: 单规格与多规格 SKU 组合领域逻辑

**Files:**
- Create: `backend/src/main/java/com/bebefish/erp/product/domain/ProductType.java`
- Create: `backend/src/main/java/com/bebefish/erp/product/domain/Specification.java`
- Create: `backend/src/main/java/com/bebefish/erp/product/domain/SkuCombination.java`
- Create: `backend/src/main/java/com/bebefish/erp/product/domain/SkuCombinationGenerator.java`
- Create: `backend/src/test/java/com/bebefish/erp/product/domain/SkuCombinationGeneratorTest.java`

**Interfaces:**
- Produces: `List<SkuCombination> generate(ProductType type, String productCode, List<Specification> specs)`。

- [ ] **Step 1: 写组合生成失败测试**

```java
@Test
void simpleProductCreatesOneDefaultSku() {
    var result = generator.generate(SIMPLE, "P100", List.of());
    assertThat(result).containsExactly(new SkuCombination("P100-DEFAULT", true, List.of()));
}

@Test
void variantProductCreatesCartesianProduct() {
    var result = generator.generate(VARIANT, "P200", List.of(
            spec("颜色", "透明", "烟灰"),
            spec("花纹", "竖纹", "樱花纹")
    ));
    assertThat(result).extracting(SkuCombination::displayText)
            .containsExactly("透明 / 竖纹", "透明 / 樱花纹", "烟灰 / 竖纹", "烟灰 / 樱花纹");
}
```

- [ ] **Step 2: 运行 RED**

Run: `cd backend && mvn -Dtest=SkuCombinationGeneratorTest test`

Expected: FAIL，生成器不存在。

- [ ] **Step 3: 实现纯函数生成器**

```java
public interface SkuCombinationGenerator {
    List<SkuCombination> generate(
            ProductType type,
            String productCode,
            List<Specification> specifications
    );
}
```

`SIMPLE` 返回一个默认 SKU；`VARIANT` 要求至少一个规格项且每项至少一个非空值，去重后按输入顺序生成组合。

- [ ] **Step 4: 运行 GREEN**

Run: `cd backend && mvn -Dtest=SkuCombinationGeneratorTest test`

Expected: PASS。

### Task 7: 产品、SKU、包装资料持久化与 API

**Files:**
- Create: `backend/src/main/java/com/bebefish/erp/product/domain/Product.java`
- Create: `backend/src/main/java/com/bebefish/erp/product/domain/Sku.java`
- Create: `backend/src/main/java/com/bebefish/erp/product/domain/ProductRepository.java`
- Create: `backend/src/main/java/com/bebefish/erp/product/application/ProductService.java`
- Create: `backend/src/main/java/com/bebefish/erp/product/api/ProductController.java`
- Create: `backend/src/main/java/com/bebefish/erp/product/api/SaveProductRequest.java`
- Create: `backend/src/main/java/com/bebefish/erp/product/api/ProductResponse.java`
- Create: `backend/src/main/java/com/bebefish/erp/product/infrastructure/ProductJpaAdapter.java`
- Create: `backend/src/test/java/com/bebefish/erp/product/application/ProductServiceTest.java`
- Create: `backend/src/test/java/com/bebefish/erp/product/api/ProductControllerTest.java`

**Interfaces:**
- Produces: `ProductService.createProduct`, `updateProduct`, `getProduct`, `listProducts`, `changeStatus`。
- Produces: `GET/POST /api/products`、`GET/PUT /api/products/{id}`、`POST /api/products/{id}/status`。

- [ ] **Step 1: 写产品创建失败测试**

```java
@Test
void createsSimpleProductWithPackagingDataOnDefaultSku() {
    var result = service.createProduct(simpleProductCommand(
            "P100", "EW43245", "高脚红酒杯",
            new PackagingCommand(new BigDecimal("42"), new BigDecimal("31"), new BigDecimal("28"),
                    null, new BigDecimal("8.5"), new BigDecimal("9.2"), new BigDecimal("350"),
                    "彩盒", 12)
    ));
    assertThat(result.skus()).hasSize(1);
    assertThat(result.skus().getFirst().packageVolumeCm3()).isEqualByComparingTo("36456.00");
}

@Test
void rejectsGrossWeightBelowNetWeight() {
    assertThatThrownBy(() -> service.createProduct(commandWithWeights("9.2", "8.5")))
            .hasMessage("毛重不能小于净重");
}
```

- [ ] **Step 2: 运行 RED，随后实现最小服务和唯一性校验**

Run RED: `cd backend && mvn -Dtest=ProductServiceTest test`

实现错误码 `DUPLICATE_PRODUCT_CODE`、`DUPLICATE_ITEM_NO`、`DUPLICATE_SKU_CODE`、`INVALID_PRODUCT_VARIANT`。体积为空时计算长宽高乘积，人工体积存在时保存人工值。

- [ ] **Step 3: 写 API 失败测试并实现控制器**

```java
mockMvc.perform(get("/api/products")
        .header("Authorization", bearerToken)
        .param("keyword", "EW43245")
        .param("categoryId", "1")
        .param("supplierId", "2")
        .param("status", "enabled"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.items[0].itemNo").value("EW43245"));
```

Run RED: `cd backend && mvn -Dtest=ProductControllerTest test`

实现列表筛选和详情响应，产品列表一行一个产品，详情内嵌 SKU 与规格。

- [ ] **Step 4: 运行 GREEN 与全量测试**

Run: `cd backend && mvn test`

Expected: PASS。

### Task 8: SKU 多供应商采购报价与标准成本同步

**Files:**
- Create: `backend/src/main/java/com/bebefish/erp/product/domain/SupplierQuote.java`
- Create: `backend/src/main/java/com/bebefish/erp/product/domain/SupplierQuoteRepository.java`
- Create: `backend/src/main/java/com/bebefish/erp/product/application/SupplierQuoteService.java`
- Create: `backend/src/main/java/com/bebefish/erp/product/api/SupplierQuoteController.java`
- Create: `backend/src/main/java/com/bebefish/erp/product/infrastructure/SupplierQuoteJpaAdapter.java`
- Create: `backend/src/test/java/com/bebefish/erp/product/application/SupplierQuoteServiceTest.java`

**Interfaces:**
- Produces: `saveQuote(skuId, command)` 和 `setDefaultQuote(skuId, quoteId, syncStandardCost)`。
- Produces: `GET/POST /api/skus/{skuId}/supplier-quotes`、`PUT /api/skus/{skuId}/supplier-quotes/{quoteId}`。

- [ ] **Step 1: 写报价规则失败测试**

```java
@Test
void skuMayHaveMultipleQuotesButOnlyOneDefault() {
    var first = service.saveQuote(10L, quote(1L, "2.20", true));
    var second = service.saveQuote(10L, quote(2L, "2.05", true));
    assertThat(repository.findById(first.id()).orElseThrow().isDefault()).isFalse();
    assertThat(repository.findById(second.id()).orElseThrow().isDefault()).isTrue();
}

@Test
void updatesStandardCostOnlyWhenExplicitlyConfirmed() {
    var quote = service.saveQuote(10L, quote(1L, "2.20", false));
    service.setDefaultQuote(10L, quote.id(), false);
    assertThat(productRepository.findSku(10L).standardCost()).isNotEqualByComparingTo("2.20");
    service.setDefaultQuote(10L, quote.id(), true);
    assertThat(productRepository.findSku(10L).standardCost()).isEqualByComparingTo("2.20");
}
```

- [ ] **Step 2: 运行 RED 并实现事务服务**

Run: `cd backend && mvn -Dtest=SupplierQuoteServiceTest test`

默认报价切换、旧默认清除和可选标准成本更新必须在同一事务内完成。同一 SKU+供应商重复报价返回 `SUPPLIER_QUOTE_CONFLICT`。

- [ ] **Step 3: 运行 GREEN 与全量测试**

Run: `cd backend && mvn test`

Expected: PASS。

### Task 9: 图片上传与可替换存储适配器

**Files:**
- Create: `backend/src/main/java/com/bebefish/erp/file/domain/FileStorage.java`
- Create: `backend/src/main/java/com/bebefish/erp/file/application/ImageUploadService.java`
- Create: `backend/src/main/java/com/bebefish/erp/file/api/FileController.java`
- Create: `backend/src/main/java/com/bebefish/erp/file/infrastructure/LocalFileStorage.java`
- Create: `backend/src/main/java/com/bebefish/erp/file/infrastructure/FileAssetJpaAdapter.java`
- Create: `backend/src/test/java/com/bebefish/erp/file/application/ImageUploadServiceTest.java`
- Create: `backend/src/test/java/com/bebefish/erp/file/api/FileControllerTest.java`

**Interfaces:**
- Produces: `StoredFile store(ImageUpload upload)`。
- Produces: `POST /api/files/images`，返回 `{id, url, contentType, size}`。

- [ ] **Step 1: 写类型、大小和唯一文件名失败测试**

```java
@Test
void rejectsNonImageContentType() {
    assertThatThrownBy(() -> service.upload(file("quote.pdf", "application/pdf", 100)))
            .hasMessage("图片类型不支持");
}

@Test
void rejectsImageLargerThanTenMegabytes() {
    assertThatThrownBy(() -> service.upload(file("large.png", "image/png", 10 * 1024 * 1024 + 1)))
            .hasMessage("图片超过 10 MB");
}
```

- [ ] **Step 2: 运行 RED，随后实现校验与本地存储**

Run: `cd backend && mvn -Dtest=ImageUploadServiceTest test`

允许类型：`image/jpeg`、`image/png`、`image/webp`。存储名使用 UUID 加安全扩展名，目录来自 `ERP_UPLOAD_DIR`，默认 `uploads/`。

- [ ] **Step 3: 写 multipart API 测试并实现控制器**

Run RED: `cd backend && mvn -Dtest=FileControllerTest test`

Expected: FAIL，上传端点不存在。实现后再次运行并确认 PASS。

- [ ] **Step 4: 运行全量后端测试**

Run: `cd backend && mvn test`

Expected: PASS。

### Task 10: 前端路由、登录跳转和 Mock/Real Service 基线

**Files:**
- Modify: `frontend/package.json`
- Modify: `frontend/src/main.ts`
- Modify: `frontend/src/App.vue`
- Create: `frontend/src/router/index.ts`
- Create: `frontend/src/layouts/ErpLayout.vue`
- Create: `frontend/src/components/navigation/SidebarNav.vue`
- Create: `frontend/src/services/http.ts`
- Create: `frontend/src/services/serviceFactory.ts`
- Create: `frontend/src/types/api.ts`
- Modify: `frontend/src/env.d.ts`
- Create: `frontend/src/router/index.test.ts`
- Create: `frontend/src/components/navigation/SidebarNav.test.ts`

**Interfaces:**
- Produces: `request<T>(path, init)` 自动附加 access token。
- Produces: `createService<T>(mockFactory, realFactory)`。
- Produces: 设计文档第 3 节的路由和菜单。

- [ ] **Step 1: 安装 Vue Router 后写失败测试**

Run: `cd frontend && npm install vue-router@4`

```typescript
it('redirects unauthenticated users to login', async () => {
  localStorage.clear();
  await router.push('/products');
  await router.isReady();
  expect(router.currentRoute.value.name).toBe('login');
});

it('shows each approved first-level menu item', () => {
  const wrapper = mount(SidebarNav, { global: { plugins: [router] } });
  for (const label of ['工作台', '产品资料', '分类管理', '客户管理', '供应商管理', '仓库管理', '库存管理', '销售开单', '销售单据', '财务管理']) {
    expect(wrapper.text()).toContain(label);
  }
});
```

- [ ] **Step 2: 运行 RED**

Run: `cd frontend && npm run test:run -- src/router/index.test.ts src/components/navigation/SidebarNav.test.ts`

Expected: FAIL，路由和菜单组件不存在。

- [ ] **Step 3: 实现路由、布局和登录成功跳转**

路由名固定为 `login`、`workbench`、`products`、`categories`、`customers`、`suppliers`、`warehouses`、`inventory-balances`、`inventory-ledger`、`inventory-adjustments`、`sales-create`、`sales-orders`、`receipts`、`receivables`。

登录成功后 `LoginView` 使用 router 跳转 `/workbench`；退出后跳转 `/login`。

- [ ] **Step 4: 写 Service 工厂失败测试并实现**

```typescript
it('uses mock service only when VITE_DATA_SOURCE is mock', () => {
  expect(selectService('mock', mockService, realService)).toBe(mockService);
  expect(selectService('real', mockService, realService)).toBe(realService);
});
```

生产构建检测到 `mock` 时抛出构建错误。HTTP Service 统一解析现有 `ApiResponse`，401 时清除令牌并跳转登录。

- [ ] **Step 5: 运行 GREEN 和前端全量测试**

Run: `cd frontend && npm run test:run && npm run build`

Expected: PASS。

### Task 11: 分类、客户、供应商和仓库前端页面

**Files:**
- Create: `frontend/src/features/masterdata/types.ts`
- Create: `frontend/src/features/masterdata/masterdataService.ts`
- Create: `frontend/src/features/masterdata/mockMasterdataService.ts`
- Create: `frontend/src/features/masterdata/httpMasterdataService.ts`
- Create: `frontend/src/features/masterdata/components/MasterdataTable.vue`
- Create: `frontend/src/features/masterdata/views/CategoryView.vue`
- Create: `frontend/src/features/masterdata/views/CustomerView.vue`
- Create: `frontend/src/features/masterdata/views/SupplierView.vue`
- Create: `frontend/src/features/masterdata/views/WarehouseView.vue`
- Create: `frontend/src/features/masterdata/views/MasterdataViews.test.ts`

**Interfaces:**
- Consumes: Task 3-5 REST API。
- Produces: 销售计划可复用的 `listActiveCustomers()`、`listActiveWarehouses()`。

- [ ] **Step 1: 写页面行为失败测试**

```typescript
it('creates a level-one category and refreshes the list', async () => {
  const wrapper = mount(CategoryView, { global: { provide: { masterdataService: fakeService } } });
  await wrapper.get('[data-testid="add-category"]').trigger('click');
  await wrapper.get('[data-testid="category-code"]').setValue('GLASS');
  await wrapper.get('[data-testid="category-name"]').setValue('玻璃杯');
  await wrapper.get('[data-testid="save-category"]').trigger('click');
  await flushPromises();
  expect(fakeService.createCategory).toHaveBeenCalledWith(expect.objectContaining({ categoryName: '玻璃杯' }));
});

it('marks exactly one warehouse as default', async () => {
  const wrapper = mount(WarehouseView, { global: { provide: { masterdataService: fakeService } } });
  await wrapper.get('[data-testid="set-default-warehouse-2"]').trigger('click');
  expect(fakeService.setDefaultWarehouse).toHaveBeenCalledWith(2);
});
```

- [ ] **Step 2: 运行 RED**

Run: `cd frontend && npm run test:run -- src/features/masterdata/views/MasterdataViews.test.ts`

Expected: FAIL，页面不存在。

- [ ] **Step 3: 实现页面和 Mock/Real Service**

页面统一包含关键字搜索、状态筛选、分页、新增、编辑和启停。客户表单包含默认运输方式和结算周期；仓库表单包含默认仓库开关。

- [ ] **Step 4: 运行 GREEN 与全量前端测试**

Run: `cd frontend && npm run test:run && npm run build`

Expected: PASS。

### Task 12: 产品列表、单/多规格表单、报价和图片前端

**Files:**
- Create: `frontend/src/features/product/types.ts`
- Create: `frontend/src/features/product/productService.ts`
- Create: `frontend/src/features/product/mockProductService.ts`
- Create: `frontend/src/features/product/httpProductService.ts`
- Create: `frontend/src/features/product/components/ProductList.vue`
- Create: `frontend/src/features/product/components/ProductForm.vue`
- Create: `frontend/src/features/product/components/VariantBuilder.vue`
- Create: `frontend/src/features/product/components/SkuEditor.vue`
- Create: `frontend/src/features/product/components/SupplierQuoteEditor.vue`
- Create: `frontend/src/features/product/components/ImageUploader.vue`
- Create: `frontend/src/features/product/views/ProductListView.vue`
- Create: `frontend/src/features/product/views/ProductDetailView.vue`
- Create: `frontend/src/features/product/ProductFeature.test.ts`
- Modify: `frontend/src/views/WorkspacePrototype.vue`

**Interfaces:**
- Consumes: Task 7-9 REST API 和 Task 11 的分类/供应商查询。
- Produces: 库存和销售计划使用的 SKU 查询类型 `SkuSummary`。

- [ ] **Step 1: 写单规格和多规格失败测试**

```typescript
it('shows one default SKU editor for a simple product', async () => {
  const wrapper = mount(ProductForm, { props: { service: fakeProductService } });
  await wrapper.get('[data-testid="product-type-simple"]').trigger('click');
  expect(wrapper.findAll('[data-testid="sku-editor"]')).toHaveLength(1);
  expect(wrapper.get('[data-testid="default-sku-badge"]').text()).toContain('默认 SKU');
});

it('generates editable color and pattern combinations', async () => {
  const wrapper = mount(ProductForm, { props: { service: fakeProductService } });
  await selectVariantValues(wrapper, { 颜色: ['透明', '烟灰'], 花纹: ['竖纹', '樱花纹'] });
  expect(wrapper.findAll('[data-testid="sku-editor"]')).toHaveLength(4);
  expect(wrapper.text()).toContain('透明 / 竖纹');
  expect(wrapper.text()).toContain('烟灰 / 樱花纹');
});
```

- [ ] **Step 2: 运行 RED**

Run: `cd frontend && npm run test:run -- src/features/product/ProductFeature.test.ts`

Expected: FAIL，产品 feature 组件不存在。

- [ ] **Step 3: 实现表单、报价和图片交互**

产品列表按一个产品一行展示并可展开 SKU。表单按基础信息、规格与价格、包装资料、供应商报价、图片分区。默认报价切换或价格修改时弹出“是否同步标准成本”确认框。

- [ ] **Step 4: 补充包装验证失败测试**

```typescript
it('shows validation when gross weight is below net weight', async () => {
  const wrapper = mount(ProductForm, { props: { service: fakeProductService } });
  await wrapper.get('[data-testid="net-weight"]').setValue('9.2');
  await wrapper.get('[data-testid="gross-weight"]').setValue('8.5');
  await wrapper.get('[data-testid="save-product"]').trigger('click');
  expect(wrapper.text()).toContain('毛重不能小于净重');
});
```

- [ ] **Step 5: 运行 GREEN 与全量前端测试**

Run: `cd frontend && npm run test:run && npm run build`

Expected: PASS。

### Task 13: 第一批真实联调与验收门槛

**Files:**
- Modify: `README.md`
- Modify: `docs/03-database-design.md`
- Modify: `docs/04-development-guide.md`
- Modify: `docs/06-phased-development-plan.md`

**Interfaces:**
- Confirms: 第一批公开 API 和前端 `SkuSummary` 可供库存计划使用。

- [ ] **Step 1: 运行后端全量测试**

先确认当前 shell 已设置测试数据库的三个 `ERP_TEST_DB_*` 环境变量，然后运行：

```bash
cd backend
mvn test
```

Expected: PASS，无跳过的产品和基础资料测试。

- [ ] **Step 2: 运行前端全量测试和构建**

Run: `cd frontend && npm run test:run && VITE_DATA_SOURCE=real npm run build`

Expected: PASS。

- [ ] **Step 3: 连接真实 API 运行浏览器验收**

依次验证：登录、分类新增、客户新增、供应商新增、设置默认仓库、创建单规格产品、创建颜色+花纹多规格产品、上传图片、设置两个供应商采购价、切换默认供应商并选择是否同步标准成本、刷新页面确认数据仍存在。

- [ ] **Step 4: 更新文档状态并等待用户验收**

README 写明 MySQL 环境变量、启动和测试命令；数据库与开发文档同步最终表和 API。展示 `git diff` 和测试结果，停止执行，不提交 Git，等待用户明确决定是否提交或进入库存计划。
