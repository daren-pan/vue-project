# 模块编码规范（Coding Standard）

> 适用于 `ruoyi-modules/**` 与 `ruoyi-common/**` 下所有 Java 业务模块。参照 `ruoyi-system` 沉淀，所有新增模块必须遵守。
> 本文件不随会话自动加载，各模块文档在开发前需引用并遵循；也由 `agent/skills/module-dev/SKILL.md` 与 `docs/process/02-development.md` 引用。

---

## 1. 包结构

```
controller/        — REST 接口，只注入 Service 接口，不注入实现
service/           — Service 接口（I 前缀，如 ISysUserService）
service/impl/      — Service 实现（如 SysUserServiceImpl）
domain/            — 实体类（如 SysUser、SysConfig）
domain/vo/         — VO/DTO 实体类（如 MetaVo、RouterVo）
domain/bo/         — 强类型入参（可选）
mapper/            — MyBatis Mapper 接口
config/            — 配置类
```

## 2. Service 分层

- **接口**放 `service/`，命名 `IXxxService`；**实现**放 `service/impl/`，命名 `XxxServiceImpl`。
- Controller 只注入接口：`@Autowired private IXxxService xxxService;`（推荐构造器注入）。
- Controller 只做：参数校验、调 Service、返回结果；**禁止**业务逻辑、BPMN 解析、流式 Map 拼装。

## 3. DTO/VO 风格

- **必须使用标准 getter/setter**，与 `SysUser`、`SysAuditLog` 一致；**禁止** builder 链式（如 `XxxDTO.ok().field(x).field2(y)`）。
- **每个字段必须有 `/** 注释 */`**；方法必须有 Javadoc 说明语义、参数与返回值。
- 正确写法：

```java
XxxVO vo = new XxxVO();
vo.setField1(x);
vo.setField2(y);
return vo;
```

## 4. 返回与异常

- 查询接口优先返回强类型 VO/DTO（如 `R<List<XxxVO>>`），禁止散落 `Map<String, Object>`；泛型推断失败用显式类型 `R.<TaskResult>ok(...)`。
- 业务异常用 `ServiceException`（`ruoyi-common-core/exception/`），在 `GlobalExceptionHandler` 注册 `@ExceptionHandler`；**禁止**裸 `RuntimeException`。

## 5. 安全与健壮性

- 入参校验：`@Validated` + `@NotNull/@NotBlank/@Size` + 自定义注解，禁止 Controller 内大面积 `if`。
- 所有写操作：`@PreAuthorize` 权限校验 + `@Log` 操作日志；支付/审批/回调类写操作加幂等键或乐观锁。
- 敏感字段按 `ruoyi-common-sensitive` 脱敏；不硬编码凭证。

## 6. 编译验证

每改一处 `.java` 后立即编译，失败即修直到通过，通过后输出 `✅ 编译通过`：

```bash
# 单模块
mvn compile -pl ruoyi-modules/<name> -am -q 2>&1
# 修改公共 API（ruoyi-common-core / ruoyi-api）后先 install 再编译依赖方
mvn -pl ruoyi-common/ruoyi-common-core,ruoyi-api -am install -DskipTests
```

---

## 关联文档

- [模块文档索引](README.md)
- 各模块文档：`docs/modules/<module>.md`
- 开发阶段规范：[../process/02-development.md](../process/02-development.md)
