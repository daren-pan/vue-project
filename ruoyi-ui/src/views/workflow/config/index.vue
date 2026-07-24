<template>
  <div class="app-container">
    <!-- 顶部信息 + 操作 -->
    <el-row :gutter="10" class="mb8">
      <el-col :span="12">
        <h3 style="margin:0;">表格化流程配置</h3>
      </el-col>
      <el-col :span="12" style="text-align:right;">
        <el-button type="primary" size="mini" icon="el-icon-upload2" @click="handleDeploy" :loading="deploying">部署流程</el-button>
        <el-button size="mini" icon="el-icon-back" @click="handleBack">返回</el-button>
      </el-col>
    </el-row>

    <!-- 基本信息 -->
    <el-card shadow="never" style="margin-bottom:16px;">
      <el-form :inline="true" size="mini">
        <el-form-item label="流程标识" required><el-input v-model="form.processKey" placeholder="如 leave" /></el-form-item>
        <el-form-item label="流程名称" required><el-input v-model="form.processName" placeholder="如 请假审批" /></el-form-item>
      </el-form>
    </el-card>

    <!-- 节点列表 -->
    <el-card shadow="never" style="margin-bottom:16px;">
      <div slot="header">
        <span>节点列表</span>
        <el-button size="mini" type="primary" style="float:right;" icon="el-icon-plus" @click="handleAddNode">新增节点</el-button>
      </div>
      <el-table :data="form.nodes" border size="small">
        <el-table-column label="节点ID" prop="id" width="150">
          <template slot-scope="s">
            <el-input v-model="s.row.id" size="mini" placeholder="如 start" />
          </template>
        </el-table-column>
        <el-table-column label="名称" prop="name" width="150">
          <template slot-scope="s">
            <el-input v-model="s.row.name" size="mini" placeholder="如 开始" />
          </template>
        </el-table-column>
        <el-table-column label="类型" prop="type" width="160">
          <template slot-scope="s">
            <el-select v-model="s.row.type" size="mini">
              <el-option label="开始事件" value="startEvent" />
              <el-option label="结束事件" value="endEvent" />
              <el-option label="用户任务(审批)" value="userTask" />
              <el-option label="排他网关(分支)" value="exclusiveGateway" />
            </el-select>
          </template>
        </el-table-column>
        <el-table-column label="审批人" prop="assignee" min-width="180">
          <template slot-scope="s">
            <el-input v-if="s.row.type === 'userTask'" v-model="s.row.assignee" size="mini" placeholder="如 ${manager} 或用户名" />
            <span v-else style="color:#999;">-</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="80">
          <template slot-scope="s">
            <el-button type="danger" size="mini" icon="el-icon-delete" @click="form.nodes.splice(s.$index,1)" />
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 连线列表 -->
    <el-card shadow="never">
      <div slot="header">
        <span>连线列表</span>
        <el-button size="mini" type="primary" style="float:right;" icon="el-icon-plus" @click="handleAddLine">新增连线</el-button>
      </div>
      <el-table :data="form.lines" border size="small">
        <el-table-column label="来源节点" prop="from" width="180">
          <template slot-scope="s">
            <el-select v-model="s.row.from" size="mini" filterable>
              <el-option v-for="n in form.nodes" :key="n.id" :label="n.name||n.id" :value="n.id" />
            </el-select>
          </template>
        </el-table-column>
        <el-table-column label="目标节点" prop="to" width="180">
          <template slot-scope="s">
            <el-select v-model="s.row.to" size="mini" filterable>
              <el-option v-for="n in form.nodes" :key="n.id" :label="n.name||n.id" :value="n.id" />
            </el-select>
          </template>
        </el-table-column>
        <el-table-column label="条件表达式" prop="condition" min-width="200">
          <template slot-scope="s">
            <el-input v-model="s.row.condition" size="mini" placeholder="如 days > 3，网关节点的分支需填写" />
          </template>
        </el-table-column>
        <el-table-column label="操作" width="80">
          <template slot-scope="s">
            <el-button type="danger" size="mini" icon="el-icon-delete" @click="form.lines.splice(s.$index,1)" />
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

<script>
import { deployTable } from "@/api/workflow/flowable"

export default {
  name: "FlowableTableConfig",
  data() {
    return {
      deploying: false,
      form: {
        processKey: 'leave',
        processName: '请假审批',
        nodes: [
          { id: 'start', name: '开始', type: 'startEvent' },
          { id: 'managerApprove', name: '部门经理审批', type: 'userTask', assignee: '${manager}' },
          { id: 'gateway1', name: '判断天数', type: 'exclusiveGateway' },
          { id: 'directorApprove', name: '总监审批', type: 'userTask', assignee: '${director}' },
          { id: 'end', name: '结束', type: 'endEvent' }
        ],
        lines: [
          { from: 'start', to: 'managerApprove' },
          { from: 'managerApprove', to: 'gateway1' },
          { from: 'gateway1', to: 'end', condition: 'days <= 3' },
          { from: 'gateway1', to: 'directorApprove', condition: 'days > 3' },
          { from: 'directorApprove', to: 'end' }
        ]
      }
    }
  },
  methods: {
    handleBack() {
      this.$router.push({ path: '/workflow/definition' })
    },
    handleAddNode() {
      this.form.nodes.push({ id: '', name: '', type: 'userTask', assignee: '' })
    },
    handleAddLine() {
      this.form.lines.push({ from: '', to: '', condition: '' })
    },
    handleDeploy() {
      if (!this.form.processKey || !this.form.processName) {
        this.$message.warning('请填写流程标识和名称')
        return
      }
      this.$confirm('确认部署「' + this.form.processName + '」？', '提示', { type: 'info' }).then(() => {
        this.deploying = true
        deployTable(this.form).then(() => {
          this.$message.success('部署成功')
        }).finally(() => {
          this.deploying = false
        })
      }).catch(() => {})
    }
  }
}
</script>

