# Logistics Management Implementation Plan

> 2026-10-29 主分支集成说明：本计划实施时的物流 V28、V29 已重编号为 V30、V31，保留主分支已发布的客户与成本迁移。下文保留实施时编号；发布顺序以 `docs/logistics-management-operations.md` 为准。

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 管理合作物流、环境隔离的加密账号及运费模板，在发货单按相同条件比价、推荐、选择并使用对应账号下单。

**Architecture:** 新建独立 `logistics` 后端和前端模块，纯计价器不依赖数据库。管理、报价及发货订单选择使用版本化存储；现有安能客户端增加每次操作的账号上下文，不修改全局配置。发货单维持现有备货、库存及物流状态边界。

**Tech Stack:** Vue 3/TypeScript/Tailwind, Java 21/Spring Boot, MySQL/Flyway, AES-GCM, Vitest/JUnit.

**Spec:** `docs/superpowers/specs/2026-10-26-logistics-management-design.md`

## Global Constraints

- 不选择 SKU、不关联或抵扣库存；不发送真实物流创建或取消请求进行测试。
- 费用在后端以十进制数计算，以整数分保存报价金额；未配置不是零元。
- 计价公式 `b + ceil(max(q - a, 0) / c) × d`；区间 `(lowerExclusive, upperInclusive]`，空上限为无穷大。
- 体积 m³ 转 kg 使用 `volume × 1000000 / volumeDivisor`，取实重和体积重较大值。
- 指定省份组先匹配，指定组空档不回退默认；未知省份不回退默认。
- 开发本地库、测试专用测试库、生产库保持现有隔离；生产/测试不自动 Mock 写入或报价。
- 账号测试/正式隔离，密钥不进入明文返回、日志或普通业务快照。
- 报价有效期 10 分钟；无报价允许先存单；仅改备货/备注不要求新报价。
- 公司未接入接口时仍可估价选择保存，但禁止通过安能接口代下单。
- 当前任务不推送远程、不合并主分支、不上线；提交仅留本地功能分支。

## Review Focus

1. 体积重无限小数恰在续量或区间边界：使用有理数交叉比较，不能先显示舍入再计费（Task 1）。
2. 模板/账号停用或更新发生在测算后：新选择和下单拒绝失效版本，但保留历史及非计价编辑（Tasks 2/4）。
3. 用户改报价金额、复用他人报价 ID 或改条件：后端只接受自己生成且匹配的快照（Tasks 2/4）。
4. 输入变化或异步结果倒序：旧结果不能恢复可下单状态，手动选择不被推荐覆盖（Tasks 3/4）。
5. 历史订单原账号未知或合作公司停用：不自动用当前账号取消，管理员核实后绑定原环境账号（Task 4）。

## Shared Contract

The following JSON names are binding for parallel tasks. All new API responses use existing `ApiResponse<T>`; lists use `{records,page,pageSize,total}`. Prices are JSON numbers in yuan; backend quote storage also records `amount_cents`.

Permissions: `logistics:view`, `logistics:edit`, `logistics:configure`, `logistics:template`. Quote creation requires `shipping:view`. Providers are `ANE` and `MANUAL`; status is `enabled`/`disabled`; current runtime environment is `test` for local/test profiles, `production` for prod.

