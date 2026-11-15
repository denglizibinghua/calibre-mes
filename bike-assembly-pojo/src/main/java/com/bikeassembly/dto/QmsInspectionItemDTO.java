package com.bikeassembly.dto;

import com.bikeassembly.validation.OnCreate;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import java.time.LocalDateTime;

import java.io.Serializable;

@Data
public class QmsInspectionItemDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 质检项ID */
    private Long inspectionItemId;
    /** 工序ID */
    private Long processId;
    /** 质检项名称 */
    @NotBlank(message = "质检项名称不能为空", groups = OnCreate.class)
    private String itemName;
    /** 检验标准 */
    private String standard;
    /** 检验方式 */
    private String checkMethod;
    /** 状态(CHAR(1)) */
    @Size(max = 1, message = "状态长度不能超过1")
    private String status;
    /** 创建时间 */
    private LocalDateTime createTime;
}
