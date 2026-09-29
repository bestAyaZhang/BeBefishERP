# 平台管理 MVP Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking. 本文是待评审计划；当前会话仅规划，不开始实施。执行方式尚未由用户指定，后续按明确指令开展。

**Goal:** 统一维护平台和所属店铺，让新发货单选择可靠的主数据，同时保留历史单据名称、物流锁定规则和备货状态独立性。

**Architecture:** 延续模块化单体，在 `com.bebefish.erp.platform` 中按 api/application/domain/infrastructure 分层，采用 JDBC、事务与版本号。平台一对多店铺；发货单保存主数据 ID 和名称快照；前端复用 Vue 页面壳层、HTTP/Mock 服务工厂及权限矩阵。

**Tech Stack:** 项目现有 Java 21、Spring Boot 3.3.5、Maven、MySQL 8、Flyway、Vue 3、TypeScript、Vite、Tailwind、JUnit/Spring Test、Vitest/Vue Test Utils，不新增基础框架。

**Spec:** 本文“产品范围”至“迁移与上线”是同一份计划内的设计规格；业务待决项见末尾。所有推荐均为规划建议，不代表已经实施或业务已确认。

## Global Constraints

- 本轮只写计划文档；不修改产品代码、SQL、配置，不执行数据库迁移，不提交、推送或合并。
- 未经明确指令不要推送远程或合并主分支。
- 当前开发分支为 `codex/shipping-status-split`，发货功能正在继续开发，不能覆盖其未提交工作。
- 后端使用项目现有 Java 21、Spring Boot、JDBC、MySQL 8 与 Flyway；前端沿用 Vue 3/TypeScript。
- 测试数据库必须是本机独立 `_test` 库，沿用项目数据库目标检查；禁止在开发库运行清库测试。
- 测试/生产前端使用 `VITE_DATA_SOURCE=real`；写入、401、403、业务错误不得降级成 Mock 成功。
- 历史迁移仅补充主数据关联，不重写名称快照、备货状态、运单或物流请求快照。
- 本文读取的是 2026-09-29 工作区内容，HEAD 为 `ce42faa`；未连接数据库核验真实数据量、配置值或迁移执行历史。

## Review Focus

1. 店铺改名、停用后，旧单仍显示保存时名称，仍可修改允许编辑的备货内容、备注与状态（任务 3、5）。
2. 同名店跨平台、历史平台为空、别名和重名碰撞不得被自动错误归并（任务 1、5）。
3. 停用与发货保存并发时，不能在校验后写入已失效的新引用；物流下单与编辑并发时不能绕过字段锁（任务 2、3）。
4. 只有发货权限的员工能选择店铺，但不能进入平台管理或读取管理备注（任务 2、4）。
5. 旧浏览器请求、历史未关联记录、失败重跑迁移均不得制造重复店铺、抹去关联或触发重复物流下单（任务 3、5、6）。

## 1. 仓库核查与结论

| 现状 | 证据（仓库相对路径） | 对本计划的影响 |
| --- | --- | --- |
| 平台管理已有一级导航，`Store` 图标与页头；路由仍为占位组件，权限暂借 `shipping:view` | `frontend/src/features/navigation/mockNavigationService.ts`、`frontend/src/components/navigation/SidebarNav.vue`、`frontend/src/layouts/ErpLayout.vue`、`frontend/src/router/index.ts` | 保留菜单位置及 `/platforms`，替换组件并切换独立权限，不重复增加菜单 |
| 前端权限目录硬编码模块，后端权限来自 `sys_permission`；非查看动作必须有同模块 view | `frontend/src/features/permission/permissionCatalog.ts`、`permissionRules.ts`；后端 `authorization/application/PermissionManagementService.java`、`AuthorizationResolver.java` | SQL 权限种子、前端目录、路由、按钮、后端鉴权一起改 |
| 超级管理员解析数据库中全部权限；已有角色数据范围枚举 | `authorization/application/AuthorizationResolver.java` | 自动拥有新增模块；其他角色显式授予；不能把已有数据范围枚举当成已实现店铺行权限 |
| `shipment.platform` 是可空语义的 100 字文本，`shop_name` 是 200 字文本 | `V17__shipping_ledger.sql`、`V19__shipping_figma_flow.sql`、`shipping/domain/ShipmentFormInput.java` | 新增可空关联列，保留原字段作为快照；当前 platform 不强制必填，新的必填策略是业务变化 |
| 店铺选项来自 `ERP_SHIPPING_SHOP_NAMES`，会去首尾空格、去重；后端保存只校验文本，并不验证店铺属于配置 | `ShippingProperties.java`、`ShippingFormOptionsService.java`、`ShipmentService.java`、`application.yml` | 不能只改下拉框；服务端必须验证 ID、归属和启停用状态 |
| 表单先加载 options；唯一店铺自动选中；列表平台筛选为文本精确匹配 | `ShipmentFormView.vue`、`ShipmentListView.vue`、`JdbcShipmentRepository.java` | 新增联动选择和 ID 筛选；保留历史文本检索通道 |
| 非 rejected 物流记录锁定平台/店铺等字段；Service 与 SQL 同时防护，安能 claim 先锁 shipment 行 | `ShipmentEditPolicy.java`、`JdbcShipmentRepository.java`、`shipping/logistics/AneOrderStore.java` | ID 也必须进入两层锁定比较，不能仅比较名称 |
| 当前最高迁移文件为 V20，测试含全表清单与版本 20 断言 | `backend/src/main/resources/db/migration/`、`common/persistence/FlywayMigrationTest.java` | 新增迁移暂定 V21，执行前重新分配空闲版本；更新清单与版本断言 |
| V20 仍将符合条件的成功物流单备货状态设为 completed，而当前工作区运行时代码在拆分两种状态 | `V20__sync_successful_logistics_orders.sql`、当前 shipping 改动 | 属于发货分支前置核对项；本模块不得改旧迁移或通过猜测恢复历史状态 |

