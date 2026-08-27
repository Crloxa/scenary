package com.scenary.config;

import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MQ 拓扑契约：单一 topic exchange `media.event`，routing key 以 <域>.<动作> 命名，
 * 只增 binding 不复用队列（docs/01 §8）。本步仅声明交换机与 JSON 序列化模板；
 * 缩略图队列、DLX 绑定与消费者在 3.7 落地。
 */
@Configuration
public class RabbitConfig {

    public static final String EXCHANGE_MEDIA_EVENT = "media.event";
    public static final String RK_MEDIA_UPLOADED = "media.uploaded";

    @Bean
    public TopicExchange mediaEventExchange() {
        return new TopicExchange(EXCHANGE_MEDIA_EVENT, true, false);
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(new Jackson2JsonMessageConverter());
        return template;
    }
}
