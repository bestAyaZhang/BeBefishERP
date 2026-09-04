# BeBeFish ERP 仓库空间与库位库存设计规格

日期：2026-09-04

## 1. 背景与设计类型

本项目将现有的“仓库 + SKU 总量”库存模型升级为可视化的库内空间与库位库存子系统。它会改变库存交易的权威粒度、影响销售出库和库存调整的接口，并新增 2D 布局、3D 浏览、移库和盘点流程，因此属于架构级变更。

现有基线：

- 后端为 Spring Boot + MySQL 模块化单体。
- 库存核心现有 `inventory_balance` 和 `inventory_ledger`，权威粒度为 warehouse + SKU。
- 销售确认、库存调整和撤销通过 `InventoryService` 改变库存。
- 前端为 Vue 桌面端 ERP，已有库存余额、库存流水和库存调整页。
- Figma 已有 `01 Foundations`、`02 Components`、`03 Master Data` 及仓库列表 / 抽屉，未有库存空间页。

## 2. 目标

1. 在桌面端中用可度量的 2D 画布规划仓库边界、固定堆位区、自由堆放区、通道和障碍物。
2. 支持以箱子落地堆码为主的仓库，不以货架、层、格为核心概念。
3. 将库位库存设为权威数据，库存总量、空间地图和 3D 场景都从它派生。
4. 支持包装层级、垂直箱堆、库位混放、容量校验和风险可视化。
5. 支持入库、系统推荐出库、原子移库、按库位冻结的盘点、库位库存查询与完整流水追溯。
6. 在不丢失现有库存、不要求一次性完成全部仓库布局的前提下逐仓迁移。
7. 以单仓 2,000 个库位、10,000 个同时活跃箱堆为设计目标。

## 3. 非目标

本次不包含：

- 移动端、专用扫码器或 PDA 作业端。
- CAD 文件自动解析和底图标定导入。
- 货架、层和格口型仓储模型。
- 跨仓调拨和在途库存。本次的“移库”仅指同一仓库内的库位间移动。
- 人工编辑每一个箱子坐标的精细 3D 建模。
- 机器人、AGV 或叉车路径规划。
- 新的批次、序列号或有效期子系统。模型可在未来增加批次维度，但本次不提供批次主数据和 FEFO 界面。

## 4. 已确认的业务决策

- 库位库存是权威来源，仓库总余额是库位余额的汇总投影。
- 同一库位默认允许多个 SKU，也可配置为单 SKU 专用库位。
- 一个垂直箱堆只允许一个 SKU 和一种包装层级；多 SKU 在同一库位内通过多个并排箱堆实现。
- 库存始终以 SKU 基础单位记账；入库放置时额外选择单品、内盒、整箱或托盘等存储包装层级。
- 仓库可同时包含固定地堆位区和自由堆放区。
- 自由堆放区在首次放货时创建可追溯的临时库位。临时库位清空后归档，不物理删除，不复用其编号。
- 3D 场景根据包装尺寸、箱子方向、每层行列数、层数和数量自动生成，可人工校正规则或实际高度，不逐箱录入。
- 出库先由系统推荐来源箱堆，操作员可调整，确认后才记账。默认优先清空零散箱堆，再按最早入库时间分配。
- 盘点创建账面快照，仅冻结盘点范围内的库位。初盘默认盲盘，有差异时复盘，批准后生成调整流水并解冻。
- 采用关系型状态表 + 不可变流水。不使用全量事件溯源，也不把 2D / 3D 场景作为库存数据库。

## 5. 领域架构

### 5.1 模块边界

#### Warehouse Spatial

负责：

- 仓库边界、区域、通道、障碍物和固定库位的几何模型。
- 草稿与发布布局版本。
- 固定和临时库位的生命周期。
- 几何边界、重叠、越界和通道侵占校验。

不负责库存数量变更。

#### Inventory Core

负责：

- 垂直箱堆和库位余额。
- 入库、出库、移库、盘点调整命令。
- 余额锁定、非负校验、幂等、并发控制和不可变流水。
- 仓库级汇总余额的事务内投影。

Inventory Core 通过 Warehouse Spatial 提供的稳定库位 ID 和发布布局查询进行容量、区域和冻结校验。

#### Spatial Read Model

负责：

