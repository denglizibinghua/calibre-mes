package com.bikeassembly.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bikeassembly.entity.UserFaceTemplate;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Insert;

@Mapper
public interface UserFaceTemplateMapper extends BaseMapper<UserFaceTemplate> {
    @Insert("""
            INSERT INTO user_face_template (user_id, embedding_ciphertext, consented_at, updated_at)
            VALUES (#{userId}, #{embeddingCiphertext}, #{consentedAt}, #{updatedAt})
            ON DUPLICATE KEY UPDATE
              embedding_ciphertext = VALUES(embedding_ciphertext),
              consented_at = VALUES(consented_at),
              updated_at = VALUES(updated_at)
            """)
    int upsertTemplate(UserFaceTemplate template);
}
