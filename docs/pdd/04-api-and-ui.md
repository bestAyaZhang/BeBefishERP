# ERP 内部 API、权限与页面设计

版本：0.1，2026-10-13。所有路径均为拟定 ERP 接口，不是拼多多官方 API。关联：[需求](01-requirements.md)、[数据模型](03-data-model.md)。

## 1. 接口契约

- 路径前缀 /api/pdd；ApiResponse 沿用 code/message/data；列表使用现有 PageResponse：records/page/pageSize/total。page 从 1 开始，pageSize 默认 20，最大 100。
- 金额作为十进制字符串传输；外部 ID 作为字符串；整数件数不推断缺失值；时刻采用带时区 ISO 8601。
- 普通更新携带 version；异步操作使用 Idempotency-Key 和服务端 payload_hash。相同用户/动作/key 相同内容返回同一任务，内容不同返回 409。
- API 请求快速创建任务并返回 202，不等待全部店铺远程调用。任务列表/详情遵守当前权限，而不是只看发起时快照。
- 日期查询显式 timeBasis：paid_at、created_at、refund_succeeded_at；报告采用 06 文档定义的模式，不能由后端自行猜测。
- 所有跨店请求先检查完整 shopIds 集合。显式提交包含无权限店铺时返回 403，不悄悄删掉；省略店铺集合表示全部当前可访问店铺。
- store grants + pdd 功能权限为双重约束。后台执行前复核发起人有效性、权限和店铺是否停用；系统周期同步按已启用连接运行，写入 system 审计，不继承普通用户的全库权限。

## 2. 权限编码

| 权限 | 行为 | 店铺范围 |
| --- | --- | --- |
| pdd:view | 模块入口、店铺连接状态、授权内订单/售后/商品/任务只读 | 必须校验 |
| pdd:authorize | 发起/撤销店铺授权、能力核验 | 必须校验 |
| pdd:manage | 设置连接/计划及店铺成员范围 | 必须校验，应用全局设置另需管理员权限 |
| pdd:sync | 手工增量、回补、读取重试、停止任务 | 必须校验 |
| pdd:import | 预览并确认官方文件导入 | 必须校验 |
| pdd:map | ERP SKU 映射及调整 | 必须校验 |
| pdd:report | 店铺销售看板和排名 | 必须校验 |
| pdd:export | 经营数据/失败行/资料包导出 | 必须校验并审计 |
| pdd:listing:edit | 创建编辑商品发布草稿、预检 | 必须校验且检查 product:view |
| pdd:listing:publish | 向远端提交及人工结果核实 | 必须校验，并重新检查授权能力 |
| pdd:listing:duplicate | 明确另建同商品链接 | 必须同时具备 publish |
| pdd:sensitive:view | 后续启用必要个人数据查询 | 首版不采集时不开放功能 |

应用 secret 配置/轮换仅限系统管理员；pdd:manage 不能单独授予全局密钥权限，也不能授予超过自身店铺范围的成员权限。SUPER_ADMIN 按既有约定可访问全部店铺，但保持审计。单店普通人员通过 grant 获范围，不自动获得全部 pdd 操作。

## 3. 店铺与授权 API

| 方法与路径 | 用途 | 权限/结果 |
| --- | --- | --- |
| GET /shops | 拼多多店铺及连接/能力/同步状态 | view，分页 |
| POST /shops/{shopId}/connection | 绑定已有目录店铺与应用 | manage，创建 pending_authorization |
| GET /shops/{shopId}/capabilities | 当前读取/发布能力矩阵 | view |
| POST /shops/{shopId}/authorization-attempts | 发起一次性授权 | authorize，返回授权 URL/attemptId/过期时刻 |
| GET /oauth/callback | 官方回调 | 以协议关联/验真，非普通员工登录接口 |
| POST /shops/{shopId}/authorization-revoke | 禁止新远程访问 | authorize，version；平台撤销支持与本地断开分别标注 |
| PUT /shops/{shopId}/sync-plan | 周期及数据集设置 | manage，version，官方额度内 |
| GET/PUT /shops/{shopId}/members | 查询/配置用户范围 | manage；不得扩大自身范围 |
| GET /application-status | 应用能力/配置状态掩码 | view；不返回凭据 |
| PUT /applications/{id}/configuration | 管理员设置密钥引用和环境 | 系统管理员；不在响应/日志回显 secret |

