package com.ruoyi.common.datasource.config;

import com.ruoyi.common.datasource.interceptor.AuditInterceptor;
import org.apache.ibatis.session.SqlSessionFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * MyBatis 配置 — 注册审计拦截器
 *
 * @author ruoyi
 */
@Configuration
public class MyBatisAuditConfig {

    @Autowired
    private List<SqlSessionFactory> sqlSessionFactories;

    @Autowired
    private AuditInterceptor auditInterceptor;

    // AuditInterceptor 已通过 @Component 由 MyBatis-Plus 自动注册
    // 无需手动 addInterceptor，否则会重复拦截
}
