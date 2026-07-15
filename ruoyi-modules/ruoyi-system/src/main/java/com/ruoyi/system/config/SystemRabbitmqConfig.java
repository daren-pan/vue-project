package com.ruoyi.system.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ 配置（system 消费端）
 *
 * @author ruoyi
 */
@Configuration
public class SystemRabbitmqConfig {

    public static final String EXCHANGE_AUTH = "auth.events";
    public static final String QUEUE_LOGIN = "auth.login.queue";
    public static final String ROUTING_LOGIN = "auth.login";

    /**
     * 声明交换机
     */
    @Bean
    public DirectExchange systemAuthEventExchange() {
        return new DirectExchange(EXCHANGE_AUTH, true, false);
    }

    /**
     * 登录事件队列（持久化 + 死信）
     */
    @Bean
    public Queue systemLoginEventQueue() {
        return QueueBuilder.durable(QUEUE_LOGIN)
                .deadLetterExchange(EXCHANGE_AUTH)
                .deadLetterRoutingKey(ROUTING_LOGIN + ".dlx")
                .build();
    }

    /**
     * 死信队列
     */
    @Bean
    public Queue systemLoginEventDlxQueue() {
        return QueueBuilder.durable(QUEUE_LOGIN + ".dlx").build();
    }

    /**
     * 绑定
     */
    @Bean
    public Binding systemLoginEventBinding() {
        return BindingBuilder.bind(systemLoginEventQueue())
                .to(systemAuthEventExchange())
                .with(ROUTING_LOGIN);
    }

    /**
     * 绑定死信
     */
    @Bean
    public Binding systemLoginEventDlxBinding() {
        return BindingBuilder.bind(systemLoginEventDlxQueue())
                .to(systemAuthEventExchange())
                .with(ROUTING_LOGIN + ".dlx");
    }

    /**
     * 配置 JSON 反序列化 + 手动确认 + 重试
     */
    @Bean
    public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
            ConnectionFactory connectionFactory) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(new Jackson2JsonMessageConverter());
        factory.setDefaultRequeueRejected(false);  // 失败不 requeue，进死信
        factory.setConcurrentConsumers(2);
        factory.setMaxConcurrentConsumers(5);
        factory.setPrefetchCount(50);
        return factory;
    }
    /**
     * RabbitTemplate — JSON 序列化，发送消息时使用
     */
    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(new Jackson2JsonMessageConverter());
        return template;
    }}
