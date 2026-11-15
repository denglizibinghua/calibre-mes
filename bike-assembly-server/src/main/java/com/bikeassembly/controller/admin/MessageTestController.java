package com.bikeassembly.controller.admin;

import com.bikeassembly.dto.message.AndonMessageDTO;
import com.bikeassembly.annotation.RequirePermission;
import com.bikeassembly.constant.PermissionConstant;
import com.bikeassembly.dto.message.EquipmentMessageDTO;
import com.bikeassembly.dto.message.InventoryMessageDTO;
import com.bikeassembly.dto.message.QualityMessageDTO;
import com.bikeassembly.dto.message.WorkOrderMessageDTO;
import com.bikeassembly.result.Result;
import com.bikeassembly.service.MessageProducerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 消息队列测试控制器。
 * <p>
 * 由 {@code app.rabbitmq.enabled} 控制是否加载。
 */
@RestController
@RequestMapping("/admin/message-test")
@Slf4j
@RequiredArgsConstructor
@Profile("dev")
@ConditionalOnProperty(name = "app.rabbitmq.enabled", havingValue = "true")
public class MessageTestController {

    private final MessageProducerService messageProducerService;

    @PostMapping("/work-order")
    @RequirePermission(PermissionConstant.ADMIN_MESSAGE_TEST)
    public Result<Void> testWorkOrder() {
        WorkOrderMessageDTO message = WorkOrderMessageDTO.builder()
                .workOrderId(1L)
                .orderNo("ORD-20260707-001")
                .itemId(100L)
                .planQty(new BigDecimal("10"))
                .eventType("CREATE")
                .createTime(LocalDateTime.now())
                .build();
        messageProducerService.sendWorkOrderMessage(message);
        return Result.success();
    }

    @PostMapping("/inventory")
    @RequirePermission(PermissionConstant.ADMIN_MESSAGE_TEST)
    public Result<Void> testInventory() {
        InventoryMessageDTO message = InventoryMessageDTO.builder()
                .inventoryId(1L)
                .itemId(100L)
                .locationId(10L)
                .quantity(new BigDecimal("50"))
                .eventType("IN")
                .createTime(LocalDateTime.now())
                .build();
        messageProducerService.sendInventoryMessage(message);
        return Result.success();
    }

    @PostMapping("/quality")
    @RequirePermission(PermissionConstant.ADMIN_MESSAGE_TEST)
    public Result<Void> testQuality() {
        QualityMessageDTO message = QualityMessageDTO.builder()
                .inspectionId(1L)
                .workOrderId(1L)
                .result("PASS")
                .eventType("INSPECT")
                .createTime(LocalDateTime.now())
                .build();
        messageProducerService.sendQualityMessage(message);
        return Result.success();
    }

    @PostMapping("/equipment")
    @RequirePermission(PermissionConstant.ADMIN_MESSAGE_TEST)
    public Result<Void> testEquipment() {
        EquipmentMessageDTO message = EquipmentMessageDTO.builder()
                .equipmentId(1L)
                .equipmentCode("EQ-001")
                .eventType("FAULT")
                .createTime(LocalDateTime.now())
                .build();
        messageProducerService.sendEquipmentMessage(message);
        return Result.success();
    }

    @PostMapping("/andon")
    @RequirePermission(PermissionConstant.ADMIN_MESSAGE_TEST)
    public Result<Void> testAndon() {
        AndonMessageDTO message = AndonMessageDTO.builder()
                .alertId(1L)
                .alertType("EQUIPMENT")
                .level("1")
                .eventType("ALERT")
                .createTime(LocalDateTime.now())
                .build();
        messageProducerService.sendAndonMessage(message);
        return Result.success();
    }
}