用户不能传 external_shop_id 就完成可信绑定；回调后通过官方身份读取确认。授权 URL 的回跳目标固定允许的 ERP 路由，不能任意 redirect。

## 4. 同步、导入与业务读取

| 方法与路径 | 用途 | 权限 |
| --- | --- | --- |
| POST /sync-jobs | shopIds、datasets、mode、时间范围 | sync；202 jobId |
| GET /jobs / GET /jobs/{id} | 父/逐店子任务进度、失败窗口 | view；仅返回可访问子任务，父计数按可见范围计算 |
| POST /job-items/{id}/retry | 明确失败读取窗口恢复 | sync；不适用于未知发布 |
| POST /jobs/{id}/cancel | 停止未发出/下一检查点的工作 | sync 或对应动作权限；每个目标店校验 |
| GET /orders / GET /orders/{id} | 订单筛选及详情/关联售后 | view |
| GET /after-sales / GET /after-sales/{id} | 售后筛选与退款事实 | view |
| GET /goods / GET /goods/{id} | 平台商品及 SKU | view |
| GET/PUT /sku-mappings/{id} | 当前映射查询/版本更新 | view/map |
| POST /sku-mappings | 外部 SKU 绑定 ERP SKU | map + product:view |
| GET /reports/overview | 多店、趋势、质量信息 | report |
| GET /reports/shops / GET /reports/products | 分店和商品/SKU排名 | report |
| POST /import-previews | 文件、shopId、dataset、templateVersion | import，202，返回预览任务 |
| GET /import-previews/{id} | 映射、行数、差错、样例 | import |
| POST /import-previews/{id}/confirm | 确认预览版本/hash并入库 | import，202 |
| POST /exports | 数据类型、店铺、筛选、口径 | export + 对应读取权限，202 |
| GET /exports/{id}/download | 短期文件获取 | 当次重新校验店铺/功能权限，不用永久公开 URL |

读取详情的 id 是内部记录 ID，查找仍必须校验该记录的 shop_id。导出不能只在创建时鉴权：任务执行和下载也检查，权限被撤销后拒绝下载；对象存储签名短期有效，最小暴露窗口。

同步请求示例：
```json
{
  "shopIds": [101, 102],
  "datasets": ["orders", "after_sales", "goods"],
  "mode": "backfill",
  "window": {
    "start": "2026-10-01T00:00:00+08:00",
    "end": "2026-10-13T00:00:00+08:00"
  }
}
```
以上 ID 为示例，数据窗口不是官方限制。goods 若无时间增量能力，则执行全量巡检并在响应标明策略，不能假装应用相同窗口筛选。

任务响应 data 示例：
```json
{
  "jobId": "job-example",
  "status": "queued",
  "acceptedShopIds": [101, 102],
  "pollAfterSeconds": 3
}
```
轮询 3–5 秒，退到后台后减频；无更新不重复刷新整表。jobId 示例值是占位，实施时按确定的内部主键契约返回。

## 5. 发布 API

| 方法与路径 | 内容 | 结果 |
| --- | --- | --- |
| POST /listing-drafts | erpProductId、shopIds | edit；逐店创建草稿，无远程发布 |
| GET /listing-drafts / GET /listing-drafts/{id} | 草稿列表/快照/预检/店铺覆盖项 | edit 或对应 view |
| PUT /listing-drafts/{id} | version、店铺字段/SKU/素材 | edit；旧版 409 |
| GET /shops/{shopId}/listing-metadata | 类目/属性/规格/模板查询 | edit；仅获官方能力时查询 |
| POST /listing-drafts/{id}/validate | 当前 version 全量预检 | edit；异步素材预检则返回 202 |
| POST /publish-jobs | draftIds + 每店 expectedVersion、validatedHash、publishMode、duplicateReason | publish；预检/能力/重复保护，通过后冻结不可变版本 |
| GET /publish-attempts/{id} | 提交结果、平台审核、链接、核实状态 | view，店铺校验 |
| POST /publish-attempts/{id}/reconcile | 查询核实未知结果 | publish；此动作不能重新提交创建 |
| POST /publish-attempts/{id}/manual-resolution | 结论、externalGoodsId、核验证据、version | publish；审计，能 API 回读必须先回读 |
| POST /listing-drafts/{id}/material-exports | 人工发布资料包 | edit + export；不标为平台已创建 |

