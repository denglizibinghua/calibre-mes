package com.bikeassembly.dto;

import com.bikeassembly.validation.OnCreate;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import java.time.LocalDateTime;

import java.io.Serializable;

@Data
public class BaseProcessDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 工序ID */
    private Long processId;
    /** 工序编码 */
    @NotBlank(message = "工序编码不能为空", groups = OnCreate.class)
    private String processCode;
    /** 工序名称 */
    @NotBlank(message = "工序名称不能为空", groups = OnCreate.class)
    private String processName;
    /** 标准工时分钟 */
    private Integer standardMinutes;
    /** 状态(CHAR(1)) */
    @Size(max = 1, message = "状态长度不能超过1")
    private String status;
    /** 创建时间 */
    private LocalDateTime createTime;
}
