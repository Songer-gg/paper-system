-- ============================================================
-- PaperHub 数据库 DDL（MySQL 8）
-- 依据：paperhub-api/api-spec.md + db/er-design.md
-- 引擎：InnoDB ｜ 字符集：utf8mb4 ｜ 库名：paperhub
-- ============================================================

-- MySQL >= 8.0.16（CHECK 生效）。仅用于新空库；已有库必须使用增量迁移。
-- 演示数据单独导入 seed-demo.sql；禁止应用启动时自动执行本文件。
CREATE DATABASE IF NOT EXISTS paperhub DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
USE paperhub;

-- ------------------------------------------------------------
-- T1 用户表（普通用户与管理员同表，role 区分）
-- ------------------------------------------------------------
CREATE TABLE `user` (
  `id`                BIGINT       NOT NULL AUTO_INCREMENT,
  `username`          VARCHAR(50)  NOT NULL COMMENT '登录名（唯一）',
  `password`          VARCHAR(255) NOT NULL COMMENT 'BCrypt 密文',
  `name`              VARCHAR(50)  NOT NULL COMMENT '姓名',
  `email`             VARCHAR(120) NOT NULL COMMENT '邮箱（唯一）',
  `role`              VARCHAR(10)  NOT NULL DEFAULT 'user' COMMENT 'user | admin',
  `status`            VARCHAR(10)  NOT NULL DEFAULT 'active' COMMENT 'active | disabled',
  `avatar`            VARCHAR(255) NULL COMMENT '头像 URL',
  `password_reset_at` DATETIME     NULL COMMENT '管理员最近一次重置密码时间',
  `token_version`     INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '禁用、改密、重置密码递增，使旧 JWT 失效',
  `registered_at`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_username` (`username`),
  UNIQUE KEY `uk_email` (`email`),
  CONSTRAINT `ck_user_role` CHECK (`role` IN ('user', 'admin')),
  CONSTRAINT `ck_user_status` CHECK (`status` IN ('active', 'disabled')),
  KEY `idx_name_role_status` (`name`, `role`, `status`)
) ENGINE = InnoDB COMMENT '用户表';

-- ------------------------------------------------------------
-- T3 分类表（树形；owner_id 为空 = 平台级，否则为用户自建）
-- ------------------------------------------------------------
CREATE TABLE `category` (
  `id`         BIGINT       NOT NULL AUTO_INCREMENT,
  `name`       VARCHAR(50)  NOT NULL,
  `parent_id`  BIGINT       NULL COMMENT '父分类，NULL = 根',
  `description` VARCHAR(255) NULL COMMENT '描述（避开保留字 desc）',
  `custom`     TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '1 = 用户自建',
  `owner_id`   BIGINT       NULL COMMENT '自建归属用户，NULL = 平台级',
  `created_at` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `owner_scope` BIGINT GENERATED ALWAYS AS (IFNULL(`owner_id`, 0)) STORED,
  `parent_scope` BIGINT GENERATED ALWAYS AS (IFNULL(`parent_id`, 0)) STORED,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_category_scope_name` (`owner_scope`, `parent_scope`, `name`),
  CONSTRAINT `ck_category_scope` CHECK ((`custom` = 0 AND `owner_id` IS NULL) OR (`custom` = 1 AND `owner_id` IS NOT NULL)),
  KEY `idx_parent` (`parent_id`),
  KEY `idx_owner` (`owner_id`),
  CONSTRAINT `fk_category_parent` FOREIGN KEY (`parent_id`) REFERENCES `category` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_category_owner` FOREIGN KEY (`owner_id`) REFERENCES `user` (`id`) ON DELETE RESTRICT
) ENGINE = InnoDB COMMENT '论文分类表（树形）';

-- ------------------------------------------------------------
-- T4 标签表 + 论文-标签关联
-- ------------------------------------------------------------
CREATE TABLE `tag` (
  `id`         BIGINT      NOT NULL AUTO_INCREMENT,
  `name`       VARCHAR(50) NOT NULL,
  `color`      VARCHAR(20) NOT NULL COMMENT '十六进制色值，如 #2563EB',
  `custom`     TINYINT(1)  NOT NULL DEFAULT 0 COMMENT '1 = 用户自建',
  `owner_id`   BIGINT      NULL COMMENT '自建归属用户，NULL = 平台级',
  `created_at` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `owner_scope` BIGINT GENERATED ALWAYS AS (IFNULL(`owner_id`, 0)) STORED,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tag_scope_name` (`owner_scope`, `name`),
  CONSTRAINT `ck_tag_scope` CHECK ((`custom` = 0 AND `owner_id` IS NULL) OR (`custom` = 1 AND `owner_id` IS NOT NULL)),
  KEY `idx_owner` (`owner_id`),
  CONSTRAINT `fk_tag_owner` FOREIGN KEY (`owner_id`) REFERENCES `user` (`id`) ON DELETE RESTRICT
) ENGINE = InnoDB COMMENT '论文标签表';

