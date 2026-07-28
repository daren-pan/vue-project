<template>
  <el-form ref="form" :model="data" :rules="rules" label-width="100px" size="small">
    <el-form-item label="申请人" prop="applicant">
      <el-input v-model="data.applicant" placeholder="请输入申请人" />
    </el-form-item>
    <el-form-item label="报销金额" prop="amount">
      <el-input-number v-model="data.amount" :min="0" :precision="2" />
    </el-form-item>
  </el-form>
</template>

<script>
export default {
  name: "CostApply",
  props: {
    applicant: { type: String, default: '' }
  },
  data() {
    return {
      data: { applicant: this.applicant, amount: 0 },
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
