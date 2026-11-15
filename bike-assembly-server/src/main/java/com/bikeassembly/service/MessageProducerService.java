package com.bikeassembly.service;

import com.bikeassembly.dto.message.AndonMessageDTO;
import com.bikeassembly.dto.message.EquipmentMessageDTO;
import com.bikeassembly.dto.message.ErpSyncMessageDTO;
import com.bikeassembly.dto.message.InventoryMessageDTO;
import com.bikeassembly.dto.message.QualityMessageDTO;
import com.bikeassembly.dto.message.WorkOrderMessageDTO;

/**
 * 消息生产者服务接口。
 */
public interface MessageProducerService {

    /** 发送工单通知消息 */
    void sendWorkOrderMessage(WorkOrderMessageDTO message);

    /** 发送库存变更消息 */
    void sendInventoryMessage(InventoryMessageDTO message);

    /** 发送质检异常消息 */
    void sendQualityMessage(QualityMessageDTO message);

    /** 发送设备维修消息 */
    void sendEquipmentMessage(EquipmentMessageDTO message);

    /** 发送安灯告警消息 */
    void sendAndonMessage(AndonMessageDTO message);

    /** 发送 ERP 同步消息 */
    void sendErpSyncMessage(ErpSyncMessageDTO message);
}
