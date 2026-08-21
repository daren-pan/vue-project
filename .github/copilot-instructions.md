# 修改 Java 文件后必须自动编译验证

## 规则

每次使用 `replace_string_in_file` 或 `create_file` 修改 `.java` 文件后，**必须立即**执行以下操作：

1. 找到该文件所属的 Maven 模块（向上查找最近的 `pom.xml`）
2. 运行：`mvn compile -pl <模块路径> -am -q 2>&1`
3. 如果编译失败：
   - 分析错误信息
   - 自动修复代码
   - 再次编译直到通过
4. 如果编译通过，简短输出 "✅ 编译通过"

## Vue/JS 文件

修改 `.vue` / `.js` / `.ts` 文件后，调用 `get_errors` 检查 lint 错误。

## 原则

- 这不是可选的建议，是**必须执行**的步骤
- 编译失败不要等用户提醒，主动修复
- 最终目标：用户不需要手动跑任何验证
- **批量替换代码必须用 `multi_replace_string_in_file` 逐个文件修改**，禁止使用 `run_in_terminal` 执行 PowerShell 批量替换命令（如 `Get-Content | ForEach-Object | Set-Content`），防止中文注释和字符乱码

## 文件操作说明

每次对文件执行**新增、修改、删除**操作时，必须：

1. **解释原因**：说明本次操作的目的（解决什么问题 / 实现什么功能 / 为什么需要删除）
2. **提供验证方法**：给出如何验证改动正确的方式（编译命令、测试命令、检查点等），并执行验证、报告结果

## 数据查询规范

需要查询数据库数据（如业务表、Nacos 配置表 `ry-config.config_info` 等）时：

1. **优先使用 MCP 工具查询**（`mysql-ry` / `mysql-flowable`），不用进 Docker 或终端敲命令
2. 只有 MCP 工具不可用（未配置、连不上、权限不足）时，才改用 `docker exec` 或终端命令等替代方式
3. 查询 Nacos 配置表时注意：库名是反引号包裹的 `ry-config`（带连字符），表为 `ry-config.config_info`

## 项目模块编码规范（参照 ruoyi-system，所有新增模块必须遵守）

### 包结构

```
controller/        — REST 接口，只注入 Service 接口，不注入实现
service/           — Service 接口（I 前缀，如 ISysUserService）
service/impl/      — Service 实现（如 SysUserServiceImpl）
domain/            — 实体类（如 SysUser、SysConfig）
domain/vo/         — VO/DTO 实体类（如 MetaVo、RouterVo）
mapper/            — MyBatis Mapper 接口
config/            — 配置类
```

### Service 分层

- **接口**放在 `service/`，命名 `IXxxService`
- **实现**放在 `service/impl/`，命名 `XxxServiceImpl`
- Controller 只注入接口：`@Autowired private IXxxService xxxService;`

### DTO/VO 风格

- **必须使用标准 getter/setter**，与 `SysUser`、`SysAuditLog` 一致
- **禁止** builder 链式模式（如 `XxxDTO.ok().field(x).field2(y)`）
- **每个字段必须有 `/** 注释 */`**
- 正确写法：
  ```java
  XxxVO vo = new XxxVO();
  vo.setField1(x);
  vo.setField2(y);
  return vo;
  ```

### Controller 职责

- **仅做**：参数校验、调用 Service、返回结果
- **禁止**：任何业务逻辑、BPMN 解析、流式 Map 拼装

### 异常处理

- 模块专用异常放在 `ruoyi-common-core` 的 `exception/` 包
- 在 `GlobalExceptionHandler` 添加对应的 `@ExceptionHandler`
- 业务异常**禁止**使用裸 `RuntimeException`

### 实体类返回

- 查询接口优先返回强类型 VO/DTO（如 `R<List<XxxVO>>`），禁止散落 `Map<String, Object>`
- 泛型推断失败时使用显式类型：`R.<TaskResult>ok(...)`

---

