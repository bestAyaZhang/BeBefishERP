# 壹米滴答电子面单实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development or superpowers:executing-plans. Track steps with checkboxes.

**Goal:** 在现有物流管理中安全配置、选择壹米电子面单，支持取号、取消和只读轨迹。

**Architecture:** 独立壹米协议客户端和参数构建器，复用既有不可变账号与报价、事务预约和状态存储。provider 分派置于现有发货集成边界，安能旧构造与协议保持兼容。

**Tech Stack:** Vue 3, TypeScript, Spring Boot, Java 21, MySQL 8, JUnit, Vitest.

**Spec:** docs/superpowers/specs/2026-10-27-yimidida-electronic-waybill-design.md

## Global Constraints

- Provider `YIMI`; only electTabelSaveOrder, cancelOrder, queryTrace. Never call ordinary saveOrder/queryByWaybillNo as a fallback.
- Sandbox/production exact pinned HTTPS gateways from spec; version1.0; unsigned public TreeMap JSON UTF8 MD5 raw bytes→Base64. Never log secrets or whole envelopes.
- Missing real sellerId/subscriptionId blocks live ordering only; no guessed example IDs. Tests never send real carrier requests.
- Old ANE/MANUAL contracts and constructor calls stay compatible; only new V30/V31 migrations.
- Credentials encrypted/versioned, account identity frozen; uncertain create/cancel cannot retry automatically.
- All Maven/test DB work is sequential and uses bebefish_platform_test. No operational DB clean. No remote push/main merge/deployment.

## Shared Contracts

- Root creates `shipping.domain.YimiOrderOptions(Integer visitDelivery,Integer deliveryFlag,String floorNo)` and appends optional `@Valid YimiOrderOptions yimi` to AneOrderDraft with the original nine-argument constructor delegating null. Normalization retains options.
- Backend account task appends `String appSecret,YimiAccount yimi` to AccountInput with old constructor. `YimiAccount(String sellerId,String subscriptionId)` is nested in LogisticsDtos. AccountView appends `boolean appSecretConfigured,YimiAccount yimi` with old constructor. All secret projections omit appSecret.
- QuoteInput appends optional YimiOrderOptions yimi with old constructor; canonical all-empty options normalize null, nonempty options participate in the condition key. Frontend mirrors optional yimi.
- Client task owns `shipping.logistics.yimi.YimiProperties(boolean testEnvironment,URI gatewayUrl,String appKey,String appSecret,String sellerId,String subscriptionId)` with redacted toString, pinned static URLs/validator and ready(). Catalog consumes its public URL validation.
- `YimiOrderClient.createOrder(Map<String,Object> body,YimiProperties account,String expectedOutsideNo): YimiOrderResult`; `cancelOrder(Map<String,Object> body,YimiProperties account): AneCancellationResult`; `queryTrace(String waybillNo,YimiProperties account): YimiTraceResult`. `YimiOrderResult(AneOrderResult order,Map<String,Object> metadata)`; `YimiTraceResult(String status,String message,List<TraceEvent> events)`, nested event(time,status,location,description); statuses available/empty/unavailable/not_supported.
- Client task owns `YimiPayloads.orderParams(ShipmentContent content,String outsideNo,YimiProperties account,String productCode): Map<String,Object>` and validateOrderContent(content,productCode). Explicit visit option; payload/validation follows spec.
- Generic trace GET `/api/shipments/{id}/logistics-trace`, shipping:view, returns ApiResponse<YimiTraceResult>; frontend getLogisticsTrace(id). Provider `YIMI` added to LogisticsOrder response via compatible constructor, default ANE for historical rows.

## Review Focus

1. Same app but seller/subscription changed must not inherit secrets or history; task2 test.
2. Carrier returned foreign external/tracking identifiers must not mark success or expose foreign trace; task1 test.
3. Changed pickup/elevator options invalidates quotes without repricing notes-only edits; task2 and task3 integration tests.
4. Company disabled/current account changed after order: cancellation/trace still use original identity, only same-account key rotation; root integration test.
5. Existing ANE drafts/snapshots with no yimi fields deserialize and preserve all old behavior; root regression suite.

## Task 1 Protocol client and payloads

