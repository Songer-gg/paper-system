-- PaperHub v1.0 -> v1.1, MySQL 8.0.16+.
-- Use scripts/migrate_local_mysql.py: fingerprint guard, isolated rehearsal and row-preservation checks.
-- DDL implicitly commits. Do not rerun after a partial failure; use the documented rollback procedure.
SET NAMES utf8mb4;
USE paperhub;

ALTER TABLE `user`
  ADD COLUMN token_version INT UNSIGNED NOT NULL DEFAULT 0 AFTER password_reset_at,
  ADD CONSTRAINT ck_user_role CHECK (role IN ('user', 'admin')),
  ADD CONSTRAINT ck_user_status CHECK (status IN ('active', 'disabled'));

ALTER TABLE category
  ADD COLUMN owner_scope BIGINT GENERATED ALWAYS AS (IFNULL(owner_id, 0)) STORED,
  ADD COLUMN parent_scope BIGINT GENERATED ALWAYS AS (IFNULL(parent_id, 0)) STORED,
  ADD UNIQUE KEY uk_category_scope_name (owner_scope, parent_scope, name),
  ADD CONSTRAINT ck_category_scope CHECK ((custom = 0 AND owner_id IS NULL) OR (custom = 1 AND owner_id IS NOT NULL)),
  DROP FOREIGN KEY fk_category_parent,
  DROP FOREIGN KEY fk_category_owner;
ALTER TABLE category
  ADD CONSTRAINT fk_category_parent FOREIGN KEY (parent_id) REFERENCES category(id) ON DELETE RESTRICT,
  ADD CONSTRAINT fk_category_owner FOREIGN KEY (owner_id) REFERENCES `user`(id) ON DELETE RESTRICT;

ALTER TABLE tag
  ADD COLUMN owner_scope BIGINT GENERATED ALWAYS AS (IFNULL(owner_id, 0)) STORED,
  ADD UNIQUE KEY uk_tag_scope_name (owner_scope, name),
  ADD CONSTRAINT ck_tag_scope CHECK ((custom = 0 AND owner_id IS NULL) OR (custom = 1 AND owner_id IS NOT NULL)),
  DROP FOREIGN KEY fk_tag_owner;
ALTER TABLE tag
  ADD CONSTRAINT fk_tag_owner FOREIGN KEY (owner_id) REFERENCES `user`(id) ON DELETE RESTRICT;

ALTER TABLE paper
  MODIFY COLUMN abstract TEXT NOT NULL,
  MODIFY COLUMN year SMALLINT UNSIGNED NOT NULL,
  MODIFY COLUMN keywords JSON NOT NULL,
  MODIFY COLUMN review_status VARCHAR(10) NULL,
  ADD COLUMN offline_reason VARCHAR(12) NULL,
  ADD COLUMN deleted_at DATETIME NULL,
  ADD COLUMN version INT UNSIGNED NOT NULL DEFAULT 0,
  ADD COLUMN file_sha256 CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL,
  ADD COLUMN doi_normalized VARCHAR(120) CHARACTER SET ascii COLLATE ascii_bin NULL,
  ADD COLUMN copyright_agreed_at DATETIME NULL;

UPDATE paper SET review_status = NULL WHERE visibility = 'private';
-- Missing historical consent is not consent. Preserve the record and allow a later legitimate resubmission.
UPDATE paper SET review_status = 'rejected',
  review_comment = COALESCE(NULLIF(review_comment, ''), '旧版记录缺少版权确认，请确认版权后重新提交')
WHERE visibility = 'shared' AND copyright_agreed = 0;
UPDATE paper SET doi_normalized = NULLIF(TRIM(REGEXP_REPLACE(LOWER(TRIM(doi)),
  '^(https?://(dx[.])?doi[.]org/|doi:[[:space:]]*)', '')), '');

ALTER TABLE paper
  ADD COLUMN shared_doi_key VARCHAR(120) CHARACTER SET ascii COLLATE ascii_bin GENERATED ALWAYS AS
    (CASE WHEN visibility = 'shared' AND deleted_at IS NULL AND review_status <> 'rejected' THEN NULLIF(doi_normalized, '') ELSE NULL END) STORED,
  ADD COLUMN shared_file_key CHAR(64) CHARACTER SET ascii COLLATE ascii_bin GENERATED ALWAYS AS
    (CASE WHEN visibility = 'shared' AND deleted_at IS NULL AND review_status <> 'rejected' THEN file_sha256 ELSE NULL END) STORED,
  ADD UNIQUE KEY uk_shared_doi (shared_doi_key),
  ADD UNIQUE KEY uk_shared_file (shared_file_key),
  ADD CONSTRAINT ck_paper_state CHECK (
    (visibility = 'private' AND review_status IS NULL AND offline_reason IS NULL) OR
    (visibility = 'shared' AND review_status IS NOT NULL AND review_status IN ('pending', 'approved', 'rejected', 'offline') AND
      ((review_status = 'offline' AND offline_reason IS NOT NULL AND offline_reason IN ('withdrawn', 'moderation')) OR
       (review_status <> 'offline' AND offline_reason IS NULL)))),
  ADD CONSTRAINT ck_paper_authors CHECK (JSON_TYPE(authors) = 'ARRAY' AND JSON_LENGTH(authors) > 0),
  ADD CONSTRAINT ck_paper_keywords CHECK (JSON_TYPE(keywords) = 'ARRAY'),
  ADD KEY idx_shared_uploaded (visibility, review_status, deleted_at, uploaded_at, id),
  ADD KEY idx_shared_downloads (visibility, review_status, deleted_at, downloads, id);