没有找到适用于该目录的 AGENTS.md。本轮未运行应用或测试，以上是静态代码核查结果；README 或旧设计与当前工作区冲突时，以待完成的发货分支验收结果确定实施基线。

## 2. 产品范围与方案选择

推荐“独立平台/店铺主数据 + 发货 ID 引用 + 名称快照”。另两个备选是：只把环境变量搬到页面（实现快，但无可靠归属/改名/历史关系）；完整电商连接中心（包含授权、拉单、同步与凭据管理，当前缺接口与业务约束）。后两者均不作为本次 MVP。

### 包含

- 平台：分页列表、名称/编码搜索、新建、编辑、启用/停用。
- 店铺：所属平台、分页列表、搜索、新建、编辑、启用/停用；平台详情显示店铺数。
- 发货：平台/店铺联动选择、服务端一致性验证、历史名称展示、按平台/店铺 ID 筛选。
- 权限：模块查看/新增/编辑，发货选项单独按发货权限提供。
- 迁移：配置候选清单、人工确认映射、历史关联回填、预演/重复执行/核对报告。
- 审计基础字段：创建人、修改人、创建时间、修改时间、版本；迁移保存执行清单和结果。暂不建设完整操作日志页面。

### 不在本期

电商平台 OAuth、App Key/Secret、订单拉取、库存同步、商品刊登、售后、结算、经营指标、每店独立物流账号/寄件人、店铺成员授权、批量导入管理 UI、硬删除、平台合并、跨平台迁店。

MVP 服务于当前单公司内部 ERP，平台/店铺为公司共享字典。现有角色 dataScope 不在本模块变成按部门/个人隐藏字典的规则；若需要多公司或店铺隔离，应先扩展主体模型与授权方案。

## 3. 数据模型、字段与规则

关系：`sales_platform 1 → N platform_shop`；`shipment → sales_platform/platform_shop`；单据仍保存 `platform` 与 `shop_name` 名称快照。

### 3.1 sales_platform

| 数据库字段 / API 字段 | 类型与必填 | 规则 |
| --- | --- | --- |
| id | bigint / number；数据库生成 | 稳定内部主键 |
| platform_code / code | varchar(50)；必填 | `[A-Z0-9_-]{1,50}`，trim 后转大写，全局唯一；创建后只读 |
| platform_name / name | varchar(100)；必填 | trim 后非空，全局唯一；可改名 |
| status | varchar(20)；必填 | `enabled` / `disabled`，新增默认 enabled |
| sort_order / sortOrder | int；必填 | 默认 0，范围 0～9999 |
| remark | varchar(1000)；可选 | 默认空字符串，仅管理接口返回 |
| version_no / version | bigint；必填 | 从 0 开始，更新/状态操作必须带预期版本 |
| created_by、updated_by | varchar(100)；必填 | 后端取 ErpPrincipal.operatorIdentifier()；不接受浏览器指定 |
| created_at、updated_at | datetime(3)；必填 | 沿用项目 Clock 与时间表示方式 |

### 3.2 platform_shop

与平台相同的 `id/code/name/status/sortOrder/remark/version/审计字段`，具体列名为 `shop_code`（50）、`shop_name`（200）；增加必填 `platform_id / platformId` 外键。

- 店铺编码全局唯一；名称只在同平台内唯一，不同平台可同名。
- 店铺创建后不允许修改 platformId。误建可停用后重新建正确归属，历史不迁店。
- 数据库采用项目现有 `utf8mb4_unicode_ci` 作为名称唯一性语义，忽略大小写/重音；仅 trim，不自动合并内部空格、全半角、别名或近似名称。服务端预检与唯一约束必须一致；数据库唯一约束兜底并发重复。
- 唯一约束：平台 code、平台 name、店铺 code、店铺 `(platform_id, shop_name)`；额外唯一索引 `(id, platform_id)` 供组合外键使用。
- 查询索引：平台 `(status, sort_order, id)`，店铺 `(platform_id, status, sort_order, id)`；排序固定 `sort_order asc, id asc`。
- 不提供删除 API，外键使用 RESTRICT。停用数据仍占用名称/编码；要恢复业务应启用原记录。

### 3.3 状态语义

- 店铺可选条件：店铺 enabled 且所属平台 enabled。
- 停用平台不批量修改子店铺自身 status。列表将子店铺有效状态显示为“平台已停用”；重启平台后，仅原本 enabled 的店铺重新可选。
- 停用确认框显示“将影响 N 家当前可选店铺；历史单据保留”。有历史引用不阻止停用。
- disabled 平台下允许维护现有店铺基础资料；新增店铺、将 disabled 店铺启用须先启用平台。现有 enabled 子店铺不因父平台停用而被改写。
- 启停用与常规修改都采用 CAS 版本更新；重复提交旧版本返回 409，不静默覆盖。
- 停用只禁止新建引用或改选到该对象；旧单原引用保持不变时仍可处理，包括继续现有物流流程。若业务要求停用即阻断所有待发业务，需另行确认规则。

### 3.4 发货单关联与快照

