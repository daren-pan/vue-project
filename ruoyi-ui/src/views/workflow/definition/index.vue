<template>
  <div class="app-container">
    <el-alert
      title="使用 BPMN 可视化设计器新建或修改流程，无需手动编辑 XML 文件"
      type="info"
      :closable="false"
      show-icon
      style="margin-bottom: 16px;"
    />

    <el-row :gutter="10" class="mb8">
      <el-col :span="1.5">
        <el-button type="primary" plain icon="el-icon-refresh" size="mini" @click="getList">刷新</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="success" plain icon="el-icon-s-operation" size="mini" @click="handleDesigner">流程配置</el-button>
      </el-col>
      <right-toolbar :showSearch.sync="showSearch" @queryTable="getList"></right-toolbar>
    </el-row>

    <el-table v-loading="loading" :data="definitionList" border stripe>
      <el-table-column label="流程ID" align="center" prop="id" min-width="280" show-overflow-tooltip />
      <el-table-column label="流程标识" align="center" prop="key" width="150" />
      <el-table-column label="流程名称" align="center" prop="name" min-width="180" />
      <el-table-column label="版本号" align="center" prop="version" width="80">
        <template slot-scope="scope">
          <el-tag size="mini">v{{ scope.row.version }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="部署ID" align="center" prop="deploymentId" min-width="280" show-overflow-tooltip />
    </el-table>
    <div v-if="definitionList.length === 0 && !loading" style="text-align:center;color:#999;padding:40px;">
      暂无流程定义，请使用设计器新建
    </div>
  </div>
</template>

<script>
import { listDefinitions } from "@/api/workflow/flowable"

export default {
  name: "FlowableDefinition",
  data() {
    return {
      loading: false,
      showSearch: false,
      definitionList: []
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
    }
  }
}
</script>
