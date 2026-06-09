package com.ruoyi.common.security.aspect;

import com.ruoyi.common.core.context.SecurityContextHolder;
import com.ruoyi.common.security.annotation.RecordSql;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.Arrays;

@Aspect
@Component
public class RecordSqlAspect {
    private static final Logger log = LoggerFactory.getLogger(RecordSqlAspect.class);
    private final ParameterNameDiscoverer parameterNameDiscoverer = new DefaultParameterNameDiscoverer();
    /**
     * 定义AOP签名 (切入所有使用鉴权注解的方法)
     */
    public static final String POINTCUT_SIGN = "@annotation(com.ruoyi.common.security.annotation.RecordSql)";

    /**
     * 声明AOP签名
     */
    @Pointcut(POINTCUT_SIGN)
    public void pointcut()
    {
    }

    /**
     * 环绕切入
     *
     * @param joinPoint 切面对象
     * @return 底层方法执行后的返回值
     * @throws Throwable 底层方法抛出的异常
     */
    @Around("pointcut()")
    public Object around(ProceedingJoinPoint joinPoint) throws Throwable
    {
        SecurityContextHolder.remove();
        String methodName = joinPoint.getSignature().toShortString();
        Object[] args = joinPoint.getArgs();
        String description = getAnnotationDescription(joinPoint);

        // 获取参数名和参数值
        String params = getParamsString(joinPoint, args);

        Object result;
        try {
            result = joinPoint.proceed();
            return result;
        } finally {
            String sql = SecurityContextHolder.get("sql");
            if (sql != null && !sql.isEmpty()) {
                log.info("【SQL监控】方法：{}，描述：{}，参数：{}，SQL：{}", methodName, description, params, sql);
            } else {
                log.debug("方法 {} 未捕获到 SQL", methodName);
            }
            // 清除 ThreadLocal，避免内存泄漏和线程复用干扰
            SecurityContextHolder.remove();
        }
    }
    private String getAnnotationDescription(ProceedingJoinPoint joinPoint) {
        try {
            MethodSignature signature = (MethodSignature) joinPoint.getSignature();
            RecordSql annotation = signature.getMethod().getAnnotation(RecordSql.class);
            return annotation != null ? annotation.value() : "";
        } catch (Exception e) {
            return "";
        }
    }

    private String getParamsString(ProceedingJoinPoint joinPoint, Object[] args) {
        if (args == null || args.length == 0) {
            return "";
        }
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        String[] paramNames = parameterNameDiscoverer.getParameterNames(signature.getMethod());
        if (paramNames == null) {
            // 若无法获取参数名，只输出值
            return Arrays.toString(args);
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < args.length; i++) {
            if (i > 0) sb.append(", ");
            sb.append(paramNames[i]).append("=").append(args[i]);
        }
        return sb.toString();
    }
}
