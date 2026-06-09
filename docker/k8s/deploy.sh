#!/bin/bash
# RuoYi-Cloud K8s - Deploy to Kubernetes
# Usage: ./deploy.sh [all|infra|nacos|micro|nginx|status|delete]

set -e

K8S_DIR="/d/RuoyiProject/RuoYi-Cloud/docker/k8s"
NAMESPACE="ruoyi"
ACTION=${1:-all}

deploy_infra() {
    echo "[Step 1/4] Deploy infrastructure (MySQL, Redis)..."

    echo "  Creating namespace..."
    kubectl apply -f "$K8S_DIR/01-namespace.yaml"

    echo "  Deploying MySQL..."
    kubectl apply -f "$K8S_DIR/02-mysql.yaml"
    echo "  Waiting for MySQL ready..."
    kubectl wait --for=condition=ready pod -l app=ruoyi-mysql -n $NAMESPACE --timeout=120s 2>/dev/null || true

    echo "  Deploying Redis..."
    kubectl apply -f "$K8S_DIR/03-redis.yaml"
    echo "  Waiting for Redis ready..."
    kubectl wait --for=condition=ready pod -l app=ruoyi-redis -n $NAMESPACE --timeout=60s 2>/dev/null || true

    echo "[OK] Infrastructure deployed!"
}

deploy_nacos() {
    echo "[Step 2/4] Deploy Nacos..."
    kubectl apply -f "$K8S_DIR/04-nacos.yaml"
    echo "  Waiting for Nacos ready..."
    kubectl wait --for=condition=ready pod -l app=ruoyi-nacos -n $NAMESPACE --timeout=120s 2>/dev/null || true
    echo "[OK] Nacos deployed!"
}

deploy_microservices() {
    echo "[Step 3/4] Deploy microservices..."
    kubectl apply -f "$K8S_DIR/05-microservices.yaml"

    echo "  Waiting for pods to start..."
    sleep 10
    kubectl get pods -n $NAMESPACE
    echo "[OK] Microservices deployed!"
}

deploy_nginx() {
    echo "[Step 4/4] Deploy Nginx..."
    kubectl apply -f "$K8S_DIR/06-nginx.yaml"
    echo "  Waiting for Nginx ready..."
    kubectl wait --for=condition=ready pod -l app=ruoyi-nginx -n $NAMESPACE --timeout=60s 2>/dev/null || true
    echo "[OK] Nginx deployed!"
}

show_status() {
    echo ""
    echo "========== Deployment Status =========="
    echo ""
    echo "--- Pods ---"
    kubectl get pods -n $NAMESPACE -o wide
    echo ""
    echo "--- Services ---"
    kubectl get svc -n $NAMESPACE
    echo ""
    echo "--- PVCs ---"
    kubectl get pvc -n $NAMESPACE
    echo ""
    echo "Access URLs:"
    echo "  Nacos:   http://localhost:8848/nacos"
    echo "  Frontend: http://localhost:30080"
}

delete_all() {
    echo "Deleting all resources..."
    kubectl delete -f "$K8S_DIR/06-nginx.yaml" --ignore-not-found 2>/dev/null || true
    kubectl delete -f "$K8S_DIR/05-microservices.yaml" --ignore-not-found 2>/dev/null || true
    kubectl delete -f "$K8S_DIR/04-nacos.yaml" --ignore-not-found 2>/dev/null || true
    kubectl delete -f "$K8S_DIR/03-redis.yaml" --ignore-not-found 2>/dev/null || true
    kubectl delete -f "$K8S_DIR/02-mysql.yaml" --ignore-not-found 2>/dev/null || true
    kubectl delete namespace $NAMESPACE --ignore-not-found 2>/dev/null || true
    echo "[OK] All resources deleted!"
}

# ====== Main ======
case "$ACTION" in
    infra)
        deploy_infra
        ;;
    nacos)
        deploy_nacos
        ;;
    micro)
        deploy_microservices
        ;;
    nginx)
        deploy_nginx
        ;;
    status)
        show_status
        ;;
    delete)
        delete_all
        ;;
    all)
        echo "===== Deploying RuoYi-Cloud to K8s ====="
        deploy_infra
        deploy_nacos
        deploy_microservices
        deploy_nginx
        show_status
        echo ""
        echo "[OK] Deployment complete!"
        ;;
    *)
        echo "Usage: $0 [all|infra|nacos|micro|nginx|status|delete]"
        echo ""
        echo "Options:"
        echo "  all       - Full deployment"
        echo "  infra     - MySQL + Redis only"
        echo "  nacos     - Nacos only"
        echo "  micro     - Microservices only"
        echo "  nginx     - Nginx only"
        echo "  status    - Show deployment status"
        echo "  delete    - Delete all resources"
        exit 1
        ;;
esac
