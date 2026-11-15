package com.bikeassembly.dto;

import com.bikeassembly.validation.OnCreate;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import java.time.LocalDateTime;

import java.io.Serializable;

@Data
public class ErpSyncLogDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 同步ID */
    private Long syncId;
    /** 业务类型 */
    @NotBlank(message = "业务类型不能为空", groups = OnCreate.class)
    private String bizType;
    /** 业务ID */
    private String bizId;
    /** 同步方向 */
    @Size(max = 1, message = "同步方向长度不能超过1")
    private String syncDirection;
    /** 同步状态 */
    @Size(max = 1, message = "同步状态长度不能超过1")
    private String syncStatus;
    /** 请求数据 */
    private String requestData;
    /** 响应数据 */
    private String responseData;
    /** 同步时间 */
    private LocalDateTime syncTime;
}
