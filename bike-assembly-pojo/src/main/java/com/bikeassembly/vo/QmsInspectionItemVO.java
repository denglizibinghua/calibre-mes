package com.bikeassembly.vo;

import lombok.Data;
import java.time.LocalDateTime;

import java.io.Serializable;

@Data
public class QmsInspectionItemVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 质检项ID */
    private Long inspectionItemId;
    /** 工序ID */
    private Long processId;
    /** 质检项名称 */
    private String itemName;
    /** 检验标准 */
    private String standard;
    /** 检验方式 */
    private String checkMethod;
    /** 状态 */
    private String status;
    /** 创建时间 */
    private LocalDateTime createTime;
}