```ts
interface Company { id:number;code:string;name:string;provider:'ANE'|'MANUAL';status:string;contactName:string;contactPhone:string;sortOrder:number;remark:string;version:number;createdAt:string;updatedAt:string }
interface Sender { name:string;phone:string;province:string;city:string;county:string;address:string }
interface AccountInput { version:number;code:string;customerCode:string;dictId:string;appKey?:string;customerPass?:string;orderUrl:string;sender:Sender }
interface AccountView { id:number;companyId:number;environment:string;version:number;accountVersionId:number;code:string;customerCode:string;dictId:string;orderUrl:string;sender:Sender;appKeyConfigured:boolean;customerPassConfigured:boolean;ready:boolean;message:string }
interface Band { lowerExclusive:number;upperInclusive:number|null;firstQuantity:number|null;firstFee:number|null;additionalQuantity:number|null;additionalFee:number|null }
interface RegionGroup { name:string;provinces:string[];bands:Band[] }
interface Rules { mode:'weight'|'piece';volumetric:boolean;volumeDivisor:number;defaultBands:Band[];regionGroups:RegionGroup[] }
interface TemplateInput { name:string;productCode:string;productName:string;originProvince:string;originCity:string;originCounty:string;rules:Rules;status:string;remark:string;version:number }
interface Template extends TemplateInput { id:number;companyId:number;accountId:number|null;updatedAt:string }
interface QuoteInput { senderProvince:string;senderCity:string;senderCounty:string;recipientProvince:string;recipientCity:string;recipientCounty:string;weight:number|null;volume:number|null;pieceAmount:number|null;goodsType:number|null;payType:number|null }
interface Quote { quoteId:string|null;companyId:number;companyName:string;provider:string;accountId:number|null;accountVersionId:number|null;templateId:number|null;templateVersion:number|null;productCode:string;productName:string;amount:number|null;billedQuantity:number|null;chargeMode:string;matchedRegion:string;lowerExclusive:number|null;upperInclusive:number|null;firstQuantity:number|null;firstFee:number|null;additionalQuantity:number|null;additionalFee:number|null;renewalCount:number|null;available:boolean;reason:string;apiReady:boolean;apiMessage:string;recommended:boolean;expiresAt:string|null }
interface QuoteBatch { quotes:Quote[];legacyOrderingAllowed:boolean;generatedAt:string }
interface LogisticsChoice { quoteId:string|null;preferredCompanyId:number|null;clear:boolean }
```

`provinces` in region groups contain the province catalog's six-digit codes. Province catalog responses are `{code,name,group}`. `quoteId` is UUID text. Null `AccountView` means no database account has been saved; absent secret inputs preserve old secrets only for the same account identity. A changed customer identity creates another account; blank secrets cannot transfer old account secrets.

REST endpoints:

- `GET/POST /api/logistics/companies`; `GET/PUT /api/logistics/companies/{id}`; `POST .../{id}/status` with `{status,version}`.
- `GET/PUT /api/logistics/companies/{id}/account`; `GET /api/logistics/companies/{id}/accounts` for historical account metadata, never secrets.
- `GET/POST /api/logistics/companies/{id}/templates`; `GET/PUT /api/logistics/templates/{id}`; `POST /api/logistics/templates/{id}/status` with `{status,version}`.
- `GET /api/logistics/provinces`; `POST /api/logistics/quotes` with `QuoteInput` returning `QuoteBatch`.
- Root integration adds `GET /api/shipments/{id}/freight-selection` returning saved `Quote|null` and `POST /api/shipments/{id}/logistics-account` with `{accountId,version,confirmed:true}` for administrator-only historical linkage.
- Existing shipment create/update bodies gain optional top-level `logisticsChoice`; HTTP frontend strips these UI fields out of `form`. Omitted choice preserves compatible selection, changed price inputs invalidate it. No core `ShipmentFormInput` fields are required merely to quote.

## Task 1: Pure freight rules and province catalog

**Owner/files:** `backend/src/main/java/com/bebefish/erp/logistics/pricing/{FreightRules,FreightCalculator,ProvinceCatalog}.java` and matching tests; no migrations or controllers.

**Interfaces:** `FreightRules` owns nested records `Band`, `RegionGroup`, `Rules`, `Input(destinationProvince,weight,volume,pieces)`, `Result(available,reason,amount,billedQuantity,matchedRegion,band,renewalCount)`. `FreightCalculator.quote(Rules,Input):Result`; `FreightCalculator.validateRules(Rules,boolean publishing):void`; `ProvinceCatalog.normalize(String):String|null`, `ProvinceCatalog.options():List<Province>` with `Province(code,name,group)`. Shared record properties map exactly to the TS contract above. Validation raises the existing `BusinessException` with `VALIDATION_FAILED`.

- [x] Write tests for exact first/renewal boundaries, partial renewal ceiling, amount zero vs missing, targeted province vs default, targeted-group gaps, overlapping bands and provinces, piece integer rules, unknown province, volume missing, sender-independent pure calculations.
- [x] Add a repeating-decimal volumetric boundary test: `volume=0.006`, divisor `6000`, gives 1 kg exactly; compare other rational bounds by cross multiplication. Test displayed rounding never changes fee steps, and high/zero/missing amounts do not overflow silently.
- [x] Run `mvn -Dtest=FreightCalculatorTest,ProvinceCatalogTest test` and observe failure before implementing.
- [x] Implement exact numerator/denominator comparisons: volumetric numerator is `max(weight*divisor,volume*1000000)`, denominator is divisor; subtract/multiply before `divide(...,0,CEILING)`. No database or external calls.
- [x] Run the targeted tests, self-review and commit the task's files only.

