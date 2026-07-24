<template>
  <div class="designer-container">
    <!-- 顶部工具栏 -->
    <el-row :gutter="10" class="toolbar">
      <el-col :span="12">
        <h3 style="margin:0; line-height:36px;">
          BPMN 流程设计器
          <el-tag size="small" style="margin-left:8px;" v-if="currentDef">{{ currentDef.name }} v{{ currentDef.version }}</el-tag>
        </h3>
      </el-col>
      <el-col :span="12" style="text-align:right;">
        <el-button size="mini" icon="el-icon-folder-opened" @click="handleLoad">打开已有流程</el-button>
        <el-button size="mini" icon="el-icon-document-add" @click="handleNew">新建空白流程</el-button>
        <el-button size="mini" type="primary" icon="el-icon-upload2" @click="handleDeploy">部署</el-button>
        <el-button size="mini" icon="el-icon-back" @click="handleBack">返回</el-button>
      </el-col>
    </el-row>

    <!-- 主画布 -->
    <div class="canvas-wrapper">
      <div ref="canvas" class="bpmn-canvas"></div>

      <!-- 右侧属性面板 -->
      <div class="properties-panel" v-if="selectedElement">
        <h4>属性编辑</h4>
        <el-form label-width="80px" size="mini">
          <el-form-item label="元素类型">
            <el-tag size="mini">{{ elementType }}</el-tag>
          </el-form-item>
          <el-form-item label="ID" v-if="selectedElement.id">
            <el-input v-model="selectedElement.id" disabled />
          </el-form-item>
          <el-form-item label="名称">
            <el-input v-model="elementName" placeholder="输入名称" @change="updateName" />
          </el-form-item>

          <!-- 用户任务专属 -->
          <template v-if="selectedElement.type === 'bpmn:UserTask'">
            <el-divider content-position="left">审批人配置</el-divider>
            <el-form-item label="审批人">
              <el-input v-model="assignee" placeholder="如 ${manager} 或具体用户名" @change="updateAssignee" />
              <span style="font-size:11px;color:#999;">支持变量: &#36;{manager}, &#36;{director}</span>
            </el-form-item>
          </template>

          <!-- 排他网关专属 -->
          <template v-if="selectedElement.type === 'bpmn:ExclusiveGateway'">
            <el-divider content-position="left">条件配置</el-divider>
            <el-form-item label="网关名称">
              <el-input v-model="elementName" placeholder="如 判断天数" @change="updateName" />
            </el-form-item>
            <span style="font-size:11px;color:#999;">请在连线(箭头)上设置条件表达式</span>
          </template>

          <!-- 连线专属 -->
          <template v-if="selectedElement.type === 'bpmn:SequenceFlow'">
            <el-divider content-position="left">条件表达式</el-divider>
            <el-form-item label="条件">
              <el-input v-model="conditionExpression" placeholder="如 ${days > 3}" @change="updateCondition" />
              <span style="font-size:11px;color:#999;">支持: &#36;{days > 3}, &#36;{approved == true}</span>
            </el-form-item>
          </template>
        </el-form>
      </div>
    </div>

    <!-- 部署对话框 -->
    <el-dialog title="部署流程" :visible.sync="deployOpen" width="450px" append-to-body>
      <el-form :model="deployForm" :rules="deployRules" ref="deployForm" label-width="80px" size="small">
        <el-form-item label="流程标识" prop="processKey">
          <el-input v-model="deployForm.processKey" placeholder="如 leave、expense" />
        </el-form-item>
        <el-form-item label="流程名称" prop="processName">
          <el-input v-model="deployForm.processName" placeholder="如 请假审批" />
        </el-form-item>
      </el-form>
      <div slot="footer">
        <el-button @click="deployOpen = false">取消</el-button>
        <el-button type="primary" @click="submitDeploy" :loading="deploying">部署</el-button>
      </div>
    </el-dialog>

    <!-- 打开已有对话框 -->
    <el-dialog title="打开已有流程" :visible.sync="loadOpen" width="600px" append-to-body>
      <el-table :data="definitionList" @row-click="handleSelectDef" highlight-current-row size="small">
        <el-table-column label="标识" prop="key" width="120" />
        <el-table-column label="名称" prop="name" />
        <el-table-column label="版本" prop="version" width="60">
          <template slot-scope="s"><el-tag size="mini">v{{ s.row.version }}</el-tag></template>
        </el-table-column>
      </el-table>
    </el-dialog>
  </div>
