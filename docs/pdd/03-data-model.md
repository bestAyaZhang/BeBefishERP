# 拼多多数据模型与迁移设计

版本：0.1，2026-10-13。关联：[接入架构](02-integration-and-sync.md)、[统计口径](06-metrics-and-quality.md)。本文件为逻辑及关键物理字段设计，不是可执行 SQL；远端契约核验后再生成 Flyway 迁移。

## 1. 通用规则

- 复用 platform_shop.id 和现有 ERP 商品/SKU 主键。全部拼多多事实以 shop_id 隔离。
- 新表采用 MySQL 8/InnoDB/utf8mb4；主键 bigint；审计 created_at、updated_at 为 datetime(3)，人工操作保存现有风格的 created_by/updated_by。
- 外部 ID 按字符串保存，建议 varchar(128)，最终按官方长度核验；唯一键列使用大小写敏感/二进制比较，不转换为 JavaScript Number，不剥离前导零。
- ERP 主键在前端沿用现有契约；外部订单/商品/SKU/店铺 ID 在 JSON 中必须为字符串。
- 时刻统一规范为 UTC 存储与传输，业务日期按 Asia/Shanghai 计算；远端时区/单位由适配器确认。接口返回带时区 ISO 8601。没有日期的金额不纳入某日统计，进入质量异常。
- 金额规范为人民币 decimal(18,2)，Java BigDecimal；远端若使用分，按确切单位转换。ERP 成本保留既有精度；不使用浮点数计算。未知金额为 NULL，不用 0 代替。
- 可编辑记录具有 version_no；更新 WHERE id=? AND version_no=?。远端事实由 source_updated_at/hash 判新旧，不套用用户编辑版本。
- 已同步事实不级联删除；停用、解绑保留历史。每日汇总可重建，事实与发布证据不能随意清空。
- 列出的长度和保留期为建议值，实施时以实际样例验证。MySQL CHECK 和外键保证基础约束，跨记录规则由短事务与服务校验保证。

## 2. 店铺、应用与授权

| 表 | 关键字段 | 唯一键及用途 |
| --- | --- | --- |
| pdd_application | id、environment、client_id、display_name、secret_reference、enabled、version_no | (environment, client_id)；secret_reference 指向部署密钥，不保存应用 secret 明文 |
| pdd_shop_connection | id、shop_id、application_id、external_shop_id、binding_status、auth_generation、last_verified_at、version_no | shop_id 唯一，external_shop_id 唯一；同一商家不可绑定两套内部店铺 |
| pdd_shop_credential | connection_id、credential_type、key_id、nonce、ciphertext、expires_at、generation | (connection_id, credential_type)；access/refresh 类型仅在官方支持时启用 |
| pdd_oauth_attempt | state_hash、user_id、shop_id、application_id、expires_at、consumed_at、candidate_ciphertext、result | state_hash 唯一；候选 token 短期存储，完成店铺验证后清理 |
| pdd_shop_capability | connection_id、capability_code、verification_status、checked_at、contract_version、error_code | (connection_id, capability_code)；每店能力与核验证据 |
| pdd_shop_user_grant | shop_id、user_id、enabled、version_no、created_by | (shop_id, user_id)；复用 sys_user，不靠负责人文本授权 |

绑定的 application/environment 不可随意切换，迁移应用需重新授权；external_shop_id 解绑后仍保留唯一约束及身份关联，重新绑定沿用原内部店铺，不让历史穿店。connection 变更增加 auth_generation。

binding_status：pending_authorization、active、needs_reauth、revoked、disabled。应用未开通、权限待核验和授权过期分别展示；禁用不等于删掉 token 行或事实。

授权回调消耗 state 与写入候选结果用原子条件更新；授权激活、generation 更新和凭据替换在同一短事务。secret_reference 和加密主密钥须有独立恢复办法。密钥版本不等于 token generation。

## 3. 同步与可恢复任务

| 表 | 关键字段 | 索引及约束 |
| --- | --- | --- |
| pdd_job | id、job_type、status、requested_by、request_key、scope_snapshot_json、created_at | (requested_by, job_type, request_key) 唯一；父任务保存目标店集合 |
| pdd_job_item | id、job_id、shop_id、dataset、status、window_start/end、checkpoint_json、attempt、next_run_at、lease_owner/until、heartbeat_at、fencing_token、auth_generation、counters_json、error_code | (status, next_run_at, id) 与 (shop_id, dataset, status)；窗口 start < end |
| pdd_sync_cursor | connection_id、dataset、completed_end、overlap_seconds、last_success_at、version_no | (connection_id, dataset) 唯一；只在完整窗口完成后推进 |
| pdd_sync_window | id、shop_id、dataset、start/end、status、job_item_id、record_count、verified_at | (shop_id, dataset, start, end, job_item_id)；多个回补窗口允许重扫 |
| pdd_shop_dataset_lease | shop_id、dataset、owner、lease_until、fencing_token | (shop_id, dataset) 主键；首版同店同数据集串行，避免回补覆盖增量游标 |
| pdd_audit_event | actor_id、shop_id、action、entity_type/id、job_id、trace_id、redacted_detail_json、created_at | (shop_id, created_at, id)；不得包含 token 或完整收件信息 |

