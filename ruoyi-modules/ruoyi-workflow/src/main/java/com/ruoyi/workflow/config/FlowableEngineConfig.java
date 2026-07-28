package com.ruoyi.workflow.config;

import com.alibaba.druid.pool.DruidDataSource;
import org.flowable.spring.SpringProcessEngineConfiguration;
import org.flowable.spring.boot.EngineConfigurationConfigurer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;

/**
 * Flowable 引擎配置
 * <p>
 * 独立数据源（ry-flowable 库），不污染主数据源
 *
 * @author ruoyi
 */
@Configuration
public class FlowableEngineConfig {

    @Value("${flowable.datasource.url}")
    private String url;

    @Value("${flowable.datasource.username}")
    private String username;

    @Value("${flowable.datasource.password}")
    private String password;

    @Value("${flowable.datasource.driver-class-name}")
    private String driverClassName;

    /**
     * Flowable 专用数据源（注册为 Spring Bean，供 Flowable 各组件自动注入）
     */
    @Bean
    public DataSource flowableDataSource() {
        DruidDataSource ds = new DruidDataSource();
        ds.setUrl(url);
        ds.setUsername(username);
        ds.setPassword(password);
        ds.setDriverClassName(driverClassName);
        return ds;
    }

    @Bean
    public EngineConfigurationConfigurer<SpringProcessEngineConfiguration> flowableConfigurer() {
        return config -> {
            config.setDataSource(flowableDataSource());
            config.setDatabaseSchemaUpdate("true");
            config.setDatabaseType("mysql");
        };
    }
}
