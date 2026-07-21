package com.ruoyi.common.core.utils;

import org.slf4j.MDC;
import java.util.UUID;

/**
 * 调用链追踪ID工具
 *
 * @author ruoyi
 */
public class TraceIdUtil {

    private static final String[] TRACE_KEYS = { "tid", "traceId", "trace_id" };

    /**
     * 获取当前线程的追踪ID（优先 SkyWalking → Micrometer → OpenTelemetry → MDC中的自定义ID → UUID）
     */
    public static String getTraceId() {
        for (String key : TRACE_KEYS) {
            String val = MDC.get(key);
            if (val != null && !val.isEmpty()) return val;
        }
        // 兜底：如果 MDC 里完全没有，生成一个放入 MDC 供本次请求后续使用
        String tid = generateTraceId();
        MDC.put("tid", tid);
        return tid;
    }

    /**
     * 生成一个纯随机 traceId
     */
    public static String generateTraceId() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    /**
     * 设置追踪ID到 MDC（Gateway/Filter 等入口处调用）
     */
    public static void setTraceId(String traceId) {
        if (traceId != null && !traceId.isEmpty()) {
            MDC.put("tid", traceId);
        }
    }

    /**
     * 清除 MDC 中的 traceId（请求结束时调用）
     */
    public static void clearTraceId() {
        MDC.remove("tid");
        MDC.remove("traceId");
        MDC.remove("trace_id");
    }
}
