<template>
  <div class="user-search-input">
    <el-autocomplete
      v-model="inputValue"
      :fetch-suggestions="querySearchAsync"
      :placeholder="placeholder"
      :debounce="300"
      :trigger-on-focus="false"
      :select-when-unmatched="false"
      value-key="label"
      clearable
      @select="handleSelect"
      @clear="handleClear"
    >
      <template slot-scope="{ item }">
        <div class="user-item">
          <span class="user-name">{{ item.label }}</span>
          <span class="user-info">{{ item.deptName }}</span>
        </div>
      </template>
    </el-autocomplete>
  </div>
</template>

<script>
import { listUser } from "@/api/system/user"

export default {
  name: "UserSearchInput",
  props: {
    // 输入框占位提示文字
    placeholder: {
      type: String,
      default: "请输入姓名搜索"
    },
    // 最小输入字符数，达到后触发搜索
    minLength: {
      type: Number,
      default: 2
    },
    // 最大返回结果数
    maxResults: {
      type: Number,
      default: 10
    },
    // v-model 绑定的值
    value: {
      type: [String, Number],
      default: ""
    }
  },
  data() {
    return {
      inputValue: this.value,
      // 缓存上次搜索关键词，避免重复请求相同内容
      lastKeyword: ""
    }
  },
  watch: {
    value(newVal) {
      this.inputValue = newVal
    }
  },
  methods: {
    /**
     * 异步搜索方法（el-autocomplete 的回调）
     * @param {string} queryString  当前输入的关键词
     * @param {Function} cb         回调函数，传入建议列表 [{ label, deptName, ... }]
     */
    querySearchAsync(queryString, cb) {
      // 输入长度不足时不搜索
      if (!queryString || queryString.length < this.minLength) {
        cb([])
        return
      }

      // 关键词没变化时不重复请求（防抖由 el-autocomplete 的 debounce 处理）
      this.lastKeyword = queryString

      this.fetchUsers(queryString).then(users => {
        // 转成 el-autocomplete 需要的格式 [{ label, deptName, userId, ... }]
        const suggestions = users.map(user => ({
          label: user.nickName,
          deptName: user.dept ? user.dept.deptName : "",
          userId: user.userId,
          userName: user.userName,
          // 原数据保留，方便外部使用
          _raw: user
        }))
        cb(suggestions.slice(0, this.maxResults))
      }).catch(() => {
        cb([])
      })
    },

    /**
     * 调用后端用户列表接口进行模糊查询
     */
    fetchUsers(keyword) {
      return listUser({
        pageNum: 1,
        pageSize: this.maxResults,
        nickName: keyword
      }).then(response => {
        return response.rows || []
      })
    },

    /**
     * 选中某一项
     */
    handleSelect(item) {
      // 触发父组件的 input 事件（实现 v-model）
      this.$emit("input", item.label)
      // 触发 select 事件，携带选中项的完整数据
      this.$emit("select", item._raw || item)
    },

    /**
     * 清空输入
     */
    handleClear() {
      this.$emit("input", "")
      this.$emit("clear")
    }
  }
}
</script>

<style scoped>
.user-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 4px 0;
}
.user-name {
  font-weight: 500;
  color: #303133;
}
.user-info {
  font-size: 12px;
  color: #909399;
  margin-left: 12px;
}
</style>