-- ------------------------------------------------------------
-- T2 论文表（核心实体）
-- ------------------------------------------------------------
CREATE TABLE `paper` (
  `id`               BIGINT        NOT NULL AUTO_INCREMENT,
  `title`            VARCHAR(255)  NOT NULL,
  `authors`          JSON          NOT NULL COMMENT '作者数组 ["张三","李四"]',
  `abstract`         TEXT          NOT NULL,
  `doi`              VARCHAR(120)  NULL,
  `journal`          VARCHAR(255)  NULL COMMENT '期刊/会议名',
  `year`             SMALLINT UNSIGNED NOT NULL,
  `keywords`         JSON          NOT NULL COMMENT '关键词数组，空数组用 []',
  `category_id`      BIGINT        NULL,
  `visibility`       VARCHAR(10)   NOT NULL DEFAULT 'private' COMMENT 'private | shared',
  `uploader_id`       BIGINT        NOT NULL,
  `uploaded_at`      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `downloads`        INT UNSIGNED  NOT NULL DEFAULT 0,
  `file_size_mb`     DECIMAL(6, 2) NOT NULL DEFAULT 0,
  `page_count`       SMALLINT UNSIGNED NOT NULL DEFAULT 0,
  `review_status`    VARCHAR(10)   NULL COMMENT '私有 NULL；共享 pending | approved | rejected | offline',
  `offline_reason`   VARCHAR(12)   NULL COMMENT 'withdrawn 用户撤回 | moderation 管理员下架',
  `deleted_at`       DATETIME      NULL COMMENT '逻辑删除，禁止访问 PDF；保留个人数据和历史',
  `version`          INT UNSIGNED NOT NULL DEFAULT 0,
  `review_comment`   VARCHAR(500)  NULL COMMENT '驳回/下架原因，仅上传者与管理员可见',
  `file_path`        VARCHAR(255)  NULL COMMENT 'PDF 服务器存储路径',
  `file_sha256`      CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '由服务端计算；仅演示缺失文件时允许为空',
  `doi_normalized`   VARCHAR(120) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '应用去 DOI URL 前缀、trim、转小写',
  `copyright_agreed` TINYINT(1)    NOT NULL DEFAULT 0 COMMENT '上传时版权授权确认',
  `copyright_agreed_at` DATETIME NULL,
  `link`             VARCHAR(500)  NULL COMMENT '原文链接',
  PRIMARY KEY (`id`),
  `shared_doi_key` VARCHAR(120) CHARACTER SET ascii COLLATE ascii_bin GENERATED ALWAYS AS
    (CASE WHEN `visibility` = 'shared' AND `deleted_at` IS NULL AND `review_status` <> 'rejected' THEN NULLIF(`doi_normalized`, '') ELSE NULL END) STORED,
  `shared_file_key` CHAR(64) CHARACTER SET ascii COLLATE ascii_bin GENERATED ALWAYS AS
    (CASE WHEN `visibility` = 'shared' AND `deleted_at` IS NULL AND `review_status` <> 'rejected' THEN `file_sha256` ELSE NULL END) STORED,
  UNIQUE KEY `uk_shared_doi` (`shared_doi_key`),
  UNIQUE KEY `uk_shared_file` (`shared_file_key`),
  CONSTRAINT `ck_paper_state` CHECK (
    (`visibility` = 'private' AND `review_status` IS NULL AND `offline_reason` IS NULL) OR
    (`visibility` = 'shared' AND `review_status` IS NOT NULL AND `review_status` IN ('pending', 'approved', 'rejected', 'offline') AND
      ((`review_status` = 'offline' AND `offline_reason` IS NOT NULL AND `offline_reason` IN ('withdrawn', 'moderation')) OR
       (`review_status` <> 'offline' AND `offline_reason` IS NULL)))),
  CONSTRAINT `ck_paper_authors` CHECK (JSON_TYPE(`authors`) = 'ARRAY' AND JSON_LENGTH(`authors`) > 0),
  CONSTRAINT `ck_paper_keywords` CHECK (JSON_TYPE(`keywords`) = 'ARRAY'),
  KEY `idx_shared_uploaded` (`visibility`, `review_status`, `deleted_at`, `uploaded_at`, `id`),
  KEY `idx_shared_downloads` (`visibility`, `review_status`, `deleted_at`, `downloads`, `id`),
  KEY `idx_status_visibility` (`review_status`, `visibility`),
  KEY `idx_title` (`title`),
  KEY `idx_doi` (`doi`),
  KEY `idx_uploader` (`uploader_id`),
  KEY `idx_category` (`category_id`),
  KEY `idx_year` (`year`),
  FULLTEXT KEY `ft_search` (`title`, `abstract`) WITH PARSER ngram COMMENT '中文全文检索（可选中缀模糊的替代方案）',
  CONSTRAINT `fk_paper_category` FOREIGN KEY (`category_id`) REFERENCES `category` (`id`) ON DELETE SET NULL,
  CONSTRAINT `fk_paper_uploader` FOREIGN KEY (`uploader_id`) REFERENCES `user` (`id`)
) ENGINE = InnoDB COMMENT '论文表';

