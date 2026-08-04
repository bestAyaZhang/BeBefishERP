# 电商内部 ERP 数据库设计

## 1. 设计原则

本数据库设计是第一版目标表结构。当前阶段 1 登录基础仍使用内存账号、内存验证码和内存令牌完成联调，尚未创建 MySQL 迁移脚本；进入用户、角色、权限持久化开发时必须以本文档为准并同步迁移脚本。

- 数据库使用 MySQL 8，字符集使用 `utf8mb4`。
- 表名使用小写下划线，主键统一为 `id`。
- 金额、成本、单价使用 `decimal(18,2)` 或 `decimal(18,4)`。
- 数量使用 `decimal(18,4)`，支持物料小数用量。
- 所有业务表保留 `created_at`、`created_by`、`updated_at`、`updated_by`、`deleted`。
- 重要业务数据采用逻辑删除。
- 确认后的单据不直接修改库存和财务历史，通过反向流水冲回。

## 2. 通用字段

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| id | bigint | 主键 |
| created_at | datetime | 创建时间 |
| created_by | bigint | 创建人 |
| updated_at | datetime | 更新时间 |
| updated_by | bigint | 更新人 |
| deleted | tinyint | 0 正常，1 删除 |
| remark | varchar(500) | 备注 |

## 3. 枚举约定

| 类型 | 值 |
| --- | --- |
| 单据状态 | draft 草稿、confirmed 已确认、voided 已作废 |
| 库存对象类型 | sku 成品 SKU、material 物料 |
| 库存流水方向 | in 入库、out 出库 |
| 收付款方向 | receive 收款、pay 付款 |
| 往来状态 | unpaid 未结清、partial 部分结清、paid 已结清、voided 已作废 |
| 平台 | 1688、taobao、tmall、pdd、douyin、jd、other |

## 4. 系统权限表

### sys_user

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| id | bigint | 用户 ID |
| employee_id | bigint | 关联员工 |
| mobile | varchar(30) | 登录手机号，唯一 |
| password_hash | varchar(255) | 密码哈希 |
| feishu_user_id | varchar(100) | 未来飞书用户 ID，第一版可为空 |
| feishu_union_id | varchar(100) | 未来飞书 union ID，第一版可为空 |
| status | varchar(20) | enabled、disabled |
| last_login_method | varchar(20) | password、sms、feishu |
| last_login_at | datetime | 最后登录时间 |

索引：

- `uk_sys_user_mobile`：`mobile` 唯一。
- `uk_sys_user_feishu_user`：`feishu_user_id` 唯一，允许为空。
- `uk_sys_user_feishu_union`：`feishu_union_id` 唯一，允许为空。
- `idx_sys_user_employee`：`employee_id`。

### sys_role

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| id | bigint | 角色 ID |
| role_code | varchar(50) | 角色编码，唯一 |
| role_name | varchar(50) | 角色名称 |
| status | varchar(20) | enabled、disabled |

### sys_permission

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| id | bigint | 权限 ID |
| permission_code | varchar(100) | 权限编码 |
| permission_name | varchar(100) | 权限名称 |
| permission_type | varchar(20) | menu、button、api |
| parent_id | bigint | 父级权限 |

### sys_user_role / sys_role_permission

用于用户角色和角色权限多对多关系。

## 5. 基础资料表

### employee

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| id | bigint | 员工 ID |
| employee_no | varchar(50) | 员工编号，唯一 |
| name | varchar(50) | 姓名 |
| mobile | varchar(30) | 手机号 |
| position | varchar(50) | 岗位 |
| hire_date | date | 入职日期 |
| status | varchar(20) | active、inactive |

### customer

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| id | bigint | 客户 ID |
| customer_no | varchar(50) | 客户编号，唯一 |
| name | varchar(100) | 客户名称 |
| contact_name | varchar(50) | 联系人 |
| mobile | varchar(30) | 手机号 |
| address | varchar(255) | 地址 |
| receivable_balance | decimal(18,2) | 应收余额 |