ALTER TABLE paper_tag DROP FOREIGN KEY fk_pt_paper;
ALTER TABLE paper_tag
  ADD CONSTRAINT fk_pt_paper FOREIGN KEY (paper_id) REFERENCES paper(id) ON DELETE RESTRICT;
ALTER TABLE favorite DROP FOREIGN KEY fk_fav_paper;
ALTER TABLE favorite
  ADD CONSTRAINT fk_fav_paper FOREIGN KEY (paper_id) REFERENCES paper(id) ON DELETE RESTRICT;
ALTER TABLE reading_record DROP FOREIGN KEY fk_rr_paper;
ALTER TABLE reading_record
  ADD CONSTRAINT fk_rr_paper FOREIGN KEY (paper_id) REFERENCES paper(id) ON DELETE RESTRICT,
  ADD CONSTRAINT ck_reading_status CHECK (status IN ('unread', 'reading', 'read', 'paused'));

ALTER TABLE note ADD COLUMN paper_title VARCHAR(255) NULL, MODIFY COLUMN title VARCHAR(300) NOT NULL;
UPDATE note n JOIN paper p ON p.id = n.paper_id SET n.paper_title = p.title, n.updated_at = n.updated_at;
ALTER TABLE note MODIFY COLUMN paper_title VARCHAR(255) NOT NULL, DROP FOREIGN KEY fk_note_paper;
ALTER TABLE note
  ADD CONSTRAINT fk_note_paper FOREIGN KEY (paper_id) REFERENCES paper(id) ON DELETE RESTRICT;

ALTER TABLE review_record ADD COLUMN paper_title VARCHAR(255) NULL, ADD COLUMN uploader_id BIGINT NULL;
UPDATE review_record r JOIN paper p ON p.id = r.paper_id SET r.paper_title = p.title, r.uploader_id = p.uploader_id;
ALTER TABLE review_record MODIFY COLUMN paper_title VARCHAR(255) NOT NULL, MODIFY COLUMN uploader_id BIGINT NOT NULL,
  DROP FOREIGN KEY fk_rev_paper;
ALTER TABLE review_record
  ADD CONSTRAINT fk_rev_paper FOREIGN KEY (paper_id) REFERENCES paper(id) ON DELETE RESTRICT;

ALTER TABLE duplicate_pair DROP FOREIGN KEY fk_dp_old, DROP FOREIGN KEY fk_dp_new,
  CHANGE COLUMN old_paper_id original_paper_id BIGINT NOT NULL,
  MODIFY COLUMN new_paper_id BIGINT NOT NULL,
  CHANGE COLUMN similarity title_similarity DECIMAL(5,2) NOT NULL,
  ADD COLUMN original_paper_title VARCHAR(255) NULL,
  ADD COLUMN new_paper_title VARCHAR(255) NULL,
  ADD COLUMN doi_match TINYINT(1) NOT NULL DEFAULT 0,
  ADD COLUMN file_hash_match TINYINT(1) NOT NULL DEFAULT 0,
  ADD COLUMN submitted_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  RENAME INDEX idx_old TO idx_original;
UPDATE duplicate_pair d JOIN paper p ON p.id = d.original_paper_id JOIN paper n ON n.id = d.new_paper_id
  SET d.original_paper_title = p.title, d.new_paper_title = n.title;
ALTER TABLE duplicate_pair MODIFY COLUMN original_paper_title VARCHAR(255) NOT NULL,
  MODIFY COLUMN new_paper_title VARCHAR(255) NOT NULL,
  ADD UNIQUE KEY uk_duplicate_pair (original_paper_id, new_paper_id),
  ADD CONSTRAINT ck_duplicate_status CHECK (status IN ('pending', 'duplicate', 'distinct', 'linked')),
  ADD CONSTRAINT ck_duplicate_pair CHECK (original_paper_id <> new_paper_id AND title_similarity BETWEEN 0 AND 100),
  ADD CONSTRAINT fk_dp_original FOREIGN KEY (original_paper_id) REFERENCES paper(id) ON DELETE RESTRICT,
  ADD CONSTRAINT fk_dp_new FOREIGN KEY (new_paper_id) REFERENCES paper(id) ON DELETE RESTRICT;

ALTER TABLE report ADD COLUMN created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP;
UPDATE report r JOIN paper p ON p.id = r.paper_id
  SET r.paper_title = COALESCE(r.paper_title, p.title);
ALTER TABLE report MODIFY COLUMN paper_id BIGINT NOT NULL, MODIFY COLUMN paper_title VARCHAR(255) NOT NULL,
  DROP FOREIGN KEY fk_rp_paper;
ALTER TABLE report
  ADD CONSTRAINT fk_rp_paper FOREIGN KEY (paper_id) REFERENCES paper(id) ON DELETE RESTRICT;

INSERT INTO operation_log(user_id,user_name,action,target,result)
VALUES(NULL,'系统','数据库迁移','v1.0 -> v1.1；历史标题使用迁移时快照；未知历史时间使用迁移捕获时间；缺少版权确认的共享记录设为驳回','成功');