读取任务状态：queued、running、retry_wait、succeeded、partial、failed、cancelled、paused。父任务状态按子任务派生；取消在下一检查点停止，已保存数据保留。unknown 专用于有远程副作用且结果不明的发布任务，不能表示一般读取失败。

checkpoint 包含固定窗口、页/continuation、契约版本；当前页提交成功才更新。dataset lease 和 item lease 都带代次，旧 Worker 无权提交。租约超时后读取可重领；发布的 sending 超时转 unknown，不直接回 queued。

## 4. 订单、售后与商品事实

| 表 | 主要字段 | 业务唯一键 |
| --- | --- | --- |
| pdd_order | id、shop_id、external_order_id、raw/normalized_status、currency、created_time、paid_at、source_updated_at、buyer_paid_amount、goods_paid_amount、shipping_paid_amount、source_type、source_reference、payload_hash、quality_status | (shop_id, external_order_id) |
| pdd_order_item | id、order_id、shop_id、external_order_id、external_line_key、external_goods_id、external_sku_id、title/spec_snapshot、quantity、line_paid_amount、allocation_method、source_updated_at | (shop_id, external_order_id, external_line_key) |
| pdd_after_sale | id、shop_id、external_after_sale_id、external_order_id、order_id 可空、raw/normalized_status、requested_refund_amount、success_refund_total、source_updated_at、source_reference、quality_status | (shop_id, external_after_sale_id) |
| pdd_refund_fact | id、shop_id、external_refund_key、after_sale_id、external_order_id、external_line_key 可空、currency、success_amount、succeeded_at、source_updated_at、source_type、quality_status | (shop_id, external_refund_key) |
| pdd_goods | id、shop_id、external_goods_id、title、external_category_id、raw/normalized_status、verified_url、source_updated_at、source_type、payload_hash、last_seen_run_id | (shop_id, external_goods_id) |
| pdd_goods_sku | id、goods_id、shop_id、external_goods_id、external_sku_id、spec_json、external_merchant_code、price_fields_json、platform_quantity、source_updated_at | (shop_id, external_goods_id, external_sku_id) |

订单列表索引 (shop_id, paid_at, id)、(shop_id, normalized_status, created_time, id)，售后索引 (shop_id, source_updated_at, id) 和 (shop_id, external_order_id)，退款索引 (shop_id, succeeded_at, id)。每个 child 保存 shop_id，以复合外键或服务断言校验与 parent 归属一致，禁止不同店的同单号串联。

不得以「数组第几项」生成会随分页/排序变化的明细键。有官方稳定明细 ID 优先使用；确无 ID 时按已核验的订单内稳定 SKU/规格组合构造键。若组合仍不唯一，保留原快照并标识不可稳定拆分，暂不提供逐行销量或商品分摊，不悄悄合并不同成交行。

pdd_refund_fact 区分「独立退款事件」与「售后累计退款事实」：
- 平台有唯一事件 ID：每事件一行，可按成功时间统计。
- 只有累计金额且只有一个经核验的完成时间：允许售后级累计事实键，更新原行，不把每次累计值都记新事件。
- 累计多次退款但无逐次时间：保留售后累计金额，不能人为拆出当日事件。订单支付批次退款可统计；退款日指标标部分/不可用。

平台没有返回或权限隐藏的字段保留 NULL 和 field_availability。商品全量巡检只有整轮完成后才能判断「本轮未见」；未见不等于平台删除，需明细确认或保留 unknown 状态。

## 5. ERP SKU 映射

pdd_sku_mapping：id、shop_id、external_goods_id、external_sku_id、erp_product_id、erp_sku_id、version_no、effective_from/to、created_by、mapping_status。

用当前表 + 历史表或独立有效映射键，保证每个 (shop_id, external_goods_id, external_sku_id) 只有一个当前有效映射。普通索引不能实现「只对 active 唯一」，实际迁移需明确实现方式；建议 pdd_sku_mapping_current 使用该三元组主键，历史 pdd_sku_mapping_history 保存每次替换。