-- ------------------------------------------------------------
-- T4 论文-标签关联表（多对多）
-- ------------------------------------------------------------
CREATE TABLE `paper_tag` (
  `paper_id` BIGINT NOT NULL,
  `tag_id`   BIGINT NOT NULL,
  PRIMARY KEY (`paper_id`, `tag_id`),
  KEY `idx_tag` (`tag_id`),
  CONSTRAINT `fk_pt_paper` FOREIGN KEY (`paper_id`) REFERENCES `paper` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_pt_tag` FOREIGN KEY (`tag_id`) REFERENCES `tag` (`id`) ON DELETE CASCADE
) ENGINE = InnoDB COMMENT '论文-标签关联表';

-- ------------------------------------------------------------
-- T5 收藏表
-- ------------------------------------------------------------
CREATE TABLE `favorite` (
  `id`           BIGINT   NOT NULL AUTO_INCREMENT,
  `user_id`      BIGINT   NOT NULL,
  `paper_id`     BIGINT   NOT NULL,
  `favorited_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_paper` (`user_id`, `paper_id`),
  CONSTRAINT `fk_fav_user` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_fav_paper` FOREIGN KEY (`paper_id`) REFERENCES `paper` (`id`) ON DELETE RESTRICT
) ENGINE = InnoDB COMMENT '收藏表';

-- ------------------------------------------------------------
-- T6 阅读记录表
-- ------------------------------------------------------------
CREATE TABLE `reading_record` (
  `id`           BIGINT      NOT NULL AUTO_INCREMENT,
  `user_id`      BIGINT      NOT NULL,
  `paper_id`     BIGINT      NOT NULL,
  `status`       VARCHAR(10) NOT NULL DEFAULT 'unread' COMMENT 'unread | reading | read | paused',
  `added_at`     DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `last_read_at` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT `ck_reading_status` CHECK (`status` IN ('unread', 'reading', 'read', 'paused')),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_paper` (`user_id`, `paper_id`),
  KEY `idx_paper` (`paper_id`),
  CONSTRAINT `fk_rr_user` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_rr_paper` FOREIGN KEY (`paper_id`) REFERENCES `paper` (`id`) ON DELETE RESTRICT
) ENGINE = InnoDB COMMENT '阅读管理记录表';

-- ------------------------------------------------------------
-- T7 阅读笔记表（仅本人可见，应用层强制 user_id 过滤）
-- ------------------------------------------------------------
CREATE TABLE `note` (
  `id`         BIGINT       NOT NULL AUTO_INCREMENT,
  `user_id`    BIGINT       NOT NULL,
  `paper_id`   BIGINT       NOT NULL,
  `title`      VARCHAR(300) NOT NULL,
  `paper_title` VARCHAR(255) NOT NULL COMMENT '关联时标题快照',
  `content`    TEXT         NOT NULL,
  `created_at` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_user_paper` (`user_id`, `paper_id`),
  CONSTRAINT `fk_note_user` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_note_paper` FOREIGN KEY (`paper_id`) REFERENCES `paper` (`id`) ON DELETE RESTRICT
) ENGINE = InnoDB COMMENT '阅读笔记表';

-- ------------------------------------------------------------
-- T8 审核记录表（只追加流水；当前状态冗余在 paper.review_status）
-- ------------------------------------------------------------
CREATE TABLE `review_record` (
  `id`          BIGINT       NOT NULL AUTO_INCREMENT,
  `paper_id`    BIGINT       NOT NULL,
  `reviewer_id` BIGINT       NOT NULL,
  `paper_title` VARCHAR(255) NOT NULL COMMENT '审核时标题快照',
  `uploader_id` BIGINT NOT NULL COMMENT '审核时上传者快照',
  `action`      VARCHAR(10)  NOT NULL COMMENT 'approved | rejected',
  `comment`     VARCHAR(500) NULL,
  `reviewed_at` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_paper` (`paper_id`),
  KEY `idx_reviewer` (`reviewer_id`),
  CONSTRAINT `fk_rev_paper` FOREIGN KEY (`paper_id`) REFERENCES `paper` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_rev_user` FOREIGN KEY (`reviewer_id`) REFERENCES `user` (`id`)
) ENGINE = InnoDB COMMENT '审核记录表';

