# 开发阶段（Development）

> 本文档定义 **「环境准备 → 编码 → 编译验证 → 联调/自测 → 提交 → MR 评审（Gate G2）」** 阶段的规范与检查点，输入来自 [01-design.md](01-design.md)（冻结的接口与数据模型）。
> 关联文档：[流程总览](00-overview.md) · [总体架构](../architecture/00-architecture-overview.md) · [AGENTS.md](../../AGENTS.md)（最高约束，本文档是其落地细则） · [workflow-management](workflow-management.md)

---

## 1. 阶段目标与出口条件

| 项 | 说明 |
|----|------|
| 输入 | G1 冻结的设计文档、openapi.yaml、schema.sql |
| 产出 | 代码、单测、Nacos 配置模板、SQL 迁移、模块文档更新 |
| 出口条件（Gate G2） | **编译通过 + 单测通过 + 自测通过**，MR 评审合并 |

---

## 2. 环境准备

### 2.1 工具版本（与 [README](../../README.md) §4.1 一致）

| 工具 | 版本 | 用途 |
|------|------|------|
| JDK | 17（LTS） | 编译运行，`JAVA_HOME` 指向 JDK 17 |
| Maven | 3.9+ | 构建，配阿里云镜像可加速 |
| Node.js | 18+（Vite） | 前端构建 |
| Docker | 可选 | 一键拉起依赖 |
| IDE | IDEA / VS Code | 建议开启 Save Actions（格式化） |

### 2.2 基础设施（本地）

```bash
# 一键拉起 Nacos / MySQL / Redis / MinIO / Sentinel / Seata
docker compose -f docker/docker-compose.yml up -d nacos mysql redis minio sentinel seata
```

- Nacos 2.x：服务注册 + 配置中心；`config/` 下为配置模板，按 `<service>-<profile>.yml` 下发。
- Redis 6+：缓存/会话/验证码；MySQL 8：`ry-cloud` 与 `ry-flowable` 两个库（初始化见 [README](../../README.md) §4.3）。

### 2.3 本地配置

- 敏感项（数据库密码、JWT 密钥、Nacos 账号）用环境变量占位，写入 **`local-env.yml`**（已 gitignore），**禁止硬编码进提交**（[AGENTS.md](../../AGENTS.md) §8）。
- 服务启动同时 `import optional:nacos:...` 与 `optional:file:./local-env.yml`，保证本地缺省可运行。

---

## 3. 开发规范（摘要，完整见 AGENTS.md §4）

### 3.1 分层与职责

```
controller/   — REST 接口：只做参数校验、调用 Service、返回结果（禁止业务逻辑/操作 Mapper）
service/      — 接口（I 前缀，如 IXxxService）；service/impl/ — 实现（XxxServiceImpl）
domain/       — 实体类；domain/vo/ — VO/DTO；domain/bo/ — 强类型入参
mapper/       — MyBatis Mapper 接口；config/ — 配置类
```

- Controller 只注入 Service 接口（推荐构造器注入）。
- 返回强类型，禁止散落 `Map<String, Object>`；泛型推断失败用 `R.<TaskResult>ok(...)`。

### 3.2 命名与注释

- 类/方法/变量驼峰；常量全大写；包名小写；SQL/表结构见 [01-design.md](01-design.md) §4。
- **DTO/VO 必须标准 getter/setter**，参照 `SysUser`；**禁止 builder 链式**（`XxxDTO.ok().field(x)`）。
- **每个字段必须有 `/** 注释 */`**；方法必须有 Javadoc 说明语义、参数与返回值。

### 3.3 异常与安全（不可省略）

- 业务异常用 `ServiceException`，禁止裸 `RuntimeException`；新异常注册到 `ruoyi-common-core/exception/` 并在 `GlobalExceptionHandler` 增加 `@ExceptionHandler`。
- 入参校验：`@Validated` + `@NotNull/@NotBlank/@Size` + 自定义注解，禁止 Controller 内大面积 `if`。
- 所有写操作：`@PreAuthorize` 权限校验 + `@Log` 操作日志；支付/审批/回调类写操作加幂等键或乐观锁；敏感字段按 `ruoyi-common-sensitive` 脱敏。

---

## 4. 编译验证流程（硬性规则）

> 每次修改 `.java` 后**必须立即**编译，失败即修复直到通过（[AGENTS.md](../../AGENTS.md) §1.1）。

```bash
# 单模块编译
mvn compile -pl ruoyi-modules/ruoyi-system -am -q 2>&1

# 多模块联调
mvn compile -pl ruoyi-modules/ruoyi-system,ruoyi-modules/ruoyi-workflow -am -q 2>&1

# 修改公共 API（ruoyi-common-core / ruoyi-api）后：先 install 再编译依赖方
mvn -pl ruoyi-common/ruoyi-common-core,ruoyi-api -am install -DskipTests
mvn compile -pl ruoyi-modules/ruoyi-system -am -q 2>&1
```

- 通过后输出 `✅ 编译通过` 再继续；CI 同样执行该检查（见 [03-testing.md](03-testing.md) §8）。

---

## 5. 接口联调（网关 / Feign）

