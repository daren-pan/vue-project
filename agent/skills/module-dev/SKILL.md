---
name: module-dev
description: 通用业务模块开发指南，涵盖分层、命名、DTO/VO 规范、编译验证、文档与配置同步。编辑 ruoyi-modules 或 ruoyi-common 下任何 Java/Vue 代码时自动加载。
---

# 通用模块开发指南（module-dev）

## 1. 适用

- 在 `ruoyi-modules/**`、`ruoyi-common/**` 新增/修改代码。
- 从头创建一个新业务模块时。

## 2. 新建模块九步清单

1. 在 `ruoyi-modules/<name>` 建目录，创建 `pom.xml`（父为 `com.ruoyi:ruoyi`）。
2. 在根 `pom.xml` 的 `<modules>` 与 `<dependencyManagement>` 登记（若为核心逻辑模块）。
3. 建立包结构：`controller / service / service/impl / domain / domain/vo / mapper / config`。
4. 写 `src/main/java/com/ruoyi/<name>/<Name>Application.java`（`@SpringBootApplication` + `@EnableDiscoveryClient`）。
5. 写 `src/main/resources/application.yml`（端口/服务名/Nacos/共享配置），参考 `ruoyi-system`。
6. 在 `config/` 添加 Nacos 模板 `config/<name>-<profile>.yml`。
7. 在 `sql/` 添加迁移脚本与对应建表；在 `agent/mcp/mcp.json`（如新增库）登记。
8. 写 `docs/modules/<name>.md` 与对应 skill（如 `agent/skills/<name>-dev/SKILL.md`）。
9. 编译验证 + 联调。

## 3. 分层与命名

```
controller/   接口，只注入 Service 接口
service/      IXxxService 接口
service/impl/ XxxServiceImpl 实现
domain/       实体（SysUser…）
domain/vo/    强类型 VO/DTO（标准 getter/setter）
mapper/       MyBatis Mapper
config/       配置类
```

- Interface 前缀 `I`；实现前缀无；Controller 只 `@Autowired IXxxService`。
- VO/DTO 禁止 builder 链式；每个字段带 `/** 注释 */`；返回强类型，禁散落 `Map<String,Object>`。

## 4. Controller 与异常

- Controller 只做：参数校验、调 Service、返回 `R<T>`。
- 业务异常用 `ServiceException`（在 common-core），在 `GlobalExceptionHandler` 注册 `@ExceptionHandler`；**禁止**裸 `RuntimeException`。
- 写操作加 `@PreAuthorize` 权限 + `@Log` 操作日志 + 幂等。

## 5. 编译验证（必须）

```bash
# 单模块（-am 连带依赖）
mvn compile -pl ruoyi-modules/<name> -am -q 2>&1
# 多模块
mvn compile -pl ruoyi-modules/<name>,ruoyi-auth -am -q 2>&1
# 改了公共 API
mvn -pl ruoyi-common/ruoyi-common-core,ruoyi-api -am install -DskipTests
```

编译失败 → 修复 → 重跑直到通过；通过后输出 `✅ 编译通过`。

## 6. 前端（Vue3）

- `npm run lint` + `npm run build` 校验 `.vue/.js/.ts`。
- 组件/页面遵循 Element Plus 约定；API 封装在 `ruoyi-ui/src/api/`。

## 7. 文档与配置同步

- 同步更新 `docs/modules/<name>.md` 与 `docs/architecture/00-architecture-overview.md` 的模块矩阵。
- 配置差异进 `config/`；本地覆盖用 `local-env.yml`（gitignore）。

## 8. 自查

- [ ] 编译通过
- [ ] 单测通过（新增逻辑）
- [ ] 权限/日志/幂等
- [ ] 模块文档已更新
