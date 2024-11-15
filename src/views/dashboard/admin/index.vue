<template>
  <div class="dashboard-editor-container">
    <el-image style="width: 100%; height: 100%" :src="require('@/assets/custom-theme/topology.png')" fit="fill" />
    <el-table :data="tableData" border style="width: 100%">
      <el-table-column prop="cluster" label="所属集群" width="150" />
      <el-table-column prop="name" label="Node 名称" width="150" />
      <el-table-column prop="address" label="Node IP" />
      <el-table-column prop="role" label="Role" />
      <el-table-column prop="uuid" label="算力统一标识" />
      <el-table-column prop="status" label="状态" width="100">
        <template slot-scope="scope">
          <el-tag :type="scope.row.status === 'Ready' ? 'success' : 'danger'" disable-transitions>{{ scope.row.status
          }}</el-tag>
        </template>
      </el-table-column>
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
      fetch(API_URL + '/dashboard/nodes', {
        method: 'Get',
        headers: {
          'Content-Type': 'application/json'
        }
      })
        .then(response => response.json())
        .then(data => {
          // 转换数据为json并处理
          console.log('looking handsome', data)

          // 创建一个空数组来存储表格数据
          const tableData = []

          // 遍历 cluster_a 和 cluster_b 集群的数据
          Object.keys(data).forEach(cluster => {
            data[cluster].forEach(node => {
              console.log(cluster)
              // 将每个节点信息转换为表格所需格式
              tableData.push({
                cluster: cluster, // 当前集群名
                name: node.NAME, // 节点名称
                address: node.ADDRESS, // 节点 IP 地址
                role: node.ROLES, // 节点角色
                status: node.STATUS, // 节点状态
                uuid: node.UUID.replace('uuid=', '') // 处理 UUID 去掉 "uuid=" 字符串
              })
            })
          })

          // 将转换后的数据赋值给表格的数据
          this.tableData = tableData
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
