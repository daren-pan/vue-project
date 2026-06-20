# 数据库迁移工具

通过 `..\db-sync.ps1` 管理数据库表结构变更。

---

## 工作流程

```
开发时修改了表结构
    │
    ▼
1. .\db-sync.ps1 new "变更说明"
    │   → 生成 sql/migrations/20260604143000_xxx.sql
    │   → 编辑该文件，写入你的 DDL/DML
    ▼
2. .\db-sync.ps1 run docker
    │   → 应用到本地 Docker MySQL（3307）
    │   → 测试功能是否正常
    ▼
3. .\db-sync.ps1 run k8s
    │   → 同步到 K8s MySQL（3308）
    ▼
4. 提交 git（包含 migrations 目录下的 SQL 文件）
```

---

## 命令说明

### 创建迁移

```powershell
# 在项目根目录执行
.\db-sync.ps1 new "给 sys_dept 增加排序字段"
```

会在 `sql/migrations/` 下生成一个带时间戳的 SQL 文件：

```
sql/migrations/
├── README.md
└── 20260604143000_给_sys_dept_增加排序字段.sql
```

打开该文件，写入具体的 SQL：

```sql
ALTER TABLE sys_dept ADD COLUMN sort_order int(4) DEFAULT 0 COMMENT '排序';
UPDATE sys_menu SET ...
```

### 查看迁移状态

```powershell
.\db-sync.ps1 status
```

输出示例：

```
===== 迁移文件列表 =====

  20260604143000_给_sys_dept_增加排序字段.sql  [PENDING]
  20260604150000_创建_xxx_表.sql                [DONE]
  ```

  [DONE] = 已执行，[PENDING] = 待执行

### 执行迁移

```powershell
# 执行到 Docker 环境
.\db-sync.ps1 run docker

# 执行到 K8s 环境
.\db-sync.ps1 run k8s
```

只会执行 [PENDING] 的迁移，已执行过的自动跳过，不会重复执行。

### 导出当前表结构

```powershell
.\db-sync.ps1 export
```

从 Docker MySQL 导出所有表的 CREATE TABLE 语句到迁移文件。

---

## 注意事项

1. **迁移文件一旦执行不要修改**——如需变更，新建一个迁移文件
2. 迁移文件按时间戳排序，确保执行顺序正确
3. 迁移文件支持多条 SQL，用分号 `;` 分隔即可
4. 执行记录存储在 `ry-cloud._migrations` 表中
