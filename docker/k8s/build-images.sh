#!/bin/bash
# RuoYi-Cloud K8s - Build Docker images
# Usage: ./build-images.sh [all|jar|image|frontend]

set -e

PROJECT_ROOT="/d/RuoyiProject/RuoYi-Cloud"
DOCKER_DIR="$PROJECT_ROOT/docker"
ACTION=${1:-all}

build_jar() {
    echo "[Step 1/3] Maven package (skip tests)..."
    cd "$PROJECT_ROOT"
    mvn clean package -DskipTests -q
    if [ $? -ne 0 ]; then
        echo "[ERROR] Maven build failed!" >&2
        exit 1
    fi
    echo "[OK] Maven build success!"
}

copy_jars() {
    echo "[Step 2/3] Copy JAR files to docker dirs..."

    cp -f "$PROJECT_ROOT/ruoyi-gateway/target/ruoyi-gateway.jar" \
          "$DOCKER_DIR/ruoyi/gateway/jar/"
    cp -f "$PROJECT_ROOT/ruoyi-auth/target/ruoyi-auth.jar" \
          "$DOCKER_DIR/ruoyi/auth/jar/"
    cp -f "$PROJECT_ROOT/ruoyi-modules/ruoyi-system/target/ruoyi-modules-system.jar" \
          "$DOCKER_DIR/ruoyi/modules/system/jar/"
    cp -f "$PROJECT_ROOT/ruoyi-modules/ruoyi-gen/target/ruoyi-modules-gen.jar" \
          "$DOCKER_DIR/ruoyi/modules/gen/jar/"
    cp -f "$PROJECT_ROOT/ruoyi-modules/ruoyi-job/target/ruoyi-modules-job.jar" \
          "$DOCKER_DIR/ruoyi/modules/job/jar/"
    cp -f "$PROJECT_ROOT/ruoyi-modules/ruoyi-file/target/ruoyi-modules-file.jar" \
          "$DOCKER_DIR/ruoyi/modules/file/jar/"
    cp -f "$PROJECT_ROOT/ruoyi-visual/ruoyi-monitor/target/ruoyi-visual-monitor.jar" \
          "$DOCKER_DIR/ruoyi/visual/monitor/jar/"

    echo "[OK] JARs copied!"
}

build_docker_images() {
    echo "[Step 3/3] Build Docker images..."

    images=(
        "ruoyi-gateway:3.6.7|$DOCKER_DIR/ruoyi/gateway"
        "ruoyi-auth:3.6.7|$DOCKER_DIR/ruoyi/auth"
        "ruoyi-modules-system:3.6.7|$DOCKER_DIR/ruoyi/modules/system"
        "ruoyi-modules-gen:3.6.7|$DOCKER_DIR/ruoyi/modules/gen"
        "ruoyi-modules-job:3.6.7|$DOCKER_DIR/ruoyi/modules/job"
        "ruoyi-modules-file:3.6.7|$DOCKER_DIR/ruoyi/modules/file"
        "ruoyi-visual-monitor:3.6.7|$DOCKER_DIR/ruoyi/visual/monitor"
    )

    for entry in "${images[@]}"; do
        IFS='|' read -r tag path <<< "$entry"
        dockerfile="$path/dockerfile"

        if [ -f "$dockerfile" ]; then
            echo "  Building $tag ..."
            docker build -t "$tag" -f "$dockerfile" "$path" 2>&1
            if [ $? -eq 0 ]; then
                echo "  [OK] $tag built!"
            else
                echo "  [FAIL] $tag build failed!" >&2
            fi
        else
            echo "  [SKIP] Dockerfile not found: $dockerfile" >&2
        fi
    done

    echo "[OK] All Docker images built!"
}

build_frontend() {
    echo "Building frontend..."
    cd "$PROJECT_ROOT/ruoyi-ui"
    npm install
    npm run build:prod
    echo "[OK] Frontend built!"
}

# ====== Main ======
case "$ACTION" in
    jar)
        build_jar
        copy_jars
        ;;
    image)
        build_docker_images
        ;;
    frontend)
        build_frontend
        ;;
    all)
        build_jar
        copy_jars
        build_docker_images
        ;;
    *)
        echo "Usage: $0 [all|jar|image|frontend]"
        exit 1
        ;;
esac
