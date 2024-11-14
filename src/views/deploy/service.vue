<template>
  <div class="dashboard-editor-container">
    <!-- 左上角的按钮 -->
    <el-button class="edit-yaml-button" type="primary" @click="openYamlEditorDialog">编辑 YAML</el-button>

    <!-- 表格展示 -->
    <el-table :data="tableData" border style="width: 100%" :fit="true">
      <el-table-column prop="cluster" label="Cluster" />
      <el-table-column prop="namespace" label="Namespace" />
      <el-table-column prop="name" label="Name" />
      <el-table-column prop="type" label="Type" />
      <el-table-column prop="clusterIp" label="Cluster IP" />
      <el-table-column prop="externalIp" label="External IP" />
      <el-table-column prop="ports" label="Ports" />
      <el-table-column prop="age" label="Age" />
    </el-table>

    <!-- YAML 编辑器弹框 -->
    <el-dialog
      title="编辑 YAML"
      :visible.sync="yamlEditorVisible"
      width="50%"
      @close="closeYamlEditorDialog"
    >
      <div class="editor-container">
        <textarea
          v-model="yamlContent"
          style="width: 100%; height: 400px;"
          placeholder="编辑 YAML 内容"
        />
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
      yamlContent: '' // YAML 编辑器中的内容
    }
  },
  mounted() {
    this.fetchTableData() // 页面加载时调用数据请求方法
  },
  methods: {
    fetchTableData() {
      fetch(API_URL + '/service/show', {
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
          this.tableData = combinedData.map(service => ({
            cluster: service.cluster,
            namespace: service.NAMESPACE,
            name: service.NAME,
            type: service.TYPE,
            clusterIp: service['CLUSTER-IP'],
            externalIp: service['EXTERNAL-IP'],
            ports: service['PORT(S)'],
            age: service.AGE
          }))

          // 转换为 YAML 格式
          // this.yamlContent = yaml.dump(combinedData)
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
      // 保存 YAML 数据，无需转换为 JSON
      console.log('保存的 YAML 数据:', this.yamlContent)
      this.closeYamlEditorDialog() // 关闭弹框
    }
  }
}
</script>

  <style lang="scss" scoped>
  .dashboard-editor-container {
    padding: 32px;
    background-color: rgb(240, 242, 245);
    position: relative;

    .edit-yaml-button {
      top: 16px;
      left: 16px;
    }

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

  .editor-container {
    position: relative;
    height: 100%;
  }

  @media (max-width:1024px) {
    .chart-wrapper {
      padding: 8px;
    }
  }
  </style>