首版新增映射不改写订单商品字段；ERP 维度统计按当前映射重算，报告返回 mapping_version。若未来要求成交时映射，新增明确的快照指标而不是混用两种口径。

## 6. 发布草稿、版本与结果

| 表 | 关键字段 | 约束 |
| --- | --- | --- |
| pdd_listing_draft | id、shop_id、erp_product_id、version_no、draft_state、title、category_id、attribute_json、spec_json、image_refs_json、price_json、stock_json、template_json、source_product_snapshot_json、validation_result_json、validated_hash、contract_version | 按店独立草稿；乐观更新 |
| pdd_listing_revision | id、draft_id、shop_id、revision_no、payload_snapshot_json、payload_hash、mapping_snapshot_json、validated_contract_version、created_by | (draft_id, revision_no) 唯一；提交后不可编辑 |
| pdd_publish_attempt | id、revision_id、shop_id、intent_id、attempt_no、request_key、local_state、resolution_state、lease_owner/until、fencing_token、auth_generation、external_request_id、external_goods_id、error_code、submitted_at、resolved_at | (shop_id, request_key) 和 (intent_id, attempt_no) 唯一 |
| pdd_listing_link | id、shop_id、erp_product_id、external_goods_id、publish_attempt_id、verified_url、raw/normalized_status、verified_at、link_source | (shop_id, external_goods_id) 唯一；支持确认为不同链接的多个商品 |
| pdd_metadata_cache | connection_id、metadata_type、cache_key、contract_version、data_json、fetched_at、expires_at | (connection_id, metadata_type, cache_key) 唯一 |

为 (shop_id, erp_product_id, publish_mode/intent) 建立短事务发布互斥记录 pdd_publish_guard，避免不同 request_key 绕过重复创建保护。已有默认链接或结果待核实的默认 intent 不允许再次创建；「另建链接」生成新 intent 并保存原因、操作者和核验证据。

任务响应与审核状态分开保存。外部请求 ID 或商品 ID 只有成功回读/可信确认后才能链接；URL 必须来自已确认官方返回或验证过程。

resolution_state：not_required、pending_check、manual_resolution、resolved。它是 unknown 的处理维度，不把 manual_resolution 当成已失败可以重发的依据。

内部文件引用复用 file 模块 ID；PDD 素材上传返回的 ID/URL 按应用/店铺和有效期单独缓存。商品详情模板、属性、运费模板为店铺/类目特定资料，不能全店通用缓存。

## 7. 汇总、导入与数据质量

| 表 | 关键字段及约束 |
| --- | --- |
| pdd_metric_daily | (shop_id, business_date, metric_version) 唯一；支付订单数/件数/支付金额、退款日金额、支付批次累计退款、未知/未映射计数、quality_json、data_revision、computed_at |
| pdd_metric_dirty_date | (shop_id, business_date, metric_version) 唯一；dirty_revision、processed_revision、next_run_at；重算按领取的 revision 完成，期间新增变更不能被误清除 |
| pdd_import_batch | shop_id、dataset、file_id/hash、template_version、status、counts、confirmed_by；(shop_id, dataset, file_hash, template_version) 唯一 |
| pdd_import_error | batch_id、row_no、business_key、error_code、redacted_message；只保留脱敏错误与受权限控制的文件引用 |
| pdd_data_issue | shop_id、dataset、business_key、issue_code、state、source_reference、first/last_seen_at、resolution_evidence；记录缺失、状态/版本冲突、币种异常 |
| pdd_reconciliation_run | shop_id、dataset、time_basis、window_start/end、filters_json、source_reference、count_delta、amount_delta、result、checked_by、checked_at |

汇总记录只保存可重建指标；跨店请求对实际授权店集合求和。未映射不漏掉店铺支付总额。商品/SKU排名首版可基于索引查询事实及当前映射；达到性能瓶颈再新增明细物化汇总。

原始业务响应不默认全量持久化；只保存经过字段白名单处理的诊断信息。导入文件如包含个人信息须受限并按留存策略清理。

## 8. 迁移与实施拆分

1. 绑定/授权/能力/权限/任务基础。
2. 订单/明细/售后/退款/商品/SKU/游标/窗口及质量记录。
3. 映射/汇总/导入/对账。
4. 发布草稿/不可变版本/任务/互斥/链接/元数据缓存。

当前最高为 V26，但不预先占用 V27–V30；实施前重新检查 main 的最高版本。追加迁移，无自动真实店铺初始化，无升级时自动远程调用。

从空库及既有 V26 的独立 _test 库验证迁移。DDL 不能依赖事务完全回滚；发布前备份。回退应用时停任务与发布开关，保留新增事实表，不执行 Flyway clean 或删除未知结果任务。
