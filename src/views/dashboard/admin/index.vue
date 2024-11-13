<template>
  <div class="dashboard-editor-container">
    <el-image
      style="width: 100%; height: 100%"
      :src="require('@/assets/custom-theme/topology.png')"
      fit="fill"
    />
    <el-table :data="tableData" border style="width: 100%">
      <el-table-column prop="date" label="所属集群" width="180" />
      <el-table-column prop="name" label="Node 名称" width="180" />
      <el-table-column prop="address" label="Node IP" />
      <el-table-column prop="role" label="Role" />
      <el-table-column prop="status" label="状态">
        <template slot-scope="scope">
          <el-tag
            :type="scope.row.status === 'Ready' ? 'success' : 'danger'"
            disable-transitions
          >{{ scope.row.status }}</el-tag>
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
          // 转换数据为json
          const parseClusterData = (clusterString, clusterName) => {
            const rows = clusterString.trim().split('\n').slice(1)
            return rows.map(row => {
              const columns = row.trim().split(/\s{2,}/)
              return {
                cluster: clusterName,
                name: columns[0],
                status: columns[1],
                role: columns[2],
                age: columns[3],
                version: columns[4],
                ip: columns[5],
                externalIp: columns[6] || '<none>',
                osImage: columns[7],
                kernelVersion: columns[8],
                containerRuntime: columns[9]
              }
            })
          }
          const clusterAData = parseClusterData(data.cluster_a, 'Cluster A')
          const clusterBData = parseClusterData(data.cluster_b, 'Cluster B')
          const combinedData = [...clusterAData, ...clusterBData]
          console.log('looking handsome', combinedData)

          // 假设返回的数据包含节点列表，并转换成表格所需格式
          this.tableData = combinedData.map(node => ({
            date: node.cluster, // 假设 cluster 表示集群
            name: node.name, // 假设 name 表示节点名称
            address: node.ip, // 假设 ip 表示节点 IP
            role: node.role, // 假设 role 表示节点角色
            status: node.status // 假设 status 表示状态
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
