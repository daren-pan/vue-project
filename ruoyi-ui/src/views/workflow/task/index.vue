<template>
  <div class="app-container">
    <!-- 搜索表单 -->
    <el-form :model="queryParams" ref="queryForm" size="small" :inline="true" v-show="showSearch" label-width="80px">
      <el-form-item label="待办人" prop="assignee">
        <el-input
          v-model="queryParams.assignee"
          placeholder="请输入用户名"
          clearable
          @keyup.enter.native="handleQuery"
        />
      </el-form-item>
      <el-form-item label="状态" prop="status">
        <el-select v-model="queryParams.status" placeholder="请选择状态" clearable>
          <el-option label="待办" value="pending" />
          <el-option label="已完成" value="completed" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" icon="el-icon-search" size="mini" @click="handleQuery">搜索</el-button>
        <el-button icon="el-icon-refresh" size="mini" @click="resetQuery">重置</el-button>
      </el-form-item>
    </el-form>

    <!-- 操作按钮栏 -->
    <el-row :gutter="10" class="mb8">
      <el-col :span="1.5">
        <el-button
          type="primary"
          plain
          icon="el-icon-refresh"
          size="mini"
          @click="getList"
        >刷新</el-button>
      </el-col>
      <right-toolbar :showSearch.sync="showSearch" @queryTable="getList"></right-toolbar>
    </el-row>

    <!-- 数据表格 -->
    <el-table v-loading="loading" :data="taskList">
      <el-table-column label="任务ID" align="center" prop="id" width="80" />
      <el-table-column label="节点名称" align="center" prop="nodeName" width="150" />
      <el-table-column label="发起人" align="center" prop="applicant" width="100" />
      <el-table-column label="待办人" align="center" prop="assignee" width="100" />
      <el-table-column label="业务单据" align="center" min-width="200">
        <template slot-scope="scope">
          <span>{{ scope.row.businessTable }} - {{ scope.row.businessKey }}</span>
        </template>
      </el-table-column>
      <el-table-column label="状态" align="center" prop="status" width="100">
        <template slot-scope="scope">
          <el-tag :type="scope.row.status === 'pending' ? 'warning' : 'success'" size="mini">
            {{ scope.row.status === 'pending' ? '待审批' : '已完成' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="创建时间" align="center" prop="createTime" width="160">
        <template slot-scope="scope">
          <span>{{ parseTime(scope.row.createTime) }}</span>
        </template>
      </el-table-column>
      <el-table-column label="操作" align="center" class-name="small-padding fixed-width" width="200">
        <template slot-scope="scope">
          <el-button
            v-if="scope.row.status === 'pending'"
            size="mini"
            type="primary"
            icon="el-icon-check"
            @click="handleApprove(scope.row)"
          >审批</el-button>
          <el-button
            size="mini"
            type="text"
            icon="el-icon-tickets"
            @click="handleHistory(scope.row)"
          >轨迹</el-button>
        </template>
      </el-table-column>
    </el-table>

    <!-- 分页 -->
    <pagination
      v-show="total>0"
      :total="total"
      :page.sync="queryParams.pageNum"
      :limit.sync="queryParams.pageSize"
      @pagination="getList"
    />

    <!-- 审批操作对话框 -->
    <el-dialog title="审批" :visible.sync="approveOpen" width="550px" append-to-body>
      <el-form ref="approveForm" :model="approveForm" label-width="80px">
        <el-form-item label="节点">
          <el-tag type="primary" size="medium">{{ currentTask.nodeName }}</el-tag>
        </el-form-item>
        <el-form-item label="发起人">
          <span>{{ currentTask.applicant }}</span>
        </el-form-item>
        <el-form-item label="业务信息">
          <span>{{ currentTask.businessTable }} - {{ currentTask.businessKey }}</span>
          <el-button type="text" size="small" @click="viewBusinessDetail(currentTask)">
            查看详情
          </el-button>
        </el-form-item>
        <el-form-item label="操作">
          <el-radio-group v-model="approveForm.action">
            <el-radio label="agree" style="color: #67C23A;">同意</el-radio>
            <el-radio label="reject" style="color: #F56C6C;">驳回</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="审批意见">
          <el-input
            v-model="approveForm.comment"
            type="textarea"
            :rows="3"
            placeholder="请输入审批意见（可选）"
          />
        </el-form-item>
      </el-form>
      <div slot="footer" class="dialog-footer">
        <el-button type="primary" @click="submitApprove">确 定</el-button>
        <el-button @click="approveOpen = false">取 消</el-button>
      </div>
    </el-dialog>

    <!-- 审批轨迹对话框 -->
    <el-dialog title="审批轨迹" :visible.sync="historyOpen" width="600px" append-to-body>
      <el-timeline>
        <el-timeline-item
          v-for="(record, index) in historyList"
          :key="index"
          :timestamp="parseTime(record.completeTime || record.createTime)"
          :color="timelineColor(record.action)"
        >
          <div style="display: flex; justify-content: space-between; align-items: center;">
            <div>
              <strong>{{ record.assignee }}</strong>
              <el-tag :type="actionTagType(record.action)" size="mini" style="margin-left: 8px;">
                {{ actionLabel(record.action) }}
              </el-tag>
              <span style="margin-left: 8px; color: #999;">{{ record.nodeName }}</span>
            </div>
          </div>
          <div v-if="record.comment" style="margin-top: 6px; color: #666; font-size: 13px;">
            审批意见：{{ record.comment }}
          </div>
        </el-timeline-item>
      </el-timeline>
    </el-dialog>
  </div>
</template>

<script>
import { listTask, approveTask, getHistory } from "@/api/workflow/task"

export default {
  name: "Task",
  data() {
    return {
      // 遮罩层
      loading: true,
      // 显示搜索条件
      showSearch: true,
      // 总条数
      total: 0,
      // 表格数据
      taskList: [],
      // 查询参数
      queryParams: {
        pageNum: 1,
        pageSize: 10,
        assignee: undefined,
        status: "pending"
      },
      // 审批对话框
      approveOpen: false,
      approveForm: {
        taskId: undefined,
        action: "agree",
        comment: ""
      },
      currentTask: {},
      // 审批轨迹对话框
      historyOpen: false,
      historyList: []
    }
  },
  created() {
    // 默认查询当前登录用户的待办
    this.queryParams.assignee = this.$store.state.user.name
    this.getList()
  },
  methods: {
    /** 查询待办列表 */
    getList() {
      this.loading = true
      listTask(this.queryParams).then(response => {
        this.taskList = response.rows
        this.total = response.total
        this.loading = false
      })
    },
    /** 搜索按钮操作 */
    handleQuery() {
      this.queryParams.pageNum = 1
      this.getList()
    },
    /** 重置按钮操作 */
    resetQuery() {
      this.resetForm("queryForm")
      this.handleQuery()
    },
    /** 打开审批对话框 */
    handleApprove(row) {
      this.currentTask = row
      this.approveForm.taskId = row.id
      this.approveForm.action = "agree"
      this.approveForm.comment = ""
      this.approveOpen = true
    },
    /** 提交审批 */
    submitApprove() {
      const actionText = this.approveForm.action === "agree" ? "同意" : "驳回"
      this.$modal.confirm('是否确认' + actionText + '该申请？').then(() => {
        return approveTask(this.approveForm)
      }).then(() => {
        this.$modal.msgSuccess(actionText + "成功")
        this.approveOpen = false
        this.getList()
      }).catch(() => {})
    },
    /** 查看业务详情（跳转到对应的业务页面） */
    viewBusinessDetail(task) {
      // 根据 businessTable 跳转到不同的业务详情页
      const routes = {
        "leave_record": "/leave/detail/" + task.businessId
      }
      const path = routes[task.businessTable]
      if (path) {
        // 在新标签页打开
        const routeData = this.$router.resolve({ path: path })
        window.open(routeData.href, '_blank')
      } else {
        this.$modal.msgWarning("暂不支持查看该业务详情")
      }
    },
    /** 查看审批轨迹 */
    handleHistory(row) {
      const params = {
        businessTable: row.businessTable,
        businessId: row.businessId
      }
      getHistory(params).then(response => {
        this.historyList = response.data
        this.historyOpen = true
      })
    },
    /** 操作对应的标签样式 */
    actionTagType(action) {
      const map = {
        submit: "primary",
        agree: "success",
        reject: "danger",
        auto_pass: "warning",
        auto_reject: "danger"
      }
      return map[action] || ""
    },
    /** 操作对应的中文名 */
    actionLabel(action) {
      const map = {
        submit: "发起",
        agree: "同意",
        reject: "驳回",
        auto_pass: "自动通过",
        auto_reject: "自动驳回"
      }
      return map[action] || action
    },
    /** 审批轨迹时间线颜色 */
    timelineColor(action) {
      const map = {
        submit: "#409EFF",
        agree: "#67C23A",
        reject: "#F56C6C",
        auto_pass: "#E6A23C",
        auto_reject: "#F56C6C"
      }
      return map[action] || "#999"
    }
  }
}
</script>