新增 nullable `platform_id`、`shop_id`；保留已有 `platform varchar(100)`、`shop_name varchar(200)`。

- 外键：platform_id → sales_platform.id；组合 `(shop_id, platform_id)` → platform_shop `(id, platform_id)`；CHECK 防止 shop_id 非空而 platform_id 为空。允许历史单只关联平台或完全未关联。
- 新建发货必须提供同属且有效的 platformId/shopId；后端从主数据取名称写入快照。UI 名称不能覆盖后端事实。
- 改选平台/店铺时重新生成名称快照。ID 未改变时保留原快照，即使主数据已经改名；不因保存备注而刷新名称。
- 历史名称快照一直用于列表、详情和物流内容；详情可额外标注“已停用”“历史未关联”。不把主数据 JOIN 名称替换为单据显示名称。
- 更新历史未关联单据时，如果引用没有改变，允许沿用空 ID 和旧文本，不强制为改备注补齐主数据。只要用户更换来源，必须选择完整有效的 ID 对。
- 来源字段的必填校验从通用 `shopName @NotBlank` 移到 Service 的新建/改选分支，避免在读取旧单之前就拒绝历史空店铺。来源是否变化在 trim 之前比较，未变的旧快照逐字保留；其余发货校验沿用现状。V17 老单若缺现有必填收件地址/备货人，列入迁移预演异常清单，由发货分支解决原有编辑限制；不在平台迁移中虚构资料，也不把此类旧缺陷算作已修复。
- 现有物流锁定包含 `processing / unknown / succeeded`；它们禁止修改两个 ID 和两个快照。`rejected` 沿用当前可编辑规则。
- 发货列表新增 `(platform_id, shipment_date, id)`、`(shop_id, shipment_date, id)` 索引；是否需要保留两个索引以测试库 EXPLAIN 与实际查询验证。

## 4. 权限设计

| 权限 | 范围 |
| --- | --- |
| platform:view | 菜单、路由、平台与店铺列表/详情 |
| platform:create | 新建平台和店铺；必须同时具备 platform:view |
| platform:edit | 编辑平台和店铺、启停用；必须同时具备 platform:view |
| shipping:view | 通过发货 options 接口获取使用所需的 ID、名称和归属；不授予管理接口权限 |

MVP 平台与店铺共用一组模块权限，避免权限矩阵新增两套几乎相同的模块。启停用复用 edit，不增加全局 PermissionAction 枚举。后端写端点同时要求 view 与相应动作，前端按钮同步判断。

迁移插入三条 sys_permission，并按已有风格为 SUPER_ADMIN 建立关系；解析器本身也支持超级管理员获取全部权限。其他角色不从 shipping:view 自动升级为平台管理员。在权限管理中显式分配；导航和 `/platforms` 都改为 platform:view。保留原一级菜单、图标和页头。

## 5. API 与模块边界

沿用 `ApiResponse<T>{code,message,data}` 和 `PageResponse<T>{records,page,pageSize,total}`，请求分页 `page=1,size=20`，size 范围 1～100。搜索关键词最多 200 字，转义 SQL LIKE 的 `%/_/!`，非法分页/状态/长度返回 400。

| 方法与路径 | 内容 | 权限 |
| --- | --- | --- |
| GET /api/platforms | page,size,keyword,status；返回平台分页，每项含 shopCount | platform:view |
| GET /api/platforms/{id} | 详情及版本 | platform:view |
| POST /api/platforms | `{code,name,sortOrder,remark}`；状态默认 enabled | view + create |
| PUT /api/platforms/{id} | `{name,sortOrder,remark,version}`，不修改 code/status | view + edit |
| POST /api/platforms/{id}/status | `{status,version}` | view + edit |
| GET /api/platform-shops | page,size,keyword,status,platformId；返回店铺及父平台 name/status | platform:view |
| GET /api/platform-shops/{id} | 详情及版本 | platform:view |
| POST /api/platform-shops | `{platformId,code,name,sortOrder,remark}` | view + create |
| PUT /api/platform-shops/{id} | `{name,sortOrder,remark,version}` | view + edit |
| POST /api/platform-shops/{id}/status | `{status,version}` | view + edit |
| GET /api/shipments/form-options | 新增 `platforms:[{id,name}]`、`shops:[{id,platformId,name}]`；保留 preparers | shipping:view |
| GET /api/shipments/filter-options | 返回全部平台/店铺的最小字段与有效状态，供历史筛选（包含停用项） | shipping:view |
| 现有 POST/PUT /api/shipments[/id] | form 增加 nullable platformId/shopId，其他请求结构保持 | 原 shipping 权限 |
| GET /api/shipments | 增加 platformId、shopId、unlinkedOnly；保留 platform 文本筛选 | shipping:view |

`form-options` 只返回有效对象；编辑历史单当前的不可选值从该单快照显示，不能伪装成全局可选对象。空数据返回空数组，不回退环境变量或自动生成店铺。选项不带 remark、创建人等管理字段。MVP 选项全量返回，仅适合内部少量平台/店铺；预演若发现店铺超过 1000 家，先把选项接口设计成搜索分页，再实施 UI。

`platform` 文本与 platformId 同时传入返回 400，避免两个筛选含义冲突。shopId 与 platformId 同时传入必须属于同一平台。`unlinkedOnly=true` 定义为 `shop_id is null`，包括只关联平台的历史单；此模式不接受 shopId，可与 platformId/历史平台文本配合。所有筛选与总数查询保持同样条件。

### 写入约定与错误

