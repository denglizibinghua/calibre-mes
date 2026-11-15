package com.bikeassembly.dto;

import com.bikeassembly.validation.OnCreate;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import java.time.LocalDateTime;

import java.io.Serializable;

@Data
public class SafTrainingDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 培训ID */
    private Long trainingId;
    /** 培训主题 */
    @NotBlank(message = "培训主题不能为空", groups = OnCreate.class)
    private String trainingTitle;
    /** 培训类型 */
    private String trainingType;
    /** 讲师 */
    private String trainer;
    /** 开始时间 */
    private LocalDateTime startTime;
    /** 结束时间 */
    private LocalDateTime endTime;
    /** 参训人员JSON */
    private String usersJson;
    /** 状态(CHAR(1)) */
    @Size(max = 1, message = "状态长度不能超过1")
    private String status;
}
