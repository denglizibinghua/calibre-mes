package com.bikeassembly.dto.message;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 安灯告警消息 DTO。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AndonMessageDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 告警ID */
    private Long alertId;
    /** 告警类型 */
    private String alertType;
    /** 告警等级 */
    private String level;
    /** 事件类型：ALERT / HANDLE / CLOSE */
    private String eventType;
    /** 事件时间 */
    private LocalDateTime createTime;
}
