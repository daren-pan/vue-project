#!/bin/bash
# ============================================================
# ECS 快速更新脚本 (Ubuntu 22.04)
# 用法: ./bin/update-test.sh
# ============================================================
set -e

PROJECT_DIR="$HOME/RuoYi-Cloud"

# 检查项目目录
if [ ! -d "$PROJECT_DIR" ]; then
    echo "❌ 项目目录不存在: $PROJECT_DIR"
    exit 1
fi

# 检查 docker compose
if ! docker compose version &> /dev/null; then
    echo "❌ 未找到 docker compose 插件"
    exit 1
fi

cd "$PROJECT_DIR"

echo "📦 保存本地修改..."
git stash push -m "auto-stash-$(date +%Y%m%d-%H%M%S)" 2>/dev/null || true

echo "⬇️  拉取最新代码..."
git pull

echo "📂 恢复本地修改..."
git stash pop 2>/dev/null || echo "  (无暂存修改)"

echo "🚀 重建核心服务..."
cd docker
docker compose up -d --force-recreate \
    ruoyi-nacos \
    ruoyi-redis \
    ruoyi-mysql \
    ruoyi-nginx \
    ruoyi-gateway \
    ruoyi-auth \
    ruoyi-modules-system \
    ruoyi-sentinel

echo ""
echo "✅ 完成！服务状态："
docker compose ps --format "table {{.Name}}\t{{.Status}}\t{{.Ports}}"
docker compose ps
