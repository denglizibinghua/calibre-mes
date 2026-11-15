package com.bikeassembly.config;

import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 统一 RabbitMQ 消费策略：手动确认、有限重投、并发和 prefetch。
 *
 * <p>业务消费者仍保持现有 void 方法签名；容器在正常返回时确认消息，
 * 未捕获异常时按配置重投，超过次数后交给队列的 DLX 策略。</p>
 */
@Configuration
@ConditionalOnProperty(name = "app.rabbitmq.enabled", havingValue = "true")
public class RabbitMQReliabilityConfiguration {

    @Bean
    public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
            ConnectionFactory connectionFactory, MessageConverter jsonMessageConverter) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        // 必须显式设置 JSON 转换器：自定义工厂不会自动继承 Boot 的 MessageConverter，
        // 否则消费端退回 SimpleMessageConverter，收到 byte[] 无法转成 DTO（MessageConversionException）。
        factory.setMessageConverter(jsonMessageConverter);
        factory.setConcurrentConsumers(1);
        factory.setMaxConcurrentConsumers(4);
        factory.setPrefetchCount(20);
        factory.setDefaultRequeueRejected(false);
        return factory;
    }
}
