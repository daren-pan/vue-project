package com.ruoyi.workflow;

import com.ruoyi.common.security.annotation.EnableCustomConfig;
import com.ruoyi.common.security.annotation.EnableRyFeignClients;
import com.alibaba.druid.spring.boot3.autoconfigure.DruidDataSourceAutoConfigure;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;

/**
 * 工作流模块（Flowable）
 * <p>
 * 排除 DataSource 自动配置，因为本模块只使用 Flowable 独立数据源，
 * 不连接业务数据库（ry-cloud）。
 *
 * @author ruoyi
 */
@EnableCustomConfig
@EnableRyFeignClients
@SpringBootApplication(exclude = {
    DataSourceAutoConfiguration.class,
    DruidDataSourceAutoConfigure.class
})
public class RuoYiWorkflowApplication {

    public static void main(String[] args) {
        SpringApplication.run(RuoYiWorkflowApplication.class, args);
        System.out.println("""
                (♥◠‿◠)ﾉﾞ  工作流模块启动成功   ლ(´ڡ`ლ)ﾞ
                 .-------.       ____     __
                 |  _ _   \\      \\   \\   /  /
                 | ( ' )  |       \\  _. /  '
                 |(_ o _) /        _( )_ .'
                 | (_,_).' __  ___(_ o _)'
                 |  |\\ \\  |  ||   |(_,_)'
                 |  | \\ `'   /|   `-'  /
                 |  |  \\    /  \\      /
                 ''-'   `'-'    `-..-'
                """);
    }
}
