package com.ruoyi.workflow.config;

import com.alibaba.druid.pool.DruidDataSource;
import com.ruoyi.workflow.core.ProcessBuilder;
import org.flowable.engine.RepositoryService;
import org.flowable.spring.SpringProcessEngineConfiguration;
import org.flowable.spring.boot.EngineConfigurationConfigurer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
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

    /**
     * 启动时自动部署流程（纯 Java 代码定义，不需要 BPMN XML 文件）
     */
    @Bean
    public ApplicationRunner deployProcesses(RepositoryService repositoryService) {
        return args -> {
            // 请假审批流程
            ProcessBuilder.create("leave", "请假审批")
                    .startEvent("start", "开始")
                    .userTask("managerApprove", "部门经理审批", "${manager}")
                    .gateway("gateway1", "判断天数")
                        .condition("days <= 3", "≤3天")
                        .endEvent("end", "结束")
                        .condition("days > 3", ">3天")
                        .userTask("directorApprove", "总监审批", "${director}")
                        .endEvent("end2", "结束")
                    .done()
                    .deploy(repositoryService);
            System.out.println("✅ 请假审批流程已部署（Java DSL）");
        };
    }
}
