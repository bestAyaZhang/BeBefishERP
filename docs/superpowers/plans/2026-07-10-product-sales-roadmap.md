# 产品资料与销售开单实施路线

## 执行顺序

1. [基础资料与产品计划](./2026-07-10-masterdata-product-implementation.md)
2. [库存基础计划](./2026-07-10-inventory-foundation-implementation.md)
3. [销售草稿计划](./2026-07-10-sales-draft-implementation.md)
4. [销售确认、财务与打印计划](./2026-07-10-sales-confirm-finance-print-implementation.md)

四份计划必须顺序执行。每一批都要连接真实后端和 MySQL 完成验收，上一批未通过时不进入下一批。

## 环境前置

- Java 21 和 Maven 已安装。
- 前端 Node.js 依赖已存在。
- 执行第一批数据库任务前需要可用的 MySQL 8 测试库。
- 当前机器未检测到 MySQL 客户端或 Docker，因此开始数据库持久化前需要先安装 MySQL 8，或准备可连接的 MySQL 8 测试实例。
- 数据库账号通过 `ERP_TEST_DB_URL`、`ERP_TEST_DB_USERNAME`、`ERP_TEST_DB_PASSWORD` 环境变量提供，不写入仓库。

## 统一门槛

- 所有功能严格先写失败测试，再写生产实现。
- 后端每批运行 `mvn test`。
- 前端每批运行 `npm run test:run` 和 `VITE_DATA_SOURCE=real npm run build`。
- 库存和财务批次必须验证事务回滚、重复操作和并发场景。
- 每批完成后展示测试结果和变更，等待用户验收。
- 未经用户明确指令，不执行 Git 暂存、提交或推送。

## 最终交付闭环

最终验收路径为：登录 → 建分类/客户/供应商/仓库 → 建单规格或多规格产品 → 设置多供应商采购价 → 初始化库存 → 创建销售草稿 → 确认扣库存 → 部分及多次收款 → 欠款结清 → B 版 A4 打印 → 作废冲回。
