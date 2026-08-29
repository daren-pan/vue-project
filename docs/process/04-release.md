# 生产验收与发布（Release & Production Acceptance）

> 本文档定义「生产验收（Release）」阶段的完整流程：**版本与发布策略、发布方式、发布单、变更评审、SQL/配置发布顺序、回滚、验收标准（Gate G4）、上线 Checklist、故障应急与复盘**。
> 上一阶段：[03-testing.md](03-testing.md) ｜ 下一阶段：[05-ops.md](05-ops.md) ｜ 配套：[00-overview.md](00-overview.md) · [workflow-management.md](workflow-management.md) · [../ops/ops-handbook.md](../ops/ops-handbook.md)

---

## 1. 阶段目标与出口条件（Gate G4）

发布阶段把通过测试的版本安全地送到生产，并在生产上证明其可用。**只有满足 Gate G4 才允许全量放量**：

| # | 验收项 | 判定 |
|---|--------|------|
| G4-1 | 用户验收测试（UAT）通过，缺陷清零或达成上线阈值 | ✅ / ❌ |
| G4-2 | staging 与生产同构演练通过（含灰度/蓝绿切换演练） | ✅ / ❌ |
| G4-3 | 回滚方案与回滚脚本就绪并演练过至少一次 | ✅ / ❌ |
| G4-4 | 发布单经变更评审（CAB）批准 | ✅ / ❌ |
| G4-5 | 监控、告警、值班人就位，发布窗口内可即时响应 | ✅ / ❌ |

> 任一 ❌ 不得放量。本阶段产出物：`docs/ops/release-notes/<ver>.md`、发布单、验收报告、回滚脚本。

---

## 2. 版本与发布策略

### 2.1 版本号规范（语义化版本）

```
v<主>.<次>.<补丁>[-<预发布>]    示例：v1.2.0、v1.2.0-rc.1、v1.2.1
```

- 主版本：不兼容的架构/接口变更；次版本：向后兼容的新功能；补丁：缺陷修复。
- 版本号在 `release/<ver>` 分支创建时定版，`main` 分支 tag 即发布版本。
- 一个 **GitHub Milestone** 对应一个可发布版本（见 [workflow-management.md](workflow-management.md)）。

### 2.2 环境推进策略（dev → test → staging → prod）

| 环境 | 触发方式 | 数据策略 | 放量 |
|------|----------|----------|------|
| dev | `develop` 合并自动部署 | 可重置 | 100%（内部） |
| test | `release/<ver>` 分支部署 | 固定，供 QA 回归 | 100%（QA） |
| staging | release 分支手工触发 | 与生产同构（脱敏） | 灰度演练 |
| prod | `v<ver>` tag 触发 | 生产数据 | 白名单 → 灰度 → 全量 |

**核心原则**：同一镜像从 test 一路推进到 prod，**禁止在环境间重新构建**；环境差异只体现在配置（Nacos profile）上。

### 2.3 发布频率与窗口

- 常规版本：固定发布窗口（如每两周一次，避开业务高峰），窗口内禁止非紧急变更。
- 紧急热修：走 `hotfix/<ver>` 分支（见 [workflow-management.md](workflow-management.md)），可走快速评审通道，但回滚方案必须齐备。

---

## 3. 发布方式（灰度 / 蓝绿 / 滚动）

| 方式 | 适用场景 | 优点 | 缺点 | 本项目用法 |
|------|----------|------|------|------------|
| **灰度（金丝雀）** | 新版本风险中等 | 风险可控、可观察业务指标 | 需要流量治理支持 | 推荐默认：网关按用户/权重切流，先 5% → 20% → 100% |
| **蓝绿** | 大版本、可整体回切 | 回滚=切回旧环境，秒级 | 成本翻倍（两套环境） | staging 演练、核心链路升级 |
| **滚动** | 无状态服务扩容场景 | 无需停机 | 新旧并存，兼容性要求高 | 与灰度结合使用 |

灰度切流示意（网关 + Nacos 权重）：