### supplier

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| id | bigint | 供应商 ID |
| supplier_no | varchar(50) | 供应商编号，唯一 |
| name | varchar(100) | 供应商名称 |
| contact_name | varchar(50) | 联系人 |
| mobile | varchar(30) | 手机号 |
| address | varchar(255) | 地址 |
| payable_balance | decimal(18,2) | 应付余额 |

### warehouse

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| id | bigint | 仓库 ID |
| warehouse_code | varchar(50) | 仓库编码，唯一 |
| warehouse_name | varchar(100) | 仓库名称 |
| status | varchar(20) | enabled、disabled |

第一版只初始化一个默认仓库，所有库存表仍保留 `warehouse_id`。

## 6. 产品、SKU、物料、BOM

### product_spu

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| id | bigint | 产品 ID |
| product_code | varchar(50) | 产品编码，唯一 |
| item_no | varchar(50) | 货号，唯一 |
| product_name | varchar(100) | 产品名称 |
| category_name | varchar(100) | 分类 |
| brand_name | varchar(100) | 品牌 |
| status | varchar(20) | enabled、disabled |

索引：

- `uk_product_spu_code`：`product_code` 唯一。
- `uk_product_spu_item_no`：`item_no` 唯一。

### product_sku

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| id | bigint | SKU ID |
| spu_id | bigint | 关联产品 |
| sku_code | varchar(50) | SKU 编码，唯一 |
| barcode | varchar(100) | 条码 |
| sku_name | varchar(100) | SKU 名称 |
| spec_text | varchar(255) | 规格描述 |
| sale_price | decimal(18,2) | 默认售价 |
| standard_cost | decimal(18,4) | 标准成本 |
| package_length_cm | decimal(18,2) | 包装长，单位 cm |
| package_width_cm | decimal(18,2) | 包装宽，单位 cm |
| package_height_cm | decimal(18,2) | 包装高，单位 cm |
| package_volume_cm3 | decimal(18,2) | 包装体积，单位 cm³，可由长宽高计算 |
| net_weight_kg | decimal(18,4) | 净重，单位 kg |
| gross_weight_kg | decimal(18,4) | 毛重，单位 kg |
| gram_weight_g | decimal(18,2) | 克重，单位 g，用于部分商品或材料标识 |
| package_type | varchar(100) | 包装方式，例如彩盒、白盒、OPP 袋 |
| package_image_url | varchar(500) | 包装图片地址 |
| carton_quantity | int | 装箱数，每箱包含 SKU 数量 |
| status | varchar(20) | enabled、disabled |

索引：

- `uk_product_sku_code`：`sku_code` 唯一。
- `idx_product_sku_spu`：`spu_id`。
- `idx_product_sku_barcode`：`barcode`。

### material

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| id | bigint | 物料 ID |
| material_code | varchar(50) | 物料编码，唯一 |
| barcode | varchar(100) | 条码 |
| material_name | varchar(100) | 物料名称 |
| unit | varchar(20) | 单位 |
| standard_cost | decimal(18,4) | 标准成本 |
| main_supplier_id | bigint | 默认供应商 |
| status | varchar(20) | enabled、disabled |

### bom

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| id | bigint | BOM ID |
| sku_id | bigint | 成品 SKU |
| bom_code | varchar(50) | BOM 编码 |
| version_no | varchar(20) | 版本号 |
| status | varchar(20) | enabled、disabled |

### bom_item

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| id | bigint | BOM 明细 ID |
| bom_id | bigint | BOM ID |
| material_id | bigint | 物料 ID |
| quantity | decimal(18,4) | 单个成品消耗数量 |
| unit | varchar(20) | 单位 |

约束：

- 同一成品 SKU 同一时间只允许一个启用 BOM。
- SKU 标准成本 = SUM(物料标准成本 * BOM 用量)。

## 7. 库存表

