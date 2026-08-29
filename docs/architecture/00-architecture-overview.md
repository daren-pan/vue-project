# 总体架构设计（Architecture Overview）

> 本文档是后端微服务架构的总纲，定义了分层、职责边界、调用链路、技术选型与关键设计原则。
> 配套文档：模块文档 `docs/modules/`、安全/健壮性/扩展性/数据设计、流程文档 `docs/process/`。

---

## 1. 架构目标与设计原则

### 1.1 目标

构建一套**可独立演进、可水平扩展、可观测、可安全下放**的微服务平台，覆盖从单体业务到大规模并发的通用场景，并沉淀为可持续维护的研发体系。

### 1.2 十条设计原则

1. **单一职责**：每个微服务只专注一个业务域，功能边界清晰。
2. **前后端分离**：RESTful API + 强类型 DTO/VO，前端只依赖接口契约。
3. **接口与实现分离**：`ruoyi-api` 定义跨服务契约，模块内部通过 OpenFeign 调用。
4. **统一治理**：Nacos 负责注册与配置，Sentinel 负责流控降级，Seata 负责分布式事务，统一走网关。
5. **配置外置**：除本地可覆盖项外，配置一律进 Nacos，环境差异用 profile + 环境变量。
6. **网关收敛**：所有外部流量经 `ruoyi-gateway`，统一鉴权、路由、限流、审计。
7. **单一入口鉴权**：Token 校验在网关集中，下游服务只做细粒度权限。
8. **数据独立**：服务只能访问自己的库；跨库一致性用事务或消息。
9. **可观测**：日志、指标（actuator/Spring Boot Admin）、链路追踪全量接入。
10. **安全默认**：入参校验、权限校验、敏感脱敏、审计日志、接口幂等默认开启。

---

## 2. 总体分层

```
                 ┌────────────────────────────────────────────┐
  用户/客户端     │                   ruoyi-ui (Vue3)           │
   ─────────────► │        Vite + Pinia + Vue Router + Element Plus │
                 └───────────────────┬────────────────────────┘
                                     │ HTTP/JSON
                 ┌───────────────────▼────────────────────────┐
                 │            ruoyi-gateway [8080]             │
                 │  路由 / 全局鉴权 / 限流(Sentinel) / 审计 / 跨域 │
                 └───────────────────┬────────────────────────┘
        ┌──────────────┬─────────────┼──────────────┬─────────────┐
        ▼              ▼             ▼              ▼             ▼
   ruoyi-auth    ruoyi-system   ruoyi-gen     ruoyi-job      ruoyi-workflow
   [9200]        [9201]         [9202]        [9203]          [9210]
   认证/令牌     用户/部门/角色   代码生成      定时任务        流程审批
        │              │             │              │             │
        └──────────────┴─────────────┴──────────────┴─────────────┘
                     OpenFeign 内部调用 + 服务注册发现 (Nacos)
                 ┌───────────────────▼────────────────────────┐
                 │           ruoyi-file [9300] (MinIO)          │
                 │           ruoyi-monitor [9100] (Admin)       │
                 └───────────────────┬────────────────────────┘
                                     │
        Infra:  Nacos(8848) Redis(6379) MySQL(3306) MinIO(9000) Seata(8091) Sentinel(8858)
```

- **流量入口**：唯一入口为网关。
- **服务间调用**：OpenFeign + Nacos 负载均衡；禁止绕过网关直连。
- **基础设施**：通过 Docker Compose 一键拉起；生产 K8s/ECS 部署。

---

## 3. 模块职责矩阵

| 模块 | 职责 | 关键依赖 | 暴露能力 |
|------|------|----------|----------|
| ruoyi-gateway | 统一入口/路由/鉴权/流控 | spring-cloud-gateway, sentinel | 转发 |
| ruoyi-auth | 登录/令牌签发刷新校验 | security, redis, jjwt | `/oauth/**` 认证 |
| ruoyi-system | 系统管理（用户/角色/菜单/字典/参数/通知/日志） | mybatis-plus, api-system | CRUD + 权限元数据 |
| ruoyi-gen | 代码生成 | velocity, druid | 模板化代码 |
| ruoyi-job | 定时任务调度 | quartz, xxl-job（二选一） | 任务调度 |
| ruoyi-workflow | 流程定义/实例/任务 | flowable(7), api-workflow | 审批流引擎 |
| ruoyi-file | 文件上传/预览/下载 | minio | 对象存储 |
| ruoyi-monitor | 服务健康监控 | spring-boot-admin | 监控面板 |
| ruoyi-ui | 前端交互 | vue3, element-plus | 界面 |

