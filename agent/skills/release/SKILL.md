---
name: release
description: 发布、回滚与运维指南，覆盖灰度/蓝绿发布、回滚脚本、审批、验收与故障应急。涉及 04-release / 05-ops 阶段或操作 bin/、.github/workflows/ 时自动加载。
---

# 发布-回滚-运维指南（release）

## 1. 阶段定位

本 skill 对应 `docs/process/04-release.md` 与 `05-ops.md`，覆盖从测试结束到生产的发布与运维。

## 2. 环境与方式

| 环境 | 部署 | 说明 |
|------|------|------|
| dev | 自动（合并触发） | 数据可重置 |
| test | 手动/定时 | QA 回归 |
| staging | 手动 | 与生产同构，灰度演练 |
| prod | 灰度/蓝绿/滚动 | 需评审 |

## 3. 发布前检查清单

- [ ] CI 全绿（lint + 单测 + mvn verify + 前端 build）
- [ ] 变更评审通过，发布单（`docs/ops/release-notes/<ver>.md`）已填
- [ ] 数据库迁移脚本向后兼容、已评审
- [ ] 配置模板 `config/<svc>-<profile>.yml` 已更新并 review
- [ ] 回滚方案与回滚脚本就绪（`bin/rollback.sh`）
- [ ] 监控/告警就绪

## 4. 回滚示例

```bash
# 回滚指定服务到上一个可用镜像/tag
.\bin\rollback.ps1 -Service ruoyi-system -PreviousTag v1.2.3
```

> 回滚优先回退**镜像版本**；若含数据库变更，回滚应用后按需执行**向前补偿**脚本（禁止直接删数据）。

## 5. 常见运维命令

```bash
docker compose -f docker/docker-compose.yml up -d <service>   # 启动
docker compose -f docker/docker-compose.yml logs -f <service> # 日志
curl http://localhost:<port>/actuator/health                  # 健康检查
```

## 6. 故障响应

按 `docs/ops/ops-handbook.md` 的 P0–P3 分级与升级路径处理；结束后复盘并输出事故报告。

## 7. 一致性

本 skill 与 [docs/process/04-release.md](../../../docs/process/04-release.md)、[docs/process/05-ops.md](../../../docs/process/05-ops.md)、[docs/ops/ops-handbook.md](../../../docs/ops/ops-handbook.md) 保持一致。