- 合并已发布布局、活跃临时库位、箱堆余额和风险状态。
- 为 2D 运营地图、3D 浏览、搜索和库位详情提供专用 DTO。
- 根据包装和堆码规则生成 3D 渲染参数，但不保存第二份库存数据。

### 5.2 核心层级

`Warehouse -> LayoutVersion -> Zone -> StorageLocation -> InventoryStack -> InventoryLocationBalance`

- Warehouse 是物理仓库。
- LayoutVersion 是仓库几何的草稿或已发布快照。
- Zone 是稳定业务区域，存储模式为 `FIXED_LOCATION` 或 `FLEXIBLE_STACKING`。
- StorageLocation 是稳定可追溯的地面存放位置，类型为 `FIXED`、`TEMPORARY` 或系统用 `UNALLOCATED`。
- InventoryStack 是一个垂直箱堆，只绑定一个 SKU 和一种包装层级。
- InventoryLocationBalance 是位于具体库位和箱堆内的权威数量。

## 6. 空间与布局模型

### 6.1 坐标系

- 仓库平面图使用右手坐标系，原点在画布左上角，X 轴向右，Y 轴向下，Z 轴为高度。
- 存储坐标和尺寸统一使用厘米，与现有 SKU 物理尺寸字段一致；界面可按米显示大尺寸。
- 区域可为多边形；固定堆位默认为带旋转角的矩形；通道和障碍物为独立图形。
- 几何记录包含边界框字段，便于在 2,000 库位规模下使用普通索引完成视口粗筛。

### 6.2 布局版本

- 每仓最多有一个活跃草稿和一个当前发布版。
- 日常作业和空间读模型只引用已发布版。
- 发布时验证仓库边界、区域重叠、堆位越界、通道侵占、业务引用和库存安全。
- 含库存的固定库位不得在草稿中删除、更换区域或改变为不可用空间。需先完成移库并清空。
- 发布后保留不可变快照、发布人、发布时间和校验结果。

### 6.3 固定堆位与自由堆放

- 固定堆位有人类可读编号、占地长宽、最大堆高、承重、SKU 数量上限、单 SKU 专用开关和禁混规则。
- 自由堆放区允许在已发布区域内新建临时库位。创建时必须保存编号、占地边界、坐标、最大堆高和当前布局版本。
- 临时库位不能越出自由堆放区、侵占通道、障碍物或其他活跃库位。
- 已有库存的临时库位不允许直接修改几何位置。实物改变位置时必须执行移库；纯测绘纠错需要独立管理权限、原因和审计日志。
- 临时库位清空后自动进入 `ARCHIVED`；历史流水继续可查。
- `UNALLOCATED` 库位没有几何坐标，不出现在 2D / 3D 地图中，但必须在库存查询和迁移待办中显示。

## 7. 包装、箱堆与容量

### 7.1 包装层级

每个 SKU 可有多个存储包装单位：

- `EACH`：单品或 SKU 基础单位。
- `INNER_BOX`：内盒。
- `CARTON`：整箱。
- `PALLET`：托盘，仅在该 SKU 实际使用时配置。
- `CUSTOM`：其他业务包装。

包装单位包含对基础单位换算数、长宽高、毛重 / 净重、是否允许旋转和默认摆放方向。现有外包装、内盒、产品尺寸和装箱数可作为首次回填来源，回填后需通过数据完整性检查。

库存流水仍以基础单位数量保存。用户输入包装数时：

`base_quantity = package_count * conversion_quantity`

当基础单位余额不能被当前包装换算数整除时，最后一个包装按已拆包处理，容量占用使用向上取整的包装数，避免低估空间。

### 7.2 垂直箱堆

每个箱堆保存：

- 库位、SKU 和包装单位。
- 堆内相对坐标、箱子摆放方向、每层行数与列数。
- 计算层数、实际高度可选覆盖值、首次入库时间和生命周期状态。

系统根据包装数和每层容量计算层数。同一库位内的多个箱堆必须在地面平面中不重叠。

### 7.3 容量和风险

容量校验依次考虑：

1. 箱堆占地是否越出库位。
2. 箱堆之间是否平面重叠。
3. 计算高度或实际高度是否超过最大堆高。
4. 计算重量是否超过库位承重。
5. SKU 数量上限、单 SKU 专用和禁混规则是否满足。

对每个可计算的物理库位，系统同时计算：

