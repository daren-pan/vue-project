<template>
  <div class="app-container">
    <el-row :gutter="10" class="mb8">
      <el-col :span="1.5">
        <el-button type="primary" plain icon="el-icon-refresh" size="mini" @click="getList">刷新</el-button>
      </el-col>
    </el-row>

    <el-table v-loading="loading" :data="processList" border stripe>
      <el-table-column label="流程标识" align="center" prop="key" width="150" />
      <el-table-column label="流程名称" align="center" prop="name" min-width="200" />
      <el-table-column label="当前版本" align="center" width="80">
        <template slot-scope="s"><el-tag size="mini">v{{ s.row.version }}</el-tag></template>
      </el-table-column>
      <el-table-column label="操作" align="center" width="100">
        <template slot-scope="s">
          <el-button type="primary" size="mini" icon="el-icon-s-promotion"
            @click="handleApply(s.row)">发起</el-button>
        </template>
      </el-table-column>
    </el-table>

    <!-- 发起弹窗 -->
    <el-dialog :title="'发起 - ' + currentProcess.name" :visible.sync="applyOpen"
      width="500px" :close-on-click-modal="false" @closed="currentProcess={}">
      <component v-if="currentProcess.key" :is="componentName" ref="applyForm" />
      <div v-else-if="applyOpen && !currentProcess.key" style="text-align:center;color:#999;padding:40px;">
        该流程暂未配置申请表单
      </div>
      <div slot="footer" v-if="currentProcess.key">
        <el-button type="primary" @click="submitApply">提 交</el-button>
        <el-button @click="applyOpen = false">取 消</el-button>
      </div>
    </el-dialog>

    <div v-if="processList.length === 0 && !loading" style="text-align:center;color:#999;padding:40px;">
      暂无可发起的流程
    </div>
  </div>
</template>

<script>
import { listDefinitions, startProcess } from "@/api/workflow/flowable"
import formRegistry from "./formRegistry"

export default {
  name: "WorkflowApply",
  components: Object.fromEntries(
    Object.entries(formRegistry).map(([k, v]) => [v.name, v.component])
  ),
  data() {
    return {
      loading: false,
      processList: [],
      applyOpen: false,
      currentProcess: {}
    }
  },
  computed: {
    componentName() {
      const entry = formRegistry[this.currentProcess.key]
      return entry ? entry.name : null
    }
  },
  created() {
    this.getList()
  },
  methods: {
    getList() {
      this.loading = true
      listDefinitions().then(res => {
        const latest = new Map()
        ;(res.data || []).forEach(d => {
          if (!latest.has(d.key) || d.version > latest.get(d.key).version) {
            latest.set(d.key, d)
          }
        })
        this.processList = Array.from(latest.values())
        this.loading = false
      }).catch(() => { this.loading = false })
    },
    handleApply(row) {
      this.currentProcess = row
      this.applyOpen = true
    },
    submitApply() {
      const form = this.$refs.applyForm
      if (!form) return
      form.validate(valid => {
        if (!valid) return
        const data = form.getData()
        // 流程定义的抄送人
        if (this.currentProcess.ccUsers) {
          data.ccUsers = this.currentProcess.ccUsers
        }
        startProcess(this.currentProcess.key, data).then(res => {
          this.$message.success(res.data.tip || '流程已发起')
          this.applyOpen = false
        }).catch(() => {})
      })
    }
  }
}
</script>
