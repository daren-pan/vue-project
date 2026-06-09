<template>
  <div class="app-container">
    <!-- 顶部信息 -->
    <el-row :gutter="10" class="mb8">
      <el-col :span="12">
        <h2 style="margin: 0; line-height: 32px;">
          流程配置 - {{ processName }}
        </h2>
      </el-col>
      <el-col :span="12" style="text-align: right;">
        <el-button type="primary" icon="el-icon-plus" size="mini" @click="handleAddNode">新增节点</el-button>
        <el-button type="warning" icon="el-icon-plus" size="mini" @click="handleAddLine">新增连线</el-button>
        <el-button icon="el-icon-back" size="mini" @click="handleBack">返回</el-button>
      </el-col>
    </el-row>

    <!-- 流程图示 -->
    <el-card shadow="never" style="margin-bottom: 20px;">
      <div slot="header">
        <span>流程图示</span>
      </div>
      <div style="display: flex; flex-wrap: wrap; align-items: center; gap: 10px; min-height: 80px;">
        <template v-for="(node, index) in nodeList">
          <!-- 节点 -->
          <el-tag
            :key="node.id"
            :type="nodeTagType(node.nodeType)"
            size="medium"
            effect="plain"
            style="padding: 6px 16px; font-size: 14px; cursor: pointer;"
            @click="handleEditNode(node)"
          >
            {{ node.nodeName }}
          </el-tag>
          <!-- 箭头 -->
          <span v-if="index < nodeList.length - 1" :key="'arrow-' + index"
                style="font-size: 18px; color: #409EFF;">→</span>
        </template>
        <span v-if="nodeList.length === 0" style="color: #999;">暂无节点，请点击"新增节点"添加</span>
      </div>
    </el-card>

    <!-- 节点列表 -->
    <el-card shadow="never">
      <div slot="header">
        <span>节点定义列表</span>
      </div>
      <el-table :data="nodeList" size="small" max-height="400">
        <el-table-column label="节点ID" prop="nodeId" width="150" />
        <el-table-column label="节点名称" prop="nodeName" width="150" />
        <el-table-column label="节点类型" prop="nodeType" width="100">
          <template slot-scope="scope">
            <el-tag :type="nodeTagType(scope.row.nodeType)" size="mini">
              {{ nodeTypeLabel(scope.row.nodeType) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="审批方式" prop="approveMode" width="100">
          <template slot-scope="scope">
            {{ approveModeLabel(scope.row.approveMode) }}
          </template>
        </el-table-column>
        <el-table-column label="串审/联审" prop="auditType" width="100">
          <template slot-scope="scope">
            {{ scope.row.auditType === 'serial' ? '串审' : '联审' }}
          </template>
        </el-table-column>
        <el-table-column label="审批人" prop="assigneeType" min-width="120">
          <template slot-scope="scope">
            {{ assigneeLabel(scope.row) }}
          </template>
        </el-table-column>
        <el-table-column label="超时(小时)" prop="timeoutHours" width="100" />
        <el-table-column label="排序" prop="sortOrder" width="60" />
        <el-table-column label="操作" width="120" align="center">
          <template slot-scope="scope">
            <el-button size="mini" type="text" icon="el-icon-edit" @click="handleEditNode(scope.row)">编辑</el-button>
            <el-button size="mini" type="text" icon="el-icon-delete" @click="handleDeleteNode(scope.row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 连线列表 -->
    <el-card shadow="never" style="margin-top: 20px;">
      <div slot="header">
        <span>连线定义列表</span>
      </div>
      <el-table :data="lineList" size="small" max-height="300">
        <el-table-column label="来源节点" prop="fromNodeId" width="150" />
        <el-table-column label="目标节点" prop="toNodeId" width="150" />
        <el-table-column label="条件表达式" prop="conditionExpression" min-width="200">
          <template slot-scope="scope">
            <el-tag v-if="scope.row.conditionExpression" type="warning" size="mini">
              {{ scope.row.conditionExpression }}
            </el-tag>
            <span v-else style="color: #999;">无条件</span>
          </template>
        </el-table-column>
        <el-table-column label="排序" prop="sortOrder" width="60" />
        <el-table-column label="操作" width="120" align="center">
          <template slot-scope="scope">
            <el-button size="mini" type="text" icon="el-icon-edit" @click="handleEditLine(scope.row)">编辑</el-button>
            <el-button size="mini" type="text" icon="el-icon-delete" @click="handleDeleteLine(scope.row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 新增/编辑节点对话框 -->
    <el-dialog :title="nodeTitle" :visible.sync="nodeOpen" width="700px" append-to-body>
      <el-form ref="nodeForm" :model="nodeForm" :rules="nodeRules" label-width="120px" size="small">
        <el-row>
          <el-col :span="12">
            <el-form-item label="节点标识" prop="nodeId">
              <el-input v-model="nodeForm.nodeId" placeholder="如 leader_approve" :disabled="nodeForm.id != undefined" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="节点名称" prop="nodeName">
              <el-input v-model="nodeForm.nodeName" placeholder="如 组长审批" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row>
          <el-col :span="12">
            <el-form-item label="节点类型" prop="nodeType">
              <el-select v-model="nodeForm.nodeType" placeholder="请选择节点类型" @change="onNodeTypeChange">
                <el-option label="开始节点" value="start" />
                <el-option label="条件节点" value="condition" />
                <el-option label="审批节点" value="approve" />
                <el-option label="结束节点" value="end" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="排序号" prop="sortOrder">
              <el-input-number v-model="nodeForm.sortOrder" :min="0" :max="999" />
            </el-form-item>
          </el-col>
        </el-row>

        <!-- 审批节点特有配置 -->
        <template v-if="nodeForm.nodeType === 'approve'">
          <el-divider content-position="left">审批配置</el-divider>
          <el-row>
            <el-col :span="12">
              <el-form-item label="审批人类型" prop="assigneeType">
                <el-select v-model="nodeForm.assigneeType" placeholder="请选择审批人类型" @change="onAssigneeTypeChange">
                  <el-option label="部门上级" value="dept_leader" />
                  <el-option label="角色" value="role" />
                  <el-option label="指定用户" value="user" />
                  <el-option label="发起人自选" value="self_choose" />
                  <el-option label="发起人自己" value="apply_self" />
                </el-select>
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="审批人配置" prop="assigneeValue">
                <el-input v-model="nodeForm.assigneeValue" :placeholder="assigneePlaceholder" />
              </el-form-item>
            </el-col>
          </el-row>
          <el-row>
            <el-col :span="12">
              <el-form-item label="审批方式" prop="approveMode">
                <el-select v-model="nodeForm.approveMode" placeholder="请选择审批方式">
                  <el-option label="单人审批" value="single" />
                  <el-option label="会签（全部通过）" value="countersign" />
                  <el-option label="或签（指定票数）" value="or_sign" />
                </el-select>
              </el-form-item>
            </el-col>
            <el-col :span="12" v-if="nodeForm.approveMode === 'or_sign'">
              <el-form-item label="通过票数" prop="approveCount">
                <el-input-number v-model="nodeForm.approveCount" :min="1" :max="99" />
              </el-form-item>
            </el-col>
            <el-col :span="12" v-else>
              <el-form-item label="串审/联审" prop="auditType">
                <el-select v-model="nodeForm.auditType" placeholder="请选择审批顺序">
                  <el-option label="串审（按顺序）" value="serial" />
                  <el-option label="联审（同时审批）" value="parallel" />
                </el-select>
              </el-form-item>
            </el-col>
          </el-row>
          <el-row>
            <el-col :span="12">
              <el-form-item label="超时(小时)" prop="timeoutHours">
                <el-input-number v-model="nodeForm.timeoutHours" :min="0" :max="720" />
              </el-form-item>
            </el-col>
            <el-col :span="12" v-if="nodeForm.timeoutHours > 0">
              <el-form-item label="超时动作" prop="timeoutAction">
                <el-select v-model="nodeForm.timeoutAction" placeholder="请选择超时动作">
                  <el-option label="系统自动通过" value="auto_pass" />
                  <el-option label="系统自动驳回" value="auto_reject" />
                  <el-option label="催办提醒" value="remind" />
                </el-select>
              </el-form-item>
            </el-col>
          </el-row>
        </template>
      </el-form>
      <div slot="footer" class="dialog-footer">
        <el-button type="primary" @click="submitNodeForm">确 定</el-button>
        <el-button @click="cancelNode">取 消</el-button>
      </div>
    </el-dialog>

    <!-- 新增/编辑连线对话框 -->
    <el-dialog :title="lineTitle" :visible.sync="lineOpen" width="500px" append-to-body>
      <el-form ref="lineForm" :model="lineForm" :rules="lineRules" label-width="100px" size="small">
        <el-form-item label="来源节点" prop="fromNodeId">
          <el-select v-model="lineForm.fromNodeId" placeholder="请选择来源节点" filterable>
            <el-option v-for="item in nodeList" :key="item.nodeId" :label="item.nodeName" :value="item.nodeId" />
          </el-select>
        </el-form-item>
        <el-form-item label="目标节点" prop="toNodeId">
          <el-select v-model="lineForm.toNodeId" placeholder="请选择目标节点" filterable>
            <el-option v-for="item in nodeList" :key="item.nodeId" :label="item.nodeName" :value="item.nodeId" />
          </el-select>
        </el-form-item>
        <el-form-item label="条件表达式" prop="conditionExpression">
          <el-input v-model="lineForm.conditionExpression" placeholder="如 days > 3，为空则无条件" />
          <span style="font-size: 12px; color: #999;">
            支持的格式：变量名 运算符 值，如 approved == true、days > 3、amount >= 5000
          </span>
        </el-form-item>
        <el-form-item label="排序号" prop="sortOrder">
          <el-input-number v-model="lineForm.sortOrder" :min="0" :max="999" />
          <span style="font-size: 12px; color: #999; margin-left: 8px;">
            相同来源节点时按排序号从小到大判断条件
          </span>
        </el-form-item>
      </el-form>
      <div slot="footer" class="dialog-footer">
        <el-button type="primary" @click="submitLineForm">确 定</el-button>
        <el-button @click="cancelLine">取 消</el-button>
      </div>
    </el-dialog>
  </div>
</template>

<script>
// 注意：页面中直接调用了 CRUD 接口，实际需要后台增加节点和连线的 CRUD 接口
// 目前先用模拟数据展示功能，后续对接后台接口时替换为真实 API
import { listDefinition, getDefinition } from "@/api/workflow/definition"

export default {
  name: "Config",
  data() {
    return {
      // 流程ID
      processDefId: undefined,
      // 流程名称
      processName: "",
      // 节点列表
      nodeList: [],
      // 连线列表
      lineList: [],
      // 节点对话框
      nodeOpen: false,
      nodeTitle: "",
      nodeForm: {},
      nodeRules: {
        nodeId: [{ required: true, message: "节点标识不能为空", trigger: "blur" }],
        nodeName: [{ required: true, message: "节点名称不能为空", trigger: "blur" }],
        nodeType: [{ required: true, message: "节点类型不能为空", trigger: "change" }]
      },
      // 连线对话框
      lineOpen: false,
      lineTitle: "",
      lineForm: {},
      lineRules: {
        fromNodeId: [{ required: true, message: "来源节点不能为空", trigger: "change" }],
        toNodeId: [{ required: true, message: "目标节点不能为空", trigger: "change" }]
      }
    }
  },
  created() {
    // 从路由参数获取流程定义ID
    this.processDefId = this.$route.params.id
    this.processName = this.$route.query.processName || ""
    this.loadData()
  },
  methods: {
    /** 加载节点和连线数据 */
    loadData() {
      // TODO: 对接后台接口，目前使用模拟数据展示
      // 真实场景应调用：
      // import { listNode } from "@/api/workflow/node"
      // import { listLine } from "@/api/workflow/line"
      // listNode({ processDefId: this.processDefId }).then(...)
      // listLine({ processDefId: this.processDefId }).then(...)
      
      // 以下为模拟数据，便于前端演示
      this.nodeList = [
        { id: 1, nodeId: "start", nodeName: "发起", nodeType: "start", sortOrder: 1 },
        { id: 2, nodeId: "condition_days", nodeName: "天数判断", nodeType: "condition", sortOrder: 2 },
        { id: 3, nodeId: "leader_approve", nodeName: "组长审批", nodeType: "approve",
          assigneeType: "dept_leader", assigneeValue: "", approveMode: "single",
          auditType: "serial", timeoutHours: 48, timeoutAction: "auto_pass", sortOrder: 3 },
        { id: 4, nodeId: "legal_approve", nodeName: "法务联审", nodeType: "approve",
          assigneeType: "role", assigneeValue: "legal", approveMode: "countersign",
          auditType: "parallel", timeoutHours: 24, timeoutAction: "remind", sortOrder: 4 },
        { id: 5, nodeId: "manager_approve", nodeName: "经理审批", nodeType: "approve",
          assigneeType: "role", assigneeValue: "manager", approveMode: "single",
          auditType: "serial", timeoutHours: 48, timeoutAction: "auto_pass", sortOrder: 5 },
        { id: 6, nodeId: "end", nodeName: "结束", nodeType: "end", sortOrder: 6 }
      ]
      this.lineList = [
        { id: 1, fromNodeId: "start", toNodeId: "condition_days", conditionExpression: "", sortOrder: 1 },
        { id: 2, fromNodeId: "condition_days", toNodeId: "leader_approve", conditionExpression: "days <= 3", sortOrder: 1 },
        { id: 3, fromNodeId: "condition_days", toNodeId: "leader_approve", conditionExpression: "days > 3", sortOrder: 2 },
        { id: 4, fromNodeId: "leader_approve", toNodeId: "legal_approve", conditionExpression: "days > 3 AND approved == true", sortOrder: 1 },
        { id: 5, fromNodeId: "leader_approve", toNodeId: "end", conditionExpression: "days <= 3 AND approved == true", sortOrder: 2 },
        { id: 6, fromNodeId: "leader_approve", toNodeId: "start", conditionExpression: "approved == false", sortOrder: 3 },
        { id: 7, fromNodeId: "legal_approve", toNodeId: "manager_approve", conditionExpression: "approved == true", sortOrder: 1 },
        { id: 8, fromNodeId: "legal_approve", toNodeId: "start", conditionExpression: "approved == false", sortOrder: 2 },
        { id: 9, fromNodeId: "manager_approve", toNodeId: "end", conditionExpression: "approved == true", sortOrder: 1 },
        { id: 10, fromNodeId: "manager_approve", toNodeId: "start", conditionExpression: "approved == false", sortOrder: 2 }
      ]
    },

    /** 返回列表页 */
    handleBack() {
      this.$router.push({ path: '/workflow/definition' })
    },

    /** 新增节点 */
    handleAddNode() {
      this.nodeForm = {
        id: undefined,
        processDefId: this.processDefId,
        nodeId: "",
        nodeName: "",
        nodeType: "approve",
        assigneeType: undefined,
        assigneeValue: "",
        approveMode: "single",
        approveCount: 1,
        auditType: "serial",
        timeoutHours: 0,
        timeoutAction: "remind",
        sortOrder: this.nodeList.length + 1
      }
      this.nodeTitle = "新增节点"
      this.nodeOpen = true
    },

    /** 编辑节点 */
    handleEditNode(row) {
      this.nodeForm = JSON.parse(JSON.stringify(row))
      this.nodeTitle = "编辑节点"
      this.nodeOpen = true
    },

    /** 删除节点 */
    handleDeleteNode(row) {
      this.$modal.confirm('是否确认删除节点"' + row.nodeName + '"？').then(() => {
        const index = this.nodeList.findIndex(item => item.id === row.id)
        if (index > -1) {
          this.nodeList.splice(index, 1)
        }
        this.$modal.msgSuccess("删除成功")
      }).catch(() => {})
    },

    /** 取消节点编辑 */
    cancelNode() {
      this.nodeOpen = false
    },

    /** 提交节点表单 */
    submitNodeForm() {
      this.$refs["nodeForm"].validate(valid => {
        if (!valid) return
        if (this.nodeForm.id) {
          // 编辑
          const index = this.nodeList.findIndex(item => item.id === this.nodeForm.id)
          if (index > -1) {
            this.$set(this.nodeList, index, { ...this.nodeForm })
          }
          this.$modal.msgSuccess("修改成功")
        } else {
          // 新增
          this.nodeForm.id = Date.now()
          this.nodeList.push({ ...this.nodeForm })
          this.$modal.msgSuccess("新增成功")
        }
        this.nodeOpen = false
      })
    },

    /** 节点类型变化时清空审批配置 */
    onNodeTypeChange() {
      if (this.nodeForm.nodeType !== "approve") {
        this.nodeForm.assigneeType = undefined
        this.nodeForm.assigneeValue = ""
        this.nodeForm.approveMode = "single"
        this.nodeForm.auditType = "serial"
        this.nodeForm.timeoutHours = 0
        this.nodeForm.timeoutAction = "remind"
      }
    },

    /** 审批人类型变化提示 */
    onAssigneeTypeChange() {
      // 清空配置值
      this.nodeForm.assigneeValue = ""
    },

    /** 审批人类型对应的输入提示 */
    assigneePlaceholder() {
      switch (this.nodeForm.assigneeType) {
        case "dept_leader": return "自动解析，无需填写"
        case "role": return "请输入角色标识，如 legal"
        case "user": return "请输入用户名"
        case "self_choose": return "由发起人自选，无需配置"
        case "apply_self": return "由发起人自己审批"
        default: return ""
      }
    },

    // ======================== 连线操作 ========================

    /** 新增连线 */
    handleAddLine() {
      this.lineForm = {
        id: undefined,
        processDefId: this.processDefId,
        fromNodeId: "",
        toNodeId: "",
        conditionExpression: "",
        sortOrder: 1
      }
      this.lineTitle = "新增连线"
      this.lineOpen = true
    },

    /** 编辑连线 */
    handleEditLine(row) {
      this.lineForm = JSON.parse(JSON.stringify(row))
      this.lineTitle = "编辑连线"
      this.lineOpen = true
    },

    /** 删除连线 */
    handleDeleteLine(row) {
      this.$modal.confirm('是否确认删除该连线？').then(() => {
        const index = this.lineList.findIndex(item => item.id === row.id)
        if (index > -1) {
          this.lineList.splice(index, 1)
        }
        this.$modal.msgSuccess("删除成功")
      }).catch(() => {})
    },

    /** 取消连线编辑 */
    cancelLine() {
      this.lineOpen = false
    },

    /** 提交连线表单 */
    submitLineForm() {
      this.$refs["lineForm"].validate(valid => {
        if (!valid) return
        if (this.lineForm.id) {
          const index = this.lineList.findIndex(item => item.id === this.lineForm.id)
          if (index > -1) {
            this.$set(this.lineList, index, { ...this.lineForm })
          }
          this.$modal.msgSuccess("修改成功")
        } else {
          this.lineForm.id = Date.now()
          this.lineList.push({ ...this.lineForm })
          this.$modal.msgSuccess("新增成功")
        }
        this.lineOpen = false
      })
    },

    // ======================== 工具方法 ========================

    /** 节点类型对应的标签样式 */
    nodeTagType(nodeType) {
      const map = { start: "success", condition: "warning", approve: "primary", end: "info" }
      return map[nodeType] || ""
    },

    /** 节点类型中文名 */
    nodeTypeLabel(nodeType) {
      const map = { start: "开始", condition: "条件", approve: "审批", end: "结束" }
      return map[nodeType] || nodeType
    },

    /** 审批方式中文名 */
    approveModeLabel(mode) {
      const map = { single: "单人审批", countersign: "会签", or_sign: "或签" }
      return map[mode] || mode
    },

    /** 审批人类型中文说明 */
    assigneeLabel(row) {
      if (row.nodeType !== "approve") return "-"
      const typeMap = {
        dept_leader: "部门上级",
        role: "角色:" + (row.assigneeValue || ""),
        user: "用户:" + (row.assigneeValue || ""),
        self_choose: "发起人自选",
        apply_self: "发起人自己"
      }
      return typeMap[row.assigneeType] || "-"
    }
  }
}
</script>

<style scoped>
.app-container >>> .el-card__header {
  padding: 10px 20px;
  font-weight: bold;
}
</style>
