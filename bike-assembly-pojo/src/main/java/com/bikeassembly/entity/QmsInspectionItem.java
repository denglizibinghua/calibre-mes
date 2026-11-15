package com.bikeassembly.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

import java.io.Serializable;

@Data
@TableName("qms_inspection_item")
public class QmsInspectionItem implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 质检项ID */
    @TableId(value = "inspection_item_id", type = IdType.AUTO)
    private Long inspectionItemId;
    /** 工序ID */
    @TableField("process_id")
    private Long processId;
    /** 质检项名称 */
    @TableField("item_name")
    private String itemName;
    /** 检验标准 */
    @TableField("standard")
    private String standard;
    /** 检验方式 */
    @TableField("check_method")
    private String checkMethod;
    /** 状态 */
    @TableField("status")
    private String status;
    /** 创建时间 */
    @TableField("create_time")
    private LocalDateTime createTime;
}
