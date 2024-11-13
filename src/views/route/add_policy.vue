<template>
  <div class="add-policy">
    <h2>添加SRv6路由策略</h2>

    <!-- 输入表单 -->
    <form @submit.prevent="submitPolicy">
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
        <label for="bsid">绑定 SID (BSID)</label>
        <input id="bsid" v-model="bsid" type="text" required placeholder="fe00::1a">
      </div>

      <div class="form-group">
        <label for="sids">SIDs (逗号分隔)</label>
        <input id="sids" v-model="sids" type="text" required placeholder="fc00:1::a,fc00:2::a">
      </div>

      <button type="submit">添加路由</button>
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
      sids: '', // SIDs
      resultMessage: '', // 返回的消息
      result: '', // 返回的详细结果
      error: null // 错误消息
    }
  },
  methods: {
    async submitPolicy() {
      try {
        const data = {
          bsid: this.bsid,
          sids: this.sids
        }

        const response = await axios.post(`http://localhost:5060/route/add_policy?router=${this.router}`, data, {
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
.add-policy {
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
