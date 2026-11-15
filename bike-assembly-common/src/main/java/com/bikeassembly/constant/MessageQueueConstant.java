package com.bikeassembly.constant;

/**
 * 消息队列常量：交换机、队列、路由键。
 */
public class MessageQueueConstant {

    // ========== 交换机 ==========
    public static final String EXCHANGE = "bike.mes.exchange";

    // ========== 队列 ==========
    /** 工单通知队列 */
    public static final String WORK_ORDER_QUEUE = "bike.mes.work.order.queue";
    /** 库存变更队列 */
    public static final String INVENTORY_QUEUE = "bike.mes.inventory.queue";
    /** 质检异常队列 */
    public static final String QUALITY_QUEUE = "bike.mes.quality.queue";
    /** 设备维修队列 */
    public static final String EQUIPMENT_QUEUE = "bike.mes.equipment.queue";
    /** 安灯告警队列 */
    public static final String ANDON_QUEUE = "bike.mes.andon.queue";
    /** ERP 同步队列 */
    public static final String ERP_QUEUE = "bike.mes.erp.queue";

    // ========== RoutingKey ==========
    public static final String WORK_ORDER_KEY = "bike.mes.work.order";
    public static final String INVENTORY_KEY = "bike.mes.inventory";
    public static final String QUALITY_KEY = "bike.mes.quality";
    public static final String EQUIPMENT_KEY = "bike.mes.equipment";
    public static final String ANDON_KEY = "bike.mes.andon";
    public static final String ERP_KEY = "bike.mes.erp.sync";

    private MessageQueueConstant() {
    }
}
