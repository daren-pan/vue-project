<template>
  <el-form ref="form" :model="data" :rules="rules" label-width="100px" size="small" :disabled="readonly">
    <el-form-item label="报销金额" prop="amount">
      <el-input-number v-model="data.amount" :min="0" :precision="2" />
    </el-form-item>
  </el-form>
</template>

<script>
export default {
  name: "CostApply",
  props: {
    formData: { type: Object, default: () => ({}) },
    readonly: { type: Boolean, default: false }
  },
  data() {
    return {
      data: { amount: this.formData.amount || 0 },
      rules: {}
    }
  },
  watch: {
    formData: {
      immediate: true,
      handler(val) {
        if (val && val.amount != null) this.data.amount = val.amount
      }
    }
  },
  methods: {
    getData() { return { ...this.data } },
    validate(cb) { this.$refs.form.validate(cb) }
  }
}
</script>
