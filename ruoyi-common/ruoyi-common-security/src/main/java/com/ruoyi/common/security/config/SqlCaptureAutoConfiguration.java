package com.ruoyi.common.security.config;

import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.ruoyi.common.security.aspect.RecordSqlAspect;
import com.ruoyi.common.security.interceptor.SqlCaptureInterceptor;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.*;

@Configuration
@ComponentScan("com.ruoyi.common.security")
@EnableAspectJAutoProxy(proxyTargetClass = true)
public class SqlCaptureAutoConfiguration {

}
