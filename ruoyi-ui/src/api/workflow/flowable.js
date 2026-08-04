import request from '@/utils/request'
import store from '@/store'

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

/**
 * 通用发起流程 —— 只需传 processKey + 业务变量，applicant 自动注入
 * @param {string} processKey 流程标识，如 "leave"、"cost"
 * @param {Object} data       业务变量，如 { days: 3, reason: "个人原因" }
 */
export function startProcess(processKey, data = {}) {
  return request({
    url: '/workflow/instance/start',
    method: 'post',
    data: { processKey, applicant: store.state.user.name, ...data }
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

/**
 * 撤回流程 —— 仅申请人可撤回尚未被审批的流程
 * @param {string} processInstanceId 流程实例 ID
 */
export function withdrawProcess(processInstanceId) {
  return request({
    url: '/workflow/instance/' + processInstanceId + '/withdraw',
    method: 'post'
  })
}

// ==================== 待办任务 ====================

/**
 * 查询待办任务（含加签任务），附带审批进度预览
 * @param {string} assignee 可选，默认当前用户；传指定用户名可查他人待办
 */
export function listTodoTasks(assignee) {
  return request({
    url: '/workflow/task/todo',
    method: 'get',
    params: { assignee: assignee || store.state.user.name }
  })
}

/**
 * 审批通过（支持并行加签）
 * @param {string} taskId  任务 ID
 * @param {string} comment 审批意见，默认 "同意"
 */
export function approveTask(taskId, comment = '同意') {
  return request({
    url: '/workflow/task/approve',
    method: 'post',
    params: { taskId, comment }
  })
}

/**
 * 审批驳回 —— 直接删除流程实例
 * @param {string} taskId 任务 ID
 * @param {string} reason 驳回原因，默认 "不同意"
 */
export function rejectTask(taskId, reason = '不同意') {
  return request({
    url: '/workflow/task/reject',
    method: 'post',
    params: { taskId, reason }
  })
}

/**
 * 驳回上一节点 —— 流程回退到上一个审批人
 * @param {string} taskId 任务 ID
 * @param {string} reason 驳回原因，默认 "需修改"
 */
export function rollbackTask(taskId, reason = '需修改') {
  return request({
    url: '/workflow/task/rollback',
    method: 'post',
    params: { taskId, reason }
  })
}

/**
 * 加签 —— 在当前任务旁新增并行审批人
 * @param {string} taskId   当前任务 ID
 * @param {string} assignee 加签人用户名
 */
export function addSign(taskId, assignee) {
  return request({
    url: '/workflow/task/addSign',
    method: 'post',
    params: { taskId, assignee }
  })
}

/**
 * 强制删除流程实例 —— 清理异常/无法通过的问题流程
 * @param {string} processInstanceId 流程实例 ID
 */
export function deleteProcessInstance(processInstanceId) {
  return request({
    url: '/workflow/task/deleteInstance',
    method: 'post',
    params: { processInstanceId }
  })
}

/**
 * 关闭抄送任务（已阅）
 * @param {string} taskId 任务 ID
 */
export function dismissTask(taskId) {
  return request({
    url: '/workflow/task/dismiss',
    method: 'post',
    params: { taskId }
  })
}

/**
 * 查询已办历史
 * @param {string} assignee 可选，默认当前用户；传指定用户名可查他人已办
 */
export function listHistoryTasks(assignee) {
  return request({
    url: '/workflow/task/history',
    method: 'get',
    params: { assignee: assignee || store.state.user.name }
  })
}
