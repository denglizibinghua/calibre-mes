package com.bikeassembly.dto;

import com.bikeassembly.validation.OnCreate;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import java.time.LocalDateTime;

import java.io.Serializable;

@Data
public class WmsStockDocDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 单据ID */
    private Long docId;
    /** 单据编号 */
    @NotBlank(message = "单据编号不能为空", groups = OnCreate.class)
    private String docNo;
    /** 单据类型 */
    @NotBlank(message = "单据类型不能为空", groups = OnCreate.class)
    @Size(max = 1, message = "单据类型长度不能超过1")
    private String docType;
    /** 关联单号 */
    private String relatedOrderNo;
    /** 单据状态 */
    @Size(max = 1, message = "状态长度不能超过1")
    private String status;
    /** 明细JSON */
    @NotBlank(message = "明细JSON不能为空", groups = OnCreate.class)
    private String detailsJson;
    /** 创建时间 */
    private LocalDateTime createTime;
}
