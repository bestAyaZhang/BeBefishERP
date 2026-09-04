# BeBefish ERP

公司内部电商 ERP 项目，按模块化单体架构开发。

## 技术栈

- Backend: Java 21, Spring Boot 3, Maven, MySQL 8, Redis
- Frontend: Vue 3, TypeScript, Vite, Tailwind CSS
- Workflow: `main` + `develop` + `feature/*`

## 当前阶段

当前已完成后台主页、商品模块以及持久化身份认证与权限管理；本地联调设置 `VITE_DATA_SOURCE=real` 后使用真实 API。

已落地：

- 正式员工飞书 OAuth 登录、临时员工密码登录、持久会话与登录审计。
- 飞书员工自动建档、企业隔离、飞书业务角色到 ERP 角色映射。
- 登录、路由守卫、登录过期处理和全高后台壳层。
- 实时工作台指标、趋势图与近期订单。
- 商品分类树、商品/SKU 搜索、分页和横向表格浏览。
- 商品详情页，以及六步商品新建/编辑流程。
- 多 SKU、供应商报价、统一或按 SKU 包装参数和图片上传。
- 分类、供应商、库存和销售单据相关页面。

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
$env:ERP_TEST_DB_URL = 'jdbc:mysql://127.0.0.1:3306/<test-database>?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai'
$env:ERP_TEST_DB_USERNAME = '<test-username>'
$env:ERP_TEST_DB_PASSWORD = '<test-password>'
mvn test
```

后端默认地址：

```text
http://localhost:8080
```

前端需要 Node.js：

```powershell
cd frontend
npm install
$env:VITE_DATA_SOURCE = 'real'
npm run dev
```

前端默认地址：

```text
http://localhost:5173
```

前端测试和构建：

```bash
npm run test:run
npm run build
```

## 图片存储

默认使用后端本地 `uploads/` 目录。线上部署七牛云时，通过环境变量切换，不要把 AK/SK 写入代码或提交到 Git：

```bash
ERP_FILE_STORAGE_PROVIDER=qiniu
QINIU_ACCESS_KEY=你的AK
QINIU_SECRET_KEY=你的SK
QINIU_BUCKET=gd-goods-img
QINIU_DOMAIN=https://tigd0hqp4.hn-bkt.clouddn.com
# 可选：z0、z1、z2、na0、as0；不填时由 SDK 自动识别
QINIU_REGION=z0
```

`QINIU_DOMAIN` 必须是该 Bucket 可访问的域名；如果空间是私有空间，还需要增加私有下载签名方案，当前版本按公开图片 URL 设计。已有本地图片不会自动迁移到七牛云。

## 登录策略

- 正式员工只能通过本企业飞书登录；首次成功登录会创建或安全合并员工与账号。
- 临时员工只能使用本地手机号和密码；不能绑定飞书身份。
- ERP 权限始终由 ERP 角色定义。飞书映射角色与本地附加角色取并集，敏感角色禁止自动映射。
- 会话、OAuth state、一次性登录票据和登录审计均保存在 MySQL；数据库只保存令牌哈希。

通用数据库迁移不会创建默认管理员。`local` Profile 会显式初始化本地临时管理员（默认仅供联调，可通过 `ERP_LOCAL_ADMIN_MOBILE` 和 `ERP_LOCAL_ADMIN_PASSWORD` 覆盖），登录页不展示凭据；生产环境不得启用 `local` 或 `test` Profile。

飞书 Contact v3 不提供业务角色列表查询。真实环境需要把管理员确认的不可变角色 ID 和显示名配置为 `ERP_FEISHU_BUSINESS_ROLES`，格式为逗号分隔的 `role_id|显示名`；系统通过官方角色成员分页接口计算成员数并判断登录员工的角色。

## 配置

环境变量模板见 [`.env.example`](.env.example)。该文件不包含真实凭据；不要把 App Secret、访问令牌或生产数据库密码提交到仓库。
