<template>
  <div class="del-policy">
    <h2>删除 SRv6 策略</h2>

    <!-- 表单 -->
    <form @submit.prevent="submitDelPolicy">
      <div class="form-group">
        <label for="router">选择路由器</label>
        <select id="router" v-model="router" required>
          <option disabled value="">选择路由器</option>
          <option value="r0">路由器r0</option>
          <option value="r3">路由器r3</option>
          <option value="r6">路由器r6</option>
        </select>
      </div>

      <div class="form-group">
        <label for="bsid">BSID</label>
        <input
          id="bsid"
          v-model="bsid"
          type="text"
          required
          placeholder="请输入 Binding SID，如 fe00:1::1"
        >
      </div>

      <button type="submit">删除策略</button>
    </form>

    <!-- 显示结果 -->
    <div v-if="resultMessage" class="result-message">
      <p><strong>{{ resultMessage }}</strong></p>
      <pre>{{ result }}</pre>
    </div>

    <!-- 错误消息 -->
    <div v-if="error" class="error-message">
      <p>错误: {{ error }}</p>
    </div>
  </div>
</template>

<script>
import axios from 'axios'
import { API_URL } from '@/config/APIconfig'
export default {
  data() {
    return {
      router: '', // 选择的路由器
      bsid: '', // Binding SID
      resultMessage: '', // 返回的消息
      result: '', // 返回的详细结果
      error: null // 错误消息
    }
  },
  methods: {
    async submitDelPolicy() {
      try {
        // 删除请求需要的 body 数据
        const data = {
          bsid: this.bsid
        }

        // 发送 DELETE 请求
        const response = await axios.delete(
          API_URL + `/route/del_policy?router=${this.router}`,
          {
            headers: { 'Content-Type': 'application/json' },
            data
          }
        )

        // 处理返回结果
        this.resultMessage = response.data.message
        this.result = response.data.result.join('\n') // 格式化返回结果
        this.error = null
      } catch (error) {
        // 处理错误
        this.error = error.message
        this.resultMessage = ''
        this.result = ''
      }
    }
  }
}
</script>

<style scoped>
.del-policy {
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

.form-group input,
.form-group select {
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

.result-message,
.error-message {
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
