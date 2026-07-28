<template>
  <div class="app-container">
    <el-row :gutter="10" class="mb8">
      <el-col :span="1.5">
        <el-button type="primary" plain icon="el-icon-refresh" size="mini" @click="getList">刷新</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="success" plain icon="el-icon-s-operation" size="mini" @click="handleDesigner">流程配置</el-button>
      </el-col>
    </el-row>

    <el-table v-loading="loading" :data="definitionList" border stripe>
      <el-table-column label="流程标识" align="center" prop="key" width="150" />
      <el-table-column label="流程名称" align="center" prop="name" min-width="180" />
      <el-table-column label="版本号" align="center" prop="version" width="80">
        <template slot-scope="scope">
          <el-tag size="mini">v{{ scope.row.version }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="部署时间" align="center" width="170">
        <template slot-scope="scope">
          <span>{{ scope.row.deployTime ? parseTime(scope.row.deployTime) : '-' }}</span>
        </template>
      </el-table-column>
      <el-table-column label="添加人" align="center" prop="deployer" width="120" />
      <el-table-column label="操作" align="center" width="180" fixed="right">
        <template slot-scope="scope">
          <el-button type="text" size="mini" icon="el-icon-edit" @click="handleEdit(scope.row)">修改</el-button>
          <el-button type="text" size="mini" icon="el-icon-time" @click="handleHistory(scope.row)">历史</el-button>
          <el-button type="text" size="mini" icon="el-icon-delete" style="color:#F56C6C;" @click="handleDelete(scope.row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <!-- 历史版本弹窗 -->
    <el-dialog :title="historyTitle" :visible.sync="historyVisible" width="700px">
      <el-table :data="historyList" border stripe v-loading="historyLoading">
        <el-table-column label="版本号" align="center" width="80">
          <template slot-scope="s"><el-tag size="mini">v{{ s.row.version }}</el-tag></template>
        </el-table-column>
        <el-table-column label="部署时间" align="center" width="170">
          <template slot-scope="s"><span>{{ s.row.deployTime ? parseTime(s.row.deployTime) : '-' }}</span></template>
        </el-table-column>
        <el-table-column label="添加人" align="center" prop="deployer" width="120" />
        <el-table-column label="操作" align="center" width="80">
          <template slot-scope="s">
            <el-button type="text" size="mini" icon="el-icon-view" @click="handleEdit(s.row);historyVisible=false">查看</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-dialog>
    <div v-if="definitionList.length === 0 && !loading" style="text-align:center;color:#999;padding:40px;">
      暂无流程定义，请使用设计器新建
    </div>
  </div>
</template>

<script>
import { listDefinitions, deleteDefinition, getDefinitionConfig, getHistoryVersions } from "@/api/workflow/flowable"

export default {
  name: "FlowableDefinition",
  data() {
    return {
      loading: false,
      definitionList: [],
      historyVisible: false,
      historyTitle: '',
      historyLoading: false,
      historyList: []
    }
  },
  created() {
    this.getList()
  },
  methods: {
    getList() {
      this.loading = true
      listDefinitions().then(res => {
        this.definitionList = res.data || []
        this.loading = false
      }).catch(() => {
        this.loading = false
      })
    },
    handleDesigner() {
      this.$router.push({ path: '/workflow/config' })
    },
    handleDelete(row) {
      this.$confirm('确定删除「' + row.name + ' v' + row.version + '」？删除后不可恢复', '警告', { type: 'warning' }).then(() => {
        deleteDefinition(row.deploymentId).then(() => {
          this.$message.success('删除成功')
          this.getList()
        })
      }).catch(() => {})
    },
    handleHistory(row) {
      this.historyTitle = row.name + ' - 历史版本'
      this.historyVisible = true
      this.historyLoading = true
      getHistoryVersions(row.key).then(res => {
        this.historyList = res.data || []
        this.historyLoading = false
      }).catch(() => { this.historyLoading = false })
    },
    handleEdit(row) {
      getDefinitionConfig(row.deploymentId).then(res => {
        this.$router.push({
          path: '/workflow/config',
          query: { config: JSON.stringify(res.data) }
        })
      })
    }
  }
}
</script>
