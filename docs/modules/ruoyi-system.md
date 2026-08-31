# ruoyi-system 模块文档

> 系统管理模块，承载用户/角色/菜单/部门/字典/参数/通知/日志等基础数据与权限元数据。
> 修改前必读本文件；通用编码规范见 [coding-standard.md](coding-standard.md)。

---

## 1. 职责

- 系统基础数据：用户、角色、菜单、部门、岗位、字典、参数、通知公告、操作日志、登录日志。
- 提供权限元数据：用户角色、角色菜单、数据权限（`@DataScope`）。
- 供网关/前端获取登录用户、菜单路由、字典下拉等。

## 2. 包与分层

```
com.ruoyi.system
├── controller/   REST 接口（只注入接口、参数校验、返回 R<T>）
├── service/      ISysUserService 等
├── service/impl/ SysUserServiceImpl 等
├── domain/       实体（SysUser、SysRole、SysMenu、SysDept…）
├── domain/vo/    强类型 VO/DTO（MetaVo、RouterVo、TreeSelect…）
├── domain/bo/    强类型入参（可选）
├── mapper/       MyBatis Mapper
└── config/       配置类
```

## 3. 依赖

- 公共：`common-core`、`common-security`、`common-redis`、`common-datasource`、`common-datascope`、`common-log`、`common-sensitive`、`common-swagger`。
- 跨服务契约：`api-system`。

## 4. 关键能力 / 接口

| 类型 | 说明 | 示例 |
|------|------|------|
| 认证 | 登录、令牌校验、用户信息 | `POST /login`、`GET /getInfo`、`GET /getRouters` |
| 用户 | 用户 CRUD、状态、重置密码 | `/system/user/**` |
| 角色/菜单 | 角色分配、菜单授权、数据权限 | `/system/role/**`、`/system/menu/**` |
| 字典 | 字典类型/数据 | `/system/dict/**` |
| 日志 | 操作日志、登录日志 | `/monitor/operlog`、`/monitor/logininfor` |

> 跨服务契约定义在 `ruoyi-api/api-system`。

## 5. 数据与配置

- `application.yml`：服务名 `ruoyi-system`、端口 `9201`、Nacos 共享配置。
- Nacos 模板：`config/ruoyi-system-<profile>.yml`；敏感项走环境变量 + `local-env.yml`。

## 6. 编译 / 启动

```bash
mvn compile -pl ruoyi-modules/ruoyi-system -am -q 2>&1
mvn -pl ruoyi-modules/ruoyi-system -am spring-boot:run
```

---

## 变更记录

| 日期 | 内容 | 验证 |
|------|------|------|
| — | 初始建立 | — |
