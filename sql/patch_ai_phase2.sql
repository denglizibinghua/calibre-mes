-- Re-runnable additive schema for private AI documents and face templates.
SET NAMES utf8mb4 COLLATE utf8mb4_unicode_ci;
SET @schema_name = DATABASE();

SET @sql = (
    SELECT IF(COUNT(*) = 0,
              'ALTER TABLE ai_knowledge_document ADD COLUMN scope VARCHAR(16) NOT NULL DEFAULT ''PUBLIC''',
              'SELECT 1')
    FROM information_schema.columns
    WHERE table_schema = @schema_name AND table_name = 'ai_knowledge_document' AND column_name = 'scope'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = (
    SELECT IF(COUNT(*) = 0,
              'ALTER TABLE ai_knowledge_document ADD COLUMN created_at DATETIME NULL',
              'SELECT 1')
    FROM information_schema.columns
    WHERE table_schema = @schema_name AND table_name = 'ai_knowledge_document' AND column_name = 'created_at'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = (
    SELECT IF(COUNT(*) = 0,
              'ALTER TABLE ai_knowledge_document ADD COLUMN updated_at DATETIME NULL',
              'SELECT 1')
    FROM information_schema.columns
    WHERE table_schema = @schema_name AND table_name = 'ai_knowledge_document' AND column_name = 'updated_at'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = (
    SELECT IF(COUNT(*) = 0,
              'ALTER TABLE ai_knowledge_document ADD COLUMN owner_id BIGINT NULL',
              'SELECT 1')
    FROM information_schema.columns
    WHERE table_schema = @schema_name AND table_name = 'ai_knowledge_document' AND column_name = 'owner_id'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = (
    SELECT IF(COUNT(*) = 0,
              'ALTER TABLE ai_knowledge_document ADD COLUMN original_name VARCHAR(255) NULL',
              'SELECT 1')
    FROM information_schema.columns
    WHERE table_schema = @schema_name AND table_name = 'ai_knowledge_document' AND column_name = 'original_name'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = (
    SELECT IF(COUNT(*) = 0,
              'ALTER TABLE ai_knowledge_document ADD COLUMN mime_type VARCHAR(255) NULL',
              'SELECT 1')
    FROM information_schema.columns
    WHERE table_schema = @schema_name AND table_name = 'ai_knowledge_document' AND column_name = 'mime_type'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = (
    SELECT IF(COUNT(*) = 0,
              'ALTER TABLE ai_knowledge_document ADD COLUMN file_size BIGINT NULL',
              'SELECT 1')
    FROM information_schema.columns
    WHERE table_schema = @schema_name AND table_name = 'ai_knowledge_document' AND column_name = 'file_size'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = (
    SELECT IF(COUNT(*) = 0,
              'ALTER TABLE ai_knowledge_document ADD COLUMN storage_path VARCHAR(1024) NULL',
              'SELECT 1')
    FROM information_schema.columns
    WHERE table_schema = @schema_name AND table_name = 'ai_knowledge_document' AND column_name = 'storage_path'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = (
    SELECT IF(COUNT(*) = 0,
              'ALTER TABLE ai_knowledge_document ADD INDEX idx_ai_knowledge_owner_scope (owner_id, scope)',
              'SELECT 1')
    FROM information_schema.statistics
    WHERE table_schema = @schema_name AND table_name = 'ai_knowledge_document'
      AND index_name = 'idx_ai_knowledge_owner_scope'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

CREATE TABLE IF NOT EXISTS user_face_template (
  user_id BIGINT NOT NULL,
  embedding_ciphertext MEDIUMBLOB NOT NULL,
  consented_at DATETIME NOT NULL,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (user_id),
  CONSTRAINT fk_face_template_user FOREIGN KEY (user_id) REFERENCES sys_user(user_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='加密的人脸特征模板，不存原始照片';