- `floor_area_ratio = 全部箱堆占地面积之和 / 库位可用面积`。
- `height_ratio = 库位内最高箱堆高度 / 库位最大堆高`。
- `weight_ratio = 库位内总重量 / 库位最大承重`。
- `capacity_risk_ratio = max(floor_area_ratio, height_ratio, weight_ratio)`。未配置的限制不参与 max，但界面需标识该维度未受管理。

风险色和预警阈值使用 `capacity_risk_ratio`，同时在库位详情中分开展示面积、高度和重量比率，避免一个总数掩盖真实原因。

容量预警默认为 70% 和 90% 两档，可按仓库或区域调整。达到预警阈值允许继续但需二次确认；超过物理上限则禁止记账。

没有完整尺寸或换算数的包装单位可保留在 `UNALLOCATED` 库位，但不能新分配到启用容量管理的物理库位；界面需给出“补全包装数据”入口。

## 8. 库存状态与流水

### 8.1 权威余额

`inventory_location_balance` 每行对应一个库位内的具体垂直箱堆，包含 warehouse_id、location_id、stack_id、sku_id、packaging_unit_id、base_quantity 和 version。

约束：

- stack_id 唯一，且箱堆、库位、SKU 和包装层级必须一致。
- base_quantity 不得小于零。
- 库位总量是其所有箱堆余额之和。
- 仓库总量 `inventory_balance` 是所有库位余额的事务内汇总投影，用于兼容现有销售和报表查询。
- 当仓库切换为空间库存模式后，不允许直接修改 `inventory_balance`；必须通过库位库存命令更新。

### 8.2 不可变流水

现有 `inventory_ledger` 继续作为唯一库存审计流水，新增：

- operation_type：`INBOUND`、`OUTBOUND`、`MOVE_OUT`、`MOVE_IN`、`STOCKTAKE_ADJUST`、`REVERSAL`。
- source_location_id / source_stack_id。
- target_location_id / target_stack_id。
- packaging_unit_id、package_count 和 base_quantity。
- location_before_quantity / location_after_quantity。
- warehouse_before_quantity / warehouse_after_quantity。
- movement_id：关联移库的成对流水。
- idempotency_key 和原始业务单据标识。

流水不物理删除。撤销通过反向命令和新的 `REVERSAL` 流水完成。

## 9. 业务流程

### 9.1 入库与开账

1. 用户选择 SKU、包装层级和数量。
2. 系统基于容量、单 SKU 专用、SKU 数上限和禁混规则推荐目标库位。
3. 用户可选择现有兼容箱堆、新建箱堆，或在自由堆放区创建临时库位。
4. 前端预览 2D 占地、3D 堆码和风险。
5. 用户确认后，后端重新校验布局版本、库位状态和容量，然后在一个事务内增加余额、更新仓库汇总并写入流水。

同 SKU、同包装层级和同堆码规则可追加到现有箱堆，否则建立新箱堆。

### 9.2 出库与领料

1. 业务单据提供 warehouse + SKU + base_quantity 需求。
2. 系统过滤冻结、归档或不可用箱堆，先选可被整体清空的零散箱堆，再按 first_stocked_at 升序选取。
3. 前端显示推荐的一个或多个来源箱堆，用户可替换或调整分配数量。
4. 确认时锁定相关余额，重新检查数量和 version，完成减量与流水。
5. 箱堆余额变为零时关闭箱堆；临时库位的所有箱堆都清空时归档库位。

现有销售确认流程在仓库启用空间库存后，必须在最终确认前展示出库分配交互；不得继续仅按 warehouse + SKU 直接扣减。

### 9.3 移库

- 移库可选择整个箱堆或部分数量。
- 目标可为现有兼容箱堆、同一库位的新箱堆、其他固定库位或新临时库位。
- 源和目标余额按库位 ID 和箱堆 ID 稳定排序后加锁，避免反向移库死锁。
- 源减量、目标增量、仓库投影校验和成对 `MOVE_OUT` / `MOVE_IN` 流水在同一事务中完成。
- 同仓移库不改变仓库总余额。

### 9.4 盘点

盘点任务状态：`DRAFT -> COUNTING -> REVIEW -> COMPLETED`，可从 DRAFT 或 COUNTING 取消为 `CANCELLED`。

