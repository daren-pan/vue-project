# ruoyi-job 模块文档

> 定时任务模块，提供定时任务的调度与管理（Quartz / XXL-Job 二选一）。
> 修改前必读本文件；通用编码规范见 [coding-standard.md](coding-standard.md)。

---

## 1. 职责

- 任务定义：Cron 表达式、执行类、执行参数。
- 调度与执行：按时间触发、日志记录、失败重试。
- 任务管理：启停、执行状态、调度日志。

## 2. 包与分层

```
com.ruoyi.job
├── controller/   REST 接口
├── service/      ISysJobService 等
├── service/impl/
├── domain/       实体（SysJob、SysJobLog）
├── domain/vo/
├── mapper/       MyBatis Mapper
└── config/       调度器配置
```

## 3. 依赖

- 公共：`common-core`、`common-security`、`common-redis`。
- 跨服务契约：`api-system`。
- 调度：`quartz` / `xxl-job`（二选一）。

## 4. 关键能力 / 接口

| 类型 | 说明 | 示例 |
|------|------|------|
| 任务 | CRUD、启停、立即执行一次 | `/monitor/job/**` |
| 日志 | 调度日志、执行日志 | `/monitor/jobLog/**` |

## 5. 数据与配置

- 库：`ry-cloud`，`sys_job*` 表。
- `application.yml`：服务名 `ruoyi-job`、端口 `9203`。

## 6. 编译 / 启动

```bash
mvn compile -pl ruoyi-modules/ruoyi-job -am -q 2>&1
mvn -pl ruoyi-modules/ruoyi-job -am spring-boot:run
```

---

## 变更记录

| 日期 | 内容 | 验证 |
|------|------|------|
| — | 初始建立 | — |
