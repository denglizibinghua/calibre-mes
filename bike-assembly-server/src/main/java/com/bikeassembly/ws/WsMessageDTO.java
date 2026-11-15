package com.bikeassembly.ws;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * WebSocket 推送消息体（与前端 JSON 契约）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WsMessageDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 业务类型：ANDON / WORK_ORDER / QUALITY / REPAIR / INVENTORY */
    private String bizType;
    /** 事件类型：CREATE / ACCEPT / CLOSE / ESCALATE / RELEASE / CONFIRM / COMPLETE 等 */
    private String eventType;
    /** 事件时间 */
    private LocalDateTime timestamp;
    /** 业务数据（变更实体关键字段，前端据此刷新） */
    private Map<String, Object> data;

    public static WsMessageDTO of(String bizType, String eventType, Map<String, Object> data) {
        return WsMessageDTO.builder()
                .bizType(bizType)
                .eventType(eventType)
                .timestamp(LocalDateTime.now())
                .data(data)
                .build();
    }

    // ==================== 业务类型 ====================

    public static final String BIZ_ANDON = "ANDON";
    public static final String BIZ_WORK_ORDER = "WORK_ORDER";
    public static final String BIZ_QUALITY = "QUALITY";
    public static final String BIZ_REPAIR = "REPAIR";
    public static final String BIZ_INVENTORY = "INVENTORY";
    public static final String BIZ_AI = "AI";

    // ==================== 事件类型 ====================

    public static final String EVENT_CREATE = "CREATE";
    public static final String EVENT_ACCEPT = "ACCEPT";
    public static final String EVENT_CLOSE = "CLOSE";
    public static final String EVENT_ESCALATE = "ESCALATE";
    public static final String EVENT_RELEASE = "RELEASE";
    public static final String EVENT_START = "START";
    public static final String EVENT_REPORT = "REPORT";
    public static final String EVENT_CONFIRM = "CONFIRM";
    public static final String EVENT_COMPLETE = "COMPLETE";
    public static final String EVENT_EXECUTE = "EXECUTE";
    public static final String EVENT_AI_REQUEST_ACCEPTED = "AI_REQUEST_ACCEPTED";
    public static final String EVENT_AI_DELTA = "AI_DELTA";
    public static final String EVENT_AI_COMPLETED = "AI_COMPLETED";
    public static final String EVENT_AI_ERROR = "AI_ERROR";
    public static final String EVENT_AI_PLAN_READY = "AI_PLAN_READY";
}
