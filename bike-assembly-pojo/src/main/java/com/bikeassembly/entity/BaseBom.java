package com.bikeassembly.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

import java.io.Serializable;

@Data
@TableName("base_bom")
public class BaseBom implements Serializable {

    private static final long serialVersionUID = 1L;

    /** BOM ID */
    @TableId(value = "bom_id", type = IdType.AUTO)
    private Long bomId;
    /** 成品物料ID */
    @TableField("item_id")
    private Long itemId;
    /** BOM编码 */
    @TableField("bom_code")
    private String bomCode;
    /** 版本 */
    @TableField("version")
    private String version;
    /** 子件明细JSON */
    @TableField("details_json")
    private String detailsJson;
    /** 状态 */
    @TableField("status")
    private String status;
    /** 创建时间 */
    @TableField("create_time")
    private LocalDateTime createTime;
    /** 更新时间 */
    @TableField("update_time")
    private LocalDateTime updateTime;
}
