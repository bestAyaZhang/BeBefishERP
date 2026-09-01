# BeBefish ERP

公司内部电商 ERP 项目，按模块化单体架构开发。

## 技术栈

- Backend: Java 21, Spring Boot 3, Maven, MySQL 8, Redis
- Frontend: Vue 3, TypeScript, Vite, Tailwind CSS
- Workflow: `main` + `develop` + `feature/*`

## 当前阶段

当前已完成后台主页与商品模块重构；本地联调设置 `VITE_DATA_SOURCE=real` 后使用真实 API。

已落地：

- 登录、路由守卫、登录过期处理和全高后台壳层。
- 实时工作台指标、趋势图与近期订单。
- 商品分类树、商品/SKU 搜索、分页和横向表格浏览。
- 商品详情页，以及六步商品新建/编辑流程。
- 多 SKU、供应商报价、统一或按 SKU 包装参数和图片上传。
- 分类、供应商、库存和销售单据相关页面。

主要路由：

```text
/login
/dashboard
/products
/products/new
/products/:id
/products/:id/edit
```

## 本地开发

后端需要 JDK 21 和 Maven：

```powershell
cd backend
$env:ERP_DB_URL = 'jdbc:mysql://localhost:3306/<database>'
$env:ERP_DB_USERNAME = '<username>'
$env:ERP_DB_PASSWORD = '<password>'
mvn -DskipTests package
java -jar target/bebefish-erp-0.1.0-SNAPSHOT.jar
```

后端测试：

```bash
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

## 本地登录账号

使用仓库现有的本地开发管理员账号联调。账号凭据应通过本地配置管理，不要写入代码或提交到 Git。

## 当前认证实现说明

当前认证基础用于开发联调：

- 用户、短信验证码和访问令牌暂使用内存实现。
- 前端登录令牌保存在浏览器 `localStorage`，key 为 `bebefish_access_token`。
- 生产目标仍是 MySQL 保存用户和权限，Redis 保存验证码、发送频率限制和短期会话数据。
- 飞书登录只展示禁用态预留入口，不调用飞书 OAuth。
