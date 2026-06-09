import request from '@/utils/request'

/**
 * 审批任务 API
 * 
 * 对应后台接口：WfTaskController
 * 用于待办列表、审批操作、审批历史查询
 */

// 查询待办任务列表
// GET /system/workflow/task/list
// 参数：assignee（用户名）
export function listTask(query) {
  return request({
    url: '/system/workflow/task/list',
    method: 'get',
    params: query
  })
}

// 审批操作（同意/驳回）
// POST /system/workflow/task/approve
// 参数：taskId（任务ID）、action（agree/reject）、comment（审批意见）
export function approveTask(data) {
  return request({
    url: '/system/workflow/task/approve',
    method: 'post',
    params: data
  })
}

// 查询审批轨迹
// GET /system/workflow/task/history
// 参数：businessTable（业务表名）、businessId（业务表主键ID）
export function getHistory(query) {
  return request({
    url: '/system/workflow/task/history',
    method: 'get',
    params: query
  })
}

// 测试推送待办（测试用）
// POST /system/workflow/task/testPushTask
export function pushTask() {
  return request({
    url: '/system/workflow/task/testPushTask',
    method: 'post'
  })
}