发布批量预检默认全部所选草稿通过后才接受提交；不通过时返回逐店错误，零提交。用户显式移除错误草稿并重新确认可提交集合后才创建任务。执行阶段仍可能某店失败，分店处理，不尝试撤销其他店已接受商品。

请求冻结之后不能原地修改 revision。素材、类目规则、ERP 来源或价格数量变化都使预检失效；validatedHash 包含业务 payload、源快照及官方契约/元数据版本。提交时再次校验版本、授权、资质与写入总开关。

## 6. 错误码与状态码

| 状态 | 业务码 | 用户操作 |
| --- | --- | --- |
| 400 | PDD_INVALID_QUERY / PDD_IMPORT_TEMPLATE_INVALID | 修正范围或模板 |
| 401 | 现有登录过期码 | 重新登录 ERP，不能自动使用演示数据 |
| 403 | PDD_SHOP_FORBIDDEN / PDD_PERMISSION_DENIED | 联系管理员分配权限 |
| 404 | PDD_RECORD_NOT_FOUND | 无权限详情也可统一 404 防泄露；不得列出其他店铺信息 |
| 409 | PDD_VERSION_CONFLICT / PDD_IDEMPOTENCY_CONFLICT | 保留输入，刷新并核对 |
| 409 | PDD_ALREADY_LINKED / PDD_PUBLISH_UNRESOLVED | 查看已有链接或先核实结果 |
| 422 | PDD_CAPABILITY_UNAVAILABLE / PDD_LISTING_INVALID | 显示缺失权限/字段，必要时走人工流程 |
| 429 | PDD_REQUEST_LIMITED | 本地请求限频；远端限流在任务中转 retry_wait |
| 503 | PDD_NOT_CONFIGURED / PDD_WRITES_DISABLED | 显示配置或发布开关状态 |
| 409 | PDD_AUTH_REQUIRED | ERP 登录仍有效，仅重新授权拼多多店铺 |

异步远端失败以任务 errorCode/字段错误列表展示，不把失败异步任务当成 HTTP 200 的业务成功。error message 脱敏，不暴露签名、token、原始个人信息。

## 7. 页面和路由

菜单「拼多多运营」下的建议路由：

| 路由 | 页面重点 |
| --- | --- |
| /pdd/overview | 多店选择、支付趋势、退款两种口径、店铺/SKU排名、数据完整性 |
| /pdd/shops | 已有店铺绑定、授权和过期、能力、负责人/成员、计划；新店跳现有平台目录 |
| /pdd/orders | 店铺、日期口径、状态、订单号查询；详情抽屉 |
| /pdd/after-sales | 售后状态、退款成功时间、订单关联、待核实 |
| /pdd/goods | 平台商品、SKU、平台库存、ERP 映射、当前上架状态 |
| /pdd/listings | ERP 选品、逐店草稿、差异预览、单店/多店提交 |
| /pdd/listings/:id | 编辑器及逐字段校验，保留失败输入 |
| /pdd/tasks | 同步/导入/导出/发布统一入口，分店进度、错误、核实 |
| /pdd/data-quality | 缺口/冲突/对账证据/未映射；进入补数与人工核验 |

页面沿用 ErpLayout、表格、筛选、分页和抽屉；发布编辑器分为选品→类目属性→素材→SKU价格数量→模板资质→逐店预览。店铺授权为界面操作指引，不在商品流程展示密钥、HTTP 参数或数据库状态。

顶部显示「最后完整同步时间」「已覆盖店铺/选择店铺」「有缺口的数据集」。真实空列表展示空态，不填 Mock；测试替身仅 local/test。首版 pdd 服务推荐显式 real，缺配置显示可恢复错误与人工导入入口。

## 8. 流程关键体验

- 点击授权提示即将进入官方页面，不索要商家账号密码。
- 多店分店覆盖规则：仅复制草稿内容，不复制 token、店铺模板 ID、资质或外部 SKU 身份。
- 发布前展示每店最终标题、价格、发布数量、模板、类目和图片，并确认真正创建商品；按钮避免把受理结果描述为「已上架」。
- unknown 界面只提供查看、核实和带证据的人工处理，不提供普通「重试上架」。
- 导出 CSV 对以 =、+、-、@ 等开头的文本采取公式注入防护，不改变数值列金额的正式口径。
- 任务完成通知用 ERP 内部提示，不自动发送飞书消息、邮件或其他外部通知。
