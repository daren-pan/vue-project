package com.ruoyi.system.service.impl;

import com.ruoyi.common.datasource.service.IAuditLogService;
import com.ruoyi.system.api.domain.SysAuditLog;
import com.ruoyi.system.mapper.SysAuditLogMapper;
import com.ruoyi.system.service.ISysAuditLogService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 数据变更审计日志 Service 实现
 *
 * @author ruoyi
 */
@Service
public class SysAuditLogServiceImpl implements ISysAuditLogService, IAuditLogService {

    @Autowired
    private SysAuditLogMapper sysAuditLogMapper;

    @Override
    public int insertAuditLog(SysAuditLog auditLog) {
        return sysAuditLogMapper.insert(auditLog);
    }

    @Override
    public List<SysAuditLog> selectAuditLogList(SysAuditLog auditLog) {
        return sysAuditLogMapper.selectList(null);
    }

    /**
     * 实现 IAuditLogService 接口，供 AuditInterceptor 调用
     */
    @Override
    public void save(String action, String sqlText, String methodName, String operator, String traceId) {
        SysAuditLog log = new SysAuditLog();
        log.setAction(action);
        log.setSqlText(sqlText);
        log.setMethodName(methodName);
        log.setOperator(operator);
        log.setTraceId(traceId);
        log.setOperateTime(LocalDateTime.now());
        sysAuditLogMapper.insert(log);
    }
}