1. 用户按区域或若干固定 / 临时库位选择范围。
2. 开始盘点时生成箱堆级账面快照并冻结范围内库位。其他库位继续正常作业。
3. 初盘人员不看账面数，按箱堆和包装层级录入实盘数。
4. 无差异行直接通过；有差异行进入复核，显示账面、初盘和复盘数。
5. 有权限的人批准后，在一个事务内写入所有 `STOCKTAKE_ADJUST` 流水、更新余额并解冻。
6. 取消任务仅解冻，不写库存变动流水。

## 10. 事务、并发和错误处理

所有库存命令使用统一流程：

1. 验证请求、业务单据和幂等键。
2. 验证当前布局版本、库位生命周期、冻结和容量。
3. 按稳定 ID 顺序锁定涉及的库位 / 箱堆余额。
4. 使用 version 防止丢失更新。
5. 更新库位余额、仓库汇总、箱堆生命周期和流水。
6. 全部成功后提交；任一失败则整体回滚。

错误语义：

- 库存不足、库位冻结、库位归档、超过物理上限和布局版本过期为可解释的业务错误，不写部分流水。
- 容量达预警阈值为可覆盖警告，需用户二次确认并写审计日志。
- 并发 version 冲突返回最新箱堆和余额状态，前端不自动重放人工选择，而是要求重新确认。
- 重复的 idempotency_key 返回原命令结果，不重复记账。

## 11. 数据模型

### 11.1 空间与位置

#### warehouse_layout_version

- id、warehouse_id、version_no、status、coordinate_unit。
- boundary_geometry、created_by、created_at、published_by、published_at。
- checksum 和发布校验摘要。
- 每仓当前发布版唯一，活跃草稿唯一。

#### warehouse_zone

- id、warehouse_id、code、name、storage_mode、status。
- max_height_cm、max_weight_kg、sku_limit、single_sku_only。
- warning_threshold_low、warning_threshold_high、mixing_policy_json。
- code 在仓库内唯一。

#### warehouse_layout_shape

- id、layout_version_id、shape_type、business_ref_type、business_ref_id。
- geometry_json、x_min、y_min、x_max、y_max、rotation_deg、z_index。
- shape_type 包含 `ZONE`、`AISLE`、`OBSTACLE`、`FIXED_LOCATION`。
- layout_version_id + business_ref_type + business_ref_id 对业务对象唯一。

#### storage_location

- id、warehouse_id、zone_id、code、location_type、status。
- max_height_cm、max_weight_kg、sku_limit、single_sku_only、mixing_policy_json。
- temporary_geometry_json、based_on_layout_version_id、created_at、archived_at。
- code 在仓库内永久唯一，归档编号不复用。

### 11.2 包装与箱堆

#### sku_packaging_unit

- id、sku_id、unit_type、name、conversion_quantity。
- length_cm、width_cm、height_cm、gross_weight_kg、net_weight_kg。
- allow_rotation、default_orientation、enabled。
- sku_id + unit_type + name 唯一。

#### inventory_stack

- id、warehouse_id、location_id、sku_id、packaging_unit_id、status。
- offset_x_cm、offset_y_cm、orientation、rows_per_layer、columns_per_layer。
- computed_layers、actual_height_cm、first_stocked_at、closed_at。computed_layers 是可重算缓存，库存数量变动时在同一事务内刷新。

### 11.3 余额、流水和业务单据

- `inventory_location_balance`：箱堆级权威余额，stack_id 唯一，带 version。
- `inventory_balance`：现有仓库 + SKU 汇总投影，继续保留唯一约束和 version。
- `inventory_ledger`：扩展现有表，增加库位、箱堆、包装、movement_id 和幂等字段。
- `stock_movement` / `stock_movement_line`：保存可见移库单据、状态和用户确认结果。
- `stocktake_task` / `stocktake_scope` / `stocktake_line`：保存范围、快照、初盘、复盘、差异、批准人和调整流水关联。

关键索引：

- inventory_location_balance(warehouse_id, location_id, sku_id)
- inventory_location_balance(warehouse_id, sku_id)
- inventory_stack(location_id, status)
- inventory_ledger(warehouse_id, sku_id, occurred_at)
- inventory_ledger(source_location_id, occurred_at)
- inventory_ledger(target_location_id, occurred_at)
- storage_location(warehouse_id, zone_id, status)
- warehouse_layout_shape(layout_version_id, x_min, x_max, y_min, y_max)

## 12. API 边界

下列路径为建议资源语义，实施时遵循现有 Controller 和版本规则。

