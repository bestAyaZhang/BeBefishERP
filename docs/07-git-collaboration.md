# Git 多人协作与版本迭代规范

## 1. 目标

本规范用于支持多名开发同时参与 ERP 项目，减少代码冲突，保证每个阶段都有可回溯的版本记录。

适用范围：

- 前端 Vue + TypeScript + Tailwind CSS。
- 后端 Java Spring Boot。
- 数据库迁移脚本。
- 项目文档和需求变更。

## 2. 分支模型

| 分支 | 用途 | 规则 |
| --- | --- | --- |
| `main` | 生产稳定分支 | 只合并已上线或准备上线的稳定版本 |
| `develop` | 日常集成分支 | 所有功能分支先合并到这里联调 |
| `feature/*` | 功能开发分支 | 每个需求或模块一个分支 |
| `fix/*` | 缺陷修复分支 | 修复测试或生产问题 |
| `release/*` | 发版准备分支 | 阶段性验收、修复发版前问题 |
| `hotfix/*` | 生产紧急修复 | 从 `main` 拉出，修复后合回 `main` 和 `develop` |

推荐流程：

```text
main
  ↑
release/v1.0.0
  ↑
develop
  ↑
feature/auth-login
feature/product-master-data
feature/inventory-purchase
```

## 3. 分支命名

功能分支：

```text
feature/auth-login
feature/product-master-data
feature/inventory-purchase
feature/assembly-sales
feature/dashboard-finance
```

修复分支：

```text
fix/login-sms-code-expired
fix/inventory-negative-check
```

发版分支：

```text
release/v0.1.0
release/v1.0.0
```

紧急修复：

```text
hotfix/v1.0.1-inventory-ledger
```

## 4. 提交规范

提交信息使用以下格式：

```text
<type>: <short summary>
```

常用类型：

| 类型 | 说明 | 示例 |
| --- | --- | --- |
| `feat` | 新功能 | `feat: add sms login` |
| `fix` | 修复缺陷 | `fix: prevent negative inventory` |
| `docs` | 文档 | `docs: add phased development plan` |
| `refactor` | 重构 | `refactor: split inventory service` |
| `test` | 测试 | `test: add purchase confirmation tests` |
| `chore` | 构建、配置、杂项 | `chore: configure git ignore` |
| `style` | 代码格式 | `style: format sku form` |

要求：

- 每次提交只解决一个明确问题。
- 不把格式化、重构、功能开发混在同一个提交。
- 涉及库存、财务、单据状态的提交必须写清影响范围。
- 涉及数据库结构变化必须同时提交迁移脚本和文档更新。

## 5. 多人开发流程

### 5.1 开始功能开发

```bash
git switch develop
git pull --rebase
git switch -c feature/product-master-data
```

### 5.2 日常同步

```bash
git fetch origin
git rebase origin/develop
```

如果 rebase 冲突，先解决冲突，再运行测试，通过后继续：

```bash
git add <resolved-files>
git rebase --continue
```

### 5.3 提交功能

```bash
git add .
git commit -m "feat: add product master data"
git push -u origin feature/product-master-data
```

### 5.4 合并规则

- 功能分支必须通过 Pull Request 或 Merge Request 合并。
- 至少 1 名开发或负责人 Review 后才能合并。
- 合并前必须通过构建和测试。
- 不直接向 `main` 或 `develop` 推送业务代码。

## 6. 阶段与版本号

版本号使用语义化版本：

```text
主版本.次版本.修订版本
```

第一版建议：

| 版本 | 对应阶段 | 内容 |
| --- | --- | --- |
| `v0.1.0` | 阶段 1 | 登录、权限、项目基础 |
| `v0.2.0` | 阶段 2 | 基础资料、产品、SKU、物料、BOM |
| `v0.3.0` | 阶段 3 | 库存、采购入库、应付付款 |
| `v0.4.0` | 阶段 4 | 组装入库、线下销售开单 |
| `v0.5.0` | 阶段 5 | 财务流水、经营看板 |
| `v0.6.0` | 阶段 6 | 平台导入、轻量考勤 |
| `v1.0.0` | 阶段 7 | 第一版正式上线 |

打标签：

```bash
git tag -a v0.1.0 -m "release: v0.1.0 auth and permissions"
git push origin v0.1.0
```

## 7. 目录协作建议

正式开发后建议目录：

```text
backend/      Java Spring Boot 后端
frontend/     Vue 3 + TypeScript 前端
docs/         需求、架构、数据库、开发规范
scripts/      部署、备份、初始化脚本
deploy/       Docker Compose、Nginx、环境配置模板
```

职责拆分：

- 前端开发主要负责 `frontend/`。
- 后端开发主要负责 `backend/`。
- 数据库迁移脚本由后端负责，但必须和需求文档同步。
- 产品和业务规则变更先更新 `docs/`，再进入开发。

## 8. 冲突控制

- 每个功能分支尽量只修改相关模块。
- 大型模块按阶段拆分，避免多人同时改同一个核心文件。
- 公共接口、枚举、数据库表结构变化必须提前通知团队。
- 前后端接口变化必须同步更新接口文档或 OpenAPI。
- 合并前先同步 `develop`，减少集成冲突。

## 9. 禁止事项

- 禁止直接在 `main` 上开发。
- 禁止把 `.env`、密码、密钥、短信服务商密钥提交到仓库。
- 禁止提交 `node_modules/`、`target/`、`dist/`、上传文件和备份文件。
- 禁止未测试就合并库存、财务、单据状态相关代码。
- 禁止多人共用同一个功能分支长期开发。

## 10. 建议的远程仓库初始化命令

创建远程仓库后，在本地执行：

```bash
git remote add origin <远程仓库地址>
git push -u origin main
git switch -c develop
git push -u origin develop
```

之后开发人员从远程仓库克隆：

```bash
git clone <远程仓库地址>
cd <项目目录>
git switch develop
```

