-- ============================================================
-- PaperHub 数据库 DDL（MySQL 8）
-- 依据：paperhub-api/api-spec.md + db/er-design.md
-- 引擎：InnoDB ｜ 字符集：utf8mb4 ｜ 库名：paperhub
-- ============================================================

DROP DATABASE IF EXISTS paperhub;
CREATE DATABASE paperhub DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
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
  `registered_at`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_username` (`username`),
  UNIQUE KEY `uk_email` (`email`),
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
  PRIMARY KEY (`id`),
  KEY `idx_parent` (`parent_id`),
  KEY `idx_owner` (`owner_id`),
  CONSTRAINT `fk_category_parent` FOREIGN KEY (`parent_id`) REFERENCES `category` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_category_owner` FOREIGN KEY (`owner_id`) REFERENCES `user` (`id`) ON DELETE CASCADE
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
  PRIMARY KEY (`id`),
  KEY `idx_owner` (`owner_id`),
  CONSTRAINT `fk_tag_owner` FOREIGN KEY (`owner_id`) REFERENCES `user` (`id`) ON DELETE CASCADE
) ENGINE = InnoDB COMMENT '论文标签表';

-- ------------------------------------------------------------
-- T2 论文表（核心实体）
-- ------------------------------------------------------------
CREATE TABLE `paper` (
  `id`               BIGINT        NOT NULL AUTO_INCREMENT,
  `title`            VARCHAR(255)  NOT NULL,
  `authors`          JSON          NOT NULL COMMENT '作者数组 ["张三","李四"]',
  `abstract`         TEXT          NULL,
  `doi`              VARCHAR(120)  NULL,
  `journal`          VARCHAR(255)  NULL COMMENT '期刊/会议名',
  `year`             SMALLINT UNSIGNED NULL,
  `keywords`         JSON          NULL COMMENT '关键词数组',
  `category_id`      BIGINT        NULL,
  `visibility`       VARCHAR(10)   NOT NULL DEFAULT 'private' COMMENT 'private | shared',
  `uploader_id`       BIGINT        NOT NULL,
  `uploaded_at`      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `downloads`        INT UNSIGNED  NOT NULL DEFAULT 0,
  `file_size_mb`     DECIMAL(6, 2) NOT NULL DEFAULT 0,
  `page_count`       SMALLINT UNSIGNED NOT NULL DEFAULT 0,
  `review_status`    VARCHAR(10)   NOT NULL DEFAULT 'pending' COMMENT 'pending | approved | rejected | offline',
  `review_comment`   VARCHAR(500)  NULL COMMENT '驳回/下架原因，仅上传者与管理员可见',
  `file_path`        VARCHAR(255)  NULL COMMENT 'PDF 服务器存储路径',
  `copyright_agreed` TINYINT(1)    NOT NULL DEFAULT 0 COMMENT '上传时版权授权确认',
  `link`             VARCHAR(500)  NULL COMMENT '原文链接',
  PRIMARY KEY (`id`),
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
  CONSTRAINT `fk_pt_paper` FOREIGN KEY (`paper_id`) REFERENCES `paper` (`id`) ON DELETE CASCADE,
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
  CONSTRAINT `fk_fav_paper` FOREIGN KEY (`paper_id`) REFERENCES `paper` (`id`) ON DELETE CASCADE
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
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_paper` (`user_id`, `paper_id`),
  KEY `idx_paper` (`paper_id`),
  CONSTRAINT `fk_rr_user` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_rr_paper` FOREIGN KEY (`paper_id`) REFERENCES `paper` (`id`) ON DELETE CASCADE
) ENGINE = InnoDB COMMENT '阅读管理记录表';

-- ------------------------------------------------------------
-- T7 阅读笔记表（仅本人可见，应用层强制 user_id 过滤）
-- ------------------------------------------------------------
CREATE TABLE `note` (
  `id`         BIGINT       NOT NULL AUTO_INCREMENT,
  `user_id`    BIGINT       NOT NULL,
  `paper_id`   BIGINT       NOT NULL,
  `title`      VARCHAR(100) NOT NULL,
  `content`    TEXT         NOT NULL,
  `created_at` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_user_paper` (`user_id`, `paper_id`),
  CONSTRAINT `fk_note_user` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_note_paper` FOREIGN KEY (`paper_id`) REFERENCES `paper` (`id`) ON DELETE CASCADE
) ENGINE = InnoDB COMMENT '阅读笔记表';