- `PLATFORM_NOT_FOUND` / `PLATFORM_SHOP_NOT_FOUND` → 404。
- `PLATFORM_CODE_DUPLICATE` / `PLATFORM_NAME_DUPLICATE` / `PLATFORM_SHOP_CODE_DUPLICATE` / `PLATFORM_SHOP_NAME_DUPLICATE` → 409，数据库 DuplicateKeyException 映射为业务错误。
- `PLATFORM_VERSION_CONFLICT` / `PLATFORM_SHOP_VERSION_CONFLICT` → 409，页面保留草稿并提示刷新核对。
- `PLATFORM_DISABLED` / `PLATFORM_SHOP_DISABLED` → 409，提示选项已变更，刷新选项且保留其他表单字段。
- `PLATFORM_SHOP_MISMATCH` / `VALIDATION_FAILED` → 400。
- 旧客户端新建仅提交文本：`SHIPMENT_SOURCE_SELECTION_REQUIRED` → 400，提示刷新并重新选择；不能偷偷按名称自动匹配。
- 保留既有 `SHIPMENT_ALREADY_ORDERED` / `SHIPMENT_VERSION_CONFLICT` 409 语义。

### 后端内部接口

- `PlatformCatalogService`：`listPlatforms(PlatformQuery,int,int): Page<Platform>`、`getPlatform(long): Platform`、`createPlatform(CreatePlatformCommand,String): Platform`、`updatePlatform(long,UpdatePlatformCommand,String): Platform`、`changePlatformStatus(long,String,long,String): Platform`；店铺对应 `listShops/getShop/createShop/updateShop/changeShopStatus`。
- 命令字段与上面 API 一致；查询对象包含表中筛选项。实体含第 3 节字段；列表 DTO 扩充统计/父平台显示信息，实体不依赖 API DTO。
- `ShippingSourceResolver.resolveForCreate(Long,Long): ShipmentSource`；`resolveForUpdate(ShipmentSource previous,Long platformId,Long shopId): ShipmentSource`。`ShipmentSource(Long platformId,Long shopId,String platform,String shopName)` 为 shipping/domain 值对象；首次绑定/改选验证归属和状态并取得名称，不变时返回原快照。
- 兼容适配在 ShipmentService：旧请求缺少两个 ID、且名称与原单完全一致时，以原 ID 调用 resolver；禁止把已有关联清空。旧请求改了文本则要求刷新。新请求完整 ID 不接受仅传其中一个作为改选来源。
- `PlatformCatalogRepository`/`JdbcPlatformCatalogRepository` 负责 CRUD、查询和锁读取；resolver 读取 domain 接口，shipping 不依赖平台 Controller。
- 发货更新先锁 shipment 行，再按 platform → shop 顺序锁定需要验证的主数据；主数据编辑/停用同样按 platform → shop 顺序，使用事务串行化“检查启用并保存”。普通详情/列表不加写锁。CAS 仍保留，安能请求仍在数据库事务外发送。
- JdbcShipmentRepository 的锁定 SQL 增加 `(platform_id <=> :platformId)`、`(shop_id <=> :shopId)` 比较；不能只在 Service 检查。AneOrderStore 使用已保存内容，不因关联新增而重新提交物流请求。

## 6. 页面与交互

`/platforms` 使用 `PlatformManagementView.vue`，一个页面两个页签“平台”“店铺”，用 query 保留 `tab/platformId/keyword/status/page`。平台行“查看店铺”进入店铺页签并带上平台筛选；无需新增一级菜单。

- 平台表格：编码、名称、店铺数、状态、排序、更新时间、操作。
- 店铺表格：编码、名称、所属平台、自身状态/实际可用状态、更新时间、操作。
- 筛选：关键词、启停用；店铺额外平台选择。分页默认 20；筛选变化回到第 1 页。
- 新建/编辑使用抽屉，复用 `MasterdataFormDrawer.vue` 的可用行为与现有表单样式，分别封装 PlatformFormDrawer、ShopFormDrawer；code 和店铺所属平台在编辑时只读。
- 提交防双击，成功后刷新当前列表；400/409 在抽屉显示并保留输入。取消有未保存内容时确认，保存成功再关闭。
- 加载、无数据、无搜索结果、接口错误分别显示；无权限用户不能进入页面。新建店铺若无有效平台，提示先创建/启用平台；无 create 权限时提示联系管理员。
- 停用需确认影响，启用直接提交；页面键盘可操作，抽屉焦点管理与关闭后焦点恢复沿用通用组件。
- 发货表单把平台文本、店铺名称下拉改为 ID 联动选择；切换平台清空不属于它的店铺。仅一个有效平台且其下仅一个有效店铺时自动选中；选中平台只有一个有效店铺时可自动选店。
- 已停用/未关联旧单显示历史快照，不被初始化默认值覆盖；锁定单两个选择器禁用，未锁定单可明确点击更换来源。当前引用未变不要求重新选。
- 发货列表增加平台/店铺 ID 筛选、历史未关联筛选，并保留“历史平台名称”文本过滤；新旧条件按第 5 节互斥，改名后 ID 筛选仍找得到旧快照。
- 页面 real/mock 服务签名相同；读取失败降级遵循既有工厂。Mock ID 从未代表真实库，写请求仍由真实后端校验，失败不能显示保存成功。

## 7. 配置、历史数据迁移与上线

### 7.1 不用环境变量直接推断平台

