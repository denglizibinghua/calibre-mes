package com.bikeassembly.vo;

import lombok.Data;
import java.time.LocalDateTime;

import java.io.Serializable;

@Data
public class ErpSyncLogVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 同步ID */
    private Long syncId;
    /** 业务类型 */
    private String bizType;
    /** 业务ID */
    private String bizId;
    /** 同步方向 */
    private String syncDirection;
    /** 同步状态 */
    private String syncStatus;
    /** 请求数据 */
    private String requestData;
    /** 响应数据 */
    private String responseData;
    /** 同步时间 */
    private LocalDateTime syncTime;
}
