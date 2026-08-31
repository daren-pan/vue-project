# 模块文档（`docs/modules/`）

> 本目录承载「开发阶段」每个模块的维护文档，是 Agent 修改模块前**必读**的上下文来源（见 [`AGENTS.md`](../../AGENTS.md) §2.1）。
> 通用编码规范见 [coding-standard.md](coding-standard.md)；模块职责矩阵见 [`../architecture/00-architecture-overview.md`](../architecture/00-architecture-overview.md)。

---

## 1. 目标

- **模块自描述**：让 Agent 在修改任一模块前，无需翻大量源码即可掌握职责、边界、依赖、关键接口与运维要点。
- **随改随更**：每次改完代码，同步更新「接口清单 / 变化 / 验证」，保持文档与实现一致（提交留痕见 `docs/changelog/<date>.md`）。

## 2. 维护规则（每次修改模块文档时遵守）

1. **改动即更新**：新增/修改接口、表、配置、依赖时，同步更新对应模块文档，避免上下文失真。
2. **只写事实**：职责、依赖、接口用稳定描述；不写易变细节（如端口、密码），环境差异归 `config/` 与 `local-env.yml`。
3. **每模块一份**：一份 `docs/modules/<module>.md`；通用跨模块规范统一放 `coding-standard.md`，不在各模块文档重复。

## 3. 各模块文档

| 模块 | 文档 | 说明 |
|------|------|------|
| ruoyi-system | [ruoyi-system.md](ruoyi-system.md) | 系统管理（用户/角色/菜单/字典/参数/通知/日志） |
| ruoyi-workflow | [ruoyi-workflow.md](ruoyi-workflow.md) | 流程定义/实例/任务（Flowable 审批流） |
| ruoyi-gen | [ruoyi-gen.md](ruoyi-gen.md) | 代码生成 |
| ruoyi-job | [ruoyi-job.md](ruoyi-job.md) | 定时任务调度 |
| ruoyi-file | [ruoyi-file.md](ruoyi-file.md) | 文件上传/预览/下载（MinIO） |

> 网关 / 认证 / 监控 / 前端等按需补充；不为不存在需的用途重复建页。

---

## 关联文档

- 编码规范：[coding-standard.md](coding-standard.md)
- 架构总览：[../architecture/00-architecture-overview.md](../architecture/00-architecture-overview.md)
- 全局规范：[../../AGENTS.md](../../AGENTS.md)
