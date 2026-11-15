package com.bikeassembly.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

import java.io.Serializable;

@Data
@TableName("saf_training")
public class SafTraining implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 培训ID */
    @TableId(value = "training_id", type = IdType.AUTO)
    private Long trainingId;
    /** 培训主题 */
    @TableField("training_title")
    private String trainingTitle;
    /** 培训类型 */
    @TableField("training_type")
    private String trainingType;
    /** 讲师 */
    @TableField("trainer")
    private String trainer;
    /** 开始时间 */
    @TableField("start_time")
    private LocalDateTime startTime;
    /** 结束时间 */
    @TableField("end_time")
    private LocalDateTime endTime;
    /** 参训人员JSON */
    @TableField("users_json")
    private String usersJson;
    /** 状态 */
    @TableField("status")
    private String status;
}
