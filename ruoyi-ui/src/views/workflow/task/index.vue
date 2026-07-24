<template>
  <div class="app-container">
    <!-- 搜索表单 -->
    <el-form :model="queryParams" ref="queryForm" size="small" :inline="true" v-show="showSearch" label-width="80px">
      <el-form-item label="审批人" prop="assignee">
        <el-input
          v-model="queryParams.assignee"
          placeholder="请输入审批人用户名"
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
      <el-col :span="1.5">
        <el-button type="success" plain icon="el-icon-plus" size="mini" @click="handleStartLeave">发起请假</el-button>
      </el-col>
      <right-toolbar :showSearch.sync="showSearch" @queryTable="handleQuery"></right-toolbar>
    </el-row>

    <!-- Tab 切换：待办 / 已办 -->
    <el-tabs v-model="activeTab" @tab-click="handleTabClick">
      <el-tab-pane label="待办任务" name="todo">
        <el-table v-loading="loading" :data="todoList" border stripe>
          <el-table-column label="任务名称" align="center" prop="taskName" width="150" />
          <el-table-column label="申请人" align="center" width="100">
            <template slot-scope="scope">
              {{ scope.row.variables.applicant || '-' }}
            </template>
          </el-table-column>
          <el-table-column label="请假天数" align="center" width="100">
            <template slot-scope="scope">
              <el-tag type="warning" size="mini">{{ scope.row.variables.days }} 天</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="流程实例ID" align="center" prop="processInstanceId" min-width="280" show-overflow-tooltip />
          <el-table-column label="创建时间" align="center" width="170">
            <template slot-scope="scope">
              {{ parseTime(scope.row.createTime) }}
            </template>
          </el-table-column>
          <el-table-column label="操作" align="center" width="220">
            <template slot-scope="scope">
              <el-button size="mini" type="primary" icon="el-icon-check" @click="handleApprove(scope.row)">审批</el-button>
              <el-button size="mini" type="text" icon="el-icon-tickets" @click="handleTrack(scope.row.processInstanceId)">轨迹</el-button>
            </template>
          </el-table-column>
        </el-table>
        <div v-if="todoList.length === 0 && !loading" style="text-align:center;color:#999;padding:40px;">
          暂无待办任务
        </div>
      </el-tab-pane>

      <el-tab-pane label="已办历史" name="history">
        <el-table v-loading="loading" :data="historyList" border stripe>
          <el-table-column label="任务名称" align="center" prop="taskName" width="150" />
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
    </el-tabs>

    <!-- 审批对话框 -->
    <el-dialog title="审批任务" :visible.sync="approveOpen" width="550px" append-to-body>
      <el-form ref="approveForm" :model="approveForm" label-width="80px">
        <el-form-item label="任务名称">
          <el-tag type="primary" size="medium">{{ currentTask.taskName }}</el-tag>
        </el-form-item>
        <el-form-item label="申请人">
          <span>{{ currentTask.variables.applicant || '-' }}</span>
        </el-form-item>
        <el-form-item label="请假天数">
          <el-tag type="warning" size="mini">{{ currentTask.variables.days }} 天</el-tag>
        </el-form-item>
        <el-form-item label="操作">
          <el-radio-group v-model="approveForm.action">
            <el-radio label="approve" style="color: #67C23A;">同意</el-radio>
            <el-radio label="reject" style="color: #F56C6C;">驳回</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="审批意见">
          <el-input
            v-model="approveForm.comment"
            type="textarea"
            :rows="3"
            :placeholder="approveForm.action === 'approve' ? '请输入审批意见（可选）' : '请输入驳回原因'"
          />
        </el-form-item>
      </el-form>
      <div slot="footer" class="dialog-footer">
        <el-button type="primary" @click="submitApprove">确 定</el-button>
        <el-button @click="approveOpen = false">取 消</el-button>
      </div>
    </el-dialog>

    <!-- 发起请假对话框 -->
    <el-dialog title="发起请假" :visible.sync="leaveOpen" width="500px" append-to-body>
      <el-form ref="leaveForm" :model="leaveForm" :rules="leaveRules" label-width="100px">
        <el-form-item label="申请人" prop="applicant">
          <el-input v-model="leaveForm.applicant" placeholder="请输入申请人" />
        </el-form-item>
        <el-form-item label="请假天数" prop="days">
          <el-input-number v-model="leaveForm.days" :min="1" :max="30" />
          <span style="margin-left:10px;color:#999;">>3天需总监审批</span>
        </el-form-item>
        <el-form-item label="部门经理" prop="manager">
          <el-input v-model="leaveForm.manager" placeholder="请输入部门经理用户名" />
        </el-form-item>
        <el-form-item label="总监" prop="director">
          <el-input v-model="leaveForm.director" placeholder=">3天时需填写" />
        </el-form-item>
      </el-form>
      <div slot="footer" class="dialog-footer">
        <el-button type="primary" @click="submitLeave">发 起</el-button>
        <el-button @click="leaveOpen = false">取 消</el-button>
      </div>
    </el-dialog>

    <!-- 审批轨迹对话框 -->
    <el-dialog title="审批轨迹" :visible.sync="trackOpen" width="600px" append-to-body>
      <el-timeline>
        <el-timeline-item
          v-for="(item, index) in trackList"
          :key="index"
          :timestamp="parseTime(item.endTime || item.startTime)"
          :color="index < trackList.length ? '#409EFF' : '#909399'"
        >
          <div>
            <strong>{{ item.assignee }}</strong>
            <el-tag type="primary" size="mini" style="margin-left: 8px;">{{ item.taskName }}</el-tag>
          </div>
          <div style="margin-top: 4px; color: #999; font-size: 13px;">
            {{ parseTime(item.startTime) }} ~ {{ parseTime(item.endTime) }}
            （{{ formatDuration(item.duration) }}）
          </div>
        </el-timeline-item>
      </el-timeline>
      <div v-if="trackList.length === 0" style="text-align:center;color:#999;padding:40px;">
        暂无审批记录
      </div>
    </el-dialog>
  </div>