## Task 2: Management, encrypted account versions, templates and server quotes

**Owner/files:** `backend/src/main/java/com/bebefish/erp/logistics/` excluding `pricing/`; tests under matching package; migration `V28__logistics_catalog_and_quotes.sql`. Do not edit existing shipment/ANE classes, V29, frontend or global application configuration.

**Interfaces:** Public records in `LogisticsDtos.java` follow Shared Contract. `LogisticsCatalogService` implements management APIs with JDBC and optimistic versions. `FreightQuoteService.quote(QuoteInput,long employeeId):QuoteBatch`, `getValid(String quoteId,long employeeId,QuoteInput):Quote`, `getForOrder(String quoteId,QuoteInput):Quote`, `managedTemplatesEnabled():boolean`. `CarrierAccountResolver.current(long companyId):Optional<ResolvedAccount>`, `resolve(long accountVersionId):ResolvedAccount`; `ResolvedAccount(companyId,accountId,accountVersionId,provider,environment,config)` exposes decrypted internal `AccountInput` only to services. `FreightQuoteService.input(ShipmentFormInput):QuoteInput` converts existing form input for integration. Publishing a template binds its current account when present; a template without API account may still quote but has `apiReady=false`.

- [x] Write failing tests for company CRUD/status/version/duplicate code; independent view/edit/configure/template permissions; masked secret responses and missing encryption master key.
- [x] Add AES-GCM tamper/round-trip tests. Use external `ERP_LOGISTICS_MASTER_KEY` (base64 32 bytes); missing key permits read/manual configuration but rejects writing API secrets. Never create a random transient persistence key.
- [x] Add persistence tests for account identity replacement preserving old records, same-account blank-secret preservation, atomic config snapshots, runtime host/environment validation, active-template uniqueness per account/product/origin, and invalid draft rules not participating in quotes.
- [x] Add quote tests for minimum valid recommendation/tie stability, one bad company's independent unavailable reason, expiry exactly 10 minutes, user ownership, condition mismatch, company/account/template updates and stopped companies.
- [x] Implement V28 tables for companies, account identities, encrypted immutable account versions, template identities/versions, and issued quotes (owner, conditions JSON, result JSON, amount cents, issued/expires dates and source versions). Register all four permissions for SUPER_ADMIN.
- [x] Implement management REST with `ApiResponse` and page responses. Encrypt complete secret payload with fresh GCM nonce; never include it in returned DTOs or logs. Pin ANE HTTPS hosts to `opc.test.ane56.com` / `opc.ane56.com` and the expected `/aneop/opwb/lb/new` path for current environment; MANUAL has no network capability.
- [x] Reuse Task 1 engine; validate origins and product/account binding before quote; persist valid UUID quotes, expose all enabled company/product candidates, and mark lowest valid price recommended even if offline-only.
- [x] Run targeted JUnit tests against `bebefish_platform_test`; no real carrier HTTP. Self-review and commit owned files only; publish any interface adjustments to root before changing the contract.

## Task 3: Logistics management UI and quote-selection component

**Owner/files:** `frontend/src/features/logistics/**`, navigation catalog, router, layout breadcrumbs, permission catalog and corresponding UI tests. Do not edit existing `ShipmentFormView.vue`, shipping types/services or backend files; Task 4 integrates your component.

**Interfaces:** TS `types.ts` implements Shared Contract; `logisticsService` provides list/create/update/status company, get/save/list account, list/create/get/update/status template, getProvinces, `getQuotes(input:QuoteInput):Promise<QuoteBatch>`, `getFreightSelection(id):Promise<Quote|null>`, `confirmLogisticsAccount(id,input)`. All use existing request/createService, mock only local controlled source. `ShipmentQuotePanel.vue` props `{input:QuoteInput,modelValue:Quote|null,disabled?:boolean}`, emits `update:modelValue(Quote|null)`, `loaded(QuoteBatch)`, `pending(boolean)`; auto quote on stable complete input with generation guard and invalidates selected quote immediately on price-input changes. Emits no carrier network calls.

