package com.bikeassembly.config;

import com.bikeassembly.constant.MessageQueueConstant;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ 配置：声明交换机、队列、绑定关系，配置 JSON 消息转换器。
 * <p>
 * 由 {@code app.rabbitmq.enabled} 控制是否加载。
 * 设为 {@code false} 时，整个 MQ 基础设施不会被创建。
 */
@Configuration
@ConditionalOnProperty(name = "app.rabbitmq.enabled", havingValue = "true")
public class RabbitMQConfiguration {

    // ==================== 交换机 ====================

    @Bean
    public DirectExchange bikeMesExchange() {
        return new DirectExchange(MessageQueueConstant.EXCHANGE, true, false);
    }

    // ==================== 队列 ====================

    /** 工单通知队列 */
    @Bean
    public Queue workOrderQueue() {
        return new Queue(MessageQueueConstant.WORK_ORDER_QUEUE, true);
    }

    /** 库存变更队列 */
    @Bean
    public Queue inventoryQueue() {
        return new Queue(MessageQueueConstant.INVENTORY_QUEUE, true);
    }

    /** 质检异常队列 */
    @Bean
    public Queue qualityQueue() {
        return new Queue(MessageQueueConstant.QUALITY_QUEUE, true);
    }

    /** 设备维修队列 */
    @Bean
    public Queue equipmentQueue() {
        return new Queue(MessageQueueConstant.EQUIPMENT_QUEUE, true);
    }

    /** 安灯告警队列 */
    @Bean
    public Queue andonQueue() {
        return new Queue(MessageQueueConstant.ANDON_QUEUE, true);
    }

    /** ERP 同步队列 */
    @Bean
    public Queue erpQueue() {
        return new Queue(MessageQueueConstant.ERP_QUEUE, true);
    }

    // ==================== 绑定关系 ====================

    @Bean
    public Binding workOrderBinding() {
        return BindingBuilder.bind(workOrderQueue())
                .to(bikeMesExchange())
                .with(MessageQueueConstant.WORK_ORDER_KEY);
    }

    @Bean
    public Binding inventoryBinding() {
        return BindingBuilder.bind(inventoryQueue())
                .to(bikeMesExchange())
                .with(MessageQueueConstant.INVENTORY_KEY);
    }

    @Bean
    public Binding qualityBinding() {
        return BindingBuilder.bind(qualityQueue())
                .to(bikeMesExchange())
                .with(MessageQueueConstant.QUALITY_KEY);
    }

    @Bean
    public Binding equipmentBinding() {
        return BindingBuilder.bind(equipmentQueue())
                .to(bikeMesExchange())
                .with(MessageQueueConstant.EQUIPMENT_KEY);
    }

    @Bean
    public Binding andonBinding() {
        return BindingBuilder.bind(andonQueue())
                .to(bikeMesExchange())
                .with(MessageQueueConstant.ANDON_KEY);
    }

    @Bean
    public Binding erpBinding() {
        return BindingBuilder.bind(erpQueue())
                .to(bikeMesExchange())
                .with(MessageQueueConstant.ERP_KEY);
    }

    // ==================== 消息转换器 ====================

    /**
     * 使用 Jackson JSON 序列化/反序列化消息体。
     */
    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}
