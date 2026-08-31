# 安全架构设计（Security Architecture）

> 本文档定义 **「纵深防御」** 视角下的安全设计：认证、授权、输入输出防护、敏感数据保护、审计与密钥治理，并给出**可落地的检查清单**。
> 配套文档：[总体架构](00-architecture-overview.md) · [健壮性设计](architecture-robustness.md) · [扩展性设计](architecture-scalability.md) · [数据与存储设计](architecture-data.md) · [研发全流程](../process/00-overview.md)。

---

## 1. 安全目标与总体模型

安全遵循 **纵深防御（Defense in Depth）**：任何一层被绕过，仍有下一层兜底。

```
客户端 ──HTTPS──► 网关(集中鉴权/限流/防刷/审计) ──内网──► 业务服务(@PreAuthorize/数据权限/脱敏/审计) ──► DB/Redis/MinIO
                        │                                        │
                     JWT 校验                              SQL 参数化 / 输入校验 / 越权校验
```

| 层次 | 防线 | 落地组件 |
|------|------|----------|
| 传输层 | HTTPS/TLS、HSTS | 网关与 LB 终止 TLS |
| 接入层 | 集中鉴权、限流、防刷、审计 | `ruoyi-gateway` + Sentinel |
| 认证层 | JWT 签发/校验、会话管理 | `ruoyi-auth` + `ruoyi-common-security` |
| 授权层 | RBAC + 数据权限 + 细粒度注解 | `ruoyi-common-security` + `ruoyi-common-datascope` |
| 数据层 | SQL 参数化、脱敏、加密存储 | MyBatis `#{}` + `ruoyi-common-sensitive` |
| 治理层 | 依赖漏洞扫描、密钥外置、审计日志 | CI/CD + `ruoyi-common-log` |

---

## 2. 认证设计：网关集中鉴权 + JWT 认证中心

### 2.1 模型

- **单一认证入口**：`ruoyi-auth`（认证中心，端口 9200）负责登录、令牌签发/刷新/校验；`ruoyi-gateway`（8080）负责**集中鉴权**——解析并校验所有外部请求的 Token。
- **无状态令牌**：JWT（HS256，密钥由环境变量注入），载荷仅含用户标识/登录名/过期时间等**非敏感**信息，不承载权限明细（权限实时查库/缓存，避免改权限后旧令牌仍有效）。
- **会话双重校验**：除 JWT 签名外，网关还会校验 Redis 中是否存在对应会话（支持主动踢下线/注销）。
- **内网信任**：服务间 OpenFeign 调用携带内部标识，业务服务仅信任来自网关的转发请求（网关过滤外部直连）。

```
UI → Gateway(校验 JWT + Redis 会话) → 注入用户上下文 → Auth(/login 签发) → 下游服务读取 SecurityContext
```

### 2.2 规范

- 登录接口限流 + 验证码（防爆破）；失败次数达阈值锁定/延迟。
- 令牌过期走 `refresh_token` 刷新，刷新令牌可撤销。
- 修改密码/踢人/封禁后，删除 Redis 会话使旧令牌立即失效。
- 白名单路径（如登录、验证码、健康检查）在网关显式声明，其余一律鉴权。

---

## 3. 授权设计：RBAC + 数据权限 + 细粒度注解

### 3.1 RBAC（角色-权限）

- 用户 → 角色 → 菜单/按钮权限（`sys_user` / `sys_role` / `sys_menu` 体系），权限编码如 `system:user:add`。
- 权限校验统一用注解，禁止在业务代码中手工 `if` 判断权限字符串：

```java
@PreAuthorize("@ss.hasPermi('system:user:add')")   // 写操作必须校验
@Log(title = "用户管理", businessType = BusinessType.INSERT)
```

### 3.2 数据权限（DataScope）

- `ruoyi-common-datascope` 提供数据范围控制：全部 / 本部门 / 本部门及以下 / 仅本人 / 自定义。
- Service 层通过 `DataScope` 注解声明数据范围，MyBatis 拦截器自动拼接过滤条件（`user_id`、`dept_id` 归属），**避免漏加导致越权查看他人数据**。

### 3.3 权限设计要点

- 权限最小化：默认拒绝，按需放行；列表/详情/编辑/删除分别校验。
- 权限变更实时生效（权限数据走缓存 + 失效通知，不依赖令牌内快照）。
- 内部 Feign 接口与对外 REST 接口分离命名（如 `/inner/**`），外部不可达。

---

## 4. 输入校验