> 当前库存基础迭代已实际落地 Flyway `V4__inventory_schema.sql` 和 `V5__stock_adjustment_schema.sql`。本轮只实现 SKU 库存；物料库存字段和采购/组装关联在后续迭代扩展。以下结构以当前已执行迁移为准。

### inventory_balance

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| id | bigint | 库存余额 ID |
| warehouse_id | bigint | 仓库 ID |
| sku_id | bigint | SKU ID |
| quantity | decimal(18,4) | 当前库存，不允许小于 0 |
| version_no | bigint | 库存版本号，由库存服务递增 |
| created_at / updated_at | datetime(3) | 创建和更新时间 |

索引：

- `uk_inventory_warehouse_sku`：`warehouse_id, sku_id` 唯一。
- `idx_inventory_balance_sku`：`sku_id`。
- `idx_inventory_balance_warehouse`：`warehouse_id`。

### inventory_ledger

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| id | bigint | 流水 ID |
| warehouse_id | bigint | 仓库 ID |
| sku_id | bigint | SKU ID |
| direction | varchar(20) | increase、decrease |
| quantity | decimal(18,4) | 变动数量 |
| before_quantity | decimal(18,4) | 变动前库存 |
| after_quantity | decimal(18,4) | 变动后库存 |
| source_type | varchar(30) | purchase、sales、assembly、adjustment、adjustment_void |
| source_id | bigint | 来源单据 ID |
| source_no | varchar(50) | 来源单号 |
| occurred_at | datetime(3) | 发生时间 |
| operator_mobile | varchar(30) | 操作人手机号 |

流水不可修改。每次余额变化必须在同一事务内写入流水，库存服务按 SKU ID 升序获取 `FOR UPDATE` 行锁；多明细扣减先全部校验，再统一写入，避免部分成功。

### stock_adjustment

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| id | bigint | 调整单 ID |
| adjustment_no | varchar(50) | 调整单号，唯一 |
| status | varchar(20) | draft、confirmed、voided |
| warehouse_id | bigint | 仓库 ID |
| reason | varchar(200) | 调整原因 |
| remark | varchar(500) | 备注 |
| created_at / updated_at | datetime(3) | 创建和更新时间 |

### stock_adjustment_item

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| id | bigint | 明细 ID |
| adjustment_id | bigint | 调整单 ID |
| sku_id | bigint | SKU ID |
| quantity_delta | decimal(18,4) | 调整数量，正数增加，负数减少 |

约束：

- `stock_adjustment.status` 只允许 `draft`、`confirmed`、`voided`。
- 同一调整单中 `sku_id` 不允许重复，`quantity_delta` 不允许为 0。
- 确认调用库存服务一次完成；重复确认和确认后修改由后端拒绝；作废确认单通过反向调整恢复库存并写 `adjustment_void` 流水。

## 8. 采购表

### purchase_order

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| id | bigint | 采购单 ID |
| purchase_no | varchar(50) | 采购单号，唯一 |
| supplier_id | bigint | 供应商 |
| warehouse_id | bigint | 入库仓库 |
| status | varchar(20) | draft、confirmed、voided |
| total_amount | decimal(18,2) | 采购总额 |
| paid_amount | decimal(18,2) | 已付金额 |
| confirmed_at | datetime | 确认时间 |

### purchase_order_item

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| id | bigint | 明细 ID |
| purchase_id | bigint | 采购单 ID |
| item_type | varchar(20) | sku、material |
| item_id | bigint | SKU ID 或物料 ID |
| quantity | decimal(18,4) | 数量 |
| unit_price | decimal(18,4) | 单价 |
| amount | decimal(18,2) | 金额 |

## 9. 组装表

### assembly_order

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| id | bigint | 组装单 ID |
| assembly_no | varchar(50) | 组装单号，唯一 |
| warehouse_id | bigint | 仓库 ID |
| sku_id | bigint | 成品 SKU |
| quantity | decimal(18,4) | 组装数量 |
| status | varchar(20) | draft、confirmed、voided |
| total_cost | decimal(18,2) | 总成本 |
| confirmed_at | datetime | 确认时间 |

