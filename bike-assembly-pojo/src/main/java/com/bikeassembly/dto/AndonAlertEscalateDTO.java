package com.bikeassembly.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;

/**
 * 安灯告警升级入参。
 */
@Data
public class AndonAlertEscalateDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 升级备注 */
    @Size(max = 255, message = "升级备注长度不能超过255")
    private String remark;
}
