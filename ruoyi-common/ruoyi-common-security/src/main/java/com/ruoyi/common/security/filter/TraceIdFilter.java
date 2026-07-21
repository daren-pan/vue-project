package com.ruoyi.common.security.filter;

import com.ruoyi.common.core.utils.TraceIdUtil;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * 请求入口 TraceId 过滤器
 * 在没有 SkyWalking 时，为每个请求生成统一的 trace_id 放入 MDC
 *
 * @author ruoyi
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TraceIdFilter implements Filter {

    @Override
    public void doFilter(jakarta.servlet.ServletRequest request, jakarta.servlet.ServletResponse response,
                         FilterChain chain) throws IOException, ServletException {
        // 从请求头获取已有 traceId（网关透传的），没有则生成
        HttpServletRequest httpReq = (HttpServletRequest) request;
        String traceId = httpReq.getHeader("X-Trace-Id");
        if (traceId == null || traceId.isEmpty()) {
            traceId = TraceIdUtil.generateTraceId();
        }
        TraceIdUtil.setTraceId(traceId);
        try {
            chain.doFilter(request, response);
        } finally {
            TraceIdUtil.clearTraceId();
        }
    }
}