**Owner:** client worker. New package shipping/logistics/yimi and tests there only. **Consumes:** root YimiOrderOptions/AneOrderDraft methods and existing content/result records. **Produces:** exact client/properties/results/payload contracts above.

- [ ] Write failing signature test against an independent known digest; controlled HTTP mock verifies exact sorted envelope, timestamp, body spelling, explicit options, no ANE signing reuse.
- [ ] Write failing result tests for success/status0/outsideNo/tracking match, explicit rejection, malformed/foreign/timeout/duplicate ambiguity, strict single-order cancellation counts, trace empty/foreign filtering.
- [ ] Write payload precision/limit/payment mapping and serviceType/delivery contradiction tests; missing seller/subscription or explicit pickup cannot order.
- [ ] Implement minimal isolated client without production URL bypasses; use injected HttpClient with mocks so no real endpoints are called.
- [ ] Run targeted tests under Maven lock, self-review, commit owned files. Fresh scoped review.

## Task 2 Provider accounts quotes and migration

**Owner:** backend account worker. LogisticsDtos/CatalogService/FreightQuoteService, new V30, logistics tests only. **Consumes:** Task1 YimiProperties pinned URLs and root domain options. **Produces:** encrypted YIMI accounts, ready quotes, DTO changes above.

- [ ] RED tests provider migration/admission, credential masking/encryption, missing config, same identity blanks, changed app/seller/subscription new identity with no secret carryover, environment pins and old ANE regression.
- [ ] RED tests Yimi API readiness, service product1/2 matches goodsType180/179 vs285, yimi canonical condition invalidation, offline quotes remain independent of API config.
- [ ] Implement V30 provider check expansion and app_secret_configured column; preserve applied migrations. Add compatible record constructors and safe audit metadata.
- [ ] Run scoped tests with no schema clean except root-coordinated dedicated reset for new migration, self-review, commit owned files. Fresh scoped review.

## Task 3 Management shipping UI

**Owner:** frontend worker. All frontend/src/features/logistics and shipping only; no backend/root files. **Consumes:** HTTP/shared contracts. **Produces:** account fields, explicit Yimi order options, trace UI and generic provider labels.

- [ ] RED tests company provider selection, Yimi-only credentials and blank preservation, environment label, missing IDs disables API but not quote/draft saving.
- [ ] RED tests Yimi product1/2 template choices, explicit pickup, elevator/floor for upstairs, payment meaning, constraints and quote input yimi invalidation. Preserve real QuotePanel historical/hand-selection tests.
- [ ] Add shipping service trace endpoint and safe mock; YIMI detail trace loading/empty/error retry displays time/status/location/description and never alters prep/order state.
- [ ] Implement corresponding provider-aware fields/labels without redesigning existing workflow; keep current create-only and notes-only permissions.
- [ ] Run scoped Vitest and vue-tsc, self-review, commit owned paths. Fresh scoped review.

## Task 4 Root integration and verification

**Owner:** root. Shipping backend/domain/controllers/services/store/ManagedRouting, V31, integration tests, migration expectations, docs.

- [ ] TDD root YimiOrderOptions and compatible draft constructor/normalization, retaining options in all copy helpers.
- [ ] Extend store with explicit testEnvironment overloads rather than fake ANE config for Yimi; new V31 carrier_provider/business_snapshot/response_metadata. Return compatible provider in LogisticsOrder.
- [ ] Dispatch managed placement by provider, validate before claim, persist business-only exact frozen Yimi payload before outbound HTTP, freeze account/version/company. Use Task1 result and store generic success/company override.
- [ ] Cancel with original frozen waybill/account and latest same-identity credentials, no alternate protocol fallback. Expose read-only trace from original account, return not_supported for ANE.
- [ ] Add real MySQL controlled-client integration tests for Yimi order+unknown/repeat, changed options, secret rotation/current-account replacement, original account cancel/trace, MANUAL never network, ANE regression.
- [ ] All affected tests, frontend full/build, full backend verify and migration tests. Stop isolated QA backend before destructive migration tests. Local UI with dummy or missing credentials only, no live order until real required IDs arrive.
- [ ] Fresh whole-change review and fix loops; update ledger; local commits only. Report missing live parameters separately from completed local implementation.