-- ------------------------------------------------------------
-- T8 审核记录表（只追加流水；当前状态冗余在 paper.review_status）
-- ------------------------------------------------------------
CREATE TABLE `review_record` (
  `id`          BIGINT       NOT NULL AUTO_INCREMENT,
  `paper_id`    BIGINT       NOT NULL,
  `reviewer_id` BIGINT       NOT NULL,
  `action`      VARCHAR(10)  NOT NULL COMMENT 'approved | rejected',
  `comment`     VARCHAR(500) NULL,
  `reviewed_at` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_paper` (`paper_id`),
  KEY `idx_reviewer` (`reviewer_id`),
  CONSTRAINT `fk_rev_paper` FOREIGN KEY (`paper_id`) REFERENCES `paper` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_rev_user` FOREIGN KEY (`reviewer_id`) REFERENCES `user` (`id`)
) ENGINE = InnoDB COMMENT '审核记录表';

-- ------------------------------------------------------------
-- T9 疑似重复对（处理记录页由 status != pending 的行映射）
-- ------------------------------------------------------------
CREATE TABLE `duplicate_pair` (
  `id`            BIGINT        NOT NULL AUTO_INCREMENT,
  `old_paper_id`  BIGINT        NULL COMMENT '原共享论文，论文被删后置 NULL 保留记录',
  `new_paper_id`  BIGINT        NULL COMMENT '新上传论文，同上',
  `similarity`    DECIMAL(5, 2) NOT NULL COMMENT '相似度 0-100',
  `reason`        VARCHAR(255)  NOT NULL COMMENT '判定原因（相同 DOI/标题相似等）',
  `status`        VARCHAR(10)   NOT NULL DEFAULT 'pending' COMMENT 'pending | confirmed | distinct | linked',
  `handler_id`    BIGINT        NULL,
  `handler_note`  VARCHAR(500)  NULL,
  `handled_at`    DATETIME      NULL,
  PRIMARY KEY (`id`),
  KEY `idx_status` (`status`),
  KEY `idx_old` (`old_paper_id`),
  KEY `idx_new` (`new_paper_id`),
  CONSTRAINT `fk_dp_old` FOREIGN KEY (`old_paper_id`) REFERENCES `paper` (`id`) ON DELETE SET NULL ON UPDATE CASCADE,
  CONSTRAINT `fk_dp_new` FOREIGN KEY (`new_paper_id`) REFERENCES `paper` (`id`) ON DELETE SET NULL ON UPDATE CASCADE,
  CONSTRAINT `fk_dp_handler` FOREIGN KEY (`handler_id`) REFERENCES `user` (`id`)
) ENGINE = InnoDB COMMENT '疑似重复论文对';

