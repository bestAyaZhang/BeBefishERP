# 平台管理：使用、迁移与验证

平台资料入口 `/platforms`。主分支发货人快照占用 V21，平台目录使用 V22、V23、V24。未向远程推送或在业务数据库执行迁移。

## 日常使用

1. 为运营管理员分配 `platform:view`、`platform:create`、`platform:edit`，刷新登录权限。超级管理员自动获得目录权限。
2. 在平台页新增编码和名称，再通过“查看店铺”建立所属店铺。编码创建后不可改，店铺不可迁到另一平台。
3. 发货人员仍使用 `shipping` 权限；新单必须选择有效平台和店铺。只有一个有效平台且其下只有一个有效店铺时自动选择。
4. 平台停用后子店铺不再供新单选择，但不改写子店铺自己的状态；重新启用平台不会启用原本停用的店铺。
5. 旧单显示原名称。改名、停用均不覆盖历史快照；旧单原来源不变时仍可维护原本允许修改的字段。已有 processing/unknown/succeeded 物流记录仍锁定来源 ID 和名称。
6. 历史列表可按平台/店铺 ID 筛选；原来未关联的单据用“历史未关联”和“历史平台名称”查找。店铺停用只限制新建/改选，旧单仍可继续已有物流流程。

MVP 不提供电商授权、订单同步、店铺成员隔离、单店物流账号、删除或跨平台迁店。当前模块按单公司共享主数据处理。

## 发布前检查

- 对齐发货人快照与平台目录的最终代码。发布前检查 V22/V23/V24 未被其他开发占用；已经应用的迁移不可编辑。
- V20 曾把部分成功物流记录的备货状态改成 completed，本功能未改 V20，也不猜测恢复历史状态。由发货分支核对旧库与全新库行为。
- 备份数据库。先在独立 `_test` 库验证从 V21 升级和从空库安装。V22/V23 加表/列/约束/权限，V24 将符合生成规则的旧标签清空为动态标签；不同的原标签保留为例外。迁移不初始化任何真实平台或店铺。
- 新版页面依赖新 API；旧浏览器纯文本新建会收到要求刷新并选择来源的错误。前后端在同一维护窗口切换，窗口内停止发货写入。
- 发布前必须由业务确认真实平台/店铺归属。不得依据店铺名称包含“淘宝”等字样自动推断平台。

## 离线迁移命令

工具入口 `com.bebefish.erp.platform.migration.CatalogMigrationApplication` 是普通 Java main，不被 Spring 扫描执行；不启动 ERP Web 应用、Flyway 或管理员初始化。库目标检查复用项目 `DatabaseEnvironmentSafetyInitializer`。

先在 backend 目录 `mvn -DskipTests package`。该命令只打包。工具使用 Boot 包内的 PropertiesLauncher；PowerShell 示例（路径请替换为实际绝对路径）：

```powershell
# 显式选择一个环境；不要把密码写到脚本、清单或版本库。
$env:SPRING_PROFILES_ACTIVE = 'test'
# 事先在进程环境中设置 ERP_TEST_DB_URL / ERP_TEST_DB_USERNAME / ERP_TEST_DB_PASSWORD。
# 本地业务库对应 local + ERP_DB_*；线上对应 prod + ERP_PROD_DB_*，并且配置 ERP_PROD_DB_HOST/NAME。
# 可选：ERP_SHIPPING_SHOP_NAMES 仅作为旧配置候选，正常 ERP 启动不再读取它。
java '-Dloader.main=com.bebefish.erp.platform.migration.CatalogMigrationApplication' `
  -cp target/bebefish-erp-0.1.0-SNAPSHOT.jar org.springframework.boot.loader.launch.PropertiesLauncher `
  '--mode=dry-run' '--output=C:\migration\preview-01'
```

父目录应存在，输出目录必须尚不存在，避免覆盖已有执行证据。dry-run 可针对升级前已有 shipment 表的数据库运行；不会自行建表。输出 `manifest.json`、`preview.json`，只含单据 ID、版本、平台店铺名称与关联，不含收件人联系方式或地址。

编辑 manifest：

- 保留 `schemaVersion=1`、目标库名、最大单据 ID、逐单原值/版本；不要手改 shipmentBaselines。
- platforms 填写 `{code,name,status,sortOrder}`；shops 填写 `{code,name,platformCode,status,sortOrder}`。
- 编码使用大写 ASCII 字母、数字、下划线或短横线，最长 50 字；名称先去首尾空格。
- mappings 的 rawPlatform/rawShopName 保留原文。填入确认的平台/店铺编码；只知道平台可以只填 platformCode。完全未知的两项 code 保持 null，执行时跳过并记录 unmapped。
- 配置中存在但从未发货的店铺也应经业务确认后加入 platforms/shops。跨平台同名店分别建档；别名只有经过确认才能映射到同一编码。

安装 V21/V22/V23/V24 后，人工确认清单、完成备份并停止发货写入，再执行：

```powershell
java '-Dloader.main=com.bebefish.erp.platform.migration.CatalogMigrationApplication' `
  -cp target/bebefish-erp-0.1.0-SNAPSHOT.jar org.springframework.boot.loader.launch.PropertiesLauncher `
  '--mode=apply' '--manifest=C:\migration\preview-01\manifest.json' '--output=C:\migration\apply-01'
```