---

## 4. 技术选型与版本基线

| 分类 | 技术 | 版本 | 说明 |
|------|------|------|------|
| 语言 | Java | 17 | LTS |
| 框架 | Spring Boot | 3.3.5 | 微服务基础 |
| 微服务 | Spring Cloud | 2023.0.3 | 注册/负载/熔断 |
| 微服务 | Spring Cloud Alibaba | 2023.0.1.2 | Nacos/Sentinel/Seata |
| 注册配置 | Nacos | 2.x | 服务发现+配置中心 |
| 认证 | Spring Security + JJWT | 0.9.1 | 无状态令牌 |
| 数据访问 | MyBatis-Plus | 3.5.7 | ORM + 分页 |
| 数据源 | dynamic-datasource + Druid | 4.3.1 / 1.2.27 | 多数据源/连接池 |
| 缓存 | Redis + Lettuce | 随 Boot | 会话/验证码/幂等 |
| 事务 | Seata (AT) + 本地事务 | 随 Alibaba | 分布式一致性 |
| 流控 | Sentinel | 随 Alibaba | 限流/降级/熔断 |
| 工作流 | Flowable | 7.0.1 | BPMN 流程引擎 |
| 存储 | MinIO | 8.2.2 | 对象存储 |
| 接口文档 | springdoc-openapi | 2.6.0 | OpenAPI 3 |
| 前端 | Vue 3 / Vite / Element Plus / Pinia | 近期 | 前端技术栈 |
| 容器 | Docker / Compose | 近期 | 交付与测试 |

---

## 5. 关键调用链路

### 5.1 登录鉴权

```
UI → Gateway(校验) → Auth(/login) → 校验验证码/用户 → 生成 JWT + 写 Redis 会话 → 返回 token
后续请求: UI → Gateway(解析 token) → 内网转发 → 下游 @ApiResource 读取上下文用户
```

### 5.2 业务请求脱敏授权

```
UI → Gateway → System(/... ) → SecurityContext 注入用户 → @PreAuthorize 权限 → Service → DB
```

### 5.3 跨服务调用（示例：文件服务）

```
UI → Gateway → System(记录文件元数据) → OpenFeign → File(上传 MinIO) → 返回 URL
```

---

## 6. 配置与外部化

- `pom.xml` 统一版本；`application.yml` 提公共信息。
- 差异配置走 `config/` 下的 Nacos 模板（`<service>-<profile>.yml`），例如 `config/ruoyi-system-dev.yml`。
- 敏感项用占位符 `${MYSQL_PASSWORD}`，由环境注入，配合 `local-env.yml`（gitignore）。
- 每个服务启动都 `import optional:nacos:...` 与 `optional:file:./local-env.yml`，保证本地可缺省运行。

---

## 7. 模块依赖关系（Maven）

```
ruoyi-gateway ──► common-security, common-redis, api-system
ruoyi-auth    ──► common-core, common-security, common-redis, api-system
ruoyi-system  ──► common-core, common-security, common-redis, common-datasource,
                  common-datascope, common-log, common-sensitive, common-swagger, api-system
ruoyi-gen     ──► common-core, common-security, common-datasource, api-system
ruoyi-job     ──► common-core, common-security, common-redis, api-system
ruoyi-workflow ──► common-core, common-security, common-redis, common-datasource, api-workflow, flowable
ruoyi-file    ──► common-core, common-security, common-redis, minio
ruoyi-monitor ──► common-core
ruoyi-common-* 共同依赖 common-core
```

> 依赖原则：业务模块只依赖 `common-*` 与 `api-*`，**禁止**反向依赖或依赖其他业务模块的具体实现。

---

## 8. 架构文档索引

| 文档 | 主题 |
|------|------|
| [00-architecture-overview.md](00-architecture-overview.md) | 总体架构（本文档） |
| [architecture-security.md](architecture-security.md) | 安全架构设计 |
| [architecture-robustness.md](architecture-robustness.md) | 健壮性/容错设计 |
| [architecture-scalability.md](architecture-scalability.md) | 扩展性/可伸缩设计 |
| [architecture-data.md](architecture-data.md) | 数据与存储设计 |
| [../process/00-overview.md](../process/00-overview.md) | 研发全流程总览 |