-- ------------------------------------------------------------
-- T10 举报表（处理记录页查询 status=resolved）
-- ------------------------------------------------------------
CREATE TABLE `report` (
  `id`          BIGINT       NOT NULL AUTO_INCREMENT,
  `paper_id`    BIGINT       NULL COMMENT '被举报论文，ON DELETE SET NULL 保留记录',
  `paper_title` VARCHAR(255) NULL COMMENT '论文标题快照（防论文删除后失义）',
  `reporter_id` BIGINT       NOT NULL,
  `type`        VARCHAR(20)  NOT NULL COMMENT 'copyright | content | duplicate | other',
  `reason`      VARCHAR(500) NOT NULL,
  `status`      VARCHAR(10)  NOT NULL DEFAULT 'pending' COMMENT 'pending | resolved',
  `result`      VARCHAR(500) NULL COMMENT '处理结果说明',
  `handler_id`  BIGINT       NULL,
  `handled_at`  DATETIME     NULL,
  PRIMARY KEY (`id`),
  KEY `idx_status` (`status`),
  KEY `idx_paper` (`paper_id`),
  CONSTRAINT `fk_rp_paper` FOREIGN KEY (`paper_id`) REFERENCES `paper` (`id`) ON DELETE SET NULL,
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

-- ============================================================
-- 演示数据初始化（对应前端 mock；密码已为 BCrypt 真实哈希）
-- ============================================================

-- 用户：id=1 演示普通用户（123456）、2-4 mock 论文上传者、id=101 管理员（admin123）
-- 密码为 BCrypt 真实哈希，Spring Security BCryptPasswordEncoder 可直接校验（$2b 前缀兼容）
INSERT INTO `user` (`id`, `username`, `password`, `name`, `email`, `role`, `registered_at`) VALUES
(1,   'researcher',   '$2b$10$KCGf5gdnEn/7KDLYwV5BUuhBOr35I9H5SDEFEmkAfR8xb7dv.yWjO', '林知远', 'researcher@paperhub.cn',  'user',  '2025-03-12 09:30:00'),
(2,   'chensiyu',     '$2b$10$KCGf5gdnEn/7KDLYwV5BUuhBOr35I9H5SDEFEmkAfR8xb7dv.yWjO', '陈思羽', 'chensiyu@paperhub.cn',    'user',  '2025-04-01 10:00:00'),
(3,   'wangmuzhi',    '$2b$10$KCGf5gdnEn/7KDLYwV5BUuhBOr35I9H5SDEFEmkAfR8xb7dv.yWjO', '王牧之', 'wangmuzhi@paperhub.cn',   'user',  '2025-04-18 14:30:00'),
(4,   'zhaoqinglan',  '$2b$10$KCGf5gdnEn/7KDLYwV5BUuhBOr35I9H5SDEFEmkAfR8xb7dv.yWjO', '赵青澜', 'zhaoqinglan@paperhub.cn', 'user',  '2025-05-06 16:20:00'),
(101, 'admin',        '$2b$10$hn0.ee53ElO./R9Xz0D/meeeQEh2FFchoz3U5h6re2djBLT8oCtC2', '平台管理员', 'admin@paperhub.cn',   'admin', '2025-01-01 08:00:00');

-- 平台级分类树（对应前端 mock categories）
INSERT INTO `category` (`id`, `name`, `parent_id`, `description`, `custom`, `owner_id`) VALUES
(1,  '人工智能',            NULL, 'AI 及其子领域',        0, NULL),
(2,  '机器学习',            1,    '传统机器学习方法',    0, NULL),
(3,  '深度学习',            1,    '神经网络与深度模型',  0, NULL),
(4,  '自然语言处理',        1,    'NLP 相关论文',        0, NULL),
(5,  '计算机视觉',          1,    'CV 相关论文',         0, NULL),
(6,  '分布式系统',          NULL, '系统与基础设施',      0, NULL),
(7,  '数据库与存储',        6,    '数据库系统研究',      0, NULL),
(8,  '并行与分布式计算',    6,    '并行算法与调度',      0, NULL),
(9,  '数据挖掘',            2,    '数据挖掘方法与应用', 0, NULL),
(10, '强化学习',            2,    '决策与控制',          0, NULL),
(11, '大语言模型',          3,    'LLM 相关研究',        0, NULL),
(12, '多模态学习',          3,    '视觉-语言模型',       0, NULL),
(13, '论文阅读',            NULL, '论文管理与阅读方法',  0, NULL),
(14, '文献综述',            13,   '综述类论文',          0, NULL),
(15, '阅读笔记',            13,   '笔记与心得',          0, NULL);

-- 平台级标签（对应前端 mock tags）
INSERT INTO `tag` (`id`, `name`, `color`) VALUES
(1,  '深度学习',   '#2563EB'),
(2,  '自然语言处理', '#7C3AED'),
(3,  '强化学习',   '#DB2777'),
(4,  '大语言模型', '#0891B2'),
(5,  '多模态',     '#16A34A'),
(6,  '知识图谱',   '#EA580C'),
(7,  '时序分析',   '#CA8A04'),
(8,  '基准测试',   '#4F46E5'),
(9,  '联邦学习',   '#0D9488'),
(10, '隐私计算',   '#9333EA'),
(11, '分布式训练', '#DC2626'),
(12, '模型压缩',   '#65A30D');

-- 论文样例（共享已过审 + 待审核各若干，uploader_id 对应 mock 用户；
-- 完整 20+ 共享 / 10+ 私有 / 5 篇待审数据由前端 mocks 迁移脚本导入）
INSERT INTO `paper` (`id`, `title`, `authors`, `abstract`, `doi`, `journal`, `year`,
                     `keywords`, `category_id`, `visibility`, `uploader_id`, `uploaded_at`,
                     `downloads`, `file_size_mb`, `page_count`, `review_status`, `file_path`) VALUES
(2001, 'Attention Is All You Need：Transformer 架构综述',
        '["Vaswani, A.","Shazeer, N."]', 'Transformer 自注意力架构的开创性工作…',
        '10.5555/neurips.2017.1', 'NeurIPS', 2017, '["自注意力","序列建模"]',
        11, 'shared', 1, '2025-04-10 14:20:00', 156, 2.35, 15, 'approved', '/storage/2001.pdf'),
(2002, '一种面向低资源场景的提示学习框架',
        '["刘天明","王思远"]', '针对低资源 NLP 任务的提示学习框架…',
        '10.1000/demo.2024.02', '中文信息学报', 2024, '["提示学习","低资源"]',
        4, 'shared', 2, '2025-05-02 09:12:00', 48, 1.88, 12, 'approved', '/storage/2002.pdf'),
(3001, '基于联邦学习的医疗数据隐私保护方法',
        '["陈静怡"]', '联邦学习在医疗数据共享中的隐私保护…',
        '10.1000/demo.2026.11', '计算机学报', 2026, '["联邦学习","隐私计算"]',
        9, 'shared', 3, '2026-10-12 16:40:00', 0, 2.66, 14, 'pending', '/storage/3001.pdf'),
(3002, '大规模多模态模型的训练效率优化实践',
        '["Daniel Cruz","Elena Volkov"]', '多模态大模型训练效率优化…',
        '10.1000/demo.2026.14', 'ICML', 2026, '["多模态","分布式训练"]',
        12, 'shared', 4, '2026-10-15 11:05:00', 0, 3.12, 18, 'pending', '/storage/3002.pdf');

-- 论文-标签关联样例
INSERT INTO `paper_tag` (`paper_id`, `tag_id`) VALUES
(2001, 4), (2001, 1), (2002, 2), (2001, 6),
(3001, 9), (3001, 10), (3002, 5), (3002, 11);

-- 阅读记录 / 收藏 / 笔记样例（当前用户 id=1）
INSERT INTO `reading_record` (`user_id`, `paper_id`, `status`, `added_at`, `last_read_at`) VALUES
(1, 2001, 'read',    '2025-04-12 10:00:00', '2025-04-20 21:30:00'),
(1, 2002, 'reading', '2025-05-03 08:20:00', '2025-05-10 14:00:00');

INSERT INTO `favorite` (`user_id`, `paper_id`, `favorited_at`) VALUES
(1, 2001, '2025-04-11 09:00:00');

INSERT INTO `note` (`user_id`, `paper_id`, `title`, `content`, `created_at`) VALUES
(1, 2001, '《Attention Is All You Need》阅读笔记',
 '自注意力机制是核心：Q/K/V 三矩阵…后续 RoPE 位置编码可结合阅读。', '2025-04-20 21:35:00');

-- 举报样例（含待处理）
INSERT INTO `report` (`id`, `paper_id`, `paper_title`, `reporter_id`, `type`, `reason`, `status`) VALUES
(1, 2002, '一种面向低资源场景的提示学习框架', 1, 'copyright', '该论文疑似未经授权转载他人成果，请核实。', 'pending');

-- 疑似重复对样例
INSERT INTO `duplicate_pair` (`id`, `old_paper_id`, `new_paper_id`, `similarity`, `reason`, `status`) VALUES
(1, 2001, 2002, 62.50, '摘要关键词重合度较高（Transformer、序列建模）', 'pending');
