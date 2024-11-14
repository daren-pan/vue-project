<template>
  <div v-loading="loading" class="dashboard-editor-container">
    <el-button class="edit-yaml-button" type="primary" @click="openYamlEditorDialog">编辑 YAML</el-button>
    <el-table :data="tableData" border style="width: 100%" height="800">
      <el-table-column prop="cluster" label="Cluster" />
      <el-table-column prop="namespace" label="Namespace" />
      <el-table-column prop="name" label="Name" />
      <el-table-column prop="status" label="Status" />
      <el-table-column prop="ip" label="IP" />
      <el-table-column prop="node" label="Node" />
      <el-table-column prop="ready" label="Ready" />
      <el-table-column prop="restarts" label="Restarts" />
      <el-table-column prop="age" label="Age" />
    </el-table>
    <!-- YAML 编辑器弹框 -->
    <el-dialog title="编辑 YAML" :visible.sync="yamlEditorVisible" width="50%" @close="closeYamlEditorDialog">
      <div class="editor-container">
        <textarea v-model="yamlContent" style="width: 100%; height: 400px;" placeholder="编辑 YAML 内容" />
      </div>
      <span slot="footer" class="dialog-footer">
        <el-button @click="closeYamlEditorDialog">取消</el-button>
        <el-button type="primary" @click="saveYamlChanges">部署</el-button>
      </span>
    </el-dialog>
  </div>
</template>

<script>
import { API_URL } from '@/config/APIconfig'
export default {
  name: 'DashboardAdmin',
  data() {
    return {
      tableData: [], // 初始为空数组，将在 mounted 时获取数据
      yamlEditorVisible: false, // 控制 YAML 编辑器弹框的显示
      yamlContent: `apiVersion: apps/v1
kind: Deployment
metadata:
  name: web
  namespace: default
  annotations:       # 记录回滚参数
    kubernetes.io/change-cause: "web.v1-nginx-1.19"   #记录到revision中的内容，记录版本号
spec:
  replicas: 3 # Pod副本预期数量
  revisionHistoryLimit: 10 # RS历史版本保存数量
  selector:
    matchLabels:
      app: web
  strategy:
    rollingUpdate:
      maxSurge: 25%             # 滚动更新过程最大pod副本数
      maxUnavailable: 25%       # 滚动更新过程中最大不可用pod副本数，
    type: RollingUpdate
  template:
    metadata:
      labels:
        app: web # Pod副本的标签
    spec:
      containers:
      - name: web
        image: nginx:1.16
        readinessProbe:          # 存活检查,如果失败，将杀死容器，来重启
          httpGet:
            port: 80
            path: /index.html
          initialDelaySeconds: 10 # 启动容器后多少秒健康检查
          periodSeconds: 10 # 以后间隔多少秒检查一次

        livenessProbe:   # 就绪检查，失败就会剔除 service 
          httpGet:
            port: 80
            path: /index.html`, // YAML 默认内容
      loading: false
    }
  },
  mounted() {
    this.fetchTableData() // 页面加载时调用数据请求方法
  },
  methods: {
    fetchTableData() {
      fetch(API_URL + '/pod/show', {
        method: 'GET',
        headers: {
          'Content-Type': 'application/json'
        }
      })
        .then(response => response.json())
        .then(data => {
          // 假设 data 是包含 cluster_a 和 cluster_b 的结构，结合两者数据
          const combinedData = [
            ...data.cluster_a.map(item => ({ ...item, cluster: 'Cluster A' })),
            ...data.cluster_b.map(item => ({ ...item, cluster: 'Cluster B' }))
          ]

          // 将 JSON 数据转换为表格所需格式
          this.tableData = combinedData.map(node => ({
            cluster: node.cluster,
            namespace: node.NAMESPACE,
            name: node.NAME,
            status: node.STATUS,
            ip: node.IP,
            node: node.NODE,
            ready: node.READY,
            restarts: node.RESTARTS,
            age: node.AGE
          }))
        })
        .catch(error => console.error('Error fetching table data:', error))
    },
    openYamlEditorDialog() {
      // 打开 YAML 编辑器弹框
      this.yamlEditorVisible = true
    },
    closeYamlEditorDialog() {
      // 关闭 YAML 编辑器弹框
      this.yamlEditorVisible = false
    },
    saveYamlChanges() {
      console.log('保存的 YAML 数据:', this.yamlContent)
      this.loading = true
      fetch(API_URL + '/pod/deploy', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json'
        },
        body: JSON.stringify({
          yaml: this.yamlContent // 将 YAML 内容作为请求体中的 "yaml" 字段
        })
      })
        .then(response => {
          console.log('meme')
          if (!response.ok) {
            throw new Error('请求失败，状态码: ' + response.status)
          }
          return response.json() // 将响应解析为 JSON
        })
        .then(data => {
          // 处理后端返回的数据
          console.log('后端响应数据:', data)
          if (!data.error) {
            console.log('部署输出:', data.stdout)
            this.$notify({
              title: '调度成功',
              message: '该应用已被调度到集群' + data.dst + '上\n' + data.stdout,
              type: 'success'
            })
          } else if (data.error) {
            console.error('部署错误:', data.error)
            this.$notify.error({
              title: '调度失败',
              message: '调度失败'
            })
          }

          this.fetchTableData()
          this.loading = false
        })
        .catch(error => {
          // 处理请求或解析中的任何错误
          console.error('请求出错:', error)
        })
        .finally(() => {
          this.closeYamlEditorDialog() // 关闭弹框
        })
    }

  }
}
</script>

<style lang="scss" scoped>
.dashboard-editor-container {
  padding: 32px;
  background-color: rgb(240, 242, 245);
  position: relative;

  .github-corner {
    position: absolute;
    top: 0px;
    border: 0;
    right: 0;
  }

  .chart-wrapper {
    background: #fff;
    padding: 16px 16px 0;
    margin-bottom: 32px;
  }
}

@media (max-width:1024px) {
  .chart-wrapper {
    padding: 8px;
  }
}
</style>
