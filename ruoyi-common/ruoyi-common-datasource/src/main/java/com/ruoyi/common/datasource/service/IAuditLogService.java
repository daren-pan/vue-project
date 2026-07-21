package com.ruoyi.common.datasource.service;

/**
 * 审计日志服务接口（各模块自行实现）
 *
 * @author ruoyi
 */
public interface IAuditLogService {

    /**
     * 保存审计日志
     *
     * @param action     操作类型（INSERT/UPDATE/DELETE）
     * @param sqlText    完整 SQL（含参数值）
     * @param methodName Mapper 方法全限定名
     * @param operator   操作人
     * @param traceId    调用链追踪ID
     */
    void save(String action, String sqlText, String methodName, String operator, String traceId);
}
