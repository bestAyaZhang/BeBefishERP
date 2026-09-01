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

## Fix Round 1

### Status

已修复审查指出的列表基础聚合 N+1 和无 enabled SKU 时图片完整度误判。Task 1/2 保存及报价语义、分类递归、批量指标 SQL 和响应字段均保持不变。

### RED / GREEN 1 - 图片完整度空集合

- 新增用例：商品基本信息和主图完整，但唯一 SKU 为 disabled；期望 `percent=20`，并按固定顺序缺失“SKU 信息、采购信息、包装重量、图片资料”。
- RED 命令：`mvn -f backend/pom.xml -Dtest=ProductCompletenessCalculatorTest test`。
- RED 结果：5 个测试中 1 个失败，期望 `20`、实际 `40`；其余 4 个通过，准确证明 `allMatch(empty)` 使图片资料错误得分。
- GREEN：`imagesComplete` 同时要求 `enabledSkus` 非空；同一命令 5/5 通过。

### RED / GREEN 2 - 列表基础聚合查询数

- 新增仓储级集成测试 `ProductJpaAdapterQueryCountTest`。项目无现成 SQL 计数设施，因此测试使用仅存在于测试代码的 `DelegatingDataSource` 连接代理，统计真实 JDBC `prepareStatement` 次数；数据和查询均连接 `bebefish_erp_test`。
- RED 命令：`mvn -f backend/pom.xml -Dtest=ProductJpaAdapterQueryCountTest test`。
- RED 结果：2 个测试中查询数用例失败；页大小 1 为 6 条 SQL，页大小 2 增长到 10 条，期望固定为 6 条。空页用例通过并确认只执行 count 与分页 ID 两条 SQL。
- GREEN：分页 ID 非空时，以四条参数化批量查询装载 `product_spu`、`product_spec` + `product_spec_value`、`product_sku_spec_value`、`product_sku`；页大小 1 和 2 均固定为 6 条 SQL，空页仍为 2 条。仓储测试 2/2 通过。
- 测试同时断言分页 ID 顺序、规格值和 SKU 规格值组装，防止通过省略聚合数据来降低查询数。

### Tests

- Task 3 聚焦测试：`mvn -f backend/pom.xml '-Dtest=ProductCompletenessCalculatorTest,ProductJpaAdapterQueryCountTest,ProductControllerTest' test`，22/22 通过。
- 完整后端回归：`mvn -q -f backend/pom.xml test`，169/169 通过，0 failures、0 errors、0 skipped。
- 所有数据库测试仅使用 `bebefish_erp_test`，连接环境变量只在对应 Maven 命令进程中设置，未写入文件。

### Files

- `backend/src/main/java/com/bebefish/erp/product/application/ProductCompletenessCalculator.java`
- `backend/src/main/java/com/bebefish/erp/product/infrastructure/ProductJpaAdapter.java`
- `backend/src/test/java/com/bebefish/erp/product/application/ProductCompletenessCalculatorTest.java`
- `backend/src/test/java/com/bebefish/erp/product/infrastructure/ProductJpaAdapterQueryCountTest.java`

### Self Review

- `findAll` 不再逐 ID 调用 `findById`；详情 `findById` 保留原行为。
- 所有新增批量查询均使用按非空 ID 数量生成的 `?` 占位符和参数数组；`findAllByIds` 在空集合时立即返回，不会生成 `IN ()`。
- SPU、规格、SKU 规格值和 SKU 按 `productId` 在内存归组，最终严格遍历分页 ID 列表组装，保持原分页排序。
- 列表基础聚合固定为 count、分页 ID 和四条 hydration SQL；Task 3 既有库存、报价、默认价格、供应商及图片五条批量指标 SQL 保持固定，均不随商品或 SKU 数量增长。
- 未新增统计快照、迁移或业务库操作，未修改 Task 1/2 持久化与报价三态语义，未编辑 SDD ledger。

### Commit

- 父提交：`b0fbdc7 feat: add product catalog read model`。
- Fix Round 1 新提交消息：`fix: address product catalog review findings`。本报告与修复代码包含在同一新提交中，最终提交哈希见交付回复。
