<template>
  <el-form ref="form" :model="data" :rules="rules" label-width="100px" size="small">
    <el-form-item label="申请人" prop="applicant">
      <el-input v-model="data.applicant" placeholder="请输入申请人" />
    </el-form-item>
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
    applicant: { type: String, default: '' }
  },
  data() {
    return {
      data: { applicant: this.applicant, days: 1 },
      rules: {
        applicant: [{ required: true, message: '请输入申请人', trigger: 'blur' }]
      }
    }
  },
  watch: {
    applicant(val) { this.data.applicant = val }
  },
  methods: {
    getData() { return { ...this.data } },
    validate(cb) { this.$refs.form.validate(cb) }
  }
}
</script>
