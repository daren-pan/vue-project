#!/bin/sh

# K8s 部署脚本
# 用法: sh k8s-deploy.sh [base|modules|elk|all|stop]

NAMESPACE="ruoyi"

usage() {
	echo "Usage: sh k8s-deploy.sh [base|nexus|modules|elk|jenkins|all|stop|rm]"
	echo ""
	echo "  base    - 启动基础中间件 (MySQL, Redis, Nacos)"
	echo "  nexus   - 启动 Maven 私服 (Nexus)"
	echo "  modules - 启动业务模块 (Gateway, Auth, System, Gen, Job, Nginx)"
	echo "  elk     - 启动 ELK 日志系统 (Elasticsearch, Logstash, Kibana, Filebeat)"
	echo "  jenkins - 启动 Jenkins CI/CD"
	echo "  all     - 启动全部"
	echo "  stop    - 停止所有 Deployment（将副本数置零）"
	echo "  rm      - 删除所有资源"
	exit 1
}

# 启动基础中间件
base(){
	echo "=== 部署基础中间件 ==="
	kubectl apply -f docker/k8s/01-namespace.yaml
	kubectl apply -f docker/k8s/02-mysql.yaml
	kubectl apply -f docker/k8s/03-redis.yaml
	kubectl apply -f docker/k8s/04-nacos.yaml
	echo "等待 Nacos 就绪..."
	kubectl rollout status deployment ruoyi-nacos -n $NAMESPACE
	echo "基础中间件部署完成"
}

# 启动业务模块
modules(){
	echo "=== 部署业务模块 ==="
	kubectl apply -f docker/k8s/05-microservices.yaml
	kubectl apply -f docker/k8s/06-nginx.yaml
	echo "等待业务模块就绪..."
	kubectl rollout status deployment ruoyi-gateway -n $NAMESPACE
	kubectl rollout status deployment ruoyi-auth -n $NAMESPACE
	kubectl rollout status deployment ruoyi-system -n $NAMESPACE
	echo "业务模块部署完成"
	echo "前端访问地址: http://localhost:30080"
}

# 启动 ELK 日志系统
elk(){
	echo "=== 部署 ELK 日志系统 ==="
	kubectl apply -f docker/k8s/07-elk.yaml
	echo "等待 Elasticsearch 就绪..."
	kubectl rollout status statefulset elasticsearch-elk -n $NAMESPACE
	echo "等待 Logstash 就绪..."
	kubectl rollout status deployment logstash -n $NAMESPACE
	echo "等待 Kibana 就绪..."
	kubectl rollout status deployment kibana -n $NAMESPACE
	echo "ELK 部署完成"
	echo "Kibana 访问地址: http://localhost:15601"
}

# 启动 Jenkins
jenkins(){
	echo "=== 部署 Jenkins ==="
	kubectl apply -f docker/k8s/09-jenkins.yaml
	kubectl apply -f docker/k8s/08-ingress.yaml
	echo "等待 Jenkins 就绪..."
	kubectl rollout status deployment jenkins -n $NAMESPACE
	# 获取初始密码
	sleep 10
	JENKINS_POD=$(kubectl get pod -n $NAMESPACE -l app=jenkins -o jsonpath="{.items[0].metadata.name}")
	INIT_PWD=$(kubectl exec -n $NAMESPACE $JENKINS_POD -- cat /var/jenkins_home/secrets/initialAdminPassword 2>/dev/null)
	echo ""
	echo "=== Jenkins 访问信息 ==="
	echo "地址: http://localhost:8880/jenkins"
	echo "初始密码: $INIT_PWD"
}

# 启动 Nexus Maven 私服
nexus(){
	echo "=== 部署 Nexus Maven 私服 ==="
	kubectl apply -f docker/k8s/10-nexus.yaml
	echo "等待 Nexus 就绪（首次启动需 1-2 分钟）..."
	kubectl rollout status deployment ruoyi-nexus -n $NAMESPACE --timeout=180s
	echo ""
	echo "Nexus 部署完成！"
	echo "  初始化密码："
	kubectl exec deploy/ruoyi-nexus -n $NAMESPACE -- cat /nexus-data/admin.password 2>/dev/null || echo "  （请稍等片刻后手动查看）"
	echo ""
	echo "  后续配置步骤:"
	echo "    1. 浏览器打开 http://localhost:30081"
	echo "    2. 用上面的密码登录（用户 admin）"
	echo "    3. 设置新密码为 admin123"
	echo "    4. 创建 maven-public 仓库组"
}

# 启动全部
all(){
	base
	nexus
	modules
	elk
	jenkins
	echo ""
	echo "=== 全部部署完成 ==="
	echo "Nexus:   http://localhost:30081"
	echo "前端:    http://localhost:30080"
	echo "Nacos:   http://localhost:8848/nacos"
	echo "Kibana:  http://localhost:15601"
}

# 停止所有服务（副本置零）
stop(){
	echo "=== 停止所有服务 ==="
	kubectl scale deployment -n $NAMESPACE --all --replicas=0 2>/dev/null
	kubectl scale statefulset -n $NAMESPACE --all --replicas=0 2>/dev/null
	echo "已停止所有服务"
}

# 删除所有资源
rm(){
	echo "=== 删除所有资源 ==="
	kubectl delete -f docker/k8s/10-nexus.yaml --ignore-not-found
	kubectl delete -f docker/k8s/09-jenkins.yaml --ignore-not-found
	kubectl delete -f docker/k8s/07-elk.yaml --ignore-not-found
	kubectl delete -f docker/k8s/06-nginx.yaml --ignore-not-found
	kubectl delete -f docker/k8s/05-microservices.yaml --ignore-not-found
	kubectl delete -f docker/k8s/04-nacos.yaml --ignore-not-found
	kubectl delete -f docker/k8s/03-redis.yaml --ignore-not-found
	kubectl delete -f docker/k8s/02-mysql.yaml --ignore-not-found
	kubectl delete -f docker/k8s/01-namespace.yaml --ignore-not-found
	echo "已删除所有 K8s 资源"
}

case "$1" in
"base")
	base
;;
"nexus")
	nexus
;;
"modules")
	modules
;;
"elk")
	elk
;;
"jenkins")
	jenkins
;;
"all")
	all
;;
"stop")
	stop
;;
"rm")
	rm
;;
*)
	usage
;;
esac