apply 先验证清单和数据库，再写 before.json，主数据在一个事务内导入，单据逐条事务回填；每条结果写 results.jsonl，最后生成 summary.json。这里采用逐单事务以隔离冲突，尚未针对大规模历史库做吞吐调优。

已存在编码的名称、归属、状态或排序与清单不同会失败，不自动覆盖。已关联相同目标时跳过；原文、版本或关联发生变化时记 conflict。关联更新只改两个 ID、version_no、updated_by/updated_at，保留业务状态、名称及物流快照。冲突数大于 0 时进程退出码为 2，异常为非零；未映射记录属于跳过，须查看 JSONL 的 unmapped 项决定是否接受。

重新运行同一清单必须使用新输出目录，数据库写入是幂等的。中断后以数据库和原清单核对，不以缺失日志断言数据库未提交。不能删除既有证据后直接重跑。

## 核对与恢复

核对总发货单数不变、名称/备货状态/运单/物流 request_snapshot 不变；关联成功、已完成跳过、未映射与冲突数量可解释。至少有一个真实有效店铺后再开放新建发货。V17 历史单缺失必填地址/备货人等原有问题单独处理，工具不会补造资料。

发生错误先停止写入，按备份及 before.json 判断恢复范围；不要清空所有 ID、DROP 新表或执行 Flyway clean。若回退旧应用，它可能继续生成未关联单据，重新切回前再次预演。MySQL DDL 隐式提交，不能把结构迁移当成可原子撤销的事务。

## 验证命令与实现取舍

后端：使用专用 `_test` 库执行 `mvn test`。前端：`npm run test:run`，再设置 `VITE_RUNTIME_ENV=test`、`VITE_DATA_SOURCE=real` 执行 `npm run build`。真实物流测试需要另行安排，本轮自动化使用测试替身，不调用安能远程下单。

实现沿用小型模块的 JDBC 风格，平台/店铺共用 DTO 容器、仓储和服务，两个 Controller 分别鉴权；未为每个命令单建 Java 文件。前端使用一个 CatalogFormDrawer 处理相同字段，并实现键盘焦点约束。未改变全局抽屉组件。CLI 使用单一数据库 profile 和独立入口，避免计划中的第二个 migration profile 与现有环境保护规则冲突。

正常启动已移除 ERP_SHIPPING_SHOP_NAMES 的配置依赖。Mock 模式平台管理与发货共用浏览器演示目录；演示目录初始为空。实际部署只使用 real，不能用演示目录代替数据库初始化。

## 飞书店铺资料

V23 为店铺补充渠道类型（电商/私域）、店铺负责人姓名和门店选项。V24 开始普通门店选项由渠道、平台和店铺名称自动生成，编辑店铺或平台名称后实时更新；已有的三条私域标签作为例外保留。管理页面只显示生成预览，不再要求手填。创建平台、店铺时编码也由系统生成，数据库编码继续作为稳定的内部键。发货下拉框显示门店选项，保存的仍是店铺 ID 和当时的店铺名称快照。

[2026-09-29 飞书截图转录清单](platform-shop-feishu-config-2026-09-29.json)包含 4 个平台、21 家店铺。请在业务数据导入前核对转录的名称和标签。当前仅在独立测试库导入和验证，不自动写入业务库。

测试服务运行在本机 `127.0.0.1:8085` 且确认连接专用 `_test` 库后，可重复执行：

```powershell
$env:ERP_IMPORT_ADMIN_MOBILE = '<测试管理员手机号>'
$env:ERP_IMPORT_ADMIN_PASSWORD = '<测试管理员密码>'
./scripts/import-feishu-shop-catalog.ps1
```

脚本只连接本机服务，按平台名称与所属平台下的店铺名称匹配；普通门店选项由服务生成，清单中的三条私域特殊标签通过例外值导入，最后逐条核对 21 个标签。相同清单重复执行不会创建重复记录。若已导入后需要更正店铺名称，先通过管理页面修改原记录，再运行清单，避免按新名称另建一条。正式导入需要重新确认清单、环境和执行安排。

## 本轮验收记录（2026-09-29）

- 专用本地 `bebefish_platform_test` 库：合并主分支后端 408 项全量测试通过，无失败或跳过；V23→V24 标签迁移回归测试通过；后端 JAR 打包成功。
- 前端 74 个测试文件、811 项测试通过，real/test 模式构建成功。
- 打包后的 CLI 完成 dry-run；使用人工构造的测试清单首次关联 1 条，重复 apply 跳过 1 条，两次均无冲突。
- 浏览器使用测试账号完成平台、所属店铺创建，并确认发货表单自动带入该平台和店铺。
- 独立只读审查发现旧单 null ID 与下拉占位项冲突；已用实际选中项断言复现并修复。
- 飞书截图的 4 个平台、21 家店铺导入测试库，同清单重跑新增/更新均为 0；接口返回 21 个可用门店选项。截图转录经独立只读审查修正 3 处字样。
- 页面新建店铺时的只读预览根据渠道、平台和名称实时生成；三条私域例外在导入后仍与飞书清单一致。

本轮未执行业务数据库迁移或安能远程下单。并发测试覆盖目录停用后的最新状态读取和版本竞争，未完整模拟停用/新建发货、物流下单/来源编辑的所有交错时序；真实映射及历史库吞吐仍需发布验收。
