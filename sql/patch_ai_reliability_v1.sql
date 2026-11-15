-- Apply this patch to an existing MES database before enabling the reliability features.
-- Every schema change is guarded so the patch can be safely re-run during deployment.
SET NAMES utf8mb4 COLLATE utf8mb4_unicode_ci;
SET @schema_name = DATABASE();

CREATE TABLE IF NOT EXISTS ai_plan_suggestion (
  plan_id BIGINT NOT NULL AUTO_INCREMENT,
  plan_uuid VARCHAR(64) NOT NULL,
  operator_id BIGINT NOT NULL,
  question VARCHAR(2000) NOT NULL,
  summary TEXT NULL,
  affected_orders_json JSON NULL,
  constraints_json JSON NULL,
  risk_level VARCHAR(16) NULL,
  status VARCHAR(32) NOT NULL DEFAULT 'PENDING_CONFIRMATION',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  confirmed_at DATETIME NULL,
  confirmed_by BIGINT NULL,
  PRIMARY KEY (plan_id),
  UNIQUE KEY uk_ai_plan_uuid_operator (plan_uuid, operator_id),
  INDEX idx_ai_plan_operator_status (operator_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI排产建议草稿';

SET @sql = (
    SELECT IF(COUNT(*) = 0,
              'ALTER TABLE sys_user ADD COLUMN work_unit_id BIGINT NULL COMMENT ''所属生产单元，DEPT 数据范围以此为根节点''',
              'SELECT 1')
    FROM information_schema.columns
    WHERE table_schema = @schema_name AND table_name = 'sys_user' AND column_name = 'work_unit_id'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = (
    SELECT IF(COUNT(*) = 0,
              'ALTER TABLE sys_user ADD INDEX idx_sys_user_work_unit (work_unit_id)',
              'SELECT 1')
    FROM information_schema.statistics
    WHERE table_schema = @schema_name AND table_name = 'sys_user' AND index_name = 'idx_sys_user_work_unit'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = (
    SELECT IF(COUNT(*) = 0, 'ALTER TABLE ai_plan_suggestion ADD COLUMN priority_order_json JSON NULL', 'SELECT 1')
    FROM information_schema.columns WHERE table_schema = @schema_name AND table_name = 'ai_plan_suggestion' AND column_name = 'priority_order_json'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @sql = (
    SELECT IF(COUNT(*) = 0, 'ALTER TABLE ai_plan_suggestion ADD COLUMN resource_constraints_json JSON NULL', 'SELECT 1')
    FROM information_schema.columns WHERE table_schema = @schema_name AND table_name = 'ai_plan_suggestion' AND column_name = 'resource_constraints_json'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @sql = (
    SELECT IF(COUNT(*) = 0, 'ALTER TABLE ai_plan_suggestion ADD COLUMN material_constraints_json JSON NULL', 'SELECT 1')
    FROM information_schema.columns WHERE table_schema = @schema_name AND table_name = 'ai_plan_suggestion' AND column_name = 'material_constraints_json'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @sql = (
    SELECT IF(COUNT(*) = 0, 'ALTER TABLE ai_plan_suggestion ADD COLUMN risk_basis VARCHAR(1000) NULL', 'SELECT 1')
    FROM information_schema.columns WHERE table_schema = @schema_name AND table_name = 'ai_plan_suggestion' AND column_name = 'risk_basis'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @sql = (
    SELECT IF(COUNT(*) = 0, 'ALTER TABLE ai_plan_suggestion ADD COLUMN data_time_range VARCHAR(128) NULL', 'SELECT 1')
    FROM information_schema.columns WHERE table_schema = @schema_name AND table_name = 'ai_plan_suggestion' AND column_name = 'data_time_range'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @sql = (
    SELECT IF(COUNT(*) = 0, 'ALTER TABLE ai_plan_suggestion ADD COLUMN sources_json JSON NULL', 'SELECT 1')
    FROM information_schema.columns WHERE table_schema = @schema_name AND table_name = 'ai_plan_suggestion' AND column_name = 'sources_json'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @sql = (
    SELECT IF(COUNT(*) = 0, 'ALTER TABLE ai_plan_suggestion ADD COLUMN input_summary VARCHAR(2000) NULL', 'SELECT 1')
    FROM information_schema.columns WHERE table_schema = @schema_name AND table_name = 'ai_plan_suggestion' AND column_name = 'input_summary'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @sql = (
    SELECT IF(COUNT(*) = 0, 'ALTER TABLE ai_plan_suggestion ADD COLUMN model VARCHAR(128) NULL', 'SELECT 1')
    FROM information_schema.columns WHERE table_schema = @schema_name AND table_name = 'ai_plan_suggestion' AND column_name = 'model'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @sql = (
    SELECT IF(COUNT(*) = 0, 'ALTER TABLE ai_plan_suggestion ADD COLUMN request_id VARCHAR(64) NULL', 'SELECT 1')
    FROM information_schema.columns WHERE table_schema = @schema_name AND table_name = 'ai_plan_suggestion' AND column_name = 'request_id'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @sql = (
    SELECT IF(COUNT(*) = 0, 'ALTER TABLE ai_plan_suggestion ADD COLUMN session_id VARCHAR(64) NULL', 'SELECT 1')
    FROM information_schema.columns WHERE table_schema = @schema_name AND table_name = 'ai_plan_suggestion' AND column_name = 'session_id'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

CREATE TABLE IF NOT EXISTS ai_request_audit (
  audit_id BIGINT NOT NULL AUTO_INCREMENT,
  request_id VARCHAR(64) NULL,
  session_id VARCHAR(64) NULL,
  user_id BIGINT NOT NULL,
  operation VARCHAR(32) NOT NULL,
  question_hash CHAR(64) NULL,
  data_scope VARCHAR(16) NULL,
  data_time_range VARCHAR(128) NULL,
  model VARCHAR(128) NULL,
  sources_json JSON NULL,
  input_summary VARCHAR(2000) NULL,
  status VARCHAR(24) NOT NULL,
  fallback TINYINT NOT NULL DEFAULT 0,
  latency_ms BIGINT NULL,
  error_code VARCHAR(64) NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (audit_id),
  INDEX idx_ai_audit_user_time (user_id, created_at),
  INDEX idx_ai_audit_request (request_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS ai_knowledge_document (
  document_id BIGINT NOT NULL AUTO_INCREMENT,
  source VARCHAR(512) NOT NULL,
  content_hash CHAR(64) NOT NULL,
  chunk_count INT NOT NULL DEFAULT 0,
  index_status VARCHAR(24) NOT NULL DEFAULT 'PENDING',
  last_modified_at DATETIME NULL,
  indexed_at DATETIME NULL,
  error_message VARCHAR(1000) NULL,
  PRIMARY KEY (document_id),
  UNIQUE KEY uk_ai_knowledge_source (source),
  INDEX idx_ai_knowledge_status (index_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS ai_knowledge_index_task (
  task_id VARCHAR(64) NOT NULL,
  status VARCHAR(24) NOT NULL DEFAULT 'QUEUED',
  max_chars INT NOT NULL,
  overlap_chars INT NOT NULL,
  scanned_files INT NOT NULL DEFAULT 0,
  files_changed INT NOT NULL DEFAULT 0,
  files_skipped INT NOT NULL DEFAULT 0,
  files_deleted INT NOT NULL DEFAULT 0,
  chunks_indexed INT NOT NULL DEFAULT 0,
  chunks_total INT NOT NULL DEFAULT 0,
  failures_json JSON NULL,
  message VARCHAR(1000) NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  started_at DATETIME NULL,
  finished_at DATETIME NULL,
  PRIMARY KEY (task_id),
  INDEX idx_ai_knowledge_task_status (status, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
