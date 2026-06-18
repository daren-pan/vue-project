#!/bin/bash
echo "========================================="
echo "  RuoYi-Cloud CI/CD Pipeline"
echo "  Build: #${BUILD_NUMBER}"
echo "========================================="

# ============================================================
# 1. 登录 ACR 镜像仓库
# ============================================================
echo ""
echo "[1/5] 登录 ACR..."
docker login --username=爱笑的小白11 --password=cai123456 crpi-zu4tna9y8drenzc4.cn-hangzhou.personal.cr.aliyuncs.com
REG=crpi-zu4tna9y8drenzc4.cn-hangzhou.personal.cr.aliyuncs.com/ruoyi-personal
echo "  ✅ 登录成功"

# ============================================================
# 2. 准备最新代码
# ============================================================
echo ""
echo "[2/5] 准备代码..."
docker run --rm -v /workspace:/workspace alpine sh -c "rm -rf /workspace/* /workspace/.[!.]* /workspace/..?*"
cp -r /var/jenkins_home/workspace/RuoYi-Cloud/. /workspace/
cd /workspace
git config --global --add safe.directory /workspace

# 生成唯一镜像标签：版本号-构建号-Git短哈希
GIT_HASH=$(git rev-parse --short HEAD)
UNIQUE_TAG="3.6.7-b${BUILD_NUMBER}-${GIT_HASH}"
echo "  镜像标签: ${UNIQUE_TAG}"

OLD=$(git rev-parse HEAD~1 2>/dev/null || echo "")
if [ -n "$OLD" ]; then
  DIFF=$(git diff --name-only $OLD HEAD 2>/dev/null || echo ALL)
else
  DIFF=ALL
fi

B_UI=$(echo "$DIFF" | grep -c "^ruoyi-ui/")
B_A=$(echo "$DIFF" | grep -cE "^(ruoyi-auth|ruoyi-api|ruoyi-common)/")
B_G=$(echo "$DIFF" | grep -cE "^(ruoyi-gateway|ruoyi-api|ruoyi-common)/")
B_S=$(echo "$DIFF" | grep -cE "^(ruoyi-modules/ruoyi-system|ruoyi-api|ruoyi-common)/")
[ "$DIFF" = ALL ] && { B_UI=1; B_A=1; B_G=1; B_S=1; }

echo "  变更文件: $DIFF"
echo "  UI=$B_UI  Auth=$B_A  Gateway=$B_G  System=$B_S"

# ============================================================
# 3. 并行编译（Maven + npm）
# ============================================================
echo ""
echo "[3/5] 编译..."

M=""
[ $B_A -gt 0 ] && M="$M,ruoyi-auth"
[ $B_G -gt 0 ] && M="$M,ruoyi-gateway"
[ $B_S -gt 0 ] && M="$M,ruoyi-modules/ruoyi-system"

M_PID=""
if [ -n "$M" ]; then
  echo "  [后端] Maven 编译: ${M#,}"
  docker run --rm -v /cache/m2:/root/.m2 -v /workspace:/workspace -w /workspace \
    maven:3.9-eclipse-temurin-17 mvn -s settings.xml clean package -DskipTests -pl ${M#,} -am &
  M_PID=$!
else
  echo "  [后端] 无变更，跳过 Maven"
fi

if [ $B_UI -gt 0 ]; then
  echo "  [前端] npm 编译 + 构建镜像..."
  docker run --rm -v /cache/npm:/root/.npm -v /workspace/ruoyi-ui:/app -w /app \
    node:18-alpine sh -c "npm install --registry=https://registry.npmmirror.com && npm run build:prod"
  echo "  [前端] 编译完成"
else
  echo "  [前端] 无变更，跳过"
fi

# ============================================================
# 4. 构建镜像 + 推送 + 部署
# ============================================================
echo ""
echo "[4/5] 构建 & 部署..."

[ -n "$M_PID" ] && { echo "  等待 Maven 完成..."; wait $M_PID; }

bd() {
  echo "  [${1}] 打包镜像..."
  cp $2/target/*.jar docker/ruoyi/$3/jar/ 2>/dev/null || true
  docker build \
    -t $REG/$1:${UNIQUE_TAG} \
    -t $REG/$1:3.6.7 \
    -f docker/ruoyi/$3/dockerfile docker/ruoyi/$3
  echo "  [${1}] 推送 ACR（唯一标签: ${UNIQUE_TAG}）..."
  docker push $REG/$1:${UNIQUE_TAG}
  docker push $REG/$1:3.6.7
}

if [ $B_UI -gt 0 ]; then
  echo "  [nginx] 打包镜像..."
  mkdir -p docker/nginx/html
  rm -rf docker/nginx/html/dist
  cp -r ruoyi-ui/dist docker/nginx/html/dist
  docker build --no-cache \
    -t $REG/ruoyi-nginx:${UNIQUE_TAG} \
    -t $REG/ruoyi-nginx:latest \
    -f docker/nginx/dockerfile docker/nginx
  echo "  [nginx] 推送 ACR（唯一标签: ${UNIQUE_TAG}）..."
  docker push $REG/ruoyi-nginx:${UNIQUE_TAG}
  docker push $REG/ruoyi-nginx:latest
fi

[ $B_A -gt 0 ] && bd ruoyi-auth ruoyi-auth auth
[ $B_G -gt 0 ] && bd ruoyi-gateway ruoyi-gateway gateway
[ $B_S -gt 0 ] && bd ruoyi-system ruoyi-modules/ruoyi-system modules/system

# Helm 统一部署（所有服务一个命令更新）
if [ $B_UI -gt 0 ] || [ $B_A -gt 0 ] || [ $B_G -gt 0 ] || [ $B_S -gt 0 ]; then
  echo ""
  echo "  [Helm] 滚动更新所有服务（imageTag: ${UNIQUE_TAG}）..."
  helm upgrade ruoyi docker/k8s/charts/ruoyi-cloud -n ruoyi \
    --set imageTag=${UNIQUE_TAG} \
    --set image.nginxTag=${UNIQUE_TAG} \
    --reuse-values
  kubectl rollout status deploy/ruoyi-gateway -n ruoyi --timeout=120s
  kubectl rollout status deploy/ruoyi-system -n ruoyi --timeout=120s
  echo "  [Helm] ✅ 完成"
fi

# ============================================================
# 5. 健康检查
# ============================================================
echo ""
echo "[5/5] 健康检查..."

hc() {
  code=$(curl -s --max-time 5 -o /dev/null -w '%{http_code}' $1 2>/dev/null)
  [ "$code" = "200" ] && echo "  [$2] ✅ OK" || echo "  [$2] ❌ FAIL ($code)"
}

[ $B_UI -gt 0 ] && { sleep 5; hc http://ruoyi-nginx:30080 nginx; }
[ $B_A -gt 0 ] && { sleep 5; hc http://ruoyi-auth:8080/actuator/health ruoyi-auth; }
[ $B_G -gt 0 ] && { sleep 5; hc http://ruoyi-gateway:8080/actuator/health ruoyi-gateway; }
[ $B_S -gt 0 ] && { sleep 5; hc http://ruoyi-system:8080/actuator/health ruoyi-system; }

echo ""
echo "========================================="
echo "  Pipeline #${BUILD_NUMBER} 完成"
echo "========================================="
