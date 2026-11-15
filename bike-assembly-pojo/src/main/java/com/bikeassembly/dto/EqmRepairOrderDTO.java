package com.bikeassembly.dto;

import com.bikeassembly.validation.OnCreate;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import java.time.LocalDateTime;

import java.io.Serializable;

@Data
public class EqmRepairOrderDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 维修单ID */
    private Long repairId;
    /** 设备ID */
    @NotNull(message = "设备ID不能为空", groups = OnCreate.class)
    private Long equipmentId;
    /** 故障描述 */
    @NotBlank(message = "故障描述不能为空", groups = OnCreate.class)
    private String faultDesc;
    /** 报修人 */
    private String reportUser;
    /** 报修人ID */
    private Long reporterUserId;
    /** 报修时间 */
    private LocalDateTime reportTime;
    /** 维修人 */
    private String repairUser;
    /** 维修人ID */
    private Long handlerUserId;
    /** 完成时间 */
    private LocalDateTime finishTime;
    /** 状态(CHAR(1)) */
    @Size(max = 1, message = "状态长度不能超过1")
    private String status;
    /** 维修结果 */
    private String resultDesc;
}
