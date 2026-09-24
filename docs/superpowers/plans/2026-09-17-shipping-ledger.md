# Shipping Ledger Implementation Plan

> 历史方案：当前实现以 [2026-09-24 发货管理 Figma 流程计划](2026-09-24-shipping-figma-flow.md) 为准。本文件保留首版台账与安能接入的决策记录。

> **For agentic workers:** Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox syntax for tracking.

**Goal:** 提供可保存、查询和修改的独立发货台账，并接入用户补充的安能电子面单下单协议。

**Architecture:** shipping 模块独立存储发货记录，复用现有认证与 ApiResponse。Vue 页面通过 serviceFactory 切换 HTTP 和开发 Mock，使用现有对话框组件。

**Tech Stack:** Java 21 / Spring Boot 3 / JdbcTemplate / MySQL / Vue 3 / TypeScript / Vitest。

**Spec:** docs/superpowers/specs/2026-09-17-shipping-ledger-design.md

## Global Constraints

- 不关联库存、不选择 SKU、不扣减库存。
- 备货内容为自由文本，四种状态人工选择。
- 不实现未经文档定义的外部下单请求。
- 保留工作区已有的导航和导航测试修改。

## Task 1: 持久化台账 API

Files: 新建 backend 的 shipping/domain、application、infrastructure、api；迁移 V12__shipping_ledger.sql；shipping 对应测试。

Interfaces: `ShipmentContent` 为业务字段，`Shipment` 包含 id/shipmentNo/content/version/审计字段；`ShipmentService.create/update/get/list`；GET/POST `/api/shipments` 和 GET/PUT `/api/shipments/{id}`。

- [x] 添加服务和接口测试：非法状态/负数/空收件人返回校验错误；不存在返回 404；旧版本返回 409；备货换行和未知重量保留。
- [x] 运行新增测试观察缺少实现造成的失败。
- [x] 实现模型、校验、参数化分页查询、版本比较更新与权限迁移。
- [x] 运行新增后端测试并编译。

## Task 2: 列表、新建和编辑

Files: frontend/src/features/shipping/{types,shippingService,httpShippingService,mockShippingService,ShipmentListView,ShipmentEditorDialog,ShipmentDetailDialog}；路由、菜单、权限目录。

Interfaces: `ShippingService.list/get/create/update` 对应后端接口；`ShipmentQuery` 包含 page/size/keyword/status/dateFrom/dateTo/platform/incompleteOnly。

- [x] 先写页面测试：录入多行文本后重新查询、切换全部未完成、修改状态、API 失败保留表单、查看权限不显示编辑。
- [x] 运行测试确认缺少页面/服务造成失败。
- [x] 实现服务、列表和对话框，增加菜单与路由权限；物流未配置时提供禁用状态及说明。
- [x] 运行 shipping、路由、侧栏与权限测试。

## Task 3: 用户补充文档后的安能下单

Files: shipping/logistics 包、LogisticsOrderController、V13__ane_logistics_order.sql、ShipmentLogisticsPanel.vue、配置模板及 docs/shipping-ane-setup.md。

- [x] 根据用户补充的电子面单文档核对 JSON envelope、MD5 十六进制再 Base64 的签名、字段限制及响应。
- [x] 完成本地受控 HTTP 服务器测试，未请求安能网络。
- [x] 完成持久化预约与防重复下单、未知结果阻断、正式运单回填、测试环境隔离。
- [x] 下单表单和独立权限接入，测试结果刷新和重复点击行为。
- [x] 代码审查并修复测试环境持久标识、刷新台账与表单校验恢复问题。

## Task 4: 验证与交付

- [x] 运行前端完整测试和 `npm run build`：620 项测试通过，类型检查和生产构建通过。
- [x] 运行新增后端测试：25 项通过，编译通过。当前没有 ERP_TEST_DB 配置，Docker 引擎未运行，未执行真实 MySQL 迁移/锁/并发验证。
- [x] 浏览器验证列表、新建、多行文本、详情、状态编辑、全部未完成筛选和缺少物流配置时的按钮状态。
- [x] 自查差异并记录配置/验收边界。保留原有用户修改；临时浏览器验收入口和服务器已清理。

正式启用仍需安能账号、密钥、寄件人和正式 URL，以及专用数据库迁移和安能测试账号联调。没有向安能创建实际订单。
