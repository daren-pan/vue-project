# ruoyi-gen 模块文档

> 代码生成模块，基于 Velocity + Druid 从数据表反向生成 CRUD 代码（Controller/Service/Mapper/实体/前端页面）。
> 修改前必读本文件；通用编码规范见 [coding-standard.md](coding-standard.md)。

---

## 1. 职责

- 表结构导入、数据源管理（可生成接入的新库）。
- 模板化代码生成：实体、Mapper、Service、Controller、Vue 页面、SQL。
- 预览与下载脚手架。

## 2. 包与分层

```
com.ruoyi.gen
├── controller/   REST 接口
├── service/      IGenTableService 等
├── service/impl/
├── domain/       实体（GenTable、GenTableColumn）
├── domain/vo/
├── mapper/       MyBatis Mapper
├── config/       模板/数据源配置
└── util/         生成与模板工具
```

## 3. 依赖

- 公共：`common-core`、`common-security`、`common-datasource`。
- 跨服务契约：`api-system`。
- 模板引擎：`velocity`；数据源：`druid`。

## 4. 关键能力 / 接口

| 类型 | 说明 | 示例 |
|------|------|------|
| 表管理 | 表列表、导入、编辑 | `/tool/gen/**` |
| 生成 | 预览、下载代码 | `/tool/gen/preview`、`/tool/gen/download` |
| 数据源 | 数据源配置（多库） | `/tool/gen/db/**` |

## 5. 数据与配置

- 库：`ry-cloud`，`gen_*` 表存储表与配置。
- `application.yml`：服务名 `ruoyi-gen`、端口 `9202`。

## 6. 编译 / 启动

```bash
mvn compile -pl ruoyi-modules/ruoyi-gen -am -q 2>&1
mvn -pl ruoyi-modules/ruoyi-gen -am spring-boot:run
```

---

## 变更记录

| 日期 | 内容 | 验证 |
|------|------|------|
| — | 初始建立 | — |
