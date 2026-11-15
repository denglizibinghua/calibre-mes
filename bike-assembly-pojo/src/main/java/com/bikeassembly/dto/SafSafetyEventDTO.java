package com.bikeassembly.dto;

import com.bikeassembly.validation.OnCreate;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import java.time.LocalDateTime;

import java.io.Serializable;

@Data
public class SafSafetyEventDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 事件ID */
    private Long eventId;
    /** 父级ID */
    private Long parentId;
    /** 事件类型 */
    @NotBlank(message = "事件类型不能为空", groups = OnCreate.class)
    @Size(max = 1, message = "事件类型长度不能超过1")
    private String eventType;
    /** 事件标题 */
    @NotBlank(message = "事件标题不能为空", groups = OnCreate.class)
    private String title;
    /** 风险等级 */
    @Size(max = 1, message = "风险等级长度不能超过1")
    private String level;
    /** 责任人 */
    private String responsibleUser;
    /** 状态 */
    @Size(max = 1, message = "状态长度不能超过1")
    private String status;
    /** 发生时间 */
    private LocalDateTime eventTime;
    /** 关闭时间 */
    private LocalDateTime closeTime;
}
