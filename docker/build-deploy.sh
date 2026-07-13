#!/bin/bash
# ============================================================
# ECS 一键构建部署脚本 (Ubuntu 22.04)
#   ./build-deploy.sh all      构建全部 + 部署全部
#   ./build-deploy.sh backend  构建后端 + 部署业务模块
#   ./build-deploy.sh frontend 构建前端 + 部署业务模块
#   ./build-deploy.sh base     仅启动基础服务
#   ./build-deploy.sh modules  仅部署业务模块（不编译）
# ============================================================
set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"

MVN_CMD="mvn clean package -DskipTests"

log()  { echo -e "\033[0;32m[INFO]\033[0m $1"; }
warn() { echo -e "\033[1;33m[WARN]\033[0m $1"; }
err()  { echo -e "\033[0;31m[ERROR]\033[0m $1"; exit 1; }

# ============================================================
# 等待容器就绪
# ============================================================
wait_for() {
    local container="$1"
    local timeout="${2:-120}"
    local elapsed=0
    log "⏳ 等待 $container 就绪（最长 ${timeout}s）..."
    while [ $elapsed -lt $timeout ]; do
        local health
        health=$(docker inspect -f '{{if .State.Running}}{{.State.Health.Status}}{{else}}stopped{{end}}' "$container" 2>/dev/null || echo "not_found")
        if [ "$health" = "healthy" ] || [ "$health" = "none" ]; then
            log "   ✅ $container 已就绪"
            return 0
        elif [ "$health" = "stopped" ] || [ "$health" = "not_found" ]; then
            warn "   ❌ $container 未运行，重试..."
        fi
        sleep 3
        elapsed=$((elapsed + 3))
    done
    warn "   ⚠️  $container 超时未就绪"
}

# ============================================================
# 构建后端
# ============================================================
build_backend() {
    log "🔧 Maven 编译后端..."
    cd "$PROJECT_DIR"
    $MVN_CMD -pl ruoyi-common,ruoyi-api -am
    local modules="ruoyi-gateway,ruoyi-auth,ruoyi-modules/ruoyi-system,ruoyi-modules/ruoyi-gen,ruoyi-modules/ruoyi-job,ruoyi-modules/ruoyi-file,ruoyi-visual/ruoyi-monitor"
    $MVN_CMD -pl "$modules" -am
    log "✅ Maven 编译完成"
}

# ============================================================
# 构建前端
# ============================================================
build_frontend() {
    log "🎨 npm 构建前端..."
    cd "$PROJECT_DIR/ruoyi-ui"
    npm install --registry=https://registry.npmmirror.com
    npm run build:prod
    log "✅ 前端构建完成"
}

# ============================================================
# 复制产物到 docker 构建目录
# ============================================================
copy_artifacts() {
    log "📋 复制构建产物..."
    cp "$PROJECT_DIR/ruoyi-gateway/target/ruoyi-gateway.jar"                       "$SCRIPT_DIR/ruoyi/gateway/jar/"
    cp "$PROJECT_DIR/ruoyi-auth/target/ruoyi-auth.jar"                             "$SCRIPT_DIR/ruoyi/auth/jar/"
    cp "$PROJECT_DIR/ruoyi-modules/ruoyi-system/target/ruoyi-modules-system.jar"   "$SCRIPT_DIR/ruoyi/modules/system/jar/"
    cp "$PROJECT_DIR/ruoyi-modules/ruoyi-gen/target/ruoyi-modules-gen.jar"         "$SCRIPT_DIR/ruoyi/modules/gen/jar/"
    cp "$PROJECT_DIR/ruoyi-modules/ruoyi-job/target/ruoyi-modules-job.jar"         "$SCRIPT_DIR/ruoyi/modules/job/jar/"
    cp "$PROJECT_DIR/ruoyi-modules/ruoyi-file/target/ruoyi-modules-file.jar"       "$SCRIPT_DIR/ruoyi/modules/file/jar/"
    cp "$PROJECT_DIR/ruoyi-visual/ruoyi-monitor/target/ruoyi-visual-monitor.jar"   "$SCRIPT_DIR/ruoyi/visual/monitor/jar/"
    mkdir -p "$SCRIPT_DIR/nginx/html/dist"
    rm -rf "$SCRIPT_DIR/nginx/html/dist/"*
    cp -r "$PROJECT_DIR/ruoyi-ui/dist/"* "$SCRIPT_DIR/nginx/html/dist/"
    log "✅ 产物复制完成"
}

# ============================================================
# 启动基础服务（逐个等待就绪：MySQL→Redis→Nacos）
# ============================================================
start_base() {
    log "🚀 启动基础服务..."

    cd "$SCRIPT_DIR"

    # MySQL 先启，等就绪
    docker compose up -d --force-recreate ruoyi-mysql
    wait_for ruoyi-mysql 90 || true

    # Redis 依赖 MySQL（实际不依赖，但保持顺序）
    docker compose up -d --force-recreate ruoyi-redis
    wait_for ruoyi-redis 30 || true

    # Nacos 依赖 MySQL，必须等 MySQL 就绪后才能连接
    docker compose up -d --force-recreate ruoyi-nacos
    wait_for ruoyi-nacos 90 || true

    log "✅ 基础服务全部就绪"
}

# ============================================================
# 启动业务模块（前提：基础服务已就绪）
# ============================================================
start_modules() {
    log "🚀 启动业务模块..."
    cd "$SCRIPT_DIR"
    docker compose up -d --force-recreate \
        ruoyi-nginx \
        ruoyi-gateway \
        ruoyi-auth \
        ruoyi-modules-system \
        ruoyi-sentinel
    log "✅ 业务模块已启动"
}

# ============================================================
# 查看状态
# ============================================================
show_status() {
    echo ""
    log "📊 容器状态："
    cd "$SCRIPT_DIR"
    docker compose ps --format "table {{.Name}}\t{{.Status}}\t{{.Ports}}"
}

# ============================================================
# 主入口
# ============================================================
case "${1:-all}" in
    all)
        build_backend      # 1. Maven 编译
        build_frontend     # 2. npm 打包
        copy_artifacts     # 3. 复制 jar/dist
        start_base         # 4. 启动 MySQL→Redis→Nacos（逐个等就绪）
        start_modules      # 5. 启动业务模块
        show_status
        ;;
    backend)
        build_backend
        copy_artifacts
        start_base
        start_modules
        show_status
        ;;
    frontend)
        build_frontend
        copy_artifacts
        start_modules
        show_status
        ;;
    base)
        start_base
        show_status
        ;;
    modules)
        copy_artifacts
        start_base
        start_modules
        show_status
        ;;
    *)
        echo "用法: $0 {all|backend|frontend|base|modules}"
        echo ""
        echo "  all      - 构建全部 → 启动 MySQL→Redis→Nacos（等待就绪）→ 启动业务模块"
        echo "  backend  - 构建后端 → 同上"
        echo "  frontend - 构建前端 → 部署业务模块"
        echo "  base     - 仅启动 MySQL→Redis→Nacos（带等待）"
        echo "  modules  - 跳过编译，复制产物 → 部署"
        exit 1
        ;;
esac