`ERP_SHIPPING_SHOP_NAMES` 只有名称，没有平台归属；不得把默认“贝贝鱼淘宝旗舰店”当成实际配置，也不得按名称含“淘宝”自动归属。平台空值、别名、同名跨平台均需预演列出，人工确认。

结构迁移只创建表、关联列、索引、约束、权限，不夹带特定公司平台数据，不从环境变量动态生成 Flyway SQL。暂定 `V21__platform_catalog.sql`，实现前检查版本是否被发货分支占用；已应用 V1–V20 不修改。

### 7.2 离线迁移工具合同

新增仅在显式 `catalog-migration` Profile 激活的独立命令入口，配合 local/test/prod 之一使用，复用数据库环境保护，`web-application-type=none`、任务结束退出。入口只装配必要 JDBC/迁移组件，不启动普通 Web 应用、管理员初始化或其他启动写入；自动 Flyway 关闭，apply 要求结构已由发布步骤安装。dry-run 对尚无新表的数据库按“目录未创建”输出候选，不写结构；使用只读事务。普通应用启动绝不导入或更新数据。工具只处理平台/店铺名称和发货 ID，不导出收件人等个人信息。

两种模式：`--catalog-migration.mode=dry-run|apply`；输入 `--catalog-migration.manifest=<绝对路径>`（apply 必填），输出 `--catalog-migration.output=<绝对路径>`。无 mode 不执行。命令模板在任务 5 中实现后写入操作手册。

`CatalogMigrationManifest` 固定字段：`schemaVersion:1`、`batchId`（最长 50 个 ASCII 字符）、`targetDatabase`、`baselineMaxShipmentId`、`platforms[{code,name,status,sortOrder}]`、`shops[{code,name,platformCode,status,sortOrder}]`、`mappings[{rawPlatform,rawShopName,platformCode,shopCode|null}]`、`shipmentBaselines[{id,version,rawPlatform,rawShopName,platformId,shopId}]`。shipmentBaselines 由 dry-run 生成，记录每个拟更新单据的预期版本和原引用；apply 逐行核对。迁移仅处理清单中的 ID 且不超过 baselineMaxShipmentId；新增记录下次重新预演，不扩大已确认批次范围。raw 字段保留原值，不在回填时改名称。

### 7.3 执行顺序

1. **盘点（dry-run）**：读取实际部署配置的去空白店铺名、历史不同 `(platform,shop_name)`、数量、当前未关联数量、最大 ID 与版本。输出候选映射和冲突报告；不会建店、改单或启停用。
2. **确认主数据**：操作者按真实归属填写稳定编码与 mappings。同名跨平台分别建店；明确别名可映射到同一个 code；仅平台确定而店铺未知时可只关联平台；完全未知保持空 ID。配置存在但无历史数据的店铺也必须显式选平台。
3. **启用新结构、暂停发货写入**：先完成备份，在短维护窗口执行结构迁移和数据导入；禁止旧新实例同时写入。历史数据规模决定分批窗口，当前未知，不承诺零停机。
4. **apply**：按 code 幂等创建/复用主数据；已存在 code 的名称/归属与清单不符立即报告冲突，不覆盖。映射存在歧义时不 apply。分批锁 shipment 行，核对原文本、原关联和 version 后补充 ID，version+1、updated_by 标记 `migration:<batchId>`。已关联到同一目标记 skipped，关联其他目标记 conflict；绝不覆盖。记录每批前后 ID/版本及结果，支持重新预演和续跑。
5. **核对**：总单数不变；所有单据 platform/shop_name/status/tracking_no、全部物流 request_snapshot 不变；应关联+未关联+冲突数与盘点一致；冲突必须处理或有明确保留说明。已下单记录只补关联元数据，不能变更物流快照；不走普通编辑 API 绕过其锁。
6. **切换应用**：后端从数据库提供 options，前端使用 ID；实际运营店铺都已确认且至少有一个有效选项才开放新建发货。生产无需为了历史空白而虚构“其他平台”。
7. **移除配置依赖**：不再在正常启动读取 `ERP_SHIPPING_SHOP_NAMES`，只允许迁移工具显式读取；`.env.example`、`application.yml`、ShippingProperties/OptionsService 与配置说明同步清理正常运行依赖。迁移工具保留一个发布周期便于补录；后续单独删除。

### 7.4 兼容与回退

- HTTP 新增字段保持可空，旧读客户端仍可显示快照；过渡期 form-options 可保留由数据库有效店铺派生的 `shopNames`，标记弃用，不再读环境变量。
- 旧客户端新建缺 ID 明确拒绝并要求刷新；旧更新仅允许原来源未变的兼容处理。不能按同名店铺静默关联。前后端在同一维护窗口发布。
- 出现关联问题时优先停写并恢复上一版应用/备份，保留扩展表结构，不直接 DROP 迁移或运行 Flyway clean。若只回退引用，须根据备份/批次前镜像及当前版本确认没有后续业务修改，再单独修复；不可全表清空 ID。
- 旧应用恢复后可能继续写出空 ID，恢复新版本前必须再次 dry-run 并核对增量。名称快照始终存在使历史读取可兼容，但不等于所有旧版本写入均安全。
- 结构迁移 DDL 与数据导入不是一个原子事务；MySQL DDL 隐式提交。部分失败时先检查 Flyway schema history 与实际结构，再按发布手册恢复，不能盲目重跑/repair。
- V20 的历史 completed 转换不能从当前状态猜回原状态。先由发货分支确认全新库/升级库路径；本模块验收只要求平台迁移不再次改变任何备货/物流状态。

## 8. 文件落点