### assembly_material_item

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| id | bigint | 明细 ID |
| assembly_id | bigint | 组装单 ID |
| material_id | bigint | 物料 ID |
| quantity | decimal(18,4) | 消耗数量 |
| standard_cost | decimal(18,4) | 物料标准成本 |
| amount | decimal(18,2) | 成本金额 |

## 10. 销售表

### sales_order_sequence

按业务日期维护销售单号序列。`business_date` 为主键，`current_value` 范围为 1-9999；销售单号由后端生成，格式为 `SOyyyyMMddNNNN`。

### sales_order

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| id | bigint | 销售单 ID |
| sales_no | varchar(50) | 销售单号，唯一 |
| customer_id/customer_name | bigint/varchar(100) | 客户引用及名称快照 |
| warehouse_id/warehouse_name | bigint/varchar(100) | 出库仓库引用及名称快照 |
| sales_date | date | 业务日期 |
| salesperson_mobile | varchar(30) | 开单业务代表手机号 |
| status | varchar(20) | `draft`、`confirmed`、`void` |
| transport_method | varchar(30) | 自提、送货上门、托运、快递 |
| settlement_cycle | varchar(30) | 月结、日结、季度、年结 |
| payment_method | varchar(30) | 收款方式，第一版可为空 |
| delivery_address | varchar(500) | 送货地址快照 |
| logistics_company/tracking_no | varchar(100) | 物流公司及运单号 |
| package_note | varchar(500) | 包装备注 |
| invoice_required/invoice_status | boolean/varchar(20) | 是否开票及 `not_required`、`pending`、`issued` |
| goods_amount | decimal(18,2) | 商品金额（折扣后） |
| discount_amount | decimal(18,2) | 折扣金额 |
| shipping_fee | decimal(18,2) | 运费 |
| total_amount | decimal(18,2) | 应收合计 |
| received_amount | decimal(18,2) | 已收金额 |
| outstanding_amount | decimal(18,2) | 欠款应收 |
| remark | varchar(1000) | 备注 |
| created_at/updated_at | datetime(3) | 创建及更新时间 |

第一版草稿保存只写入销售表及明细表，不扣库存、不写库存流水、不生成应收或收款记录。确认能力接入后，必须在同一事务中完成库存和财务处理，并保留确认时成本快照。

销售确认补充：确认会在同一事务中扣减 `inventory_balance` 并写入 `inventory_ledger`，作废时反向恢复库存；应收和收款记录待财务模块接入后补齐。

### sales_order_item

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| id | bigint | 明细 ID |
| sales_order_id | bigint | 销售单 ID |
| sku_id | bigint | SKU ID |
| quantity | decimal(18,4) | 销售数量 |
| default_unit_price | decimal(18,2) | SKU 默认售价快照 |
| unit_price | decimal(18,2) | 本单实际售价 |
| discount_rate | decimal(7,4) | 折扣率，0-100 |
| amount | decimal(18,2) | 明细金额 |
| standard_cost_snapshot | decimal(18,4) | 草稿/确认时标准成本快照 |
| item_no_snapshot | varchar(100) | 产品货号快照 |
| product_name_snapshot | varchar(200) | 产品名称快照 |
| sku_code_snapshot/sku_name_snapshot | varchar(100/200) | SKU 编码和名称快照 |
| specification_snapshot | varchar(500) | 规格快照 |
| packaging_snapshot | varchar(200) | 包装方式快照 |
| carton_quantity_snapshot | int | 装箱数快照 |
| barcode_snapshot | varchar(100) | 条形码快照 |
| sales_unit_snapshot | varchar(30) | 销售单位快照 |

## 11. 财务表

### receivable

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| id | bigint | 应收 ID |
| source_type | varchar(50) | sales |
| source_id | bigint | 来源销售单 |
| customer_id | bigint | 客户 ID |
| amount | decimal(18,2) | 应收金额 |
| received_amount | decimal(18,2) | 已收金额 |
| status | varchar(20) | unpaid、partial、paid、voided |

