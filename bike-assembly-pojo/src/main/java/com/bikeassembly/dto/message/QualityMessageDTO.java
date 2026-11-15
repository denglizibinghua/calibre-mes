package com.bikeassembly.dto.message;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 质检异常消息 DTO。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QualityMessageDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 质检单ID */
    private Long inspectionId;
    /** 工单ID */
    private Long workOrderId;
    /** 检验结果：PASS / FAIL */
    private String result;
    /** 事件类型：INSPECT / DEFECT */
    private String eventType;
    /** 事件时间 */
    private LocalDateTime createTime;
}