后端前缀 `backend/src/main/java/com/bebefish/erp/`，对应测试前缀 `backend/src/test/java/com/bebefish/erp/`。

- 新增 `platform/domain/{Platform,PlatformShop,PlatformQuery,PlatformShopQuery,PlatformCatalogRepository}.java`：实体、查询、仓储边界。
- 新增 `platform/application/{PlatformCatalogService,ShippingSourceResolver,CreatePlatformCommand,UpdatePlatformCommand,CreateShopCommand,UpdateShopCommand}.java`：规则、命令与发货引用解析。
- 新增 `platform/infrastructure/JdbcPlatformCatalogRepository.java`，`platform/api/{PlatformController,PlatformShopController,PlatformDtos}.java`。
- 新增 `platform/migration/{CatalogMigrationApplication,CatalogMigrationRunner,CatalogMigrationService,CatalogMigrationManifest}.java`；仅显式运行，独立入口不进入普通应用组件扫描。
- 新增 `shipping/domain/ShipmentSource.java`；修改现有 `ShipmentFormInput.java`、`ShipmentContent.java`、`ShipmentQuery.java`、`ShipmentRepository.java`、`ShipmentEditPolicy.java`、`application/ShipmentService.java`、`ShippingFormOptionsService.java`、`api/ShippingFormOptions.java`、`ShipmentController.java`、`infrastructure/JdbcShipmentRepository.java`。`SaveShipmentRequest.java` 的 `{form,status,version}` 外层保持；相邻物流快照反序列化兼容性随测试验证。
- 修改 `ShippingProperties.java` 与 `backend/src/main/resources/application.yml` 正常运行配置；新增 `db/migration/V21__platform_catalog.sql`（版本为暂定）。
- 前端新增 `frontend/src/features/platform/{types.ts,platformService.ts,httpPlatformService.ts,mockPlatformService.ts,PlatformManagementView.vue,PlatformFormDrawer.vue,ShopFormDrawer.vue}`。
- 修改前端 `features/shipping/{types.ts,httpShippingService.ts,mockShippingService.ts,ShipmentFormView.vue,ShipmentListView.vue,ShipmentDetailView.vue}`、`router/index.ts`、`features/navigation/mockNavigationService.ts`、`features/permission/permissionCatalog.ts`。SidebarNav 与 ErpLayout 已有入口，不重复搭壳，只按必要测试调整。
- 文档更新 `docs/shipping-ane-setup.md`、`.env.example`、`README.md`；新增 `docs/platform-management-rollout.md`。

## 9. 分阶段执行任务

建议串行推进核心合同；每个任务交付后再进入依赖任务。所有代码步骤都是未来待执行项。本轮不生成这些产品文件。

### 任务 1：固定基线与数据库结构（约 0.5～1 天）

**文件：** 新迁移 SQL；`common/persistence/FlywayMigrationTest.java`；新增 `platform/infrastructure/PlatformSchemaIntegrationTest.java`。

**接口：** 产出第 3 节表、索引、外键、三条权限；后续所有任务消费它们。

- [ ] 核对发货分支最新差异与已完成的状态拆分验收结果，记录基线提交；不 stash/reset/提交别的工作。后续需隔离时从已确认包含入口和状态拆分的本地提交创建 `codex/platform-management`，不默认从 main 开始，不合并 main。
- [ ] 编写失败测试 `createsPlatformCatalogWithNullableShipmentReferences`：两张表/两列存在，老发货记录 null 关联可保留，三条新权限齐全。
- [ ] 编写 `rejectsInvalidStatusDuplicateNamesAndCrossPlatformShop`：同平台重名拒绝、跨平台同名允许、非法状态与交叉引用拒绝，排序范围和非负版本受约束。
- [ ] 在安全测试库执行 `mvn '-Dtest=FlywayMigrationTest,PlatformSchemaIntegrationTest' test`，确认因待实现结构而失败；再新增迁移与调整全表/版本断言，运行至通过。
- [ ] 增加从 target 20 带历史 shipment 的升级测试，验证所有历史业务字段原样保留。V20 自身行为单独作为发货基线记录。
- [ ] 交付可独立审查的结构 diff；按后续用户授权进行本地提交，不推送。

### 任务 2：平台/店铺管理服务和 API（约 1～1.5 天）

**文件：** 第 8 节 platform 的 domain/application（暂不含 resolver）/infrastructure/api；新增 `platform/application/PlatformCatalogServiceTest.java`、`platform/api/PlatformControllerTest.java`、`PlatformShopControllerTest.java`、`platform/infrastructure/PlatformCatalogConcurrencyTest.java`。

**接口：** 消费任务 1 表；产出第 5 节管理 API 以及仓储查询/锁读取能力。

- [ ] 测试 `normalizesCodesAndNames`、`allowsSameShopNameAcrossPlatforms`、`doesNotMoveShopOnUpdate`、`rejectsStaleVersion`、`disablingPlatformPreservesShopStatus`，断言第 3/5 节精确字段、状态和错误码。
- [ ] Controller 测试 401、无 view 的 403、只有 view 的写入 403、view+create/edit 成功；确认请求不能伪造审计操作者。
- [ ] 先运行 `mvn '-Dtest=PlatformCatalogServiceTest,PlatformControllerTest,PlatformShopControllerTest,PlatformCatalogConcurrencyTest' test` 观察预期失败，再实现并运行通过。
- [ ] 并发测试证明两次同名创建只有一次成功且另一次返回业务冲突；两个同版本编辑仅一次成功；父平台停用与店铺启用不会产生违背第 3.3 节规则的提交。
- [ ] 交付接口样例、错误码及事务验证记录。