```yaml
# config/ruoyi-gateway-prod.yml（灰度期间调整）
spring:
  cloud:
    gateway:
      routes:
        - id: ruoyi-system
          uri: lb://ruoyi-system          # 服务级负载均衡
          predicates: [ Path=/system/** ]
          filters:
            - name: RequestRateLimiter      # 配合 Sentinel 限流，保护灰度流量
```

- **灰度放量节奏**：5%（观察 30 min）→ 20%（观察 2 h）→ 50% → 100%；每一步都要看错误率、P95 延迟、业务指标。
- **退出条件**：任一步出现 P0/P1 故障 → 立即回滚，不再放量。

---

## 4. 发布单模板

发布单存放于 `docs/ops/release-notes/<ver>.md`，并在发布系统中登记：

```markdown
# 发布单 v1.2.0

- 发布日期 / 窗口：2026-06-20 22:00–23:00（低峰窗口）
- 发布负责人 / 审批人：张三 / 李四（CAB）
- 影响范围：ruoyi-system、ruoyi-workflow、ruoyi-ui
- 变更内容：
  1. [feat] 流程审批加签/转办（workflow）
  2. [fix] 用户批量导入慢 SQL 优化（system）
  3. [docs] 无
- 关联 Issue / PR：#123 / PR #456、#457
- 数据库变更：`sql/v1.2.0__workflow_cc.sql`（向前迁移）
- 配置变更：`config/ruoyi-workflow-prod.yml`（+ 抄送配置项）
- 发布方式：灰度 5%→20%→50%→100%
- 回滚方案：回滚镜像 v1.2.0 → v1.1.4 + 配置回滚（见下）
- 验收项：Gate G4 全部通过
```

---

## 5. 变更评审（CAB）

所有生产变更需提交变更评审，按风险分级：

| 等级 | 定义 | 示例 | 审批 |
|------|------|------|------|
| 常规 | 低风险、可快速回滚 | 配置项调整、补丁版本 | 发布负责人确认即可 |
| 重大 | 影响面大或数据变更 | 数据库结构变更、接口不兼容、跨服务改造 | CAB 会议评审（架构/测试/运维负责人） |
| 紧急 | 线上故障修复 | hotfix | 值班负责人 + 事后 24h 内补评审 |

评审要点：变更必要性、影响面、测试证据、回滚方案、发布窗口、值班安排。评审结论记录在发布单中。

---

## 6. SQL 与配置变更的发布顺序

**顺序原则：配置先行、SQL 向前、代码随后、清理最后**，保证任意时刻新旧代码并存都能工作：

1. **配置先行（Nacos）**：新增配置项（带默认值）先下发，发布后再清理废弃项；配置变更**不进代码包**，可独立回滚。
2. **SQL 向前迁移**：`sql/<ver>__<desc>.sql` 只允许向前、向后兼容（先加列/建新表，不删不改旧结构）；生产**禁止数据回退**（见 [00-overview.md](00-overview.md) §3.4）。
3. **代码发布**：按 §3 的方式放量。
4. **清理收尾**：下个版本窗口删除废弃字段/配置/旧代码路径（向后兼容期过后）。

```sql
-- 示例：v1.2.0 向前迁移（先加列，勿删旧列）
ALTER TABLE `ry-cloud`.sys_workflow_task
  ADD COLUMN cc_user_ids varchar(500) NULL COMMENT '抄送人ID(逗号分隔)' AFTER assignee_id;
```

> 变更**先扩后缩**：容量/字段先放大兼容，确认稳定后再收缩。任何 DDL 先备份、先在 staging 执行一遍。

---

## 7. 回滚方案与回滚脚本

### 7.1 回滚原则

- **镜像回滚**：默认回滚到上一可用镜像（`v1.2.0 → v1.1.4`），不依赖数据回退。
- **配置回滚**：Nacos 配置保留多版本历史，一键回退到上一个已发布版本。
- **数据**：禁止回退已生效的 DDL/DML；数据异常走**补偿脚本**（`sql/rollback/` 目录，仅补偿不动结构）。

### 7.2 回滚触发条件

- 错误率 > 阈值、核心接口 P95 明显劣化、业务指标异常、安全事件。

### 7.3 回滚命令示例（Docker Compose 场景）

