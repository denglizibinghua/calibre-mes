package com.bikeassembly.vo;

import lombok.Data;
import java.time.LocalDateTime;

import java.io.Serializable;

@Data
public class SafTrainingVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 培训ID */
    private Long trainingId;
    /** 培训主题 */
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
    /** 状态 */
    private String status;
}
