# 飞书企业登录接入与验收

本文只列出把 BeBefish ERP 接入真实飞书企业所需的最小管理员操作。任何 App Secret、用户访问令牌或刷新令牌都不要粘贴到对话、工单或 Git 仓库。

## 1. 创建和授权企业自建应用

1. 在目标飞书企业中创建企业自建应用，并启用网页 OAuth 登录能力。
2. 添加 ERP 的 OAuth 回调地址：本地联调可使用后端可访问地址；生产必须使用 ERP 公网 HTTPS 同域下的 `/api/auth/feishu/callback`。
3. 将应用可用范围限制为允许使用 ERP 的正式员工。
4. 申请并由企业管理员审批以下只读能力：企业信息、用户基本信息（包括手机号）、用户所属部门、飞书业务角色成员关系。
5. 在管理后台的角色管理页面复制需要接入 ERP 的不可变业务角色 ID，并记录对应显示名。
6. 发布应用版本，使权限与回调配置在企业内生效。

## 2. 注入部署配置

通过部署平台的环境变量或密钥管理系统设置：

```dotenv
ERP_FEISHU_ENABLED=true
ERP_FEISHU_APP_ID=<企业自建应用 App ID>
ERP_FEISHU_APP_SECRET=<只保存在密钥系统中的 App Secret>
ERP_FEISHU_REDIRECT_URI=https://erp.example.com/api/auth/feishu/callback
ERP_FEISHU_ALLOWED_TENANT_KEY=<唯一允许登录的企业 tenant_key>
ERP_FEISHU_BUSINESS_ROLES=<role_id|显示名，多个角色用逗号分隔>
ERP_FEISHU_MOCK_ENABLED=false
```

生产启动前同时确认：

- `SPRING_PROFILES_ACTIVE` 不包含 `local` 或 `test`。
- 回调 URL 与飞书开放平台登记值逐字符一致，并可从用户浏览器访问。
- 前端使用 `VITE_DATA_SOURCE=real`，API 地址指向同一 ERP 后端。
- `ERP_FEISHU_BUSINESS_ROLES` 中只配置已核对的角色 ID；系统不会创建、修改或删除飞书角色。
- 飞书返回多个所属部门时，ERP 不把数组第一项猜作主部门，部门/岗位资料保持待完善，直到有明确映射来源。

服务启动时会校验飞书配置和当前应用所属企业；不完整配置、企业不匹配或生产启用 Mock 都会阻止错误配置继续运行。

## 3. 最小验收清单

1. 访问 `GET /api/auth/feishu/status`，确认只返回可用状态和提示，不返回 App ID、App Secret 或令牌。
2. 在“权限管理 → 飞书角色映射”同步角色，将一个非敏感飞书业务角色映射到普通 ERP 角色。
3. 使用目标企业正式员工扫码：确认首次登录自动建档、获得 `BASIC_EMPLOYEE` 和已映射角色、进入工作台。
4. 移除该员工的飞书业务角色后再次登录：确认对应 `FEISHU` 来源角色被撤销，而手工分配的 `LOCAL` 角色保留。
5. 临时制造飞书角色接口不可用：确认登录仍成功，只保留基础/本地角色，并显示 `FEISHU_ROLE_SYNC_DEGRADED` 提示。
6. 使用另一个飞书企业发起 OAuth：确认收到 `FEISHU_TENANT_NOT_ALLOWED`，且 ERP 未新增员工、账号或飞书身份。
7. 注销后用原访问令牌调用 `/api/auth/me`：确认返回 401；重复交换同一登录票据也必须失败。
8. 检查 `sys_login_audit`、`sys_auth_session`：成功与失败有审计记录，数据库中只存在令牌哈希，不存在明文访问令牌。

## 4. 回滚开关

紧急关闭飞书登录时，将 `ERP_FEISHU_ENABLED=false` 并重启后端。临时员工密码入口仍可使用；已有 ERP 角色和映射数据不会被删除。恢复前先修正配置或飞书侧授权，再重新开启。
