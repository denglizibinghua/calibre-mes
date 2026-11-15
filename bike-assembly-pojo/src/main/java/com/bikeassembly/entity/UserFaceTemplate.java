package com.bikeassembly.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@TableName("user_face_template")
public class UserFaceTemplate implements Serializable {
    @TableId("user_id")
    private Long userId;
    private byte[] embeddingCiphertext;
    private LocalDateTime consentedAt;
    private LocalDateTime updatedAt;
}
