package com.bikeassembly.dto.message;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * ERP 同步消息 DTO。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ErpSyncMessageDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 业务类型：WORK_ORDER / INVENTORY */
    private String bizType;
    /** 业务ID（工单ID / 库存单据ID） */
    private String bizId;
    /** 同步方向：O=出站（MES→ERP） */
    private String direction;
    /** 事件类型：SYNC */
    private String eventType;
    /** 事件时间 */
    private LocalDateTime createTime;
}
