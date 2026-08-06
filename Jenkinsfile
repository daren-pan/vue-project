pipeline {
    agent any

    tools {
        maven 'maven-3.9'
        nodejs 'node-18'
    }

    environment {
        ACR_REGISTRY = 'crpi-zu4tna9y8drenzc4.cn-hangzhou.personal.cr.aliyuncs.com'
        ACR_NAMESPACE = 'ruoyi-personal'
        REG = "${ACR_REGISTRY}/${ACR_NAMESPACE}"
        IMAGE_TAG = '3.6.7'
        // ACR 登录凭证（从 Jenkins Credentials 安全注入，日志不会泄露）
        ACR_CREDENTIALS = credentials('acr-credentials')
    }

    stages {

        // ============================================================
        // 步骤 1：拉代码 + 增量检测
        // ============================================================
        stage('■ 1/4 拉代码 & 检测变更') {
            steps {
                echo '━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━'
                echo '  📥 从 Codeup 拉取代码...'
                echo '━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━'
                git url: 'https://codeup.aliyun.com/6a31f41b5a46a9699cb73592/daren-pan/RuoYi-Cloud.git',
                    branch: 'springboot3',
                    credentialsId: 'codeup-creds'

                script {
                    def GIT_HASH = sh(script: 'git rev-parse --short HEAD', returnStdout: true).trim()
                    env.UNIQUE_TAG = "${IMAGE_TAG}-b${BUILD_NUMBER}-${GIT_HASH}"
                    echo "🏷️  镜像标签: ${env.UNIQUE_TAG}"

                    echo '🔍 检测哪些模块有变更...'
                    def DIFF = 'ALL'
                    try {
                        def oldCommit = sh(script: 'git rev-parse HEAD~1', returnStdout: true).trim()
                        DIFF = sh(script: "git diff --name-only ${oldCommit} HEAD", returnStdout: true).trim()
                    } catch (Exception e) { }
                    if (DIFF == '') { DIFF = 'ALL' }

                    env.CHANGED_UI   = (DIFF == 'ALL' || DIFF.contains('ruoyi-ui/')) ? 'true' : 'false'
                    def CHANGED_COM  = (DIFF == 'ALL' || DIFF.contains('ruoyi-common/') || DIFF.contains('ruoyi-api/'))
                    env.CHANGED_AUTH = (CHANGED_COM || DIFF.contains('ruoyi-auth/')) ? 'true' : 'false'
                    env.CHANGED_GATE = (CHANGED_COM || DIFF.contains('ruoyi-gateway/')) ? 'true' : 'false'
                    env.CHANGED_SYS  = (CHANGED_COM || DIFF.contains('ruoyi-modules/ruoyi-system/')) ? 'true' : 'false'
                    env.CHANGED_GEN  = (CHANGED_COM || DIFF.contains('ruoyi-modules/ruoyi-gen/')) ? 'true' : 'false'
                    env.CHANGED_JOB  = (CHANGED_COM || DIFF.contains('ruoyi-modules/ruoyi-job/')) ? 'true' : 'false'
                    env.CHANGED_FILE = (CHANGED_COM || DIFF.contains('ruoyi-modules/ruoyi-file/')) ? 'true' : 'false'

                    echo "  UI=${env.CHANGED_UI}  Auth=${env.CHANGED_AUTH}  Gateway=${env.CHANGED_GATE}  System=${env.CHANGED_SYS}"
                    echo "  Gen=${env.CHANGED_GEN}  Job=${env.CHANGED_JOB}  File=${env.CHANGED_FILE}"
                }
            }
        }

        // ============================================================
        // 步骤 2：Maven 编译后端
        // ============================================================
        stage('■ 2/5 Maven 后端') {
            when { expression { env.CHANGED_AUTH == 'true' || env.CHANGED_GATE == 'true' || env.CHANGED_SYS == 'true' || env.CHANGED_GEN == 'true' || env.CHANGED_JOB == 'true' || env.CHANGED_FILE == 'true' } }
            steps {
                echo '━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━'
                echo '  🔧 Maven 编译后端模块...'
                echo '━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━'
                script {
                    def modules = []
                    if (env.CHANGED_AUTH == 'true') modules.add('ruoyi-auth')
                    if (env.CHANGED_GATE == 'true') modules.add('ruoyi-gateway')
                    if (env.CHANGED_SYS  == 'true') modules.add('ruoyi-modules/ruoyi-system')
                    if (env.CHANGED_GEN  == 'true') modules.add('ruoyi-modules/ruoyi-gen')
                    if (env.CHANGED_JOB  == 'true') modules.add('ruoyi-modules/ruoyi-job')
                    if (env.CHANGED_FILE == 'true') modules.add('ruoyi-modules/ruoyi-file')
                    echo "  编译模块: ${modules.join(', ')}"
                    sh "mvn clean package -DskipTests -pl ${modules.join(',')} -am"
                }
                echo '  ✅ Maven 编译完成'
            }
        }

        // ============================================================
        // 步骤 3：npm 构建前端
        // ============================================================
        stage('■ 3/5 npm 前端') {
            when { expression { env.CHANGED_UI == 'true' } }
            steps {
                echo '━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━'
                echo '  🎨 npm 构建前端...'
                echo '━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━'
                dir('ruoyi-ui') {
                    sh 'npm install --registry=https://registry.npmmirror.com'
                    sh 'npm run build:prod'
                }
                echo '  ✅ npm 构建完成'
            }
        }

        // ============================================================
        // 步骤 3：Docker 构建 & 推送 ACR
        // ============================================================
        stage('■ 4/5 Docker 构建 & 推送') {
            steps {
                echo '━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━'
                echo '  🔐 登录 ACR 镜像仓库...'
                echo '━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━'
                sh 'echo "$ACR_CREDENTIALS_PSW" | docker login --username "$ACR_CREDENTIALS_USR" --password-stdin $ACR_REGISTRY'

                script {
                    def buildAndPush = { name, jarDir, ctx ->
                        echo ''
                        echo "━━━ 🐳 构建 ruoyi-${name} ━━━"
                        sh "cp ${jarDir}/target/*.jar docker/ruoyi/${ctx}/jar/"
                        echo "  📦 打包镜像..."
                        sh "docker build -t ${REG}/ruoyi-${name}:${env.UNIQUE_TAG} -t ${REG}/ruoyi-${name}:latest -f docker/ruoyi/${ctx}/dockerfile docker/ruoyi/${ctx}"
                        echo "  📤 推送 ACR..."
                        sh "docker push ${REG}/ruoyi-${name}:${env.UNIQUE_TAG}"
                        sh "docker push ${REG}/ruoyi-${name}:latest"
                        echo "  ✅ ruoyi-${name}:${env.UNIQUE_TAG}"
                    }

                    if (env.CHANGED_UI == 'true') {
                        echo ''
                        echo '━━━ 🐳 构建 ruoyi-nginx ━━━'
                        sh 'mkdir -p docker/nginx/html && rm -rf docker/nginx/html/dist && cp -r ruoyi-ui/dist docker/nginx/html/dist'
                        echo '  📦 打包镜像...'
                        sh "docker build --no-cache -t ${REG}/ruoyi-nginx:${env.UNIQUE_TAG} -t ${REG}/ruoyi-nginx:latest -f docker/nginx/dockerfile docker/nginx"
                        echo '  📤 推送 ACR...'
                        sh "docker push ${REG}/ruoyi-nginx:${env.UNIQUE_TAG}"
                        sh "docker push ${REG}/ruoyi-nginx:latest"
                        echo "  ✅ ruoyi-nginx:${env.UNIQUE_TAG}"
                    }
                    if (env.CHANGED_AUTH == 'true') buildAndPush('auth', 'ruoyi-auth', 'auth')
                    if (env.CHANGED_GATE == 'true') buildAndPush('gateway', 'ruoyi-gateway', 'gateway')
                    if (env.CHANGED_SYS  == 'true') buildAndPush('system', 'ruoyi-modules/ruoyi-system', 'modules/system')
                    if (env.CHANGED_GEN  == 'true') buildAndPush('gen', 'ruoyi-modules/ruoyi-gen', 'modules/gen')
                    if (env.CHANGED_JOB  == 'true') buildAndPush('job', 'ruoyi-modules/ruoyi-job', 'modules/job')
                    if (env.CHANGED_FILE == 'true') buildAndPush('file', 'ruoyi-modules/ruoyi-file', 'modules/file')
                }
            }
        }

        // ============================================================
        // 步骤 4：kubectl 部署到 K8s
        // ============================================================
        stage('■ 5/5 部署到 K8s') {
            steps {
                echo '━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━'
                echo '  🚀 更新镜像并部署...'
                echo '━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━'

                script {
                    def setImage = { deployName, containerName ->
                        echo "  📤 ${deployName} → ${env.UNIQUE_TAG}"
                        sh "kubectl set image deploy/${deployName} -n ruoyi-dev ${containerName}=${REG}/${deployName}:${env.UNIQUE_TAG}"
                        sh "kubectl rollout status deploy/${deployName} -n ruoyi-dev --timeout=120s"
                    }

                    if (env.CHANGED_UI   == 'true') setImage('ruoyi-nginx', 'nginx')
                    if (env.CHANGED_AUTH == 'true') setImage('ruoyi-auth', 'auth')
                    if (env.CHANGED_GATE == 'true') setImage('ruoyi-gateway', 'gateway')
                    if (env.CHANGED_SYS  == 'true') setImage('ruoyi-system', 'system')
                    if (env.CHANGED_GEN  == 'true') setImage('ruoyi-gen', 'gen')
                    if (env.CHANGED_JOB  == 'true') setImage('ruoyi-job', 'job')
                    if (env.CHANGED_FILE == 'true') setImage('ruoyi-file', 'file')
                }
                echo '  ✅ 全部部署完成'
            }
        }
    }

    post {
        success {
            echo ''
            echo '╔══════════════════════════════════════════╗'
            echo "║  ✅ Pipeline #${BUILD_NUMBER} 成功！              ║"
            echo "║  标签: ${env.UNIQUE_TAG}    ║"
            echo '╚══════════════════════════════════════════╝'
        }
        failure {
            echo ''
            echo '╔══════════════════════════════════════════╗'
            echo "║  ❌ Pipeline #${BUILD_NUMBER} 失败！              ║"
            echo '╚══════════════════════════════════════════╝'
        }
    }
}