-- ------------------------------------------------------------
-- T9 疑似重复对（仅允许 pending 转终态；已处理不可覆盖）
-- ------------------------------------------------------------
CREATE TABLE `duplicate_pair` (
  `id`            BIGINT        NOT NULL AUTO_INCREMENT,
  `original_paper_id` BIGINT NOT NULL,
  `new_paper_id` BIGINT NOT NULL,
  `original_paper_title` VARCHAR(255) NOT NULL,
  `new_paper_title` VARCHAR(255) NOT NULL,
  `doi_match` TINYINT(1) NOT NULL DEFAULT 0,
  `file_hash_match` TINYINT(1) NOT NULL DEFAULT 0,
  `submitted_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `title_similarity` DECIMAL(5, 2) NOT NULL COMMENT '标题相似度 0-100',
  `reason`        VARCHAR(255)  NOT NULL COMMENT '判定原因（相同 DOI/标题相似等）',
  `status`        VARCHAR(10)   NOT NULL DEFAULT 'pending' COMMENT 'pending | duplicate | distinct | linked',
  `handler_id`    BIGINT        NULL,
  `handler_note`  VARCHAR(500)  NULL,
  `handled_at`    DATETIME      NULL,
  CONSTRAINT `ck_duplicate_status` CHECK (`status` IN ('pending', 'duplicate', 'distinct', 'linked')),
  PRIMARY KEY (`id`),
  KEY `idx_status` (`status`),
  UNIQUE KEY `uk_duplicate_pair` (`original_paper_id`, `new_paper_id`),
  CONSTRAINT `ck_duplicate_pair` CHECK (`original_paper_id` <> `new_paper_id` AND `title_similarity` BETWEEN 0 AND 100),
  KEY `idx_original` (`original_paper_id`),
  KEY `idx_new` (`new_paper_id`),
  CONSTRAINT `fk_dp_original` FOREIGN KEY (`original_paper_id`) REFERENCES `paper` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_dp_new` FOREIGN KEY (`new_paper_id`) REFERENCES `paper` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_dp_handler` FOREIGN KEY (`handler_id`) REFERENCES `user` (`id`)
) ENGINE = InnoDB COMMENT '疑似重复论文对';

-- ------------------------------------------------------------
-- T10 举报表（处理记录页查询 status=resolved）
-- ------------------------------------------------------------
CREATE TABLE `report` (
  `id`          BIGINT       NOT NULL AUTO_INCREMENT,
  `paper_id`    BIGINT       NOT NULL COMMENT '逻辑删除后保留关联',
  `paper_title` VARCHAR(255) NOT NULL COMMENT '论文标题快照（防论文删除后失义）',
  `reporter_id` BIGINT       NOT NULL,
  `type`        VARCHAR(20)  NOT NULL COMMENT 'copyright | content | duplicate | other',
  `reason`      VARCHAR(500) NOT NULL,
  `created_at`  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `status`      VARCHAR(10)  NOT NULL DEFAULT 'pending' COMMENT 'pending | resolved',
  `result`      VARCHAR(500) NULL COMMENT '处理结果说明',
  `handler_id`  BIGINT       NULL,
  `handled_at`  DATETIME     NULL,
  PRIMARY KEY (`id`),
  KEY `idx_status` (`status`),
  KEY `idx_paper` (`paper_id`),
  CONSTRAINT `fk_rp_paper` FOREIGN KEY (`paper_id`) REFERENCES `paper` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_rp_reporter` FOREIGN KEY (`reporter_id`) REFERENCES `user` (`id`),
  CONSTRAINT `fk_rp_handler` FOREIGN KEY (`handler_id`) REFERENCES `user` (`id`)
) ENGINE = InnoDB COMMENT '论文举报表';

-- ------------------------------------------------------------
-- T11 操作日志表（服务端写操作自动留痕）
-- ------------------------------------------------------------
CREATE TABLE `operation_log` (
  `id`        BIGINT       NOT NULL AUTO_INCREMENT,
  `user_id`   BIGINT       NULL COMMENT '操作人，NULL = 系统',
  `user_name` VARCHAR(50)  NOT NULL COMMENT '操作人姓名快照',
  `action`    VARCHAR(50)  NOT NULL COMMENT '操作类型，如 论文审核/论文下架/用户禁用',
  `target`    VARCHAR(255) NOT NULL COMMENT '操作对象描述',
  `result`    VARCHAR(10)  NOT NULL DEFAULT '成功' COMMENT '成功 | 失败',
  `time`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_time` (`time`),
  KEY `idx_action` (`action`),
  KEY `idx_user` (`user_id`)
) ENGINE = InnoDB COMMENT '操作日志表';