- 所有对外接口入参使用 Bean Validation：`@Validated` + `@NotNull/@NotBlank/@Size/@Pattern` 等，并支持自定义校验注解（如枚举校验、手机号格式）。
- 校验逻辑放 DTO/VO 注解上，**禁止**在 Controller 内堆 `if`。
- 长度、数值范围、枚举白名单全覆盖；分页参数做上限钳制（如 `pageSize ≤ 100`）。
- 校验失败统一返回 `R.fail` 风格的参数错误码（见 [健壮性设计](architecture-robustness.md) 错误码规范）。

---

## 5. SQL 注入防护

- **一律使用 MyBatis 参数化 `#{}` 占位**；`${}` 仅限动态表名/列名等无法参数化的场景，且必须经过**白名单**校验。
- 排序字段、查询列名通过枚举/映射表白名单转换，禁止直接拼接前端入参。
- 使用 Druid 的 SQL 防火墙（`wall`）作为纵深兜底，拦截危险 SQL。
- 代码评审与 CI 扫描（如 `gitleaks`、语义分析）禁止 `$ { }` 拼接查询。

```xml
<!-- 正确：参数化 -->
<select id="selectUser" resultType="SysUser">
    SELECT * FROM sys_user WHERE user_name = #{userName}
</select>
```

---

## 6. XSS 防护

- **输出侧**：前端 Vue 默认转义；富文本场景使用白名单过滤（如 DOMPurify），禁止 `v-html` 渲染不可信内容。
- **存储侧**：入库前对危险字符（`<script>` 等）按字段语义做转义或过滤（JSON 字段、富文本字段策略不同）。
- **请求侧**：网关/过滤器对请求参数做 XSS 清洗（保留合法富文本标签白名单）。
- 安全响应头：`X-Content-Type-Options: nosniff`、`X-Frame-Options: DENY`（或 CSP）、`Content-Security-Policy` 由网关统一注入。

---

## 7. CSRF 防护

- 系统采用 **Token 无状态会话**（JWT 放请求头而非 Cookie），天然免疫传统 CSRF。
- 若某接口必须使用 Cookie（如监控面板），开启 CSRF Token 校验（Spring Security `CsrfFilter`）并限定 `SameSite=Strict/Lax`。
- 跨域写操作（CORS 预检）只放行白名单来源。

---

## 8. 越权防护（水平/垂直）

- **垂直越权**：每个接口校验角色/权限（3.1 注解），防止低权限调用高权限接口。
- **水平越权（IDOR）**：按 ID 操作资源时，必须校验资源归属（`dept_id` / `user_id` / 创建人），典型模式：
  1. 查询/修改前校验「数据权限 + 归属」；
  2. 列表查询强制走 DataScope 过滤，不允许绕过 Service 直查 Mapper；
  3. 批量接口校验每个 ID 的归属，禁止批量越权。
- 接口幂等 + 状态机校验（如只能删除自己创建且未提交的草稿），防止基于状态的越权流转。
- 审计日志记录操作者与资源 ID，便于事后追查。

---

## 9. CORS

- 生产环境**默认关闭**全局 CORS，仅由网关对白名单域名开启（`allowed-origins` 精确匹配，禁止 `*` + `allow-credentials` 组合）。
- 敏感接口只接受同源或白名单来源；自定义请求头（`Authorization`）纳入允许列表。
- 前端代理（Vite dev proxy）转发到网关，避免开发期放开 CORS。

---

## 10. 敏感数据脱敏（ruoyi-common-sensitive）

- `ruoyi-common-sensitive` 提供字段级脱敏：手机号、身份证、银行卡、邮箱、地址、姓名等规则。
- 用法：DTO/VO 字段标注 `@Sensitive(type = SensitiveType.MOBILE)`，序列化时自动脱敏（中间打码），**敏感字段绝不原样返回前端**。

```java
@Sensitive(type = SensitiveType.MOBILE)
private String phonenumber;   // 138****1234
```

- 规则：
  - 默认返回脱敏值；需要明文的下游（内部服务）使用独立接口并校验内网标识 + 权限。
  - 数据库中敏感字段按需加密存储（如 AES），密钥由环境变量注入，禁止硬编码。
  - 日志打印对象时使用脱敏后的 toString / 日志模板，禁止打印完整敏感字段。

---

## 11. 日志审计（ruoyi-common-log）

- 写操作（增删改、登录、导出、审批）统一记录操作日志：操作人、IP、接口、参数（敏感字段脱敏）、结果、耗时。
- 登录日志记录成功/失败与失败原因，供安全分析。
- 审计日志只增不改不删，保存周期满足合规要求，接入监控告警（异常登录、越权尝试、批量导出）。
- 禁止将密码、Token、密钥写入任何日志。

---

## 12. 接口幂等

