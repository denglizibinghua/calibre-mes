package com.bikeassembly.vo;

import lombok.Data;
import java.time.LocalDateTime;

import java.io.Serializable;

@Data
public class BaseProcessVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 工序ID */
    private Long processId;
    /** 工序编码 */
    private String processCode;
    /** 工序名称 */
    private String processName;
    /** 标准工时分钟 */
    private Integer standardMinutes;
    /** 状态 */
    private String status;
    /** 创建时间 */
    private LocalDateTime createTime;
}