</template>

<script>
import BpmnModeler from 'bpmn-js/lib/Modeler'
import { layoutProcess } from 'bpmn-auto-layout/dist/index.js'
import 'bpmn-js/dist/assets/diagram-js.css'
import 'bpmn-js/dist/assets/bpmn-js.css'
import 'bpmn-js/dist/assets/bpmn-font/css/bpmn.css'
import 'bpmn-js/dist/assets/bpmn-font/css/bpmn-codes.css'
import 'bpmn-js/dist/assets/bpmn-font/css/bpmn-embedded.css'
import { listDefinitions, deployBpmnXml, getBpmnXml } from "@/api/workflow/flowable"

const DEFAULT_BPMN = `<?xml version="1.0" encoding="UTF-8"?>
<bpmn:definitions xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
  xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL"
  xmlns:bpmndi="http://www.omg.org/spec/BPMN/20100524/DI"
  xmlns:dc="http://www.omg.org/spec/DD/20100524/DC"
  xmlns:flowable="http://flowable.org/bpmn"
  xmlns:di="http://www.omg.org/spec/DD/20100524/DI"
  targetNamespace="http://www.flowable.org/processdef">
  <bpmn:process id="process" name="新流程" isExecutable="true">
    <bpmn:startEvent id="StartEvent_1" name="开始" />
  </bpmn:process>
  <bpmndi:BPMNDiagram id="BPMNDiagram_1">
    <bpmndi:BPMNPlane id="BPMNPlane_1" bpmnElement="process" />
  </bpmndi:BPMNDiagram>
</bpmn:definitions>`

