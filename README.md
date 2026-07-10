# BeBefish ERP

公司内部电商 ERP 项目，按模块化单体架构开发。

## 技术栈

- Backend: Java 21, Spring Boot 3, Maven, MySQL 8, Redis
- Frontend: Vue 3, TypeScript, Vite, Tailwind CSS
- Workflow: `main` + `develop` + `feature/*`

## 当前阶段

当前进入阶段 1：系统基础与登录权限。`main` 已合并阶段 1 登录基础和登录页 UI 调整，但阶段 1 尚未全部完成。

已落地：

- 后端认证基础接口：发送验证码、手机号密码登录、手机号验证码登录、退出登录、当前用户信息。
- 前端登录页：黑白背景、左侧交互动效、右侧登录表单。
- 登录页模式切换位于密码/验证码输入区下方两端，使用文字展示。
- 手机号、密码和验证码字段使用 `MOBILE`、`PASSWORD`、`SMS CODE` 标签。
- 本地开发账号、内存验证码和内存令牌用于阶段 1 联调。
- 前后端登录相关自动化测试。

待继续：

- MySQL 用户、角色、权限、菜单持久化。
- Redis 验证码、发送频率限制和令牌/会话存储。
- 后台主页布局、路由守卫、登录过期统一处理。
- 角色权限管理页面和菜单权限分配。
- 飞书 OAuth 登录暂不启用，仅保留禁用态入口和后续字段边界。

阶段 1 历史开发分支：

```text
feature/auth-login-permission
```

## 本地开发

后端需要 JDK 21 和 Maven：

```bash
cd backend
mvn spring-boot:run
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

```bash
cd frontend
npm install
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

## 本地登录账号

本地开发默认账号用于阶段 1 联调：

```text
手机号：13800138000
密码：Admin@123456
短信验证码：123456
```

## 当前认证实现说明

当前认证基础用于开发联调：

- 用户、短信验证码和访问令牌暂使用内存实现。
- 前端登录令牌保存在浏览器 `localStorage`，key 为 `bebefish_access_token`。
- 生产目标仍是 MySQL 保存用户和权限，Redis 保存验证码、发送频率限制和短期会话数据。
- 飞书登录只展示禁用态预留入口，不调用飞书 OAuth。
