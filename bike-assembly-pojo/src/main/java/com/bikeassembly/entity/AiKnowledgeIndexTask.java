package com.bikeassembly.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@TableName("ai_knowledge_index_task")
public class AiKnowledgeIndexTask implements Serializable {
    @TableId(value = "task_id", type = IdType.INPUT)
    private String taskId;
    private String status;
    private Integer maxChars;
    private Integer overlapChars;
    private Integer scannedFiles;
    private Integer filesChanged;
    private Integer filesSkipped;
    private Integer filesDeleted;
    private Integer chunksIndexed;
    private Integer chunksTotal;
    private String failuresJson;
    private String message;
    private LocalDateTime createdAt;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
}
