import request from '@/utils/request'

/**
 * 流程定义管理 API
 * 
 * 对应后台接口：WfProcessDefinitionController
 * 用于管理所有的流程模板（请假审批、报销审批等）
 */

// 分页查询流程定义列表
// GET /system/workflow/definition/list
// 参数：processKey（流程标识）、processName（流程名称）、status（状态）
export function listDefinition(query) {
  return request({
    url: '/system/workflow/definition/list',
    method: 'get',
    params: query
  })
}

// 查询流程定义详情
// GET /system/workflow/definition/{id}
export function getDefinition(id) {
  return request({
    url: '/system/workflow/definition/' + id,
    method: 'get'
  })
}

// 新增流程定义
// POST /system/workflow/definition
export function addDefinition(data) {
  return request({
    url: '/system/workflow/definition',
    method: 'post',
    data: data
  })
}

// 修改流程定义
// PUT /system/workflow/definition
export function updateDefinition(data) {
  return request({
    url: '/system/workflow/definition',
    method: 'put',
    data: data
  })
}

// 删除流程定义
// DELETE /system/workflow/definition/{id}
export function delDefinition(id) {
  return request({
    url: '/system/workflow/definition/' + id,
    method: 'delete'
  })
}
