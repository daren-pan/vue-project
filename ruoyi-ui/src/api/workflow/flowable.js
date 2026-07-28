import request from '@/utils/request'

/**
 * Flowable 工作流 API
 *
 * 后端服务：ruoyi-workflow（独立微服务，端口 9208）
 * 网关路由：/workflow/** → ruoyi-workflow（StripPrefix=1）
 */

// ==================== 流程定义 ====================

export function listDefinitions() {
  return request({
    url: '/workflow/definition/list',
    method: 'get'
  })
}

// 表格配置部署（前端传节点+连线JSON，后端Java DSL部署）
export function deployTable(config) {
  return request({
    url: '/workflow/definition/deploy-table',
    method: 'post',
    data: config
  })
}

// 删除指定版本的流程定义
export function deleteDefinition(deploymentId) {
  return request({
    url: '/workflow/definition/' + deploymentId,
    method: 'delete'
  })
}

// 应用某版本（克隆为最新版本）
export function applyDefinition(deploymentId) {
  return request({
    url: '/workflow/definition/' + deploymentId + '/apply',
    method: 'post'
  })
}

// 获取流程配置（用于修改，提取节点+连线）
export function getDefinitionConfig(deploymentId) {
  return request({
    url: '/workflow/definition/' + deploymentId + '/config',
    method: 'get'
  })
}

// 查询指定 key 的所有历史版本
export function getHistoryVersions(processKey) {
  return request({
    url: '/workflow/definition/history/' + processKey,
    method: 'get'
  })
}

// ==================== 流程实例 ====================

export function startLeave(data) {
  return request({
    url: '/workflow/instance/leave/start',
    method: 'post',
    params: data
  })
}

export function listRunningProcesses() {
  return request({
    url: '/workflow/instance/running',
    method: 'get'
  })
}

export function getProcessStatus(processInstanceId) {
  return request({
    url: '/workflow/instance/' + processInstanceId,
    method: 'get'
  })
}

export function getProcessTrack(processInstanceId) {
  return request({
    url: '/workflow/instance/' + processInstanceId + '/track',
    method: 'get'
  })
}

// ==================== 待办任务 ====================

export function listTodoTasks(assignee) {
  return request({
    url: '/workflow/task/todo',
    method: 'get',
    params: { assignee }
  })
}

export function approveTask(taskId, comment) {
  return request({
    url: '/workflow/task/approve',
    method: 'post',
    params: { taskId, comment }
  })
}

export function rejectTask(taskId, reason) {
  return request({
    url: '/workflow/task/reject',
    method: 'post',
    params: { taskId, reason }
  })
}

export function listHistoryTasks(assignee) {
  return request({
    url: '/workflow/task/history',
    method: 'get',
    params: { assignee }
  })
}