### 12.1 布局

- `GET /api/warehouses/{warehouseId}/layout/current`：读取当前发布布局。
- `POST /api/warehouses/{warehouseId}/layout/drafts`：创建或复制草稿。
- `PUT /api/warehouses/{warehouseId}/layout/drafts/{draftId}`：幂等保存草稿。
- `POST /api/warehouses/{warehouseId}/layout/drafts/{draftId}/validate`：返回发布校验结果。
- `POST /api/warehouses/{warehouseId}/layout/drafts/{draftId}/publish`：发布新版本。

### 12.2 空间读模型

- `GET /api/warehouses/{warehouseId}/spatial-scene`：按 zoneIds 或 viewport 返回 2D / 3D 所需布局、库位、箱堆和风险。
- `GET /api/inventory/location-balances`：按仓库、区域、库位、SKU 和状态分页查询。
- `GET /api/inventory/locations/{locationId}`：返回库位、箱堆、余额、容量和近期流水。
- `GET /api/inventory/ledgers`：扩展现有流水查询的库位和移库条件。

### 12.3 库存命令

- `POST /api/inventory/inbound/recommendations`：预览可放置目标和风险，不记账。
- `POST /api/inventory/outbound/recommendations`：生成可编辑的来源箱堆分配，不记账。
- `POST /api/inventory/movements`：幂等提交入库、出库或移库。
- `POST /api/inventory/stocktakes`：创建盘点任务。
- `POST /api/inventory/stocktakes/{id}/start`：快照并冻结范围。
- `PUT /api/inventory/stocktakes/{id}/counts`：幂等保存初盘或复盘数。
- `POST /api/inventory/stocktakes/{id}/approve`：批准差异、记账并解冻。
- `POST /api/inventory/stocktakes/{id}/cancel`：取消并解冻，不改库存。

所有记账命令必须包含 idempotencyKey 和客户端所见的余额 / 布局 version。

## 13. 桌面端信息架构

### 13.1 导航

保留“基础资料 > 仓库管理”，在仓库行操作中增加“空间管理”入口。

在“库存管理”下增加：

1. 仓库空间
2. 库位库存
3. 库内移库
4. 盘点任务

保留现有库存余额、库存流水和库存调整页，逐步增加库位维度。

### 13.2 仓库空间

- 默认进入 `2D 监控`，页面包含仓库选择、SKU / 库位搜索、风险筛选、图层树、画布、图例和库位详情侧栏。
- 搜索 SKU 时高亮所有命中库位，自动聚焦首个结果，并允许在结果间跳转。
- 点击库位打开右侧详情面板，显示容量、SKU、箱堆、包装数、基础单位数、堆高、冻结和近期流水。
- 从详情面板可发起移库或查看流水。
- `3D 浏览` 与 2D 共享当前仓库、搜索、风险筛选和库位详情，但不提供布局编辑。

### 13.3 布局编辑器

- 布局编辑使用独立草稿模式，不与日常库存操作混在同一模式。
- 左侧为元素 / 图层树，中间为带标尺和吸附网格的 2D 画布，右侧为属性检查器。
- 可创建仓库边界、固定堆位区、自由堆放区、通道、障碍物和固定堆位。
- 支持选择、多选、移动、调整尺寸、旋转、对齐、吸附、复制、撤销 / 重做。
- 保存草稿不影响作业；发布前显示错误、警告和影响的库位。

### 13.4 库位库存、移库和盘点

- 库位库存使用密集表格补充地图视图，支持按仓库、区域、库位、SKU、包装层级、容量和风险查询。
- 移库列表保留单号、状态、来源、目标、数量、操作人和时间；新建移库使用源 / 目标双栏流程和 2D / 3D 预览。
- 盘点页包含任务列表、范围选择、盲盘录入、差异复核和调整结果。

## 14. Figma 设计系统与交付画板

现有 Figma 文件保持以下基础变量：

- Brand Primary `#536DFF`，Hover `#465EEA`。
- Page `#F6F7FB`，Panel `#FFFFFF`，Subtle `#F8FAFC`。
- Text Primary `#25314D`，Secondary `#64748B`，Muted `#94A3B8`。
- Border `#E2E8F0`。
- Success `#16A36A`，Info `#22B8CF`，Warning `#F59E0B`，Danger `#EF476F`。
- 间距 4 / 8 / 12 / 16 / 20 / 24 / 32 / 40，圆角 6 / 8。
- 中文使用 Noto Sans SC，英文数字使用 Inter。

