pipeline {
    agent any

    environment {
        // 本地镜像 tag（与 K8s 部署文件保持一致）
        IMAGE_TAG = '3.6.7'

        // K8s 命名空间
        K8S_NAMESPACE = 'ruoyi'

        // Maven 配置
        MAVEN_OPTS = '-Dmaven.test.skip=true'
    }

    stages {
        stage('Checkout') {
            steps {
                echo "拉取代码..."
                checkout scm
            }
        }

        stage('Build Backend') {
            steps {
                echo "Maven 编译后端..."
                sh '''
                    mvn clean package -DskipTests -pl \
                      ruoyi-auth,\
                      ruoyi-gateway,\
                      ruoyi-modules/ruoyi-system,\
                      ruoyi-modules/ruoyi-gen,\
                      ruoyi-modules/ruoyi-job,\
                      ruoyi-modules/ruoyi-file,\
                      ruoyi-visual/ruoyi-monitor \
                      -am
                '''
            }
        }

        stage('Build Frontend') {
            steps {
                echo "npm 构建前端..."
                dir('ruoyi-ui') {
                    sh 'npm install'
                    sh 'npm run build:prod'
                }
                sh 'cp -r ruoyi-ui/dist docker/nginx/html/dist'
            }
        }

        stage('Copy Jars') {
            steps {
                echo "复制 jar 包到 Docker 上下文..."
                sh '''
                    cp ruoyi-auth/target/ruoyi-auth.jar docker/ruoyi/auth/jar/
                    cp ruoyi-gateway/target/ruoyi-gateway.jar docker/ruoyi/gateway/jar/
                    cp ruoyi-modules/ruoyi-system/target/ruoyi-modules-system.jar docker/ruoyi/modules/system/jar/
                    cp ruoyi-modules/ruoyi-gen/target/ruoyi-modules-gen.jar docker/ruoyi/modules/gen/jar/
                    cp ruoyi-modules/ruoyi-job/target/ruoyi-modules-job.jar docker/ruoyi/modules/job/jar/
                    cp ruoyi-modules/ruoyi-file/target/ruoyi-modules-file.jar docker/ruoyi/modules/file/jar/
                    cp ruoyi-visual/ruoyi-monitor/target/ruoyi-visual-monitor.jar docker/ruoyi/visual/monitor/jar/
                '''
            }
        }

        stage('Build Docker Images') {
            steps {
                echo "构建本地 Docker 镜像..."
                sh '''
                    docker build -t docker-ruoyi-auth:${IMAGE_TAG} -f docker/ruoyi/auth/dockerfile docker/ruoyi/auth
                    docker build -t docker-ruoyi-gateway:${IMAGE_TAG} -f docker/ruoyi/gateway/dockerfile docker/ruoyi/gateway
                    docker build -t docker-ruoyi-system:${IMAGE_TAG} -f docker/ruoyi/modules/system/dockerfile docker/ruoyi/modules/system
                    docker build -t docker-ruoyi-gen:${IMAGE_TAG} -f docker/ruoyi/modules/gen/dockerfile docker/ruoyi/modules/gen
                    docker build -t docker-ruoyi-job:${IMAGE_TAG} -f docker/ruoyi/modules/job/dockerfile docker/ruoyi/modules/job
                    docker build -t docker-ruoyi-file:${IMAGE_TAG} -f docker/ruoyi/modules/file/dockerfile docker/ruoyi/modules/file
                    docker build -t docker-ruoyi-monitor:${IMAGE_TAG} -f docker/ruoyi/visual/monitor/dockerfile docker/ruoyi/visual/monitor
                    docker build -t ruoyi-nginx:latest -f docker/nginx/dockerfile docker/nginx
                '''
            }
        }

        stage('Deploy to K8s') {
            steps {
                echo "滚动重启 K8s 服务..."
                sh '''
                    kubectl rollout restart deployment/ruoyi-auth -n ${K8S_NAMESPACE}
                    kubectl rollout restart deployment/ruoyi-gateway -n ${K8S_NAMESPACE}
                    kubectl rollout restart deployment/ruoyi-system -n ${K8S_NAMESPACE}
                    kubectl rollout restart deployment/ruoyi-gen -n ${K8S_NAMESPACE}
                    kubectl rollout restart deployment/ruoyi-job -n ${K8S_NAMESPACE}
                    kubectl rollout restart deployment/ruoyi-nginx -n ${K8S_NAMESPACE}

                    echo "等待服务就绪..."
                    kubectl rollout status deployment/ruoyi-gateway -n ${K8S_NAMESPACE} --timeout=180s
                    kubectl rollout status deployment/ruoyi-system -n ${K8S_NAMESPACE} --timeout=180s
                    kubectl rollout status deployment/ruoyi-nginx -n ${K8S_NAMESPACE} --timeout=180s
                '''
            }
        }
    }

    post {
        success {
            echo "============================================"
            echo "CI/CD 流水线执行成功！构建号: ${BUILD_NUMBER}"
            echo "前端访问: http://localhost:8880"
            echo "============================================"
        }
        failure {
            echo "CI/CD 流水线执行失败！请检查日志。"
        }
    }
}
