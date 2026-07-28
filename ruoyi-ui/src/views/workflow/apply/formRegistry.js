/**
 * 流程表单注册表
 * 新增流程表单时只需在此文件添加一行
 */
import LeaveApply from './forms/leave.vue'
import CostApply from './forms/cost.vue'

// processKey → { component, name }
const registry = {
  'leave': { component: LeaveApply, name: 'LeaveApply' },
  'cost':  { component: CostApply,  name: 'CostApply' }
}

export default registry
