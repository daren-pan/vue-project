-- ============================================================
-- RuoYi-Cloud 工作流（wf_）相关表初始化脚本
-- 数据库：ry-cloud
-- ============================================================

-- 流程定义表
DROP TABLE IF EXISTS `wf_process_definition`;
CREATE TABLE `wf_process_definition` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `process_key` varchar(100) NOT NULL COMMENT '流程标识（如 leave、expense）',
  `process_name` varchar(200) NOT NULL COMMENT '流程名称（如请假审批）',
  `version` int(11) NOT NULL DEFAULT '1' COMMENT '版本号',
  `status` char(1) DEFAULT '0' COMMENT '状态（0正常 1停用）',
  `del_flag` char(1) DEFAULT '0' COMMENT '删除标志（0代表存在 2代表删除）',
  `create_by` varchar(64) DEFAULT '' COMMENT '创建者',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_by` varchar(64) DEFAULT '' COMMENT '更新者',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_process_key_version` (`process_key`,`version`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='流程定义表';

-- 流程节点定义表
DROP TABLE IF EXISTS `wf_node_definition`;
CREATE TABLE `wf_node_definition` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `process_def_id` bigint(20) NOT NULL COMMENT '所属流程定义ID',
  `node_id` varchar(100) NOT NULL COMMENT '节点标识（如 leader_approve）',
  `node_name` varchar(200) NOT NULL COMMENT '节点名称（如组长审批）',
  `node_type` varchar(50) NOT NULL COMMENT '节点类型：start/condition/approve/end',
  `assignee_type` varchar(50) DEFAULT NULL COMMENT '审批人类型：role/user/dept_leader/self_choose/apply_self',
  `assignee_value` varchar(500) DEFAULT NULL COMMENT '审批人配置值（角色标识/用户ID/空）',
  `approve_mode` varchar(50) DEFAULT 'single' COMMENT '审批方式：single(单人)/countersign(会签)/or_sign(或签)',
  `approve_count` int(11) DEFAULT '1' COMMENT '或签时达到几票通过',
  `audit_type` varchar(50) DEFAULT 'serial' COMMENT 'serial(串审)/parallel(联审)',
  `timeout_hours` int(11) DEFAULT '0' COMMENT '超时时长（小时），0表示不超时',
  `timeout_action` varchar(50) DEFAULT 'remind' COMMENT '超时动作：remind(催办)/auto_pass(自动通过)/auto_reject(自动驳回)/auto_transfer(转交上级)',
  `actions` varchar(200) DEFAULT 'agree,reject' COMMENT '允许的操作：agree,reject,transfer,add_sign,terminate',
  `sort_order` int(11) DEFAULT '0' COMMENT '排序号',
  `create_by` varchar(64) DEFAULT '' COMMENT '创建者',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_by` varchar(64) DEFAULT '' COMMENT '更新者',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `process_def_id` (`process_def_id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='流程节点定义表';

-- 流程连线定义表
DROP TABLE IF EXISTS `wf_line_definition`;
CREATE TABLE `wf_line_definition` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `process_def_id` bigint(20) NOT NULL COMMENT '所属流程定义ID',
  `from_node_id` varchar(100) NOT NULL COMMENT '来源节点标识',
  `to_node_id` varchar(100) NOT NULL COMMENT '目标节点标识',
  `condition_expression` varchar(500) DEFAULT NULL COMMENT '条件表达式（如 days > 3），为空表示无条件',
  `sort_order` int(11) DEFAULT '0' COMMENT '排序号',
  `create_by` varchar(64) DEFAULT '' COMMENT '创建者',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_by` varchar(64) DEFAULT '' COMMENT '更新者',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `process_def_id` (`process_def_id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='流程连线定义表';

-- 流程实例表
DROP TABLE IF EXISTS `wf_process_instance`;
CREATE TABLE `wf_process_instance` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `process_def_id` bigint(20) NOT NULL COMMENT '所属流程定义ID',
  `process_def_version` int(11) NOT NULL DEFAULT '1' COMMENT '发起时的流程定义版本号',
  `business_key` varchar(200) NOT NULL COMMENT '业务主键（如 LEAVE-001）',
  `business_table` varchar(100) NOT NULL COMMENT '业务表名（如 leave_record）',
  `business_id` bigint(20) NOT NULL COMMENT '业务表主键ID',
  `applicant` varchar(100) NOT NULL COMMENT '发起人',
  `status` varchar(50) DEFAULT 'running' COMMENT '状态：running(运行中)/completed(已完成)/rejected(已驳回)/canceled(已取消)',
  `current_node_id` varchar(100) DEFAULT NULL COMMENT '当前所在节点标识',
  `start_time` datetime DEFAULT NULL COMMENT '发起时间',
  `end_time` datetime DEFAULT NULL COMMENT '结束时间',
  `create_by` varchar(64) DEFAULT '' COMMENT '创建者',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_by` varchar(64) DEFAULT '' COMMENT '更新者',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`id`),
  KEY `process_def_id` (`process_def_id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='流程实例表';

-- 任务表（待办）
DROP TABLE IF EXISTS `wf_task`;
CREATE TABLE `wf_task` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `instance_id` bigint(20) NOT NULL COMMENT '流程实例ID',
  `node_id` varchar(100) NOT NULL COMMENT '节点标识',
  `node_name` varchar(200) DEFAULT NULL COMMENT '节点名称',
  `assignee` varchar(100) NOT NULL COMMENT '待办人',
  `status` varchar(50) DEFAULT 'pending' COMMENT '状态：pending(待办)/completed(已完成)/transferred(已转办)',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `complete_time` datetime DEFAULT NULL COMMENT '完成时间',
  PRIMARY KEY (`id`),
  KEY `instance_id` (`instance_id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='任务表（待办）';

-- 审批记录表
DROP TABLE IF EXISTS `wf_audit_record`;
CREATE TABLE `wf_audit_record` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `instance_id` bigint(20) NOT NULL COMMENT '流程实例ID',
  `business_table` varchar(100) NOT NULL COMMENT '业务表名',
  `business_id` bigint(20) NOT NULL COMMENT '业务表主键ID',
  `node_id` varchar(100) NOT NULL COMMENT '节点标识',
  `node_name` varchar(200) DEFAULT NULL COMMENT '节点名称',
  `assignee` varchar(100) NOT NULL COMMENT '审批人',
  `action` varchar(50) DEFAULT NULL COMMENT '操作：submit/agree/reject/transfer/auto_pass/auto_reject',
  `comment` text COMMENT '审批意见',
  `status` varchar(50) DEFAULT 'pending' COMMENT '状态：pending(待审批)/completed(已完成)',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `complete_time` datetime DEFAULT NULL COMMENT '完成时间',
  PRIMARY KEY (`id`),
  KEY `instance_id` (`instance_id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='单据审核表（审批轨迹）';

-- 流程变量表
DROP TABLE IF EXISTS `wf_variable`;
CREATE TABLE `wf_variable` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `instance_id` bigint(20) NOT NULL COMMENT '流程实例ID',
  `var_name` varchar(100) NOT NULL COMMENT '变量名',
  `var_value` varchar(2000) DEFAULT NULL COMMENT '变量值',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `instance_id` (`instance_id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='流程变量表';
