package com.bikeassembly.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

import java.io.Serializable;

@Data
@TableName("erp_sync_log")
public class ErpSyncLog implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 同步ID */
    @TableId(value = "sync_id", type = IdType.AUTO)
    private Long syncId;
    /** 业务类型 */
    @TableField("biz_type")
    private String bizType;
    /** 业务ID */
    @TableField("biz_id")
    private String bizId;
    /** 同步方向 */
    @TableField("sync_direction")
    private String syncDirection;
    /** 同步状态 */
    @TableField("sync_status")
    private String syncStatus;
    /** 请求数据 */
    @TableField("request_data")
    private String requestData;
    /** 响应数据 */
    @TableField("response_data")
    private String responseData;
    /** 同步时间 */
    @TableField("sync_time")
    private LocalDateTime syncTime;
}
