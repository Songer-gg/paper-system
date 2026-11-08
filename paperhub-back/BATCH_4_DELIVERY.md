# 第4批交付与验收（2026-11-08）

本批实现U5/U6/U10–U12、M7–M20，共19个端点，累计39/67。前端继续使用Mock；真实Service适配与页面联调按第9批实施。本批复用现有12张表，没有新增API、数据库表、运行时依赖或追加备份。

## 接口范围

| 接口 | 实现 |
| --- | --- |
| U5 PATCH /papers/{id}/review-flow | 本人share/unshare/resubmit，版权与分类校验，撤回保留共享记录 |
| U6 POST /papers/duplicate-check | DOI、文件哈希、标题提示；不可见命中不返回Paper或资源ID |
| U10 GET /shared-papers | 仅approved/未删除共享，服务端分页、多条件筛选、稳定排序 |
| U11 GET /shared-papers/{id} | 仅已通过共享，即使上传者或管理员也遵守此入口范围 |
| U12 GET /shared-papers/filter-options | 从已通过共享池计算分类及祖先、标签、年份、期刊 |
| M7/M8 | 共享管理列表及管理员论文详情；私有详情使用既有独立审计 |
| M9/M10/M11 | 管理员下架、恢复、逻辑删除；撤回资源不可由管理员恢复 |
| M12/M13/M14/M15 | 三态分组、审核通过/驳回、不可变标题/上传者快照及姓名关联 |
| M16/M17/M18/M19/M20 | 疑似对查询、确认重复、确认非重复、关联原文、处理历史 |

M7保持共享资源页范围；M8/M11可管理私有论文。管理员他人收藏/阅读/笔记仍属第6批，不将本批权限覆盖描述为全部个人数据接口已完成。

## 共享状态与版权