复用 `02 Components` 中的 Sidebar、Top Bar、Page Header、Button、Field、Select、Status Tag、Table、Pagination、Drawer、Dialog 和空 / 错误 / 加载状态。

新增组件：

- Spatial / Canvas Toolbar
- Spatial / Layer Tree Row
- Spatial / Zone Shape
- Spatial / Fixed Location Shape
- Spatial / Temporary Location Shape
- Spatial / Stack Marker
- Spatial / Capacity Legend
- Spatial / Risk Badge
- Spatial / Location Inspector
- Inventory / Source Target Selector
- Inventory / Stack Allocation Row
- Inventory / Stocktake Count Row
- Inventory / Publish Validation Item

新增 `04 Inventory & Warehouse` 页，至少包含：

1. Warehouse Spatial / 2D Monitor
2. Warehouse Spatial / 3D Browse
3. Warehouse Layout / Draft Editor
4. Warehouse Layout / Publish Validation
5. Inventory / Location Balances
6. Inventory / Outbound Allocation
7. Inventory / Movement List
8. Inventory / Movement Create
9. Inventory / Stocktake List
10. Inventory / Stocktake Scope
11. Inventory / Stocktake Counting
12. Inventory / Stocktake Review

所有画板只做 1440px 桌面端，不做移动端。

## 15. 权限与审计

在现有权限体系中增加独立权限：

- 查看仓库空间。
- 编辑布局草稿。
- 发布布局。
- 查看库位库存。
- 发起 / 确认移库。
- 发起 / 执行盘点。
- 复核 / 批准盘点差异。
- 覆盖容量预警。

布局发布、临时库位创建 / 归档、移库确认、容量预警覆盖、盘点冻结、差异批准和取消都写操作日志。前端权限只负责可见性，后端必须在命令入口重新校验。

## 16. 迁移和切换

### 阶段 1：增量结构

- 新增包装、空间、库位、箱堆、库位余额、移库和盘点表。
- 扩展现有 `inventory_ledger`，不删除现有字段和查询。
- 为 Warehouse 增加空间库存启用状态和切换时间。

### 阶段 2：无损回填

- 每个仓库创建一个系统 `UNALLOCATED` 库位。
- 每个现有 warehouse + SKU 余额创建一个合成箱堆和一行库位余额，默认包装层级为可用的基础单位。
- 回填前后逐仓、逐 SKU 对比数量；任一不等则不开启新模式。
- 将现有物理尺寸回填到包装单位，标记缺失或不可推导的数据。

### 阶段 3：兼容过渡

- 未启用空间库存的仓库，现有业务通过兼容适配器将变动写入 `UNALLOCATED` 箱堆。
- 新界面可查看未分配库存并通过移库放入物理库位。
- 定时对账仓库汇总与库位汇总，发现差异立即报警并阻止该仓切换。

### 阶段 4：逐仓启用

- 仓库完成包装数据补全、布局发布和实物移库后，将该仓标记为空间库存已启用。
- 启用后，所有变动必须包含库位 / 箱堆分配，禁止仅按 warehouse + SKU 直接改数。
- 若需紧急回退，可关闭新界面和强制物理分配入口，让兼容适配器将新变动写入 `UNALLOCATED`。回退不改回库存权威模型，也不删除已生成的库位余额和流水。

## 17. 性能与降级

规模目标：单仓 2,000 个库位、10,000 个活跃箱堆。

- 2D 首屏先返回仓库边界、区域汇总和风险计数，再按当前区域 / 视口加载库位与箱堆。
- 库位库存和流水始终服务端分页，搜索使用仓库、区域、库位和 SKU 组合索引。
- 3D 只加载当前可见区域。近景用实例化网格显示单箱，远景使用箱堆聚合块，相同包装尺寸共享几何。
- 首次生成的布局几何和包装网格可缓存；库存数量变化只刷新影响的箱堆实例数和风险色。
- 现代桌面浏览器无 WebGL2 或 3D 初始化失败时，保持 2D 地图和表格可用，不影响库存作业。

验收性能目标：