### 任务 3：发货引用、历史兼容与物流锁定（约 1～1.5 天）

**文件：** ShippingSourceResolver、ShipmentSource、全部列明的 shipping 后端文件；新增 `platform/application/ShippingSourceResolverTest.java`、`shipping/infrastructure/ShipmentSourceIntegrationTest.java`；修改 `shipping/application/{ShipmentServiceTest,ShippingFormOptionsServiceTest}.java`、`shipping/api/ShipmentControllerTest.java`、`shipping/domain/ShipmentEditPolicyTest.java`、`shipping/logistics/{AneOrderStoreTest,AneOrderServiceTest}.java`。

**接口：** 消费任务 2 仓储；产出 ID+快照发货合同、form/filter options 和 ID/历史筛选。

- [ ] 先增加失败用例：`createUsesCatalogSnapshot`、`rejectsDisabledOrMismatchedSource`、`renameDoesNotRewriteExistingSnapshot`、`legacyUpdatePreservesSource`、`legacyCreateRequiresSourceIds`；分别断言有效写入、400/409、旧文本不变、原 ID 不丢失。
- [ ] 增加 `orderedShipmentRejectsSourceIdChangeEvenWhenNamesMatch` 与 `orderedShipmentAllowsRemarkOnlyAfterDisable`，覆盖 processing/unknown/succeeded/rejected，包含仓储 SQL 直接验证。
- [ ] 实现第 5 节 resolver 签名、请求兼容、nullable ID 映射、快照保存及查询；迁移旧 Java record 构造调用，避免用默认 0 替代 null。
- [ ] 增加 MySQL 并发用例 `disableAndCreateSerialize`、`orderAndSourceEditCannotBypassLock`；接受先完成的一方，后完成的一方必须重验或冲突，不能两边基于旧状态放行。
- [ ] 执行 `mvn '-Dtest=ShippingSourceResolverTest,ShipmentSourceIntegrationTest,ShipmentServiceTest,ShippingFormOptionsServiceTest,ShipmentControllerTest,ShipmentEditPolicyTest,AneOrderStoreTest,AneOrderServiceTest,ShippingStatusIndependenceIntegrationTest' test` 并通过；物流远程接口使用已有测试替身，不真实下单。
- [ ] 验证具备 shipping:view、无 platform:view 的请求能取得最小 options，而管理接口仍 403；停用数据可供历史筛选，不出现在新建选项中。

### 任务 4：前端管理页、权限和发货联动（约 1.5～2 天）

**文件：** 第 8 节前端文件；新增 `features/platform/{PlatformManagementView.test.ts,httpPlatformService.test.ts,mockPlatformService.test.ts}`；修改现有发货页面测试、`components/navigation/SidebarNav.test.ts`、`router/index.test.ts`、`features/permission/permissionRules.test.ts`。

**接口：** 消费任务 2/3 API；新增 PlatformService 方法 `listPlatforms/getPlatform/createPlatform/updatePlatform/changePlatformStatus/listShops/getShop/createShop/updateShop/changeShopStatus`，参数/返回值与第 5 节一致；ShippingService 新增 `getFilterOptions()` 并扩展已有 types。其返回类型 `ShippingFilterOptions` 含 `platforms:[{id,name,status}]` 与 `shops:[{id,platformId,name,status,platformStatus}]`。

- [ ] 先写权限、平台/店铺页签筛选、抽屉保存错误保留草稿、停用确认测试；实现页面与真实/Mock 服务直至通过。
- [ ] 增加前端权限目录 `{key:'platform', supportedActions:['view','create','edit']}`；菜单/路由变更及只读用户按钮行为测试通过。
- [ ] 编写发货测试：平台切换清空异平台店铺、唯一有效项默认值、历史无关联不被自动覆盖、停用旧引用保留、改名不刷新快照、锁定字段不可编辑。
- [ ] 增加 ID 筛选与历史文本互斥、停用对象筛选、无候选项时禁止新建、接口 403/409 不 Mock 成功的测试；实现联动。
- [ ] 为 `getFilterOptions()`（HTTP 路径 filter-options）验证工厂的读取降级和权限错误不降级；该命名符合当前 serviceFactory 白名单，无需修改全局策略。
- [ ] 在 frontend 执行 `npm run test:run -- src/features/platform src/features/shipping src/features/permission src/components/navigation/SidebarNav.test.ts src/router/index.test.ts`，应全部通过；人工检查桌面/窄屏、键盘焦点、空/错/加载状态。

### 任务 5：迁移预演、回填工具与操作手册（约 1～1.5 天）

**文件：** 第 8 节 migration 文件；新增 `platform/migration/CatalogMigrationServiceTest.java`；第 8 节文档/配置更新。

**接口：** 消费任务 1 表和任务 2 主数据规范；产出第 7 节 manifest、dry-run/apply 和逐批 JSONL 结果（含旧/新 ID、预期/结果版本、skipped/conflict、批次标识）。

