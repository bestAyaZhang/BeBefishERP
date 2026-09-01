# Task 3 Implementer Report

## Status

完成商品列表与详情聚合读模型：实时计算资料完整度，批量聚合库存、报价、默认 SKU 售价、默认供应商和图片原始 URL，扩展列表/详情响应，并提供递归分类筛选及分类计数接口。未新增统计快照，未修改 Task 1/2 的保存流程和报价三态语义。

## RED Evidence

1. 完整度纯函数：
   - 命令：`mvn -f backend/pom.xml -Dtest=ProductCompletenessCalculatorTest test`
   - 结果：`BUILD FAILURE`。
   - 预期失败：测试编译报错 `cannot find symbol ProductCompletenessCalculator`，证明测试先于生产实现。
2. 聚合响应与递归分类：
   - 命令：`mvn -f backend/pom.xml -Dtest=ProductControllerTest test`
   - 结果：15 个测试中 2 个失败，其余 13 个通过。
   - 预期失败一：父分类查询的 `$.data.total` 期望 `1`、实际 `0`，证明尚未包含孙分类商品。
   - 预期失败二：`$.data.records[0].totalStock` 不存在，证明聚合读模型字段尚未实现。

## GREEN Evidence

- 完整度实现后：`ProductCompletenessCalculatorTest`，4/4 通过。
- 聚焦测试：`mvn -f backend/pom.xml '-Dtest=ProductCompletenessCalculatorTest,ProductControllerTest' test`，19/19 通过。
- 完整后端回归：`mvn -f backend/pom.xml test`，166/166 通过，0 failures、0 errors、0 skipped。
- 所有数据库测试仅使用 `bebefish_erp_test`，数据库环境变量仅在对应 Maven 命令进程中设置，未写入文件。

## Batch Query And N+1 Review

- `ProductController.list` 先获取整页 `Product`，再对 `products.getContent()` 调用一次 `ProductCatalogQueryService.load`；响应映射只查内存 `Map`。
- `ProductController.get` 使用同一 `ProductCatalogQueryService.load`，以单元素集合加载详情指标。
- `ProductRepository.loadCatalogData(Collection<Long>)` 是聚合读取边界；`ProductJpaAdapter` 对产品 ID 集合执行固定五条批量 SQL：跨仓 SKU 库存、全部 SKU 报价、默认 SKU 售价、默认供应商、图片原始 URL。查询数不随商品或 SKU 数量增长。
- `ProductCompletenessCalculator` 是纯函数，无 repository/JDBC 依赖，不执行逐商品或逐 SKU 查询。
- 控制器不再逐商品调用旧的 `imageUrls` / `defaultSupplierName`；批量取得的图片原始 URL 仍逐值交给 `FileAccessUrlResolver`，未构造伪 URL。
- 已知既有风险：`ProductJpaAdapter.findAll` 对分页 ID 仍沿用 `findById` 装载商品基础聚合，存在既有的基础资料 N+1；本任务新增的库存、报价、价格、供应商和图片聚合均已批量化，未扩大该问题。

## Behavior Covered

- 五个等权完整度组，每组 20；`100 => complete`，否则 `incomplete`；缺失组固定按“基本信息、SKU 信息、采购信息、包装重量、图片资料”返回。
- 只以 enabled SKU 参与完整度判断；每个 enabled SKU 必须存在 enabled 默认报价。
- 商品总库存和 SKU 库存均跨全部仓库求和；总安全库存覆盖商品全部 SKU。
- 商品级 `defaultSalePrice` 来自默认 SKU；每个 SKU 返回全部 supplier quotes，包括 disabled 非默认报价。
- 父分类筛选包含任意深度后代；`/api/products/category-counts` 返回每个分类自身及后代的 `count(distinct product.id)`，包括零商品分类。
- 列表和详情保持原字段，仅追加聚合字段、完整度字段、SKU 库存及报价字段。

## Files

- `backend/src/main/java/com/bebefish/erp/product/application/ProductCompletenessCalculator.java`
- `backend/src/main/java/com/bebefish/erp/product/application/ProductCatalogMetrics.java`
- `backend/src/main/java/com/bebefish/erp/product/application/ProductCatalogQueryService.java`
- `backend/src/main/java/com/bebefish/erp/product/domain/ProductRepository.java`
- `backend/src/main/java/com/bebefish/erp/product/infrastructure/ProductJpaAdapter.java`
- `backend/src/main/java/com/bebefish/erp/product/api/ProductResponse.java`
- `backend/src/main/java/com/bebefish/erp/product/api/ProductController.java`
- `backend/src/test/java/com/bebefish/erp/product/application/ProductCompletenessCalculatorTest.java`
- `backend/src/test/java/com/bebefish/erp/product/api/ProductControllerTest.java`

## Risks

- 商品基础聚合分页装载仍保留既有 `findById` N+1，后续若列表页规模或 SKU/规格数量显著增长，建议单独任务批量化基础聚合装载并增加 SQL 计数测试。
- 分类递归依赖 `product_category` 保持无环树结构；异常环数据会触及 MySQL 递归深度限制。现有业务约束下未新增持久化字段或迁移。
