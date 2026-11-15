package com.bikeassembly.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@TableName("ai_knowledge_document")
public class AiKnowledgeDocument implements Serializable {
    @TableId(value = "document_id", type = IdType.AUTO)
    private Long documentId;
    private String source;
    private String scope;
    private Long ownerId;
    private String originalName;
    private String mimeType;
    private Long fileSize;
    private String storagePath;
    private String contentHash;
    private Integer chunkCount;
    private String indexStatus;
    private LocalDateTime lastModifiedAt;
    private LocalDateTime indexedAt;
    private String errorMessage;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
