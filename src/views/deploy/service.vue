<template>
  <div class="dashboard-editor-container">
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
  </div>
</template>

<script>
import { API_URL } from '@/config/APIconfig'
export default {
  name: 'DashboardAdmin',
  data() {
    return {
      tableData: [] // 初始为空数组，将在 mounted 时获取数据
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
        })
        .catch(error => console.error('Error fetching table data:', error))
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
