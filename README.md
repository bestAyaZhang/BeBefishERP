# BeBefish ERP

公司内部电商 ERP 项目，按模块化单体架构开发。

## 技术栈

- Backend: Java 21, Spring Boot 3, Maven, MySQL 8, Redis
- Frontend: Vue 3, TypeScript, Vite, Tailwind CSS
- Workflow: `main` + `develop` + `feature/*`

## 当前阶段

当前进入阶段 1：系统基础与登录权限。

阶段 1 分支：

```text
feature/auth-login-permission
```

## 本地开发

后端需要 JDK 21 和 Maven：

```bash
cd backend
mvn test
```

本地开发默认账号用于阶段 1 联调：

```text
手机号：13800138000
密码：Admin@123456
短信验证码：123456
```

前端需要 Node.js：

```bash
cd frontend
npm install
npm run test:run
npm run build
```