</template>

<script>
import { listTodoTasks, listHistoryTasks, approveTask, rejectTask, startLeave, getProcessTrack } from "@/api/workflow/flowable"

export default {
  name: "FlowableTask",
  data() {
    return {
      loading: false,
      showSearch: true,
      activeTab: 'todo',
      queryParams: {
        assignee: undefined
      },
      todoList: [],
      historyList: [],
      approveOpen: false,
      approveForm: {
        action: 'approve',
        comment: ''
      },
      currentTask: { variables: {} },
      leaveOpen: false,
      leaveForm: {
        applicant: '',
        days: 1,
        manager: '',
        director: ''
      },
      leaveRules: {
        applicant: [{ required: true, message: '请输入申请人', trigger: 'blur' }],
        manager: [{ required: true, message: '请输入部门经理', trigger: 'blur' }]
      },
      trackOpen: false,
      trackList: []
    }
  },
  created() {
    this.queryParams.assignee = this.$store.state.user.name
    this.loadTodo()
  },
  methods: {
    handleTabClick(tab) {
      if (tab.name === 'todo') {
        this.loadTodo()
      } else {
        this.loadHistory()
      }
    },
    handleQuery() {
      if (this.activeTab === 'todo') {
        this.loadTodo()
      } else {
        this.loadHistory()
      }
    },
    resetQuery() {
      this.queryParams.assignee = this.$store.state.user.name
      this.handleQuery()
    },
    loadTodo() {
      if (!this.queryParams.assignee) {
        this.todoList = []
        return
      }
      this.loading = true
      listTodoTasks(this.queryParams.assignee).then(res => {
        this.todoList = res.data || []
        this.loading = false
      }).catch(() => {
        this.loading = false
      })
    },
    loadHistory() {
      if (!this.queryParams.assignee) {
        this.historyList = []
        return
      }
      this.loading = true
      listHistoryTasks(this.queryParams.assignee).then(res => {
        this.historyList = res.data || []
        this.loading = false
      }).catch(() => {
        this.loading = false
      })
    },
    handleApprove(row) {
      this.currentTask = row
      this.approveForm = { action: 'approve', comment: '' }
      this.approveOpen = true
    },
    submitApprove() {
      const { action, comment } = this.approveForm
      const taskId = this.currentTask.taskId
      if (action === 'approve') {
        approveTask(taskId, comment || '同意').then(() => {
          this.msgSuccess('审批通过')
          this.approveOpen = false
          this.loadTodo()
        })
      } else {
        rejectTask(taskId, comment || '不同意').then(() => {
          this.msgSuccess('已驳回')
          this.approveOpen = false
          this.loadTodo()
        })
      }
    },
    handleTrack(processInstanceId) {
      this.trackOpen = true
      this.trackList = []
      getProcessTrack(processInstanceId).then(res => {
        this.trackList = res.data || []
      })
    },
    handleStartLeave() {
      this.leaveForm = { applicant: this.queryParams.assignee || '', days: 1, manager: '', director: '' }
      this.leaveOpen = true
    },
    submitLeave() {
      this.$refs.leaveForm.validate(valid => {
        if (!valid) return
        startLeave(this.leaveForm).then(() => {
          this.msgSuccess('请假流程已发起')
          this.leaveOpen = false
          this.loadTodo()
        })
      })
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