- U5仅上传者可操作，管理员不能通过用户流转接口冒充上传者。
- 私有share必须显式copyrightAgreed=true；私有分类/标签须先通过U3调整为平台项。未分类null可保留，不把个人标签公开。
- unshare允许pending/approved，结果保持shared/offline/withdrawn，仍在我的共享列表。管理员restore拒绝withdrawn；上传者resubmit拒绝moderation。
- resubmit只接受rejected或offline/withdrawn，回到pending并清除旧原因。历史记录缺少版权确认时必须传true，显式false总是拒绝；已有有效确认且省略参数时可沿用。
- 审核通过同时要求上传者确认及管理员copyrightConfirmed=true；不由后端补造勾选。M9既有接口没有原因输入，写入固定“管理员下架”原因并保留审核历史。
- share/resubmit/approve/restore重新检查真实受控PDF、SHA-256、大小、元数据、平台分类标签及精确重复。文件缺失、变化、遗留无哈希样例不能发布；元数据编辑不替换PDF。
- 按API原有输入规则补齐年份上限：1000–当前年+1，使用应用Clock；第2批原有9999上限不再作为上传/编辑上限。
- 旧库4条缺少版权确认的共享样例继续保留rejected，原/storage/*.pdf不存在；需要真实上传和确认后才能进入共享库。本批没有自动发布旧样例。

## 查重和历史

精确DOI由服务器去前缀、trim并转小写，PDF哈希来自实际字节；U6客户端哈希仅作预检。pending/approved/offline的未删除共享占用条件唯一键，rejected/私有/已删除不占用。最终检查与MySQL生成列唯一键共同约束，并发相同共享提交只留一份。

标题继续使用规范化二元字符Jaccard相似度>=80%创建pending疑似对。扫描双方已有关系，避免反向重复记录；新提交方始终是newPaperId，不能按自增ID大小判断，覆盖旧私有论文后续转共享的场景。

任一侧存在pending疑似对时不能审核通过。审核/恢复最终扫描补发现证据后，事务提交疑似对和失败日志，Controller再返回400/1006；不创建“审核通过”流水，不丢掉需要管理员处理的证据。

- distinct只关闭疑似对，不自动批准；相同DOI/文件不能标记非重复。
- duplicate/linked驳回仍为pending/approved的新论文，同时写rejected审核流水。原文关联须指向已通过、未删除共享，关联由duplicate_pair保存，不误用外部link URL字段。
- 对已撤回、管理员下架或已删除新论文，仅关闭历史疑似对，保留原状态，不恢复、不覆盖撤回原因。
- 已处理对只能处理一次，重复请求返回409。已确认duplicate/linked且原共享仍有效时，同一不可替换PDF的新论文不能通过resubmit绕过判定。
- 审核标题/上传者ID为审核时快照；重复历史标题来自首次比对快照。重命名或逻辑删除不改写快照。M15关联uploaderName/reviewerName，M20关联handlerName。
- 反馈给上传者的重复原因使用通用说明，不包含其无权查看的原论文标题；linked只指向当时公开可见的原共享记录。

## 检索与数据范围

q覆盖标题、作者、摘要、DOI、期刊和关键词，作者/关键词通过MySQL JSON_TABLE逐项检索。LIKE中的%、_和!按字面转义；SQL值绑定，排序列使用白名单。

categoryId包含平台后代；tagIds为逗号分隔正整数，最多50项，语义为OR。标签EXISTS过滤使每篇仅计一次，total在分页前计算。page>=1，pageSize默认10、上限100；越界页返回空list及正确total。sort仅uploaded-desc/downloads-desc/year-desc，并追加id倒序作为稳定排序。

U10/U11/U12无论角色，都只来自approved共享池；自己的私有、待审、驳回、下架和所有已删除论文均不进入。U12仅返回有公开论文的分类及祖先、标签、年份和非空期刊。M7仍只查共享，但可筛选全部审核状态、上传者姓名及分类后代。

## 并发、事务及审计

共享目录写操作使用MySQL GET_LOCK协调同一数据库主节点上的应用实例，顺序为“共享目录锁 → 分类/标签锁 → 论文主键 → 疑似对”。共享上传先解析PDF，再取锁；私有上传不占共享目录锁。已有论文编辑/删除也遵循目录锁，防止与共享、审核及重复处理交错。

命名锁是连接级资源，不随COMMIT自动释放；在事务完成回调中释放同一连接的锁，支持回滚及事务内重入去重。等待最多5秒，超时返回409；获取结果不确定或释放失败时淘汰连接，防止锁随连接池复用而泄漏。当前范围为单MySQL主节点，不能当作跨多个数据库主节点的协调机制。

实现及验证依据：[MySQL命名锁说明](https://dev.mysql.com/doc/refman/8.0/en/locking-functions.html)、[Spring事务完成回调](https://docs.spring.io/spring-framework/docs/5.3.31/javadoc-api/org/springframework/transaction/support/TransactionSynchronization.html)。

读取待写论文改为完整FOR UPDATE当前读；不会在重复对查询建立的旧快照中重新读旧论文，再覆盖并发下载已递增的计数。新增可控交错测试验证“先查重复对→另一请求完成下载→处理重复”的计数保留。

状态、审核流水、处理结果和成功日志同事务；注入流水/日志故障时整体回滚。管理员私有元数据访问沿用REQUIRES_NEW审计，失败不返回内容。日志仅资源ID、所属用户及动作，不写正文或PDF字节。

## 验证

- JDK 1.8.0_471 / Maven Wrapper 3.9.9，15项单元测试、96项真实MySQL集成测试，共111项通过。
- 比第3批增加4项命名锁单元测试、38项共享/审核/检索集成测试，原69项继续通过。
- 覆盖版权双重确认、私人分类限制、撤回恢复保护、管理员下架隔离、旧样例缺失PDF、隐藏命中防泄漏、实际哈希复核。
- 覆盖精确重复并发、标题并发、补发现证据持久化、旧私有ID转换、重复判定一次性、审核与撤回竞争、双重审核、下载与处理并发。
- 覆盖审核流水/操作日志失败回滚、独立私有审计故障、快照改名/删除后保留，以及分页、标签OR、祖先范围、JSON检索、字面通配符、中文上传者筛选。
- Java8可执行JAR经真实Tomcat HTTP执行本批全部19个端点，并保留K1–K11及PDF端到端回归。
- TypeScript和API契约回归通过。62个运行时JAR、17566个基础class通过Java8字节码检查。
- OSV扫描仍为19个组件、113个去重公告，生产上线门禁未通过，见SECURITY_AUDIT.md。

测试只写随机端口的临时MySQL与临时文件目录；没有在用户现有库执行测试CRUD或迁移。原始报告位于target/surefire-reports、target/failsafe-reports、target/mysql-verification.log、target/batch4-validation.json；打包HTTP日志为target/application-smoke.log。

## 本机验证与后续

新版后端API已重新运行于http://127.0.0.1:8080/api/v1，进程ID记录在target/local-application.pid。本机230项结构复查一致；普通用户/管理员登录、共享搜索/筛选项、审核分组/历史、疑似对/处理历史、中文姓名筛选和401/403验证通过，结果在target/batch4-local-http-check.json。

当前已有库公开共享数为0，管理共享记录4条且均为rejected，符合第2批缺少版权确认的迁移处理。页面仍来自Vue开发服务及Mock，8080继续是受保护API地址。

下一批实现R1–R9收藏、阅读状态与笔记隔离，保留失效论文占位和笔记正文；继续沿用逻辑删除及权限规则。

