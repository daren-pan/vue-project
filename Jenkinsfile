pipeline {
    agent any

    environment {
        // 镜像版本（与 K8s 部署文件保持一致）
        IMAGE_TAG = '3.6.7'

        // 阿里云 ACR 镜像仓库
        ACR_REGISTRY = 'crpi-zu4tna9y8drenzc4.cn-hangzhou.personal.cr.aliyuncs.com'
        ACR_NAMESPACE = 'ruoyi-personal'
        REG = "${ACR_REGISTRY}/${ACR_NAMESPACE}"

        // K8s 命名空间
        K8S_NAMESPACE = 'ruoyi'

        // 缓存路径（Maven .m2 + npm）
        M2_CACHE = '/cache/m2'
        NPM_CACHE = '/cache/npm'
    }

    stages {

        // ============================================================
        // 1. 登录 ACR 镜像仓库
        // ============================================================
        stage('Login ACR') {
            steps {
                echo '[1/5] 登录 ACR...'
                sh '''
                    docker login \
                        --username=爱笑的小白11 \
                        --password=cai123456 \
                        ${ACR_REGISTRY}
                '''
                echo '  ✅ 登录成功'
            }
        }

        // ============================================================
        // 2. 准备代码 + 增量检测 + 生成唯一标签
        // ============================================================
        stage('Prepare & Detect Changes') {
            steps {
                echo '[2/5] 准备代码...'
                checkout scm

                script {
                    // 生成唯一镜像标签：版本号-构建号-Git短哈希
                    GIT_HASH = sh(script: 'git rev-parse --short HEAD', returnStdout: true).trim()
                    UNIQUE_TAG = "3.6.7-b${BUILD_NUMBER}-${GIT_HASH}"
                    echo "  镜像标签: ${UNIQUE_TAG}"

                    echo '  检测文件变更...'
                    try {
                        def oldCommit = sh(script: 'git rev-parse HEAD~1', returnStdout: true).trim()
                        DIFF = sh(script: "git diff --name-only ${oldCommit} HEAD", returnStdout: true).trim()
                    } catch (Exception e) {
                        DIFF = 'ALL'
                    }
                    if (DIFF == '') { DIFF = 'ALL' }

                    B_UI = sh(script: "echo '${DIFF}' | grep -c '^ruoyi-ui/' || true", returnStdout: true).trim().toInteger()
                    B_A  = sh(script: "echo '${DIFF}' | grep -cE '^(ruoyi-auth|ruoyi-api|ruoyi-common)/' || true", returnStdout: true).trim().toInteger()
                    B_G  = sh(script: "echo '${DIFF}' | grep -cE '^(ruoyi-gateway|ruoyi-api|ruoyi-common)/' || true", returnStdout: true).trim().toInteger()
                    B_S  = sh(script: "echo '${DIFF}' | grep -cE '^(ruoyi-modules/ruoyi-system|ruoyi-api|ruoyi-common)/' || true", returnStdout: true).trim().toInteger()
                    B_Gen = sh(script: "echo '${DIFF}' | grep -cE '^(ruoyi-modules/ruoyi-gen|ruoyi-api|ruoyi-common)/' || true", returnStdout: true).trim().toInteger()
                    B_Job = sh(script: "echo '${DIFF}' | grep -cE '^(ruoyi-modules/ruoyi-job|ruoyi-api|ruoyi-common)/' || true", returnStdout: true).trim().toInteger()
                    B_File= sh(script: "echo '${DIFF}' | grep -cE '^(ruoyi-modules/ruoyi-file|ruoyi-api|ruoyi-common)/' || true", returnStdout: true).trim().toInteger()

                    if (DIFF == 'ALL') {
                        B_UI = 1; B_A = 1; B_G = 1; B_S = 1; B_Gen = 1; B_Job = 1; B_File = 1
                    }

                    echo "  变更文件: ${DIFF}"
                    echo "  UI=${B_UI}  Auth=${B_A}  Gateway=${B_G}  System=${B_S}  Gen=${B_Gen}  Job=${B_Job}  File=${B_File}"
                }
            }
        }

        // ============================================================
        // 3. 并行编译（Maven + npm）
        // ============================================================
        stage('Compile') {
            steps {
                echo '[3/5] 编译...'

                script {
                    def mavenModules = []
                    if (B_A > 0)   { mavenModules.add('ruoyi-auth') }
                    if (B_G > 0)   { mavenModules.add('ruoyi-gateway') }
                    if (B_S > 0)   { mavenModules.add('ruoyi-modules/ruoyi-system') }
                    if (B_Gen > 0) { mavenModules.add('ruoyi-modules/ruoyi-gen') }
                    if (B_Job > 0) { mavenModules.add('ruoyi-modules/ruoyi-job') }
                    if (B_File> 0) { mavenModules.add('ruoyi-modules/ruoyi-file') }

                    def parallelTasks = [:]

                    // Maven 后端编译（使用云效 settings.xml）
                    if (mavenModules) {
                        def modules = mavenModules.join(',')
                        echo "  [后端] Maven 编译: ${modules}"
                        parallelTasks['maven'] = {
                            sh """
                                docker run --rm \
                                    -v ${M2_CACHE}:/root/.m2 \
                                    -v ${WORKSPACE}:/workspace \
                                    -w /workspace \
                                    maven:3.9-eclipse-temurin-17 \
                                    mvn -s settings.xml clean package -DskipTests -pl ${modules} -am
                            """
                        }
                    } else {
                        echo '  [后端] 无变更，跳过 Maven'
                    }

                    // npm 前端编译
                    if (B_UI > 0) {
                        echo '  [前端] npm 编译...'
                        parallelTasks['npm'] = {
                            sh """
                                docker run --rm \
                                    -v ${NPM_CACHE}:/root/.npm \
                                    -v ${WORKSPACE}/ruoyi-ui:/app \
                                    -w /app \
                                    node:18-alpine sh -c 'npm install --registry=https://registry.npmmirror.com && npm run build:prod'
                            """
                        }
                    } else {
                        echo '  [前端] 无变更，跳过'
                    }

                    if (parallelTasks) {
                        parallel parallelTasks
                    }
                }
            }
        }

        // ============================================================
        // 4. 构建镜像 + 推送 ACR + Helm 部署
        // ============================================================
        stage('Build, Push & Deploy') {
            steps {
                echo '[4/5] 构建 & 部署...'

                script {
                    // 构建并推送：双标签（唯一标签 + 滚动标签）
                    def buildAndDeploy = { serviceName, jarPath, dockerContext, extraTag = null ->
                        echo "  [${serviceName}] 打包镜像..."
                        sh "cp ${jarPath}/target/*.jar docker/ruoyi/${dockerContext}/jar/ 2>/dev/null || true"
                        def rollingTag = extraTag ?: IMAGE_TAG
                        sh """
                            docker build \
                                -t ${REG}/${serviceName}:${UNIQUE_TAG} \
                                -t ${REG}/${serviceName}:${rollingTag} \
                                -f docker/ruoyi/${dockerContext}/dockerfile \
                                docker/ruoyi/${dockerContext}
                        """
                        echo "  [${serviceName}] 推送 ACR（唯一: ${UNIQUE_TAG}, 滚动: ${rollingTag}）..."
                        sh "docker push ${REG}/${serviceName}:${UNIQUE_TAG}"
                        sh "docker push ${REG}/${serviceName}:${rollingTag}"
                    }

                    // nginx 前端镜像
                    if (B_UI > 0) {
                        echo "  [nginx] 打包镜像..."
                        sh 'mkdir -p docker/nginx/html && rm -rf docker/nginx/html/dist && cp -r ruoyi-ui/dist docker/nginx/html/dist'
                        sh """
                            docker build --no-cache \
                                -t ${REG}/ruoyi-nginx:${UNIQUE_TAG} \
                                -t ${REG}/ruoyi-nginx:latest \
                                -f docker/nginx/dockerfile docker/nginx
                        """
                        echo "  [nginx] 推送 ACR（唯一: ${UNIQUE_TAG}, 滚动: latest）..."
                        sh "docker push ${REG}/ruoyi-nginx:${UNIQUE_TAG}"
                        sh "docker push ${REG}/ruoyi-nginx:latest"
                    }

                    // 后端微服务
                    if (B_A > 0)   { buildAndDeploy('ruoyi-auth', 'ruoyi-auth', 'auth') }
                    if (B_G > 0)   { buildAndDeploy('ruoyi-gateway', 'ruoyi-gateway', 'gateway') }
                    if (B_S > 0)   { buildAndDeploy('ruoyi-system', 'ruoyi-modules/ruoyi-system', 'modules/system') }
                    if (B_Gen > 0) { buildAndDeploy('ruoyi-gen', 'ruoyi-modules/ruoyi-gen', 'modules/gen') }
                    if (B_Job > 0) { buildAndDeploy('ruoyi-job', 'ruoyi-modules/ruoyi-job', 'modules/job') }
                    if (B_File> 0) { buildAndDeploy('ruoyi-file', 'ruoyi-modules/ruoyi-file', 'modules/file') }

                    // Helm 统一部署
                    if (B_UI > 0 || B_A > 0 || B_G > 0 || B_S > 0 || B_Gen > 0 || B_Job > 0 || B_File > 0) {
                        echo "  [Helm] 滚动更新所有服务（imageTag: ${UNIQUE_TAG}）..."
                        sh """
                            helm upgrade ruoyi docker/k8s/charts/ruoyi-cloud -n ${K8S_NAMESPACE} \
                                --set imageTag=${UNIQUE_TAG} \
                                --set image.nginxTag=${UNIQUE_TAG} \
                                --reuse-values
                        """
                        sh "kubectl rollout status deploy/ruoyi-gateway -n ${K8S_NAMESPACE} --timeout=120s"
                        sh "kubectl rollout status deploy/ruoyi-system -n ${K8S_NAMESPACE} --timeout=120s"
                        echo "  [Helm] ✅ 完成"
                    }
                }
            }
        }

        // ============================================================
        // 5. 健康检查
        // ============================================================
        stage('Health Check') {
            steps {
                echo '[5/5] 健康检查...'

                script {
                    def healthCheck = { url, name ->
                        try {
                            sleep 5
                            def code = sh(script: "curl -s --max-time 5 -o /dev/null -w '%{http_code}' ${url}", returnStdout: true).trim()
                            if (code == '200') {
                                echo "  [${name}] ✅ OK"
                            } else {
                                echo "  [${name}] ❌ FAIL (${code})"
                            }
                        } catch (Exception e) {
                            echo "  [${name}] ❌ FAIL (连接失败)"
                        }
                    }

                    if (B_UI > 0) { healthCheck('http://ruoyi-nginx:30080', 'nginx') }
                    if (B_A > 0)  { healthCheck('http://ruoyi-auth:8080/actuator/health', 'ruoyi-auth') }
                    if (B_G > 0)  { healthCheck('http://ruoyi-gateway:8080/actuator/health', 'ruoyi-gateway') }
                    if (B_S > 0)  { healthCheck('http://ruoyi-system:8080/actuator/health', 'ruoyi-system') }
                }
            }
        }
    }

    post {
        success {
            echo '============================================'
            echo "  Pipeline #${BUILD_NUMBER} 完成"
            echo "  唯一标签: ${UNIQUE_TAG}"
            echo '============================================'
        }
        failure {
            echo 'CI/CD 流水线执行失败！请检查日志。'
        }
    }
}
