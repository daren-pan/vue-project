<template>
  <div class="show-paths">
    <h2>最短路径信息</h2>
    <!-- 检查是否在加载状态 -->
    <div v-if="loading">加载中...</div>

    <!-- 如果有路径数据则显示 -->
    <div v-else>
      <ul v-if="paths.length > 0">
        <li v-for="(path, index) in paths" :key="index">{{ path }}</li>
      </ul>

      <!-- 如果没有路径数据则显示消息 -->
      <div v-else>
        <p>未找到</p>
      </div>
    </div>

    <!-- 错误消息 -->
    <div v-if="error" class="error-message">
      <p>加载路径错误: {{ error }}</p>
    </div>
  </div>
</template>

<script>
import axios from 'axios'

export default {
  name: 'ShowPaths',
  data() {
    return {
      paths: [],
      loading: false,
      error: null
    }
  },
  mounted() {
    // 组件挂载时请求路径数据
    this.fetchPaths()
  },
  methods: {
    // 请求 API 以获取路径信息
    fetchPaths() {
      this.loading = true
      this.error = null
      axios
        .get('http://localhost:5060/route/show_paths')
        .then((response) => {
          // 检查响应中是否有 paths 数据
          if (response.data && response.data.paths) {
            this.paths = response.data.paths
          } else {
            this.error = 'No paths data found in the response.'
          }
        })
        .catch((error) => {
          this.error = error.message || 'An error occurred while fetching paths.'
        })
        .finally(() => {
          this.loading = false
        })
    }
  }
}
</script>

<style scoped>
.show-paths {
  font-family: Arial, sans-serif;
  padding: 20px;
}

ul {
  list-style-type: none;
  padding: 0;
}

li {
  margin-bottom: 10px;
}

.error-message {
  color: red;
  font-weight: bold;
}
</style>