1. **网关联调**：前端经 `http://localhost:8080/prod-api/<service-prefix>/...` 访问，网关校验 token 并路由；`POST /login` 拿 token 后放入 `Authorization: Bearer <token>`。
2. **服务间调用**：跨服务契约定义在 `ruoyi-api`（如 `ruoyi-api-system`），用 OpenFeign + Nacos 负载均衡调用；**禁止绕过网关直连、禁止服务间 HTTP 裸调用**。
3. **联调检查**：接口路径与 openapi.yaml 一致；下游异常能正确透传（Feign 解码 → 业务码）；超时/降级配置生效（Sentinel）。

---

## 6. 前端开发（Vue3 + Element Plus）

- 技术栈：Vue 3 + Vite + Pinia + Vue Router 4 + Element Plus（`ruoyi-ui/`）。
- 目录约定：`src/api/` 封装接口（与后端契约一一对应）、`src/views/` 页面、`src/store/` 状态。
- 页面开发遵循设计阶段原型；接口调用统一走 `src/utils/request.js`（注入 token、统一错误处理）。

```bash
cd ruoyi-ui
npm install
npm run dev        # 本地开发，代理 /prod-api 到网关 8080
npm run lint       # ESLint 校验（.vue/.js/.ts）
npm run build      # 生产构建，有错即修
```

> 修改 `.vue`/`.js`/`.ts` 后执行 `npm run lint` 与 `npm run build`，全部通过才能提交（[AGENTS.md](../../AGENTS.md) §1.2）。

---

## 7. 自测与单测

- **自测**：本地启动依赖 + 网关 + 涉及服务，按验收标准逐条走查（含异常场景：无权限、参数非法、重复提交）。
- **单测**：新增业务逻辑**必须**配套单测（`src/test/java/...`，JUnit5 + Mockito）：

```bash
# 运行指定模块单测
mvn test -pl ruoyi-modules/ruoyi-system -am -q

# 只跑单个测试类
mvn test -pl ruoyi-modules/ruoyi-system -am -Dtest=SysUserServiceImplTest -q
```

- 单测规范：类名 `XxxTest` / `XxxServiceImplTest`；用 Mockito mock Mapper/外部服务，不依赖真实数据库；命名 `方法名_场景_期望`（如 `listUsers_whenNoPermission_throws403`）。
- 集成测试（连真实依赖）命名 `*IT`，在测试阶段统一执行（见 [03-testing.md](03-testing.md) §12）。

---

## 8. 提交规范（Conventional Commits）

```bash
git checkout -b feature/<ticket>-<desc>   # 或 bugfix/<ticket>-<desc>
git add <files>
git commit -m "feat(system): 新增用户审批功能，含单测与迁移脚本"
```

| type | 场景 |
|------|------|
| feat / fix | 新功能 / 缺陷修复 |
| docs / style | 文档 / 格式（不影响逻辑） |
| refactor / perf | 重构 / 性能优化 |
| test / build / ci / chore | 测试 / 构建 / CI / 杂项 |

- subject 中文或英文均可但须清晰；一条提交只做一件事；**禁止直接向 `main`/`develop` 推送**（[workflow-management](workflow-management.md)）。
- 大量并行开发用 Git Worktree 隔离工作树（[worktree-management](worktree-management.md)）。

---

## 9. MR / 评审流程（Gate G2）

1. 推送 `feature/<ticket>-<desc>` 分支 → 创建 MR/PR → 触发 CI（lint + 单测 + `mvn verify`）。
2. 自评清单（随 MR 提交）：

- [ ] `mvn compile -pl <模块> -am -q` 通过
- [ ] `mvn test -pl <模块> -am` 单测通过
- [ ] 前端 `npm run lint` / `npm run build` 通过（涉前端时）
- [ ] 按验收标准完成自测（含异常场景）
- [ ] 文档/配置/SQL 已同步（§10）

3. 评审关注：接口与设计契约一致、分层合规、权限/日志/幂等齐全、单测覆盖关键分支。
4. **CI 全绿 + 评审通过**才可合并，合并后删除分支。

---

## 10. 文档 / 配置 / SQL 同步

- **文档**：新增/修改模块或接口时，同步更新 `docs/modules/<module>.md` 与 `docs/architecture/` 关联图（[AGENTS.md](../../AGENTS.md) §7）。
- **配置**：改动配置先改 `config/<service>-<profile>.yml` 模板，确认 `NACOS_DATA_ID` 与 `spring.application.name` 一致后再同步到 Nacos。
- **SQL**：表结构变更产出 `sql/<version>__<desc>.sql`（只向前迁移），语法先经 MCP 或 MySQL 客户端校验再入库。

---

## 11. 本地启动命令速查

```bash
# 依赖（已启动可跳过）
docker compose -f docker/docker-compose.yml up -d nacos mysql redis minio sentinel seata

# 后端：网关 + 认证 + 业务服务（按需 -pl）
mvn -pl ruoyi-gateway,ruoyi-auth -am spring-boot:run
mvn -pl ruoyi-modules/ruoyi-system -am spring-boot:run

# 前端
cd ruoyi-ui && npm install && npm run dev

# 一键联调（等价上述步骤）
./bin/dev-up.sh        # 或 PowerShell： .\bin\dev-up.ps1
```

---

## 12. 进入测试阶段的前置

- [ ] Gate G2 达成：编译通过、单测通过、自测通过，MR 已合并
- [ ] 单测与集成测试用例入库，测试数据与迁移脚本就绪
- [ ] 模块文档、架构图、配置模板、SQL 与代码同步

满足以上条件后进入 [03-testing.md](03-testing.md)。
