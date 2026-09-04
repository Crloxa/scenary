package com.scenary.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MQ 拓扑契约：单一 topic exchange `media.event`，routing key 以 <域>.<动作> 命名，
 * 只增 binding 不复用队列（docs/01 §8）。
 *
 * 工作链路：media.uploaded -> media.thumbnail.q；video.transcode -> video.transcode.q（均带 DLX）
 * 兜底链路：消费最终失败先落状态，再 nack(requeue=false) -> media.dead exchange -> 各自 DLQ 留档人工重放
 */
@Configuration
public class RabbitConfig {

    public static final String EXCHANGE_MEDIA_EVENT = "media.event";
    public static final String RK_MEDIA_UPLOADED = "media.uploaded";
    public static final String RK_VIDEO_TRANSCODE = "video.transcode";

    public static final String QUEUE_MEDIA_THUMBNAIL = "media.thumbnail.q";
    public static final String QUEUE_VIDEO_TRANSCODE = "video.transcode.q";
    public static final String EXCHANGE_MEDIA_DEAD = "media.dead";
    public static final String QUEUE_MEDIA_DLQ = "media.dlq";
    public static final String QUEUE_VIDEO_DLQ = "video.dlq";

    @Bean
    public TopicExchange mediaEventExchange() {
        return new TopicExchange(EXCHANGE_MEDIA_EVENT, true, false);
    }

    @Bean
    public TopicExchange mediaDeadExchange() {
        return new TopicExchange(EXCHANGE_MEDIA_DEAD, true, false);
    }

    /** x-dead-letter-* 参数在队列首次声明后不可变，改参数需换新队列名 */
    @Bean
    public Queue mediaThumbnailQueue() {
        return QueueBuilder.durable(QUEUE_MEDIA_THUMBNAIL)
                .deadLetterExchange(EXCHANGE_MEDIA_DEAD)
                .deadLetterRoutingKey(RK_MEDIA_UPLOADED)
                .build();
    }

    @Bean
    public Queue mediaDlq() {
        return QueueBuilder.durable(QUEUE_MEDIA_DLQ).build();
    }

    @Bean
    public Queue videoTranscodeQueue() {
        return QueueBuilder.durable(QUEUE_VIDEO_TRANSCODE)
                .deadLetterExchange(EXCHANGE_MEDIA_DEAD)
                .deadLetterRoutingKey(RK_VIDEO_TRANSCODE)
                .build();
    }

    @Bean
    public Queue videoDlq() {
        return QueueBuilder.durable(QUEUE_VIDEO_DLQ).build();
    }

    @Bean
    public Binding mediaThumbnailBinding(TopicExchange mediaEventExchange,
                                         Queue mediaThumbnailQueue) {
        return BindingBuilder.bind(mediaThumbnailQueue)
                .to(mediaEventExchange).with(RK_MEDIA_UPLOADED);
    }

    @Bean
    public Binding mediaDlqBinding(TopicExchange mediaDeadExchange, Queue mediaDlq) {
        return BindingBuilder.bind(mediaDlq)
                .to(mediaDeadExchange).with(RK_MEDIA_UPLOADED);
    }

    @Bean
    public Binding videoTranscodeBinding(TopicExchange mediaEventExchange,
                                         Queue videoTranscodeQueue) {
        return BindingBuilder.bind(videoTranscodeQueue)
                .to(mediaEventExchange).with(RK_VIDEO_TRANSCODE);
    }

    @Bean
    public Binding videoDlqBinding(TopicExchange mediaDeadExchange, Queue videoDlq) {
        return BindingBuilder.bind(videoDlq)
                .to(mediaDeadExchange).with(RK_VIDEO_TRANSCODE);
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(new Jackson2JsonMessageConverter());
        return template;
    }
}
