# BeBefish ERP

公司内部电商 ERP 项目，按模块化单体架构开发。

## 技术栈

- Backend: Java 21, Spring Boot 3, Maven, MySQL 8, Redis
- Frontend: Vue 3, TypeScript, Vite, Tailwind CSS
- Workflow: `main` + `develop` + `feature/*`

## 当前阶段

当前已完成后台主页、商品模块以及持久化身份认证与权限管理；本地开发默认优先使用真实 API 和本地数据库，接口不可用时允许只读 Mock 降级。

已落地：

- 正式员工飞书 OAuth 登录、临时员工密码登录、持久会话与登录审计。
- 飞书员工自动建档、企业隔离、飞书业务角色到 ERP 角色映射。
- 登录、路由守卫、登录过期处理和全高后台壳层。
- 实时工作台指标、趋势图与近期订单。
- 商品分类树、商品/SKU 搜索、分页和横向表格浏览。
- 商品详情页，以及六步商品新建/编辑流程。
- 多 SKU、供应商报价、统一或按 SKU 包装参数和图片上传。
- 分类、供应商、库存和销售单据相关页面。
- 发货管理：文本备货台账、收件信息智能识别、状态/日期筛选，以及在发货单内完成安能电子面单取号（需配置店铺、账号和寄件人）。配置及验收见 [发货管理与安能接入](docs/shipping-ane-setup.md)。

主要路由：

```text
/login
/workbench
/products
/products/new
/products/:id
/products/:id/edit
/permissions
/auth/feishu/result
```

## 本地开发

后端需要 JDK 21 和 Maven：

```powershell
cd backend
$env:ERP_DB_URL = 'jdbc:mysql://localhost:3306/<database>'
$env:ERP_DB_USERNAME = '<username>'
$env:ERP_DB_PASSWORD = '<password>'
mvn spring-boot:run
```

默认不开启飞书。无需真实凭据的本地 OAuth 闭环只允许在 `local` 或 `test` Profile 使用：

```powershell
$env:SPRING_PROFILES_ACTIVE = 'local'
$env:ERP_FEISHU_ENABLED = 'true'
$env:ERP_FEISHU_APP_ID = 'mock-app'
$env:ERP_FEISHU_APP_SECRET = 'mock-secret'
$env:ERP_FEISHU_REDIRECT_URI = 'http://127.0.0.1:5173/api/auth/feishu/callback'
$env:ERP_FEISHU_ALLOWED_TENANT_KEY = 'tenant-a'
$env:ERP_FEISHU_MOCK_ENABLED = 'true'
$env:ERP_LOCAL_ADMIN_ENABLED = 'true'
$env:ERP_LOCAL_ADMIN_MOBILE = '13800138000'
$env:ERP_LOCAL_ADMIN_PASSWORD = 'Admin@123456'
mvn spring-boot:run
```

模拟凭据仅是本地开关所需的非敏感占位值。生产 Profile 会拒绝模拟客户端。真实企业接入见 [飞书企业验收说明](docs/feishu-enterprise-acceptance.md)，密钥只通过部署环境或密钥管理系统注入。

在 Windows 的 Unicode / 非 ASCII 工作区路径下，如果 `mvn spring-boot:run` 生成的 argfile 或 classpath 出现乱码，优先使用已在此类路径中验证过的可执行 JAR 启动方式：

```powershell
mvn -DskipTests package
java -jar target/bebefish-erp-0.1.0-SNAPSHOT.jar
```

后端测试需要独立 MySQL 测试库：

```powershell
$env:ERP_TEST_DB_URL = 'jdbc:mysql://127.0.0.1:3306/bebefish_test?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai'
$env:ERP_TEST_DB_USERNAME = '<test-username>'
$env:ERP_TEST_DB_PASSWORD = '<test-password>'
mvn test
```

测试启动器会拒绝数据库名不以 `_test` 结尾的 JDBC URL，避免集成测试误连开发或生产库。

后端默认地址：

```text
http://localhost:8080
```

前端需要 Node.js：

```powershell
cd frontend
npm install
$env:VITE_RUNTIME_ENV = 'local'
$env:VITE_DATA_SOURCE = 'auto'
npm run dev
```

