package com.ruoyi.system.api.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.ruoyi.common.core.web.domain.BaseEntity;

import java.time.LocalDateTime;

/**
 * 数据变更审计日志 sys_audit_log
 *
 * @author ruoyi
 */
public class SysAuditLog extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 日志主键 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 操作类型（INSERT/UPDATE/DELETE） */
    private String action;

    /** 完整 SQL（含参数值） */
    private String sqlText;

    /** Mapper 方法全限定名 */
    private String methodName;

    /** 操作人 */
    private String operator;

    /** 调用链追踪ID */
    private String traceId;

    /** 操作时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime operateTime;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }

    public String getSqlText() { return sqlText; }
    public void setSqlText(String sqlText) { this.sqlText = sqlText; }

    public String getMethodName() { return methodName; }
    public void setMethodName(String methodName) { this.methodName = methodName; }

    public String getOperator() { return operator; }
    public void setOperator(String operator) { this.operator = operator; }

    public String getTraceId() { return traceId; }
    public void setTraceId(String traceId) { this.traceId = traceId; }

    public LocalDateTime getOperateTime() { return operateTime; }
    public void setOperateTime(LocalDateTime operateTime) { this.operateTime = operateTime; }
}