- 对重复触发的写操作（支付、审批、回调、导入）使用**幂等键**（前端生成 UUID / 业务单号），服务端幂等表或 Redis 去重（见 [健壮性设计](architecture-robustness.md) 幂等章节）。
- 幂等校验在 Service 层统一处理，Controller 不重复实现。

---

## 13. 密钥与凭证管理

| 凭证 | 管理方式 |
|------|----------|
| JWT 密钥 | 环境变量 `JWT_SECRET`，生产用独立强随机密钥，定期轮换 |
| 数据库/Redis 密码 | Nacos 配置占位符 `${MYSQL_PASSWORD}` + 环境变量；本地 `local-env.yml`（已 gitignore） |
| Nacos 账号 | 独立账号最小权限，禁用默认 admin 弱口令 |
| MinIO 密钥 | 环境变量注入，访问凭证按桶最小化授权 |
| 第三方密钥 | 统一放配置中心，禁止提交仓库 |

- **禁止硬编码**：任何密钥不得进入提交的代码/配置；CI 增加密钥扫描（`gitleaks`）门禁。
- Agent 与成员不得将密钥回显到日志或外部工具（见 [AGENTS.md](../../AGENTS.md) §1）。

---

## 14. 传输安全（HTTPS/TLS）

- 网关/LB 终止 TLS，生产强制 HTTPS（HTTP 301 跳转），启用 HSTS。
- TLS 版本 ≥ 1.2，证书自动化续期（ACME）；内部服务间建议 mTLS 或至少内网隔离 + 防火墙。
- 服务间调用不允许明文穿越公网；`ruoyi-file` 生成的 MinIO 预签名 URL 限时有效（如 15 分钟）。

---

## 15. 依赖漏洞治理

- 统一依赖版本（父 POM + BOM），锁定已知安全版本（Spring Boot 3.3.5 / Spring Cloud 2023.0.3 / SCA 2023.0.1.2）。
- CI 集成依赖扫描（`OWASP Dependency-Check` / Trivy 镜像扫描），高危漏洞阻断发布。
- 定期升级安全补丁，保留升级记录（`release-notes`），对 Log4j/Jackson/Spring 等组件高危公告 24h 内响应。
- 生成 SBOM（软件物料清单）随发布归档。

---

## 16. 常见攻击清单与应对

| 攻击 | 应对措施 | 关联章节 |
|------|----------|----------|
| 暴力破解登录 | 验证码 + 登录限流 + 失败锁定 | §2、§3 |
| SQL 注入 | `#{}` 参数化 + 白名单 + Druid wall | §5 |
| XSS | 输出转义 + 富文本白名单 + 响应头 | §6 |
| CSRF | 无状态 Token + SameSite + 白名单 | §7 |
| 水平越权 | 资源归属校验 + DataScope | §8 |
| 垂直越权 | `@PreAuthorize` 权限注解 | §3 |
| 敏感数据泄露 | 脱敏 + 加密存储 + 日志脱敏 | §10、§11 |
| 令牌盗用/重放 | 短时 Token + Redis 会话 + 刷新令牌 | §2 |
| 接口刷量/DoS | Sentinel 限流 + 网关防刷 | §2、[扩展性](architecture-scalability.md) |
| 依赖漏洞 | 依赖扫描 + 补丁门禁 | §15 |

---

## 17. 安全落地检查清单（Checklist）

- [ ] 网关集中鉴权开启，白名单最小化，无绕过网关的外部直连
- [ ] JWT 密钥/数据库密码等全部走环境变量，仓库内无硬编码（CI 密钥扫描通过）
- [ ] 所有写接口有 `@PreAuthorize` + `@Log`，列表查询走 DataScope
- [ ] 所有对外入参 `@Validated` 校验；无 `$ {}` 拼接 SQL（评审 + 扫描）
- [ ] 敏感字段按 `ruoyi-common-sensitive` 规则脱敏，日志不打印明文
- [ ] CORS 白名单精确配置；生产启用 HTTPS + 安全响应头
- [ ] 支付/审批/回调等写操作实现幂等
- [ ] 依赖扫描无高危漏洞，SBOM 已归档
- [ ] 安全回归用例（越权/注入/爆破/脱敏）纳入 CI 或发布前检查

---

## 18. 相关文档

| 文档 | 主题 |
|------|------|
| [00-architecture-overview.md](00-architecture-overview.md) | 总体架构与调用链路 |
| [architecture-robustness.md](architecture-robustness.md) | 幂等/熔断/异常体系 |
| [architecture-data.md](architecture-data.md) | 数据加密/备份/索引 |
| [../process/00-overview.md](../process/00-overview.md) | 研发全流程总览（设计→开发→测试→验收→运维） |