- [ ] `dryRunNeverWrites`：配置与历史组合输出完整；数据库零改动。`ambiguousNameNeedsExplicitMapping`：跨平台同名、空平台、全半角/别名均不自动合并。
- [ ] `repeatedApplyIsIdempotent`：相同清单再次执行无重复店铺、无重复版本递增。`concurrentShipmentEditIsReportedAsConflict`：版本/原文变化不能覆盖。
- [ ] `backfillPreservesBusinessAndLogisticsSnapshots`：包括已下单单据，除关联/版本/修改审计字段外逐字段比较不变；只关联平台、完全未关联均有计数。
- [ ] 实现 dry-run/apply；每批准备前镜像文件成功后才允许事务写入，提交后输出结果。崩溃恢复以数据库当前目标 ID 与清单比较，不把缺少最后一行日志当成未写入；出现不一致重新预演，不盲目重放。
- [ ] `mvn '-Dtest=CatalogMigrationServiceTest' test` 通过；在脱敏测试副本上完成预演→apply→重复 apply→核对流程，记录耗时与批量大小，再估维护窗口。
- [ ] 更新运行文档、环境变量弃用说明和回退步骤；用户确认的实际映射及生产执行结果不提交公共仓库。

### 任务 6：回归与可交付验收（约 0.5～1 天）

**文件：** 必要的回归测试与 `docs/platform-management-rollout.md` 验收记录；不做无关模块重构。

**接口：** 消费任务 1～5 的完整功能，产出可评审的本地变更和测试证据。

- [ ] 后端在安全测试库 `mvn test` 全部通过；记录未运行/环境缺失项，不把测试替身结果描述成真实安能联调成功。
- [ ] frontend 执行 `npm run test:run`；设置 `$env:VITE_RUNTIME_ENV='test'`、`$env:VITE_DATA_SOURCE='real'` 后执行 `npm run build`，测试与类型/构建均通过。
- [ ] 依据下面验收表，以不同权限账号在真实本地 API 上人工验证；不在真实物流账号发单。
- [ ] 与发货分支最终基线比较，确认无回退状态拆分、测试运单隔离、下单幂等与备货字段锁；审查最新 Flyway 版本不冲突。
- [ ] 提供变更清单、迁移预演统计、待处理映射与测试结果。远程推送、合并和生产迁移仅在后续明确指令下执行。

## 10. 验收标准

| 场景 | 通过标准 |
| --- | --- |
| 菜单与授权 | 平台入口独立权限；发货普通用户可用选项但不能管理；直接调用 API 同样 403 |
| 平台/店铺维护 | 新建、编辑、搜索分页、启停用闭环；空白/超长/重复/旧版本均给出对应错误，输入不丢失 |
| 数据归属 | 同平台重名被拒；跨平台同名可存；提交不匹配 ID 必须拒绝；不支持隐式迁店 |
| 状态 | 父平台停用后全部子店不可新选；再启用不复活自身 disabled 的店铺；原单业务按约定继续 |
| 新发货 | 必须有效 ID 对；服务端生成正确快照；旧纯文本新建明确提示刷新 |
| 历史发货 | 名称快照不因改名/停用/回填改变；未关联单可阅读和维护原本允许字段 |
| 并发/物流 | 停用与保存、下单与编辑、重复保存/状态操作测试通过；ID 不能绕过下单锁，物流不被重复提交 |
| 筛选 | 按稳定 ID 找到改名前历史快照；历史未关联仍可按原文本检索；停用对象可检索 |
| 迁移 | dry-run 零写；apply 幂等；歧义不猜；单数/业务字段/物流请求快照不变，冲突与未关联数量可解释 |
| 数据源与回归 | 写失败不显示 Mock 成功；前后端测试、构建通过；原发货状态拆分与测试/正式物流隔离保留 |

## 11. 用户需要决定的业务问题

当前无需为完成规划逐条确认；以下带着推荐值进入评审，真正实施对应阶段前再落实。

| 问题 | 推荐方案 | 决定时点 |
| --- | --- | --- |
| “平台管理”是否只是平台/店铺资料，还是包含电商授权与拉单？ | 本期只做主数据与发货接入，连接能力另立项目 | 实施前确认 MVP 范围 |
| 实际有哪些平台和店铺，各自归属、别名是什么？淘宝/天猫是否分开？ | 按运营实际分别建平台；同一渠道的多店放同平台；不能从配置名称猜 | 迁移前必须提供并确认清单 |
| 线下、补发、无平台业务如何填写？ | 确有业务则建明确“线下”等平台及实际业务店铺；新单必须选择，旧空值保留 | 表单实施前确认必填策略 |
| 谁能维护平台店铺，是否需要不同店铺人员隔离？ | 少量运营管理员获 platform 权限；发货人员仅使用公共选项；不做店铺行权限 | 权限实施前；若需要隔离须重设计 |
| 停用是否应阻止此前创建但尚未下单的发货单继续物流下单？ | 仅禁止新增/改选引用，允许旧单继续处理 | 业务规则实施前确认 |
| 无法归属的历史单据是否必须全部人工补齐？ | 可保留“历史未关联”，不影响旧单维护；只回填确定匹配项 | 迁移核对时确认遗留清单 |

## 12. 实施顺序与工作量

顺序：**发货基线确认 → 结构/权限 → 管理 API → 发货后端兼容 → 管理页与发货联动 → 迁移演练 → 全量回归 → 等待明确上线指令**。

单人熟悉项目的开发估算约 **6～8.5 个工作日**，实际历史数据清洗、业务决策等待、现有发货分支收尾与生产联调不计在内。任务 5 的只读盘点可在业务映射确认前开展，但正式 apply 必须在已确认清单与部署窗口内执行。

规划自检：已覆盖产品、数据、状态、权限、API、页面、配置/历史迁移、并发、回退、测试和实施顺序。所有代码与测试步骤均未执行；本文件是本轮唯一新增交付物。
