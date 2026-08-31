# ruoyi-file 模块文档

> 文件模块，基于 MinIO 提供对象的上传、预览、下载能力，供各业务模块复用。
> 修改前必读本文件；通用编码规范见 [coding-standard.md](coding-standard.md)。

---

## 1. 职责

- 文件上传（分片/直传）、预览、下载、删除。
- MinIO 对象桶管理与访问链接生成。
- 跨服务提供文件元数据（OpenFeign 供 `ruoyi-system` 等调用）。

## 2. 包与分层

```
com.ruoyi.file
├── controller/   REST 接口
├── service/      ISysFileService 等
├── service/impl/
├── domain/       实体（SysFile）
├── domain/vo/
├── mapper/       MyBatis Mapper
└── config/       MinIO 客户端配置
```

## 3. 依赖

- 公共：`common-core`、`common-security`、`common-redis`。
- 存储：`minio`。

## 4. 关键能力 / 接口

| 类型 | 说明 | 示例 |
|------|------|------|
| 上传 | 文件上传、分片 | `/file/upload` |
| 资源 | 预览、下载、删除 | `/file/**` |

## 5. 数据与配置

- 存储：MinIO（`common-minio` 配置）；库：`ry-cloud`，`sys_file` 表。
- `application.yml`：服务名 `ruoyi-file`、端口 `9300`；MinIO 端点/桶/密钥走环境变量。

## 6. 编译 / 启动

```bash
mvn compile -pl ruoyi-modules/ruoyi-file -am -q 2>&1
mvn -pl ruoyi-modules/ruoyi-file -am spring-boot:run
```

---

## 变更记录

| 日期 | 内容 | 验证 |
|------|------|------|
| — | 初始建立 | — |
