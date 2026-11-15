-- Re-runnable additive schema for private AI documents and face templates.
-- PostgreSQL 16 / psql: guarded so the patch can be re-applied during deployment.
SET client_encoding TO 'UTF8';

DO $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                 WHERE table_schema = current_schema() AND table_name = 'ai_knowledge_document'
                   AND column_name = 'scope') THEN
    ALTER TABLE ai_knowledge_document ADD COLUMN scope varchar(16) NOT NULL DEFAULT 'PUBLIC';
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                 WHERE table_schema = current_schema() AND table_name = 'ai_knowledge_document'
                   AND column_name = 'created_at') THEN
    ALTER TABLE ai_knowledge_document ADD COLUMN created_at timestamp(0) without time zone NULL;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                 WHERE table_schema = current_schema() AND table_name = 'ai_knowledge_document'
                   AND column_name = 'updated_at') THEN
    ALTER TABLE ai_knowledge_document ADD COLUMN updated_at timestamp(0) without time zone NULL;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                 WHERE table_schema = current_schema() AND table_name = 'ai_knowledge_document'
                   AND column_name = 'owner_id') THEN
    ALTER TABLE ai_knowledge_document ADD COLUMN owner_id bigint NULL;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                 WHERE table_schema = current_schema() AND table_name = 'ai_knowledge_document'
                   AND column_name = 'original_name') THEN
    ALTER TABLE ai_knowledge_document ADD COLUMN original_name varchar(255) NULL;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                 WHERE table_schema = current_schema() AND table_name = 'ai_knowledge_document'
                   AND column_name = 'mime_type') THEN
    ALTER TABLE ai_knowledge_document ADD COLUMN mime_type varchar(255) NULL;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                 WHERE table_schema = current_schema() AND table_name = 'ai_knowledge_document'
                   AND column_name = 'file_size') THEN
    ALTER TABLE ai_knowledge_document ADD COLUMN file_size bigint NULL;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                 WHERE table_schema = current_schema() AND table_name = 'ai_knowledge_document'
                   AND column_name = 'storage_path') THEN
    ALTER TABLE ai_knowledge_document ADD COLUMN storage_path varchar(1024) NULL;
  END IF;
END
$$;

DO $$
BEGIN
  IF to_regclass('idx_ai_knowledge_owner_scope') IS NULL THEN
    CREATE INDEX idx_ai_knowledge_owner_scope ON ai_knowledge_document (owner_id, scope);
  END IF;
END
$$;

CREATE TABLE IF NOT EXISTS user_face_template (
  user_id bigint NOT NULL,
  embedding_ciphertext bytea NOT NULL,
  consented_at timestamp(0) without time zone NOT NULL,
  updated_at timestamp(0) without time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (user_id),
  CONSTRAINT fk_face_template_user FOREIGN KEY (user_id) REFERENCES sys_user(user_id) ON DELETE CASCADE
);

-- init_database.sql drops sys_user CASCADE on a rebuild, which removes this FK from an
-- already-existing user_face_template; re-add it so the patch heals that case as well.
DO $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint
                 WHERE conname = 'fk_face_template_user'
                   AND conrelid = to_regclass('user_face_template')) THEN
    ALTER TABLE user_face_template
      ADD CONSTRAINT fk_face_template_user FOREIGN KEY (user_id)
      REFERENCES sys_user(user_id) ON DELETE CASCADE;
  END IF;
END
$$;

DO $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_trigger
                 WHERE tgname = 'trg_user_face_template_touch'
                   AND tgrelid = to_regclass('user_face_template')) THEN
    CREATE TRIGGER trg_user_face_template_touch BEFORE UPDATE ON user_face_template
      FOR EACH ROW EXECUTE FUNCTION set_updated_at('updated_at');
  END IF;
END
$$;

COMMENT ON TABLE user_face_template IS '加密的人脸特征模板，不存原始照片';
