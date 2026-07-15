package com.ruoyi.auth.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ 配置（auth 端）
 *
 * @author ruoyi
 */
@Configuration
public class AuthRabbitmqConfig {

    /** 登录事件交换机 */
    public static final String EXCHANGE_AUTH = "auth.events";
    /** 登录事件队列 */
    public static final String QUEUE_LOGIN = "auth.login.queue";
    /** 路由键 */
    public static final String ROUTING_LOGIN = "auth.login";

    /**
     * 声明交换机
     */
    @Bean
    public DirectExchange authEventExchange() {
        return new DirectExchange(EXCHANGE_AUTH, true, false);
    }

    /**
     * 声明登录事件队列（持久化 + 死信队列）
     */
    @Bean
    public Queue loginEventQueue() {
        return QueueBuilder.durable(QUEUE_LOGIN)
                .deadLetterExchange(EXCHANGE_AUTH)
                .deadLetterRoutingKey(ROUTING_LOGIN + ".dlx")
                .build();
    }

    /**
     * 死信队列
     */
    @Bean
    public Queue loginEventDlxQueue() {
        return QueueBuilder.durable(QUEUE_LOGIN + ".dlx").build();
    }

    /**
     * 绑定：交换机 → 主队列
     */
    @Bean
    public Binding loginEventBinding() {
        return BindingBuilder.bind(loginEventQueue())
                .to(authEventExchange())
                .with(ROUTING_LOGIN);
    }

    /**
     * 绑定：交换机 → 死信队列
     */
    @Bean
    public Binding loginEventDlxBinding() {
        return BindingBuilder.bind(loginEventDlxQueue())
                .to(authEventExchange())
                .with(ROUTING_LOGIN + ".dlx");
    }

    /**
     * RabbitTemplate — JSON 序列化
     */
    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(new Jackson2JsonMessageConverter());
        return template;
    }
}
