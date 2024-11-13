<template>
  <div class="show-sid">
    <h2>SID信息</h2>

    <!-- 下拉选择路由器 -->
    <div class="router-selector">
      <label for="router">选择路由器:</label>
      <select v-model="selectedRouter" @change="fetchSIDInfo">
        <option value="r0">r0</option>
        <option value="r3">r3</option>
        <option value="r6">r6</option>
      </select>
    </div>

    <!-- 显示加载提示 -->
    <div v-if="loading">加载SID信息...</div>

    <!-- 列表形式展示 SID 信息 -->
    <ul v-if="!loading && sidData.length > 0" class="sid-list">
      <li v-for="(sid, index) in sidData" :key="index">
        {{ sid }}
      </li>
    </ul>

    <!-- 如果没有 SID 数据则显示消息 -->
    <div v-else-if="!loading && sidData.length === 0">
      <p>No SID information available for the selected router.</p>
    </div>

    <!-- 错误信息 -->
    <div v-if="error" class="error-message">
      <p>Error loading SID information: {{ error }}</p>
    </div>
  </div>
</template>

<script>
import axios from 'axios'

export default {
  name: 'ShowSID',
  data() {
    return {
      selectedRouter: 'r0', // 默认选择 r0
      sidData: [],
      loading: false,
      error: null
    }
  },
  mounted() {
    // 组件挂载时请求默认路由器的 SID 信息
    this.fetchSIDInfo()
  },
  methods: {
    // 请求 API 以获取 SID 信息
    fetchSIDInfo() {
      this.loading = true
      this.error = null
      axios
        .get(`http://localhost:3000/route/show_sid`, { params: { router: this.selectedRouter }})
        .then((response) => {
          if (response.data && response.data.localsids) {
            this.sidData = response.data.localsids // 保存 SID 数据
          } else {
            this.sidData = []
            this.error = 'No SID data found in the response.'
          }
        })
        .catch((error) => {
          this.error = error.message || 'An error occurred while fetching SID data.'
        })
        .finally(() => {
          this.loading = false
        })
    }
  }
}
</script>

<style scoped>
.show-sid {
  font-family: Arial, sans-serif;
  padding: 20px;
}

.router-selector {
  margin-bottom: 20px;
}

ul.sid-list {
  list-style-type: none;
  padding-left: 0;
}

ul.sid-list li {
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