### payable

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| id | bigint | 应付 ID |
| source_type | varchar(50) | purchase |
| source_id | bigint | 来源采购单 |
| supplier_id | bigint | 供应商 ID |
| amount | decimal(18,2) | 应付金额 |
| paid_amount | decimal(18,2) | 已付金额 |
| status | varchar(20) | unpaid、partial、paid、voided |

### payment_record

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| id | bigint | 收付款 ID |
| direction | varchar(20) | receive、pay |
| related_type | varchar(50) | receivable、payable、expense |
| related_id | bigint | 关联记录 ID |
| amount | decimal(18,2) | 金额 |
| payment_method | varchar(50) | cash、wechat、alipay、bank、other |
| paid_at | datetime | 收付款时间 |

### expense

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| id | bigint | 费用 ID |
| expense_no | varchar(50) | 费用单号，唯一 |
| expense_type | varchar(50) | 费用类型 |
| amount | decimal(18,2) | 金额 |
| occurred_at | datetime | 发生时间 |
| status | varchar(20) | confirmed、voided |

## 12. 平台导入与看板表

### platform_import_batch

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| id | bigint | 导入批次 ID |
| platform | varchar(30) | 平台 |
| shop_name | varchar(100) | 店铺名称 |
| import_type | varchar(50) | order、fee、attendance |
| file_name | varchar(255) | 原始文件名 |
| total_rows | int | 总行数 |
| success_rows | int | 成功行数 |
| failed_rows | int | 失败行数 |
| status | varchar(20) | processing、success、partial_failed、failed |

### platform_sales_order

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| id | bigint | 平台订单 ID |
| batch_id | bigint | 导入批次 ID |
| platform | varchar(30) | 平台 |
| shop_name | varchar(100) | 店铺名称 |
| external_order_no | varchar(100) | 平台订单号 |
| order_time | datetime | 下单时间 |
| buyer_name | varchar(100) | 买家 |
| total_amount | decimal(18,2) | 订单金额 |
| platform_fee | decimal(18,2) | 平台费用 |
| status_text | varchar(100) | 平台原始状态 |

### platform_sales_item

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| id | bigint | 平台订单明细 ID |
| platform_order_id | bigint | 平台订单 ID |
| external_sku_code | varchar(100) | 平台 SKU 编码 |
| sku_id | bigint | 关联内部 SKU，可为空 |
| item_name | varchar(255) | 商品名称 |
| quantity | decimal(18,4) | 数量 |
| amount | decimal(18,2) | 金额 |

约束：

- 同一平台、同一店铺、同一外部订单号不可重复导入。
- 平台 SKU 可先为空，后续通过映射补齐内部 SKU。

## 13. 排班考勤表

### attendance_shift

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| id | bigint | 班次 ID |
| shift_name | varchar(50) | 班次名称 |
| start_time | time | 上班时间 |
| end_time | time | 下班时间 |
| status | varchar(20) | enabled、disabled |

### attendance_record

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| id | bigint | 考勤记录 ID |
| employee_id | bigint | 员工 ID |
| shift_id | bigint | 班次 ID |
| attendance_date | date | 考勤日期 |
| check_in_time | datetime | 上班时间 |
| check_out_time | datetime | 下班时间 |
| status | varchar(20) | normal、late、early_leave、absent、leave |
| source | varchar(20) | manual、import |

## 14. 库存与财务一致性规则

- `inventory_balance.quantity` 不允许小于 0，除非管理员开启临时负库存配置；第一版默认不允许负库存。
- 每次库存余额变化必须同时写入 `inventory_ledger`。
- 单据确认和库存流水写入必须在同一个事务内完成。
- 销售单确认时保存当时的 SKU 标准成本，后续成本变更不影响历史销售毛利。
- 采购单确认后生成应付，销售单确认后生成应收。
- 收付款记录更新应收应付已结金额和状态。
- 作废已确认单据时生成反向库存流水和反向财务记录。
