# 汇森指定揽收业务员实施计划

> **For agentic workers:** Use superpowers:executing-plans or subagent-driven-development; complete the checked steps with focused tests.

**Goal:** 按用户提供的电子面单 PDF，配置并传递汇森的指定揽收业务员工号与姓名，使用用户补齐的测试参数联调。

**Architecture:** 复用现有 YIMI 平台适配器，账号的 yimi 公共配置增加 designatedPickup、salesmanCode、salesmanName。业务员配置是可更新的揽收设置，不属于商家身份，不改变账号身份哈希或转移密钥。下单业务快照冻结实际提交值。普通壹米旧账号无需指定业务员；汇森/汇霖开启指定揽收后，下单必须补全工号及姓名。原订单取消、查询不以后来新增的揽收设置为前提。

**Tech Stack:** Spring Boot/Java21/MySQL、Vue3/TypeScript、JUnit/Vitest。

**Spec:** 本文件的用户要求、协议约束及共享契约为既有电子面单设计的增量补充。

## 用户要求及协议约束

- 用户明确要求汇森下单传 salesmanCode/salesmanName，测试商家及订购参数已补齐；测试值只写本地测试配置，正式值由合作网点提供，不预置到产品代码。
- PDF 将 salesmanCode 标为汇森/汇霖必填，姓名按本次合作要求一起传；字段为根级字符串，最长50，保留工号前导0。designatedPickup 只用于 ERP 校验，不发送给物流平台。
- 文档的旧 HTTP 网关/version v1.0 与已验证的当前平台 HTTPS/version1.0 有冲突。保留受控 HTTPS/1.0，不向未核实的旧HTTP地址传密钥；受控测试环境实际响应再作为联调依据。
- 汇森指定揽收模式的 settlementType 按 PDF 发送字符串；普通壹米既有数值映射保持兼容。其余更宽的字段限制/服务方式不在本次指定揽收增量中静默扩展。
- 不新增物流协议 provider 或修改安能；公司名称可配置为汇森，协议类型仍为 YIMI。无新数据库列，配置保存于既有版本化 JSON。
- 测试请求只使用测试网关、用户提供测试账号及收寄件资料。一次取号取得明确结果后查询并取消；不确定结果禁止重发。记录实际响应，不伪造成功。无推送、合并或部署。

### 实际联调后的取消协议补充

2026-10-28，原通用取消服务在首张测试单上未获得可确认的取消结果，该单保留 cancel_unknown，不重试、不换协议重发。PDF 给汇森电子面单定义 operatorType=cancel，与原普通壹米独立 cancelOrder 不同；给汇森新增文档定义的电子面单取消路径，使用原已冻结的完整业务报文，改 operatorType=cancel 并附原运单号。不能依据后来修改的账号开关决定历史单取消协议；以原业务快照中的指定业务员字段识别该订单模式。用一张独立的新测试发货单验证，绝不重试旧待核实单。原单状态已请求汇森对接人核查。

电子取消成功仍需外层 success=true、业务 status=0、outSideNo 与 wayBillNo 匹配；错误或异常保持待核实并给出脱敏的错误码/简短返回说明。取消使用 ready() 的历史凭据，不要求后来揽收配置完整。禁止协议回退或请求重试。

## 共享契约

- YimiAccount(String sellerId,String subscriptionId,boolean designatedPickup,String salesmanCode,String salesmanName)，保留原2参构造，默认false/空串。账号标准化时，任一业务员字段有值也启用指定揽收。
- YimiProperties 在原6参后追加同3字段，保留6参兼容构造；ready()仍表示原商家凭据可用，orderReady()另校验指定揽收的工号、姓名。根级参数仅在有值时发送；超50/单边缺失拒绝接口下单。
- 账号的秘密保留比较只依赖 sellerId/subscriptionId，业务员变化保持同一身份。账号 view/报价 apiReady 使用 orderReady；取消/轨迹仍使用原凭据就绪规则。
- 前端 YimiAccount 同名可选字段；配置表单提供“指定揽收业务员（汇森/汇霖）”开关及两项输入，开启时标必填，允许保存不完整配置但提示无法下单，不清空既有密钥。

## Tasks

- [x] Backend: RED account round-trip/leading-zero/identity-key-preservation/incomplete-ready + payload tests; implement record/config/routing/payload split; GREEN affected catalog/protocol/shipping tests.
- [x] Frontend (parallel exclusive paths): RED form serialization/loaded fields/identity notice tests; fields/type/mock public projection; GREEN affected logistics tests and type check.
- [x] Root: independent review, package/restart isolated test preview, configure real supplied test values only in test DB; perform electronic-order/trace/cancel attempts safely; report actual result and leave local commits only.
- [ ] External acceptance: 汇森对接人确认两张测试运单的取消实际状态及正确取消响应契约。实际电子取消响应缺少业务 status，未达到确认条件；不据此推断成功或失败，不再次提交这两张单。

## 验证与结果

- 后端新配置 RED：6用例中5项预期失败；第一次受影响回归49项通过。电子取消客户端 RED：21用例中6项预期失败，GREEN21项通过；路由 RED：11用例中2项预期失败。最终受影响后端90项通过并完成打包。
- 前端 RED：12项预期失败；最终90文件1029项通过，类型检查和构建通过。实际浏览器验证业务员工号前导零、姓名及密钥替换框不回显；验收标签页已关闭。
- 两次测试环境取号均成功，使用用户提供的账号、业务员、收寄件资料；轨迹返回暂无节点。两次取消分别只提交一次，均保留待核实。第二次电子面单取消返回对象缺少文档列出的业务 status；没有把字段缺失当作成功。
- 仅使用独立测试库；未连接生产物流、未改变生产配置。临时零元测试费率已停用。测试单号、实际返回和测试参数留在被Git忽略的本地联调目录，产品代码不预置这些值。
- 指定揽收配置、原快照取消及防重复逻辑均经独立代码复核；真实取消验收仍是外部待办。本次不推送、不合并、不部署。