前端默认地址：

```text
http://localhost:5173
```

开发环境 `auto` 模式先访问本地后端；仅在网络不可达或后端明确返回“接口不存在”/未实现时，读取操作才降级为 Mock。真实接口返回空列表、登录或权限失败、业务错误及写入失败都不会降级。也可以显式设置 `VITE_DATA_SOURCE=real` 禁止降级，或在纯界面开发时设置 `mock`。Mock 数据不会写入数据库。

后端在 Flyway 迁移前校验数据库目标。未设置 Profile 时按 `local` 处理，使用 `ERP_DB_*`，只允许连接本机非测试数据库；本地测试设置 `SPRING_PROFILES_ACTIVE=test`，使用独立的 `ERP_TEST_DB_*`，数据库必须位于本机且库名以 `_test` 结尾。线上设置 `SPRING_PROFILES_ACTIVE=prod`，使用独立的 `ERP_PROD_DB_*`，同时配置 `ERP_PROD_DB_HOST`、`ERP_PROD_DB_NAME` 为实际线上库的主机和库名；连接目标必须精确匹配且不能是回环地址。测试和线上前端分别设置 `VITE_RUNTIME_ENV=test`、`VITE_RUNTIME_ENV=prod`，并统一设置 `VITE_DATA_SOURCE=real`，禁止 Mock 或自动降级。生产构建也要求 `test` 或 `prod` 运行环境。`SPRING_PROFILES_ACTIVE` 与数据库凭据须在启动进程的环境变量中设置；根目录 `.env.example` 只是模板。

前端测试和测试环境构建：

```powershell
npm run test:run
$env:VITE_RUNTIME_ENV = 'test'
$env:VITE_DATA_SOURCE = 'real'
npm run build
```

## 图片存储

默认使用后端本地 `uploads/` 目录。线上部署七牛云时，通过环境变量切换，不要把 AK/SK 写入代码或提交到 Git：

```bash
ERP_FILE_STORAGE_PROVIDER=qiniu
QINIU_ACCESS_KEY=你的AK
QINIU_SECRET_KEY=你的SK
QINIU_BUCKET=gd-goods-img
QINIU_DOMAIN=https://img.example.com
# 可选：z0、z1、z2、na0、as0；不填时由 SDK 自动识别
QINIU_REGION=z0
```

`QINIU_DOMAIN` 填写该 Bucket 绑定的正式域名，用于记录上传文件的来源 URL；线上不要使用有期限的七牛测试域名。读取图片时，后端会根据 Bucket 自动查询七牛源站、生成短时下载签名，并通过同域 `/api/files/content/` 地址向浏览器提供图片，避免 HTTPS 页面加载 HTTP 图片时被浏览器拦截。已有本地图片不会自动迁移到七牛云，已保存的七牛 URL 无需迁移。

## 登录策略

- 正式员工只能通过本企业飞书登录；首次成功登录会创建或安全合并员工与账号。
- 临时员工只能使用本地手机号和密码；不能绑定飞书身份。
- ERP 权限始终由 ERP 角色定义。飞书映射角色与本地附加角色取并集，敏感角色禁止自动映射。
- 会话、OAuth state、一次性登录票据和登录审计均保存在 MySQL；数据库只保存令牌哈希。

通用数据库迁移不会创建默认管理员。`local` / `test` Profile 只有在显式设置 `ERP_LOCAL_ADMIN_ENABLED=true` 并提供手机号、密码时才初始化本地临时管理员，登录页不展示凭据；`prod` 与 `local` / `test` 同时启用会直接拒绝启动。

飞书 Contact v3 不提供业务角色列表查询。真实环境需要把管理员确认的不可变角色 ID 和显示名配置为 `ERP_FEISHU_BUSINESS_ROLES`，格式为逗号分隔的 `role_id|显示名`；系统用单成员查询判断登录员工的角色，仅在管理页同步统计时分页读取角色成员。

## 配置

环境变量模板见 [`.env.example`](.env.example)。该文件不包含真实凭据；不要把 App Secret、访问令牌或生产数据库密码提交到仓库。