- [x] Write failing tests for logistics menu/permission isolation; company list/new/edit; masked secrets left blank; status/message feedback; unknown region; quote stale response/manual selection retention and disabling old choice while recomputing.
- [x] Build the management page in existing ERP style with company list and company configuration sections (company/account/templates). Separate `CompanyForm`, `AccountForm`, `FreightTemplateEditor`, `ProvinceGroupDialog`, `FreightBandEditor`, `ShipmentQuotePanel` components. Follow screenshot fields and dense full-width rate rows, use current AccessibleDialog and Message.
- [x] Support weight/piece switching, volumetric toggle/coefficient, default enable switch, province groups with move confirmation, multiple range rows/infinity, draft vs enabled validation, product and origin fields. Do not prefill actual prices or treat dummy screenshot fees as real.
- [x] Use backend province catalog; a complete fixed fixture may support local Mock. Preserve backend validation errors and form inputs. Distinguish company, quote, and API readiness; inactive/unpriced rows are not zero prices.
- [x] Add controlled local mock CRUD with version guards and hidden secrets. It can return explicitly marked test quotes but never mark real shipment API ordering as mocked success; test/prod use real service exclusively.
- [x] Add route `shipping/logistics`, name `logistics-management`, requiredPermission `logistics:view`; add submenu under 发货管理 and breadcrumbs, permission labels.
- [x] Run scoped Vitest tests and Vue type checking; self-review and commit only owned files.

## Task 4: Shipment selection, account routing and historical cancellation

**Owner/files:** existing `shipping` backend/frontend, migration `V29__shipment_logistics_selection.sql`, `.env.example` documentation, shipping setup docs, new integration tests; root coordinates these edits after Task 2 interfaces exist.

**Interfaces:** Store issued quote JSON plus references as per-shipment selection in a sidecar table. Expose GET selection separately so existing Shipment record/constructor compatibility is retained. Accept `LogisticsChoice` in save bodies. Add `ShipmentFreightCoordinator` wrapping create/update in one transaction, uses Task 2 validators, controls authoritative `estimatedFreight` and selected company, and clears stale selection on pricing changes. Existing narrow preparation-content API does not reprice.

- [x] Write RED tests for authoritative selected price, changed conditions invalidating active quote, no quote still saved, non-price edits retaining expired historical selection, foreign/forged quote rejection and freezing selection when logistics has been submitted.
- [x] Add carrier-context overloads to client/store/service without mutating `AneProperties`. Persist company/account/config-version refs when claiming a carrier order; validate current selection under the existing transaction/row lock before HTTP, use the same resolved immutable context for parameters/signing/URL and freeze source references.
- [x] Keep old constructor overloads for existing isolated unit tests, but actual Spring routes must use new integration. MANUAL selections return unsupported action and never call ANE; old server-config path exists only for unchanged legacy ANE compatibility before managed template adoption.
- [x] Add administrator-confirmed account linkage for historical orders with missing identity, environment/identity/version guards and audit. Unknown historical account cannot send cancellation; company disable does not erase old configuration needed by confirmed historical requests.
- [x] Wire HTTP save choices and form GET selection; show `ShipmentQuotePanel`, apply chosen product/sender as explicit price-input changes, persist only selected quote ID, and disable API order when quote pending/stale/offline-only. Preserve create-only save and content-only owner edit paths. Detail shows company, historical predicted amount and basis independently from actual weight and prep status.
- [x] Test new integration with controlled/mocked client: expired/template-changed quote causes zero calls; managed ANE request uses selected account and environment; historical cancellation uses confirmed original account after current account changes; duplicate reservation stays single call.
- [x] Run full frontend Vitest, production real-source build, full backend `mvn verify` on dedicated local test database, and migration tests updated to V29. Package in ignored release workspace if the live local JAR is locked. Test real local UI without creating a real carrier order. Preserve UI acceptance records; screenshot capture was attempted but Ego capture timed out and native capture had no target window.
- [x] Fresh whole-branch review; fix material findings, rerun affected verification, complete plan ledger. Leave tested changes on local feature branch pending user's push/merge instructions.

## Execution Rulings

- User approved design and requested continued development; execute continuously using parallel specialists for Tasks 1–3 and root integration for Task 4, without another workflow-selection approval prompt.
- Only root runs shared full suites and updates global progress; workers run focused tests, coordinate Maven timing, and do not stage or commit other workers' changes.
- If the runtime adds provider-specific fields, retain existing ANE product/weight/volume validation. Alternative providers without adapters stay honest about offline-only handling.
