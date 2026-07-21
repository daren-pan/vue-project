package com.ruoyi.system.service;

import com.ruoyi.system.api.domain.SysAuditLog;

import java.util.List;

/**
 * 数据变更审计日志 Service 接口
 *
 * @author ruoyi
 */
public interface ISysAuditLogService {

    /**
     * 新增审计日志
     */
    int insertAuditLog(SysAuditLog auditLog);

    /**
     * 查询审计日志列表
     */
    List<SysAuditLog> selectAuditLogList(SysAuditLog auditLog);
}
