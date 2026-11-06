-- 仅开发空库导入一次；包含公开演示凭据，生产环境禁止导入。
USE paperhub;

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
-- 仅提供少量样例；前端 Mock 不会自动迁移，PDF 需另行导入）
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

INSERT INTO `note` (`user_id`, `paper_id`, `paper_title`, `title`, `content`, `created_at`) VALUES
(1, 2001, 'Attention Is All You Need：Transformer 架构综述', '《Attention Is All You Need》阅读笔记',
 '自注意力机制是核心：Q/K/V 三矩阵…后续 RoPE 位置编码可结合阅读。', '2025-04-20 21:35:00');

-- 举报样例（含待处理）
INSERT INTO `report` (`id`, `paper_id`, `paper_title`, `reporter_id`, `type`, `reason`, `status`) VALUES
(1, 2002, '一种面向低资源场景的提示学习框架', 1, 'copyright', '该论文疑似未经授权转载他人成果，请核实。', 'pending');

-- 疑似重复对样例
INSERT INTO `duplicate_pair` (`id`, `original_paper_id`, `new_paper_id`, `original_paper_title`, `new_paper_title`, `title_similarity`, `reason`, `status`) VALUES
(1, 2001, 3001, 'Attention Is All You Need：Transformer 架构综述', '基于联邦学习的医疗数据隐私保护方法', 62.50, '摘要关键词重合度较高（Transformer、序列建模）', 'pending');

UPDATE `paper` SET `doi_normalized` = LOWER(TRIM(`doi`)), `copyright_agreed` = 1, `copyright_agreed_at` = `uploaded_at` WHERE `visibility` = 'shared';
