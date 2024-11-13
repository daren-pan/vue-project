<template>
  <div class="show-policy">
    <h2>SRv6路径: {{ selectedRouter }}</h2>

    <!-- 路由器选择器 -->
    <div class="router-selector">
      <label for="router">选择路由器:</label>
      <select v-model="selectedRouter" @change="fetchPolicyInfo">
        <option value="r0">r0</option>
        <option value="r3">r3</option>
        <option value="r6">r6</option>
      </select>
    </div>

    <!-- 显示加载状态 -->
    <div v-if="loading">加载路径信息...</div>

    <!-- 列表形式展示策略信息 -->
    <ul v-if="!loading && policyData.length > 0" class="policy-list">
      <li v-for="(policy, index) in policyData" :key="index">
        {{ policy }}
      </li>
    </ul>

    <!-- 如果没有策略信息 -->
    <div v-else-if="!loading && policyData.length === 0">
      <p>No policy information available for the selected router.</p>
    </div>

    <!-- 错误信息 -->
    <div v-if="error" class="error-message">
      <p>Error loading policy information: {{ error }}</p>
    </div>
  </div>
</template>

<script>
import axios from 'axios'

export default {
  name: 'ShowPolicy',
  data() {
    return {
      selectedRouter: 'r0', // 默认选择 r0
      policyData: [],
      loading: false,
      error: null
    }
  },
  mounted() {
    // 组件挂载时请求默认路由器的策略信息
    this.fetchPolicyInfo()
  },
  methods: {
    // 请求 API 以获取策略信息
    fetchPolicyInfo() {
      this.loading = true
      this.error = null
      axios
        .get(`http://localhost:5060/route/show_policy`, { params: { router: this.selectedRouter }})
        .then((response) => {
          if (response.data && response.data.policies) {
            this.policyData = response.data.policies // 保存策略数据
          } else {
            this.policyData = []
            this.error = 'No policy data found in the response.'
          }
        })
        .catch((error) => {
          this.error = error.message || 'An error occurred while fetching policy data.'
        })
        .finally(() => {
          this.loading = false
        })
    }
  }
}
</script>

<style scoped>
.show-policy {
  font-family: Arial, sans-serif;
  padding: 20px;
}

.router-selector {
  margin-bottom: 20px;
}

ul.policy-list {
  list-style-type: none;
  padding-left: 0;
}

ul.policy-list li {
  background-color: #ffffff;
  margin-bottom: 5px;
  padding: 5px;
  border: 1px solid #ffffff;
}

.error-message {
  color: red;
  font-weight: bold;
}
</style>