export default {
  name: "BpmnDesigner",
  data() {
    return {
      bpmnModeler: null,
      selectedElement: null,
      currentDef: null,
      // 部署对话框
      deployOpen: false,
      deploying: false,
      deployForm: { processKey: '', processName: '' },
      deployRules: {
        processKey: [{ required: true, message: '请输入流程标识', trigger: 'blur' }],
        processName: [{ required: true, message: '请输入流程名称', trigger: 'blur' }]
      },
      // 打开已有流程
      loadOpen: false,
      definitionList: []
    }
  },
  computed: {
    elementType() {
      const map = {
        'bpmn:StartEvent': '开始事件', 'bpmn:EndEvent': '结束事件',
        'bpmn:UserTask': '用户任务', 'bpmn:ServiceTask': '服务任务',
        'bpmn:ExclusiveGateway': '排他网关', 'bpmn:ParallelGateway': '并行网关',
        'bpmn:SequenceFlow': '连线'
      }
      return map[this.selectedElement?.type] || this.selectedElement?.type || ''
    },
    elementName: {
      get() { return this.selectedElement?.businessObject?.name || '' },
      set(val) { /* 通过 updateName 设置 */ }
    },
    assignee: {
      get() { return this.selectedElement?.businessObject?.assignee || '' },
      set(val) { /* 通过 updateAssignee 设置 */ }
    },
    conditionExpression: {
      get() {
        const bo = this.selectedElement?.businessObject
        if (!bo?.conditionExpression) return ''
        return bo.conditionExpression.body || ''
      },
      set(val) { /* 通过 updateCondition 设置 */ }
    }
  },
  mounted() {
    this.initModeler()
  },
  beforeDestroy() {
    if (this.bpmnModeler) this.bpmnModeler.destroy()
  },
  methods: {
    /** 初始化 bpmn-js */
    async initModeler() {
      this.bpmnModeler = new BpmnModeler({ container: this.$refs.canvas })
      // 监听元素选中
      this.bpmnModeler.on('selection.changed', (e) => {
        this.selectedElement = e.newSelection[0] || null
      })
      // 默认加载空白模板
      await this.bpmnModeler.importXML(DEFAULT_BPMN)
      this.fitViewport()
    },

    fitViewport() {
      const canvas = this.bpmnModeler.get('canvas')
      canvas.zoom('fit-viewport', 'auto')
    },

    /** 更新元素名称 */
    updateName() {
      if (!this.selectedElement) return
      const modeling = this.bpmnModeler.get('modeling')
      modeling.updateProperties(this.selectedElement, {
        name: this.selectedElement.businessObject.name
      })
    },

    /** 更新审批人 */
    updateAssignee() {
      if (!this.selectedElement) return
      const modeling = this.bpmnModeler.get('modeling')
      modeling.updateProperties(this.selectedElement, {
        assignee: this.selectedElement.businessObject.assignee
      })
    },

    /** 更新条件表达式 */
    updateCondition() {
      if (!this.selectedElement) return
      const bo = this.selectedElement.businessObject
      const modeling = this.bpmnModeler.get('modeling')
      const condition = bo.conditionExpression
      if (condition) {
        condition.body = this.selectedElement.businessObject.conditionExpression?.body || ''
      } else {
        modeling.updateProperties(this.selectedElement, {
          conditionExpression: this.bpmnModeler.get('moddle').create('bpmn:FormalExpression', {
            body: ''
          })
        })
      }
    },

    /** 新建 */
    async handleNew() {
      await this.bpmnModeler.importXML(DEFAULT_BPMN)
      this.currentDef = null
      this.fitViewport()
      this.$message.success('已创建空白流程')
    },

    /** 打开已有流程 */
    async handleLoad() {
      this.loadOpen = true
      const res = await listDefinitions()
      this.definitionList = res.data || []
    },

    async handleSelectDef(row) {
      this.loadOpen = false
      try {
        const res = await getBpmnXml(row.deploymentId)
        const xml = res.data
        if (!xml) { this.$message.error('获取流程 XML 失败'); return }

        // layoutProcess: 自动计算每个元素和连线的坐标
        const laidOut = await layoutProcess(xml, row.key)
        await this.bpmnModeler.importXML(laidOut)
        this.currentDef = row
        this.fitViewport()
        this.$message.success('已加载: ' + row.name)
      } catch (e) {
        console.error('加载失败:', e)
        this.$message.error('加载失败: ' + (e.message || '未知错误'))
      }
    },
    /** 部署 */
    handleDeploy() {
      this.deployForm = { processKey: 'process', processName: '新流程' }
      this.deployOpen = true
    },

    async submitDeploy() {
      this.$refs.deployForm.validate(async (valid) => {
        if (!valid) return
        this.deploying = true
        try {
          const { xml } = await this.bpmnModeler.saveXML({ format: true })
          await deployBpmnXml(this.deployForm.processName, this.deployForm.processKey, xml)
          this.$message.success('部署成功')
          this.deployOpen = false
        } catch (e) {
          this.$message.error('部署失败: ' + (e.message || e.msg))
        } finally {
          this.deploying = false
        }
      })
    },

    handleBack() {
      this.$router.push({ path: '/workflow/definition' })
    }
  }
}
</script>

<style scoped>
.designer-container {
  height: calc(100vh - 84px);
  display: flex;
  flex-direction: column;
}

.toolbar {
  padding: 8px 16px;
  background: #fff;
  border-bottom: 1px solid #e8e8e8;
  flex-shrink: 0;
}

.canvas-wrapper {
  flex: 1;
  display: flex;
  overflow: hidden;
}

.bpmn-canvas {
  flex: 1;
  height: 100%;
}

.properties-panel {
  width: 280px;
  padding: 12px;
  background: #fafafa;
  border-left: 1px solid #e8e8e8;
  overflow-y: auto;
  flex-shrink: 0;
}

.properties-panel h4 {
  margin: 0 0 12px 0;
  padding-bottom: 8px;
  border-bottom: 1px solid #e8e8e8;
}
</style>
