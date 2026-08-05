<template>
  <div class="app-container">
    <!-- 搜索表单 -->
    <el-form :model="queryParams" ref="queryForm" size="small" :inline="true" v-show="showSearch" label-width="80px">
      <el-form-item label="审批人" prop="assignee">
        <el-input
          v-model="queryParams.assignee"
          placeholder="输入用户名查他人，留空查自己"
          clearable
          @keyup.enter.native="handleQuery"
        />
      </el-form-item>
      <el-form-item>
        <el-button type="primary" icon="el-icon-search" size="mini" @click="handleQuery">搜索</el-button>
        <el-button icon="el-icon-refresh" size="mini" @click="resetQuery">重置</el-button>
      </el-form-item>
    </el-form>

    <!-- 操作按钮栏 -->
    <el-row :gutter="10" class="mb8">
      <el-col :span="1.5">
        <el-button type="primary" plain icon="el-icon-refresh" size="mini" @click="handleQuery">刷新</el-button>
      </el-col>
      <el-col :span="6">
        <span style="line-height:28px;font-size:12px;color:#666;">
          颜色说明：<el-tag size="mini" type="warning" effect="plain">待审批</el-tag>
          <el-tag size="mini" type="info" effect="plain" style="margin-left:4px;">抄送</el-tag>
        </span>
      </el-col>
      <right-toolbar :showSearch.sync="showSearch" @queryTable="handleQuery"></right-toolbar>
    </el-row>

    <!-- Tab 切换：待办 / 已办 -->
    <el-tabs v-model="activeTab" @tab-click="handleTabClick">
      <el-tab-pane label="待办任务" name="todo">
        <el-table v-loading="loading" :data="todoList" border stripe>
          <el-table-column type="index" label="序号" width="50" align="center" />
          <el-table-column label="流程名称" align="center" prop="processName" min-width="140" />
          <el-table-column label="任务名称" align="center" width="160">
            <template slot-scope="scope">
              <template v-if="isCcTask(scope.row.taskName)">
                <el-tag type="info" effect="plain" size="small">{{ scope.row.taskName }}</el-tag>
              </template>
              <template v-else>
                <el-tag type="warning" effect="plain" size="small">{{ scope.row.taskName }}</el-tag>
              </template>
            </template>
          </el-table-column>
          <el-table-column label="流程实例ID" align="center" prop="processInstanceId" min-width="280" show-overflow-tooltip />
          <el-table-column label="创建时间" align="center" width="170">
            <template slot-scope="scope">
              {{ parseTime(scope.row.createTime) }}
            </template>
          </el-table-column>
          <el-table-column label="操作" align="center" width="300">
            <template slot-scope="scope">
              <el-button v-if="!isCcTask(scope.row.taskName)" size="mini" type="primary" icon="el-icon-check" @click="handleApprove(scope.row)">审批</el-button>
              <el-button v-else size="mini" type="success" icon="el-icon-check" @click="handleDismiss(scope.row)">已阅</el-button>
              <el-button size="mini" type="text" icon="el-icon-view" @click="handleViewDetail(scope.row)">详情</el-button>
              <el-button size="mini" type="text" icon="el-icon-tickets" @click="handleTrack(scope.row.processInstanceId)">轨迹</el-button>
              <el-button size="mini" type="text" icon="el-icon-delete" style="color:#F56C6C;" @click="handleDeleteInstance(scope.row)">删除实例</el-button>
            </template>
          </el-table-column>
        </el-table>
        <div v-if="todoList.length === 0 && !loading" style="text-align:center;color:#999;padding:40px;">
          暂无待办任务
        </div>
      </el-tab-pane>

      <el-tab-pane label="已办历史" name="history">
        <el-table v-loading="loading" :data="historyList" border stripe>
          <el-table-column label="流程名称" align="center" prop="processName" min-width="140" />
          <el-table-column label="任务名称" align="center" prop="taskName" width="150" />
          <el-table-column label="状态" align="center" width="80">
            <template slot-scope="scope">
              <el-tag :type="scope.row.status === '已退回' ? 'danger' : 'success'" size="mini">{{ scope.row.status }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="流程实例ID" align="center" prop="processInstanceId" min-width="280" show-overflow-tooltip />
          <el-table-column label="开始时间" align="center" width="170">
            <template slot-scope="scope">
              {{ parseTime(scope.row.startTime) }}
            </template>
          </el-table-column>
          <el-table-column label="结束时间" align="center" width="170">
            <template slot-scope="scope">
              {{ parseTime(scope.row.endTime) }}
            </template>
          </el-table-column>
          <el-table-column label="耗时" align="center" width="100">
            <template slot-scope="scope">
              {{ formatDuration(scope.row.duration) }}
            </template>
          </el-table-column>
          <el-table-column label="操作" align="center" width="100">
            <template slot-scope="scope">
              <el-button size="mini" type="text" icon="el-icon-tickets" @click="handleTrack(scope.row.processInstanceId)">轨迹</el-button>
            </template>
          </el-table-column>
        </el-table>
        <div v-if="historyList.length === 0 && !loading" style="text-align:center;color:#999;padding:40px;">
          暂无已办记录
        </div>
      </el-tab-pane>

      <el-tab-pane label="我发起的" name="mine">
        <el-table v-loading="loading" :data="mineList" border stripe>
          <el-table-column label="流程名称" align="center" prop="processName" min-width="140" />
          <el-table-column label="申请人" align="center" width="100">
            <template slot-scope="scope">
              {{ scope.row.variables.applicant || '-' }}
            </template>
          </el-table-column>
          <el-table-column label="流程实例ID" align="center" prop="processInstanceId" min-width="280" show-overflow-tooltip />
          <el-table-column label="发起时间" align="center" width="170">
            <template slot-scope="scope">
              {{ parseTime(scope.row.startTime) }}
            </template>
          </el-table-column>
          <el-table-column label="操作" align="center" width="240">
            <template slot-scope="scope">
              <el-button size="mini" type="warning" icon="el-icon-back"
                @click="handleWithdraw(scope.row)">撤回</el-button>
              <el-button size="mini" type="text" icon="el-icon-tickets"
                @click="handleTrack(scope.row.processInstanceId)">轨迹</el-button>
              <el-button size="mini" type="text" icon="el-icon-delete" style="color:#F56C6C;"
                @click="handleDeleteInstance(scope.row)">删除实例</el-button>
            </template>
          </el-table-column>
        </el-table>
        <div v-if="mineList.length === 0 && !loading" style="text-align:center;color:#999;padding:40px;">
          暂无我发起的流程
        </div>
      </el-tab-pane>
    </el-tabs>

    <!-- 审批对话框 -->
    <el-dialog title="审批任务" :visible.sync="approveOpen" width="450px" append-to-body>
      <el-form ref="approveForm" :model="approveForm" label-width="80px">
        <el-form-item label="任务名称">
          <el-tag type="primary" size="medium">{{ currentTask.taskName }}</el-tag>
        </el-form-item>
        <el-form-item label="操作">
          <el-radio-group v-model="approveForm.action">
            <el-radio label="approve" style="color: #67C23A;">同意</el-radio>
            <el-radio label="reject" style="color: #F56C6C;">直接驳回</el-radio>
            <el-radio label="rollback" style="color: #E6A23C;">驳回上一步</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="意见">
          <el-input
            v-model="approveForm.comment"
            type="textarea"
            :rows="3"
            :placeholder="commentPlaceholder"
          />
        </el-form-item>
        <el-form-item label="加签">
          <el-input v-model="approveForm.signUser" placeholder="输入加签人用户名（可选）" style="width:200px;" />
          <el-button size="mini" @click="handleAddSign" :loading="signLoading" style="margin-left:8px;">确认加签</el-button>
        </el-form-item>
      </el-form>
      <div slot="footer" class="dialog-footer">
        <el-button type="primary" @click="submitApprove">确 定</el-button>
        <el-button @click="approveOpen = false">取 消</el-button>
      </div>
    </el-dialog>

    <!-- 审批轨迹对话框 -->
    <el-dialog title="审批轨迹" :visible.sync="trackOpen" width="650px" append-to-body>
      <div v-if="stagedList.length > 0">
        <div v-for="(stage, si) in stagedList" :key="si" style="margin-bottom:12px;">
          <div style="font-weight:bold;color:#409EFF;margin-bottom:6px;font-size:13px;">
            【{{ stage.label }}】
          </div>
          <el-timeline>
            <el-timeline-item
              v-for="(item, ii) in stage.items"
              :key="ii"
              :timestamp="item.endTime ? '完成 ' + parseTime(item.endTime) : '审批中...'"
              :color="item.status === 'completed' ? '#67C23A' : '#E6A23C'"
            >
              <div>
                <strong>{{ item.assignee }}</strong>
                <el-tag :type="item.status === 'completed' ? 'success' : 'warning'" size="mini" style="margin-left: 8px;">
                  {{ item.node }}
                </el-tag>
                <el-tag v-if="item.action" size="mini" effect="plain"
                  :type="item.action === '驳回' || item.action === '驳回到上一步' ? 'danger' : ''"
                  style="margin-left:4px;">{{ item.action }}</el-tag>
                <el-tag v-if="isMainApprover(item, stage)" size="mini" effect="dark" style="margin-left:4px;">主审</el-tag>
                <span v-if="item.status === 'pending'" style="color:#E6A23C;margin-left:8px;font-size:12px;">⏳ 审批中</span>
              </div>
              <div v-if="item.comment" style="margin-top: 2px; color: #666; font-size: 13px;">
                审批意见：{{ item.comment }}
              </div>
            </el-timeline-item>
          </el-timeline>
        </div>
      </div>
      <div v-if="trackList.length === 0" style="text-align:center;color:#999;padding:40px;">
        暂无审批记录
      </div>
    </el-dialog>

    <!-- 查看详情对话框 -->
    <el-dialog title="流程详情" :visible.sync="detailOpen" width="500px" append-to-body>
      <component v-if="detailFormName" :is="detailFormName" ref="detailForm"
        :form-data="detailFormData" :readonly="true" />
      <div v-else style="text-align:center;color:#999;padding:20px;">无法加载表单</div>
    </el-dialog>
  </div>
</template>

<script>
import { listTodoTasks, listHistoryTasks, approveTask, rejectTask, rollbackTask, addSign, dismissTask, getProcessTrack, listRunningProcesses, withdrawProcess, deleteProcessInstance } from "@/api/workflow/flowable"
import formRegistry from "@/views/workflow/apply/formRegistry"

export default {
  name: "FlowableTask",
  components: Object.fromEntries(
    Object.entries(formRegistry).map(([k, v]) => [v.name, v.component])
  ),
  data() {
    return {
      loading: false,
      showSearch: true,
      activeTab: 'todo',
      queryParams: { assignee: undefined },
      todoList: [],
      historyList: [],
      mineList: [],
      approveOpen: false,
      approveForm: {
        action: 'approve',
        comment: '',
        signUser: ''
      },
      signLoading: false,
      currentTask: { variables: {} },
      trackOpen: false,
      trackList: [],
      detailOpen: false,
      detailFormName: null,
      detailFormData: {}
    }
  },
  created() {
    this.loadTodo()
  },
  computed: {
    commentPlaceholder() {
      const map = { approve: '审批意见（可选）', reject: '驳回原因', rollback: '退回原因' }
      return map[this.approveForm.action] || ''
    },
    stagedList() {
      // 将轨迹按阶段分组（去掉(加签)后缀归为同一阶段），按最早完成时间排序
      const groups = []
      let currentLabel = null
      let currentItems = []
      this.trackList.forEach(item => {
        const baseNode = (item.node || '').replace('(加签)', '').trim()
        if (baseNode !== currentLabel) {
          if (currentItems.length > 0) groups.push({ label: currentLabel, items: currentItems })
          currentLabel = baseNode
          currentItems = []
        }
        currentItems.push(item)
      })
      if (currentItems.length > 0) groups.push({ label: currentLabel, items: currentItems })
      // 按每组最早完成时间排序（未完成的排最后）
      groups.sort((a, b) => {
        const aTime = a.items.reduce((min, it) => it.endTime && (!min || it.endTime < min) ? it.endTime : min, null)
        const bTime = b.items.reduce((min, it) => it.endTime && (!min || it.endTime < min) ? it.endTime : min, null)
        if (!aTime && !bTime) return 0
        if (!aTime) return 1
        if (!bTime) return -1
        return new Date(aTime) - new Date(bTime)
      })
      return groups
    }
  },
  methods: {
    handleTabClick(tab) {
      if (tab.name === 'todo') {
        this.loadTodo()
      } else if (tab.name === 'history') {
        this.loadHistory()
      } else {
        this.loadMine()
      }
    },
    handleQuery() {
      if (this.activeTab === 'todo') {
        this.loadTodo()
      } else if (this.activeTab === 'history') {
        this.loadHistory()
      } else {
        this.loadMine()
      }
    },
    resetQuery() {
      this.queryParams.assignee = undefined
      this.handleQuery()
    },
    loadTodo() {
      this.loading = true
      listTodoTasks(this.queryParams.assignee).then(res => {
        this.todoList = res.data || []
        this.loading = false
      }).catch(() => {
        this.loading = false
      })
    },
    loadHistory() {
      this.loading = true
      listHistoryTasks(this.queryParams.assignee).then(res => {
        this.historyList = res.data || []
        this.loading = false
      }).catch(() => {
        this.loading = false
      })
    },
    loadMine() {
      this.loading = true
      listRunningProcesses().then(res => {
        const all = res.data || []
        const target = this.queryParams.assignee || this.$store.state.user.name
        this.mineList = all.filter(p => {
          const vars = p.variables || {}
          return vars.applicant === target
        }).map(p => {
          const vars = p.variables || {}
          const key = vars.processKey || p.processDefinitionKey || ''
          return { ...p, processName: key, variables: vars }
        })
        this.loading = false
      }).catch(() => { this.loading = false })
    },
    handleWithdraw(row) {
      this.$confirm('确认撤回该流程？', '提示', { type: 'warning' }).then(() => {
        withdrawProcess(row.processInstanceId).then(res => {
          this.$message.success(res.data.tip || '已撤回')
          this.loadMine()
        }).catch(() => {})
      }).catch(() => {})
    },
    handleApprove(row) {
      this.currentTask = row
      this.approveForm = { action: 'approve', comment: '', signUser: '' }
      this.approveOpen = true
    },
    handleDismiss(row) {
      dismissTask(row.taskId).then(() => {
        this.$message.success('已阅')
        this.loadTodo()
      }).catch(() => {})
    },
    handleDeleteInstance(row) {
      const piId = row.processInstanceId
      if (!piId) {
        this.$message.warning('该任务无关联流程实例')
        return
      }
      this.$confirm('确认强制删除该流程实例？此操作不可恢复！', '警告', {
        confirmButtonText: '确认删除',
        cancelButtonText: '取消',
        type: 'warning'
      }).then(() => {
        deleteProcessInstance(piId).then(res => {
          this.$message.success('流程实例已删除')
          if (this.activeTab === 'mine') this.loadMine()
          else this.loadTodo()
        }).catch(() => {})
      }).catch(() => {})
    },
    handleViewDetail(row) {
      const vars = row.variables || {}
      const processKey = vars.processKey || ''
      const entry = formRegistry[processKey]
      if (entry) {
        this.detailFormName = entry.name
        this.detailFormData = { ...vars }
      } else {
        this.detailFormName = null
        this.detailFormData = {}
      }
      this.detailOpen = true
    },
    handleAddSign() {
      const user = this.approveForm.signUser.trim()
      if (!user) { this.$message.warning('请输入加签人用户名'); return }
      this.signLoading = true
      addSign(this.currentTask.taskId, user).then(res => {
        this.$message.success(res.data.tip || '加签成功')
        this.approveForm.signUser = ''
        this.signLoading = false
        this.loadTodo()
      }).catch(() => { this.signLoading = false })
    },
    submitApprove() {
      const { action, comment } = this.approveForm
      const taskId = this.currentTask.taskId
      const done = () => {
        this.approveOpen = false
        this.loadTodo()
      }
      if (action === 'approve') {
        approveTask(taskId, comment || '同意').then(() => {
          this.$message.success('审批通过')
          done()
        }).catch(() => {})
      } else if (action === 'rollback') {
        rollbackTask(taskId, comment || '需修改').then(() => {
          this.$message.success('已驳回至上一节点')
          done()
        }).catch(() => {})
      } else {
        rejectTask(taskId, comment || '不同意').then(() => {
          this.$message.success('已驳回')
          done()
        }).catch(() => {})
      }
    },
    handleTrack(processInstanceId) {
      this.trackOpen = true
      this.trackList = []
      getProcessTrack(processInstanceId).then(res => {
        this.trackList = res.data || []
      })
    },
    isCcTask(name) {
      return name && name.startsWith('[抄送]')
    },
    isMainApprover(item, stage) {
      // 有加签时，所有非加签的都是主审（会签+加签场景）
      const hasSign = stage.items.some(i => (i.node || '').includes('(加签)'))
      return hasSign && !(item.node || '').includes('(加签)')
    },

    formatDuration(ms) {
      if (!ms) return '-'
      const seconds = Math.floor(ms / 1000)
      if (seconds < 60) return seconds + '秒'
      const minutes = Math.floor(seconds / 60)
      if (minutes < 60) return minutes + '分' + (seconds % 60) + '秒'
      const hours = Math.floor(minutes / 60)
      return hours + '时' + (minutes % 60) + '分'
    }
  }
}
</script>
