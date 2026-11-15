package com.bikeassembly.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;

/**
 * 维修完成专用DTO。
 * 仅接收维修人员可以填写的内容，status/handler/finishTime等由后端生成。
 */
@Data
public class CompleteRepairDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 维修结果/处理说明（必填） */
    @NotBlank(message = "维修结果不能为空")
    @Size(max = 255, message = "维修结果不能超过255个字符")
    private String resultDesc;

    /** 故障原因（选填） */
    @Size(max = 255, message = "故障原因不能超过255个字符")
    private String faultReason;

    /** 维修措施（选填） */
    @Size(max = 255, message = "维修措施不能超过255个字符")
    private String repairAction;

    /** 备注（选填） */
    @Size(max = 500, message = "备注不能超过500个字符")
    private String remark;
}
