<template>
  <div class="steer-policy">
    <h2>更新引导策略</h2>

    <!-- 输入表单 -->
    <form @submit.prevent="submitSteerPolicy">
      <div class="form-group">
        <label for="router">路由器</label>
        <select id="router" v-model="router" required>
          <option disabled value="">选择路由器</option>
          <option value="r0">路由器r0</option>
          <option value="r3">路由器r3</option>
          <option value="r6">路由器r6</option>
        </select>
      </div>

      <div class="form-group">
        <label for="bsid">绑定SID (BSID)</label>
        <input id="bsid" v-model="bsid" type="text" required placeholder="fe00::1a">
      </div>

      <div class="form-group">
        <label for="ip_prefix">目标IP</label>
        <input id="ip_prefix" v-model="ip_prefix" type="text" required placeholder="10.10.0.0/24">
      </div>

      <button type="submit">添加引导策略</button>
    </form>

    <!-- 显示结果 -->
    <div v-if="resultMessage" class="result-message">
      <p><strong>{{ resultMessage }}</strong></p>
      <pre>{{ result }}</pre>
    </div>

    <!-- 错误消息 -->
    <div v-if="error" class="error-message">
      <p>Error: {{ error }}</p>
    </div>
  </div>
</template>

<script>
import axios from 'axios'

export default {
  data() {
    return {
      router: '', // 路由器选择
      bsid: '', // BSID
      ip_prefix: '', // IP Prefix
      resultMessage: '', // 返回的消息
      result: '', // 返回的详细结果
      error: null // 错误消息
    }
  },
  methods: {
    async submitSteerPolicy() {
      try {
        const data = {
          bsid: this.bsid,
          ip_prefix: this.ip_prefix
        }

        const response = await axios.post(`http://localhost:3000/route/steer?router=${this.router}`, data, {
          headers: { 'Content-Type': 'application/json' }
        })

        this.resultMessage = response.data.message
        this.result = response.data.result.join('\n') // 格式化结果信息
        this.error = null
      } catch (error) {
        this.error = error.message
        this.resultMessage = ''
        this.result = ''
      }
    }
  }
}
</script>

<style scoped>
.steer-policy {
  max-width: 600px;
  margin: 0 auto;
  padding: 20px;
  font-family: Arial, sans-serif;
}

.form-group {
  margin-bottom: 15px;
}

.form-group label {
  display: block;
  margin-bottom: 5px;
}

.form-group input, .form-group select {
  width: 100%;
  padding: 8px;
  margin-bottom: 5px;
  border: 1px solid #ccc;
  border-radius: 4px;
}

button {
  padding: 10px 20px;
  background-color: #007bff;
  color: white;
  border: none;
  border-radius: 4px;
  cursor: pointer;
}

button:hover {
  background-color: #0056b3;
}

.result-message, .error-message {
  margin-top: 20px;
  padding: 10px;
  border-radius: 4px;
}

.result-message {
  background-color: #e7f4e4;
  color: #3c763d;
}

.error-message {
  background-color: #f8d7da;
  color: #721c24;
}
</style>