```bash
# 1) 确认当前版本
docker compose -f docker/docker-compose.prod.yml ps

# 2) 回滚指定服务到上一镜像（如 system 服务）
docker compose -f docker/docker-compose.prod.yml up -d --no-deps \
  --force-recreate ruoyi-system
#    镜像 tag 在 .env 或 compose 中切换：SYSTEM_IMAGE=registry.example.com/ruoyi-system:v1.1.4

# 3) 回滚网关路由/权重（灰度全退）→ 改配置后刷新 Nacos
curl -X POST http://nacos:8848/nacos/v1/cs/configs \
  -d "dataId=ruoyi-gateway-prod.yml&group=DEFAULT_GROUP&content=$(cat config/ruoyi-gateway-prod.yml)"

# 4) 验证回滚：健康检查 + 灰度观察
curl -fsS http://gateway:8080/ruoyi-system/actuator/health
```

> K8s 场景等价于 `kubectl rollout undo deployment/ruoyi-system`。回滚脚本需在 staging **演练通过**并随发布单提交。

---

## 8. 验收标准（Gate G4）与上线 Checklist

### 8.1 上线前 Checklist

- [ ] 版本号与 tag 正确，镜像已推送且签名/校验通过
- [ ] 发布单已批准（CAB），回滚脚本已就绪并通过演练
- [ ] SQL 已在 staging 执行过、生产执行计划确认（备份先行）
- [ ] Nacos 配置已下发且生效（`/actuator/configprops` 核对）
- [ ] 监控/告警已配置（健康检查、错误率、P95、业务指标）
- [ ] 值班人确认，告警通道（钉钉/企微/邮件）可达
- [ ] 前端资源已构建并上传（CDN/MinIO），版本号可见

### 8.2 上线中 Checklist（灰度放量期间）

- [ ] 每步放量后检查：服务健康、错误率、延迟、业务指标（订单/审批单量）
- [ ] 新实例日志无异常堆栈、无 Feign/Sentinel/Seata 告警
- [ ] 观察窗口内无 P0/P1；如有则走 §7 回滚

### 8.3 验收报告（Gate G4 出口）

- [ ] UAT 通过记录（截图/用例编号）
- [ ] 灰度数据与指标对比（新旧版本对照表）
- [ ] 回滚演练记录
- [ ] 遗留问题清单与责任人（若存在，需有上线阈值批准）

---

## 9. 故障应急与复盘

### 9.1 应急响应

1. **止血**：立即回滚或降级（Sentinel 降级/摘流量），先恢复服务。
2. **通报**：按 [05-ops.md](05-ops.md) 故障等级通报值班群与负责人。
3. **定位**：查日志（traceId）、指标、慢 SQL、Nacos 配置变更记录。
4. **恢复确认**：监控恢复正常、业务指标回升、用户可访问。

### 9.2 复盘（RCA，48h 内）

```markdown
# 故障复盘 <F-2026-06-21-01>

- 时间线：发现 → 通报 → 定位 → 恢复
- 影响范围 / 时长 / SLA 扣减
- 根因（5-Why）：技术根因 + 流程根因
- 改进项：代码修复 / 告警补全 / 流程修订（责任人 + 截止日）
- 结论：是否关闭，是否进入下一版本发布
```

复盘结论必须转化为 action（新增测试、补告警、改发布流程），否则复盘无效。

---

## 10. 与相邻文档的关系

| 文档 | 与本阶段的关系 |
|------|----------------|
| [00-overview.md](00-overview.md) | Gate G4 定义与阶段闸门总览 |
| [03-testing.md](03-testing.md) | 上游输入：测试报告与缺陷清单 |
| [workflow-management.md](workflow-management.md) | `release/<ver>`、`hotfix/<ver>` 分支与 tag 触发部署 |
| [05-ops.md](05-ops.md) | 下游：发布后的监控、告警、值班 |
| [../architecture/00-architecture-overview.md](../architecture/00-architecture-overview.md) | 服务端口/健康检查约定（发布探活用） |
| [../ops/ops-handbook.md](../ops/ops-handbook.md) | 运维手册：备份、巡检、应急操作手册 |
