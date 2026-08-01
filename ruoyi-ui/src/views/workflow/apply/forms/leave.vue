<template>
  <el-form ref="form" :model="data" :rules="rules" label-width="100px" size="small" :disabled="readonly">
    <el-form-item label="请假天数" prop="days">
      <el-input-number v-model="data.days" :min="1" :max="30" />
      <span style="margin-left:10px;color:#999;">>3天需总监审批</span>
    </el-form-item>
  </el-form>
</template>

<script>
export default {
  name: "LeaveApply",
  props: {
    formData: { type: Object, default: () => ({}) },
    readonly: { type: Boolean, default: false }
  },
  data() {
    return {
      data: { days: this.formData.days || 1 },
      rules: {}
    }
  },
  watch: {
    formData: {
      immediate: true,
      handler(val) {
        if (val && val.days) this.data.days = val.days
      }
    }
  },
  methods: {
    getData() { return { ...this.data } },
    validate(cb) { this.$refs.form.validate(cb) }
  }
}
</script>
