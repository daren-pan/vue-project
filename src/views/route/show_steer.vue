<template>
  <div class="steer-management">
    <div class="header">
      <h2>引导策略管理</h2>
      <el-button type="primary" @click="openAddPolicyDialog">添加引导</el-button>
    </div>

    <div class="router-selector">
      <label for="router-select">选择路由器: </label>
      <el-select v-model="selectedRouter" placeholder="选择路由器" @change="fetchPolicies">
        <el-option label="r0" value="r0" />
        <el-option label="r3" value="r3" />
        <el-option label="r6" value="r6" />
      </el-select>
    </div>

    <el-table :data="policies" border style="width: 100%" height="400">
      <el-table-column label="BSID" prop="BSID" />
      <el-table-column label="目标IP" prop="Traffic" />
      <el-table-column label="操作">
        <template slot-scope="scope">
          <el-button type="danger" icon="el-icon-delete" @click="deletePolicy(scope.row.Traffic)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <!-- 添加策略弹窗 -->
    <el-dialog
      title="添加SRv6引导策略"
      :visible.sync="isAddPolicyDialogVisible"
      width="400px"
      @close="resetForm"
    >
      <el-form ref="policyForm" :model="policyForm" label-width="100px">
        <el-form-item label="路由器" prop="router" :rules="[{ required: true, message: '请选择路由器', trigger: 'blur' }]">
          <el-select v-model="policyForm.router" placeholder="选择路由器">
            <el-option label="r0" value="r0" />
            <el-option label="r3" value="r3" />
            <el-option label="r6" value="r6" />
          </el-select>
        </el-form-item>

        <el-form-item label="BSID" prop="bsid" :rules="[{ required: true, message: '请输入BSID', trigger: 'blur' }]">
          <el-input v-model="policyForm.bsid" placeholder="fe00::1a" />
        </el-form-item>

        <el-form-item label="目标IP" prop="sids" :rules="[{ required: true, message: '请输入IP', trigger: 'blur' }]">
          <el-input v-model="policyForm.ip" placeholder="10.0.10.0/24" />
        </el-form-item>
      </el-form>

      <div slot="footer" class="dialog-footer">
        <el-button @click="isAddPolicyDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitPolicy">添加路由</el-button>
      </div>
    </el-dialog>
  </div>
</template>

<script>
import axios from 'axios'
import { API_URL } from '@/config/APIconfig'

export default {
  name: 'PolicyManagement',
  data() {
    return {
      selectedRouter: 'r0', // 默认路由器
      policies: [], // 存储策略信息
      error: null,
      loading: false,
      isAddPolicyDialogVisible: false, // 控制弹窗显示
      policyForm: {
        router: '',
        bsid: '',
        ip: ''
      }
    }
  },
  mounted() {
    this.fetchPolicies()
  },
  methods: {
    // 获取策略信息
    fetchPolicies() {
      axios.get(`${API_URL}/route/show_steer?router=${this.selectedRouter}`)
        .then(response => {
          if (response.data.steer) {
            this.policies = response.data.steer
          }
        })
        .catch(error => {
          console.error('获取引导策略数据失败:', error)
        })
    },
    // 删除策略
    deletePolicy(ip) {
      axios.delete(`${API_URL}/route/del_steer?router=${this.selectedRouter}`, {
        data: {
          ip_prefix: ip
        }
      })
        .then(response => {
          if (response.data.success) {
            this.$message.success('删除成功')
            this.fetchPolicies() // 刷新策略列表
          } else {
            this.$message.error('删除失败')
          }
        })
        .catch(error => {
          console.error('删除引导失败:', error)
          this.$message.error('删除失败')
        })
    },
    // 打开添加策略弹窗
    openAddPolicyDialog() {
      this.isAddPolicyDialogVisible = true
    },
    // 提交添加策略
    submitPolicy() {
      axios.post(`${API_URL}/route/steer?router=${this.policyForm.router}`, {
        bsid: this.policyForm.bsid,
        ip_prefix: this.policyForm.ip
      })
        .then(response => {
          if (response.data.success) {
            this.$message.success('添加成功')
            this.isAddPolicyDialogVisible = false
            this.fetchPolicies() // 刷新策略列表
          } else {
            this.$message.error('添加失败')
          }
        })
        .catch(error => {
          console.error('添加引导策略失败:', error)
          this.$message.error('添加失败')
        })
    },
    // 重置表单
    resetForm() {
      this.policyForm.bsid = ''
      this.policyForm.ip = ''
      this.policyForm.router = ''
    }
  }
}
</script>

<style scoped>
.steer-management {
  font-family: Arial, sans-serif;
  padding: 20px;
}

.el-table {
  margin-top: 20px;
}

.error-message {
  color: red;
  font-weight: bold;
}

.el-button {
  margin-bottom: 20px;
}
</style>
