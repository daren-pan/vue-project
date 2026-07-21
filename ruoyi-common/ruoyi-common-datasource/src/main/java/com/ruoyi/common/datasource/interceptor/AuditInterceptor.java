package com.ruoyi.common.datasource.interceptor;

import com.ruoyi.common.core.utils.TraceIdUtil;
import com.ruoyi.common.datasource.service.IAuditLogService;
import com.ruoyi.common.security.utils.SecurityUtils;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.ParameterMapping;
import org.apache.ibatis.mapping.SqlCommandType;
import org.apache.ibatis.plugin.*;
import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.session.Configuration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.sql.Date;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Properties;

/**
 * MyBatis 审计拦截器 — 直接写库
 * 自动拦截 INSERT / UPDATE / DELETE，拼出完整 SQL 写入 sys_audit_log
 *
 * @author ruoyi
 */
@Component
@Intercepts({
    @Signature(type = Executor.class, method = "update", args = {MappedStatement.class, Object.class})
})
public class AuditInterceptor implements Interceptor {

    private static final Logger log = LoggerFactory.getLogger(AuditInterceptor.class);

    private static final String[] AUDIT_PACKAGES = {
        "com.ruoyi.system.mapper",
        "com.ruoyi.gen.mapper",
        "com.ruoyi.job.mapper",
        "com.ruoyi.file.mapper"
    };

    @Lazy
    @Autowired(required = false)
    private IAuditLogService auditLogService;

    @Value("${audit.log.enabled:true}")
    private boolean auditEnabled;

    @Override
    public Object intercept(Invocation invocation) throws Throwable {
        Object[] args = invocation.getArgs();
        MappedStatement ms = (MappedStatement) args[0];
        Object param = args[1];

        String sqlId = ms.getId();
        SqlCommandType cmdType = ms.getSqlCommandType();
        log.info("AuditInterceptor 拦截到: sqlId={}, cmdType={}", sqlId, cmdType);

        if (!shouldAudit(sqlId)) {
            return invocation.proceed();
        }

        // 开关关闭则跳过
        if (!auditEnabled) {
            return invocation.proceed();
        }

        // 执行前拼好完整 SQL
        String completeSql = buildCompleteSql(ms, param);

        // 执行原始 SQL
        Object result = invocation.proceed();

        // 通过 Service 写审计日志
        if (completeSql != null && auditLogService != null) {
            try {
                auditLogService.save(cmdType.name(), completeSql,
                        sqlId, SecurityUtils.getUsername(), TraceIdUtil.getTraceId());
            } catch (Exception e) {
                log.error("审计日志写入失败: sqlId={}", sqlId, e);
            }
        }

        return result;
    }

    @Override
    public Object plugin(Object target) {
        return Plugin.wrap(target, this);
    }

    @Override
    public void setProperties(Properties properties) {}

    /** 判断是否在审计范围内 */
    private boolean shouldAudit(String sqlId) {
        // 排除审计日志自身 + 操作日志，防止无限循环
        if (sqlId.contains("SysAuditLogMapper") || sqlId.contains("SysOperLogMapper")) return false;
        for (String pkg : AUDIT_PACKAGES) {
            if (sqlId.startsWith(pkg)) return true;
        }
        return false;
    }

    /**
     * 拼出完整 SQL（用实际参数值替换 ?）
     */
    private String buildCompleteSql(MappedStatement ms, Object param) {
        try {
            BoundSql boundSql = ms.getBoundSql(param);
            String sql = boundSql.getSql().replaceAll("\\s+", " ").trim();
            List<ParameterMapping> mappings = boundSql.getParameterMappings();
            if (mappings == null || mappings.isEmpty()) {
                return sql;
            }

            Configuration config = ms.getConfiguration();
            MetaObject metaObject = config.newMetaObject(
                boundSql.getParameterObject() != null ? boundSql.getParameterObject() : param
            );

            StringBuilder result = new StringBuilder();
            int lastIdx = 0;

            for (ParameterMapping mapping : mappings) {
                int idx = sql.indexOf('?', lastIdx);
                if (idx < 0) break;

                result.append(sql, lastIdx, idx);

                Object value = resolveParamValue(boundSql, metaObject, mapping);
                result.append(formatSqlValue(value));

                lastIdx = idx + 1;
            }
            // 拼上剩余 SQL
            if (lastIdx < sql.length()) {
                result.append(sql.substring(lastIdx));
            }

            return result.toString();
        } catch (Exception e) {
            log.debug("构建完整SQL失败, 回退到参数化SQL: sqlId={}", ms.getId(), e);
            try {
                return ms.getBoundSql(param).getSql();
            } catch (Exception ex) {
                return null;
            }
        }
    }

    /** 解析单个参数值 */
    private Object resolveParamValue(BoundSql boundSql, MetaObject metaObject,
                                      ParameterMapping mapping) {
        String property = mapping.getProperty();
        try {
            // 1. 优先从 additionalParameters 取
            if (boundSql.hasAdditionalParameter(property)) {
                return boundSql.getAdditionalParameter(property);
            }
            // 2. MyBatis-Plus 可能把实体放在 "et" 下
            if (property.startsWith("et.") && metaObject.hasGetter("et." + property.substring(3))) {
                return metaObject.getValue("et." + property.substring(3));
            }
            // 3. 直接取值
            if (metaObject.hasGetter(property)) {
                return metaObject.getValue(property);
            }
            // 4. 兜底：参数本身就是值
            return metaObject.getOriginalObject();
        } catch (Exception e) {
            return "?";
        }
    }


    /** 参数值 → SQL 字面量 */
    private String formatSqlValue(Object value) {
        if (value == null) return "NULL";
        if (value instanceof Number) return value.toString();
        if (value instanceof Boolean) return value.toString();
        if (value instanceof Date || value instanceof LocalDateTime) {
            return "'" + value.toString() + "'";
        }
        // 字符串及其他 → 加引号并转义
        String s = value.toString().replace("'", "''");
        return "'" + s + "'";
    }
}
