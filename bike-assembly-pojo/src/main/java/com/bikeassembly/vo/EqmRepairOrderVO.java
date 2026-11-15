package com.bikeassembly.vo;

import lombok.Data;
import java.time.LocalDateTime;

import java.io.Serializable;

@Data
public class EqmRepairOrderVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 维修单ID */
    private Long repairId;
    /** 设备ID */
    private Long equipmentId;
    /** 故障描述 */
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
    /** 状态 */
    private String status;
    /** 维修结果 */
    private String resultDesc;
}
