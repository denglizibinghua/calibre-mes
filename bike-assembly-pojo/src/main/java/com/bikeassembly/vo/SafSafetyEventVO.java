package com.bikeassembly.vo;

import lombok.Data;
import java.time.LocalDateTime;

import java.io.Serializable;

@Data
public class SafSafetyEventVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 事件ID */
    private Long eventId;
    /** 父级ID */
    private Long parentId;
    /** 事件类型 */
    private String eventType;
    /** 事件标题 */
    private String title;
    /** 风险等级 */
    private String level;
    /** 责任人 */
    private String responsibleUser;
    /** 状态 */
    private String status;
    /** 发生时间 */
    private LocalDateTime eventTime;
    /** 关闭时间 */
    private LocalDateTime closeTime;
}