- 在目标规模和内网环境下，2D 首个可交互视图 P95 不超过 2 秒。
- 库位 / SKU 搜索 P95 不超过 500 毫秒。
- 3D 首个可操作区域 P95 不超过 3 秒，支持正常桌面设备上的流畅旋转和缩放。
- 库存命令在不包含人工操作时间时 P95 不超过 1 秒。

## 18. 测试和验证

### 18.1 后端单元测试

- 包装换算、向上取整占用包装数和不可整除的拆包情况。
- 箱堆行列、层数、占地、高度、重量和容量阈值计算。
- 越界、重叠、通道侵占、单 SKU、SKU 数上限和禁混规则。
- 出库“先清零散、后最早入库”推荐顺序。
- 布局发布对含库存库位的保护规则。

### 18.2 后端集成测试

- 入库、出库、移库和盘点在余额、仓库汇总、箱堆状态和流水之间的原子性。
- 两个并发出库命令不会造成负库存。
- 双向移库锁定顺序不产生死锁或不完整流水。
- 冻结库位拒绝入库、出库和移库，其他库位仍可操作。
- 重复幂等键不重复记账。
- 任一业务校验失败时整个事务回滚。

### 18.3 迁移测试

- 迁移前后按仓库、SKU 汇总数量完全一致。
- 每个现有余额都有且仅有一个 UNALLOCATED 合成箱堆对应。
- 重跑迁移不产生重复库位、箱堆或余额。
- 尺寸缺失标记和包装回填规则可重复验证。

### 18.4 前端与端到端测试

- 2D 画布的选中、缩放、框选、吸附、撤销 / 重做、草稿保存和发布校验。
- SKU / 库位搜索的地图高亮、结果跳转和详情面板。
- 2D 和 3D 在同一布局版本和库存快照下展示相同库位、箱堆、数量和风险。
- 系统推荐出库、人工调整、并发冲突后重新确认。
- 整堆 / 部分移库和临时库位创建 / 归档。
- 盘点范围、冻结、盲盘、复盘、差异批准和取消解冻。
- 无 WebGL2 或 3D 加载失败时的 2D 降级。

### 18.5 视觉验证

- 所有 Figma 画板使用现有变量和组件实例，新重复元素必须组件化。
- 在 1440px 桌面框架中验证左侧 244px 侧边栏、页头、画布、图层树和详情面板无裁切、重叠或无效留白。
- 验证正常、预警、高风险、冻结、草稿、发布错误、加载、空数据和 3D 失败降级状态。
- 核心流程建立 Figma 原型连接，包括布局草稿到发布、SKU 定位到库位详情、出库分配、移库和盘点差异复核。

## 19. 分阶段交付顺序

1. Figma 仓库空间设计系统扩展与全部核心画板。
2. 包装层级、空间领域、库位余额和无损迁移基础。
3. 库位库存命令、流水扩展、查询和对账。
4. 2D 布局编辑器、发布校验和 2D 运营地图。
5. 入库 / 出库分配、移库和销售确认集成。
6. 盘点任务、冻结和差异调整。
7. 3D 场景、逐箱推导、LOD / 实例化渲染和 2D 降级。
8. 按仓迁移、性能压测、端到端验证和上线检查。

## 20. 验收标准

下列条件全部满足时，本子系统视为完成：

- 管理员可在 2D 草稿中建立仓库边界、固定堆位区、自由堆放区、通道、障碍物和固定库位，并在校验通过后发布。
- 自由堆放区可创建有唯一编号和几何坐标的临时库位，清空后自动归档且历史可追溯。
- 同一库位可包含多个 SKU，但每个垂直箱堆只含一个 SKU 和一种包装层级。
- 库存以基础单位记账，容量和 3D 按存储包装层级计算。
- 用户可以从 SKU 找到全部库位，也可从库位查看箱堆、数量、容量、风险和流水。
- 出库可生成推荐箱堆并允许人工确认；确认前不改变库存。
- 整堆和部分移库都在一个事务中完成，同仓移库前后仓库总量不变。
- 盘点仅冻结选定库位，支持盲盘、差异复核、批准调整和取消解冻。
- 库位余额、仓库汇总和流水在并发、重试和失败回滚下保持一致。
- 2D 和 3D 展示相同布局版本和库存快照；3D 不可用时 2D 和库存业务仍可使用。
- 现有库存迁移前后按仓库、SKU 对账结果为零差异。
- 核心 Figma 画板使用现有变量和公共组件，不创建与现有 ERP 不一致的新视觉体系。
