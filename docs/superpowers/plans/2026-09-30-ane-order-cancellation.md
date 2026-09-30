# 安能取消订单 Implementation Plan

> **For agentic workers:** Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox syntax for tracking.

**Goal:** 发货详情支持取消已成功创建、尚未揽收的安能订单，并同步物流状态。

**Architecture:** 沿用单发货单单物流订单与持久化请求预约机制，网络请求在数据库事务之外执行。取消使用原下单快照与当前服务器凭据，`action=10`，签名格式与下单一致；`/update` 从已配置 `/new` 同环境地址派生。

**Tech Stack:** Java 21 / Spring Boot / MySQL Flyway / Vue 3 / Vitest。

**Spec:** 用户当前请求与[安能电子面单接口文档](https://itxuqiu.yuque.com/gzlcs4/uucisb/zulotg?singleDoc)第 2.2 节。

## Global Constraints

- 开发阶段仅本地修改；2026-10-01 用户授权推送当前远程分支、构建并部署线上。发布仍不操作真实安能订单。
- 物流与备货状态独立；取消成功清除发货单有效单号，物流记录保留原单号。
- 测试订单不能使用正式接口取消，反之亦然。
- 取消中、取消待核实、已取消的重复请求不能再次发送；已取消发货单不可再次下单。
- 明确拒绝允许人工再次发起，超时/未知/矛盾响应保留待核实。
- 使用独立 `shipping:cancel` 权限，默认分配超级管理员。

## Review Focus

- 重复点击/并发请求不能发送两次取消。
- 取消成功与拒绝/未知必须分别处理有效单号。
- 原订单资料或环境不一致时阻止请求。
- HTTP 超时后页面不能继续显示可提交按钮。
- 老的下单与备货编辑锁定行为保持一致。

### Task 1: 取消协议与持久化流程

Files: `shipping/logistics/AneOrderClient.java`, `AneOrderService.java`, `AneOrderStore.java`, `AneProperties.java`, `api/LogisticsOrderController.java`, 新增 `CancelAneOrderRequest.java` / `AneCancellationResult.java`，`V25__ane_order_cancellation.sql`。

Interfaces: `cancelOrder(Map<String,Object>) -> AneCancellationResult`; `cancel(long,CancelAneOrderRequest,String) -> LogisticsOrder`; `claimCancellation(long,long,String)` / `finishCancellation(long,AneCancellationResult,String)`。

- [x] 先写 HTTP 签名/成功/拒绝/异常、取消权限与数据库状态独立、重复预约/版本/环境/快照测试。
- [x] 执行测试确认缺失功能失败。
- [x] 实现 `cancel_processing`, `cancel_rejected`, `cancel_unknown`, `cancelled`，取消成功清除 shipment.tracking_no，物流记录保留。
- [x] 对真实数据库与本地 HTTP 替身执行测试，禁止对安能进行操作。

### Task 2: 页面操作与同步

Files: `frontend/src/features/shipping/{types.ts,httpShippingService.ts,mockShippingService.ts,ShipmentDetailView.vue,ShipmentListView.vue}` 及相关测试。

Interfaces: `cancelLogisticsOrder(id,{version}) -> Promise<LogisticsOrder>`。

- [x] 先写详情确认/取消成功/拒绝/未知/权限与列表单号展示测试，执行确认失败。
- [x] 详情取消入口与确认弹框，防重复提交，取消后重新读取状态，网络失败时隐藏操作直到刷新核实。
- [x] 列表显示已取消并停止展示有效单号，详情保留历史单号；提供刷新状态入口。
- [x] 更新接口说明；运行后端 verify、前端全量测试与生产构建，自查最终 diff。

## 验证记录

- 后端全量 423 项测试通过，0 失败/错误；旧本地进程占用 JAR 导致首次 repackage 失败，停止该进程后同一代码执行 mvn -DskipTests verify 打包通过。
- 前端全量 823 项测试通过，生产构建通过。
- 独立代码评审发现失败响应订单身份遗漏；反例先失败，再修复为统一校验，随后后端全量通过。
- 本地数据库已执行 V25；本地管理员重新登录获得取消权限；确认框在浏览器验证。未发送真实安能取消请求。
- 分支 codex/ane-order-cancellation，修改未提交、未合并、未推送、未部署。

## 2026-10-01 取消待核实排查

- 用户在本地发起取消后的测试单已持久化为 `cancel_unknown`，不是前端刷新问题。原代码未保留响应诊断，无法追溯该次 HTTP / 安能返回内容，用户也无法从安能端核实；原记录保持不变，未重置或重发。
- 补充 HTTP 状态、超时/中断/网络异常、JSON / resultInfo 格式、订单身份及数字返回码的具体提示，并将固定诊断、哈希订单引用、环境、HTTP 状态写入日志；请求/响应正文及异常内容均不记录。
- 回归测试先验证 3 项失败，再通过；后端全量 425 项通过，0 失败/错误/跳过，`mvn verify` 打包通过。本地后端加载新包、连接本地数据库、HTTP 200。
- 此次只有后端诊断和文档改动，未执行新的安能取号或取消操作；下一次测试可保留具体诊断，不能据此声称已确认旧单取消成功。
