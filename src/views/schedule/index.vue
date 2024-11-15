<template>
  <div class="schedule-decision">
    <h2>调度决策</h2>

    <!-- 表格展示平均资源和评分 -->
    <table class="decision-table">
      <thead>
        <tr>
          <th>集群</th>
          <th>平均空闲CPU (%)</th>
          <th>平均空闲内存 (Mb)</th>
          <th>平均空闲存储 (Mb)</th>
          <th>分数</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="(resource, index) in averageResources" :key="index" :class="{ best: clusters[index] === bestCluster }">
          <td>{{ clusters[index] }}</td>
          <td>{{ resource[0].toFixed(2) }}</td>
          <td>{{ resource[1].toFixed(0).toLocaleString() }}</td>
          <td>{{ resource[2].toFixed(0).toLocaleString() }}</td>
          <td>{{ scores[index].toFixed(2) }}</td>
        </tr>
      </tbody>
    </table>

    <!-- 最佳集群展示 -->
    <div class="best-cluster">
      <h3>最佳集群: {{ bestCluster }}</h3>
    </div>

    <!-- 错误消息 -->
    <div v-if="error" class="error-message">
      <p>Error loading schedule data: {{ error }}</p>
    </div>
  </div>
</template>

<script>
import { API_URL } from '@/config/APIconfig'
import axios from 'axios'

export default {
  name: 'ScheduleDecision',
  data() {
    return {
      clusters: ['hosta', 'hostb'],
      averageResources: [],
      scores: [],
      bestCluster: '',
      error: null
    }
  },
  mounted() {
    this.fetchScheduleDecision()
  },
  methods: {
    fetchScheduleDecision() {
      axios
        .get(API_URL + '/schedule')
        .then((response) => {
          const data = response.data
          this.averageResources = data.average_resource || []
          this.scores = data.scores || []
          this.bestCluster = data.best_cluster || ''
        })
        .catch((error) => {
          this.error = error.message || 'An error occurred while fetching schedule data.'
        })
    }
  }
}
</script>

<style scoped>
.schedule-decision {
  font-family: Arial, sans-serif;
  padding: 20px;
}

table.decision-table {
  width: 100%;
  border-collapse: collapse;
  margin-bottom: 20px;
}

table.decision-table th, table.decision-table td {
  padding: 10px;
  border: 1px solid #ddd;
  text-align: center;
}

table.decision-table tr.best {
  background-color: #e0f7fa;
}

.best-cluster h3 {
  color: #00796b;
}

.error-message {
  color: red;
  font-weight: bold;
}
</style>
