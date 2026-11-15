package com.bikeassembly.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

import java.io.Serializable;

@Data
@TableName("saf_safety_event")
public class SafSafetyEvent implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 事件ID */
    @TableId(value = "event_id", type = IdType.AUTO)
    private Long eventId;
    /** 父级ID */
    @TableField("parent_id")
    private Long parentId;
    /** 事件类型 */
    @TableField("event_type")
    private String eventType;
    /** 事件标题 */
    @TableField("title")
    private String title;
    /** 风险等级 */
    @TableField("level")
    private String level;
    /** 责任人 */
    @TableField("responsible_user")
    private String responsibleUser;
    /** 状态 */
    @TableField("status")
    private String status;
    /** 发生时间 */
    @TableField("event_time")
    private LocalDateTime eventTime;
    /** 关闭时间 */
    @TableField("close_time")
    private LocalDateTime closeTime;
}
