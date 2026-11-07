# 第2批交付与验收（2026-11-08）

本批完成文件与论文核心，共8个业务端点：U1–U4、U7–U9、P3。加上第1批A1，目前9/67端点已实现。前端仍使用Mock；本批通过HTTP及集成测试验证Java后端，不声称完成页面联调。

## 已实现

| 接口 | 实现行为 |
| --- | --- |
| U1 GET /papers/mine | token本人的未删除论文，privatePapers/sharedPapers两组；不接受切换userId |
| U2 GET /papers/{id} | 本人、approved共享或管理员可见；管理员私有元数据读取先持久化审计 |
| U3 PATCH /papers/{id} | 白名单元数据；分类可清空；标签原子替换；有效编辑approved论文转pending |
| U4 DELETE /papers/{id} | 逻辑删除；保留收藏、阅读、笔记、审核及处理历史；提交后清理PDF |
| U7 POST /papers | multipart file + application/json payload；真实PDF解析、SHA-256、页数/字节计算；私有/待审共享 |
| U8 POST /papers/{id}/download | PDF attachment；授权并确认文件存在后原子递增downloads |
| U9 GET /papers/{id}/file | PDF inline；不增加downloads；与详情使用同一可见性检查 |
| P3 PUT /me/avatar | PNG/JPEG实际解码及PNG重编码；随机文件名；仅修改token本人 |

上传共享必须确认版权，精确DOI/文件重复由MySQL生成列唯一键兜底。非精确标题相似使用规范化二元字符集合Jaccard相似度，>=80%保存待处理疑似对，不直接拒绝上传。第4批必须在审核时再次检测，覆盖并发上传的标题候选；本批没有实现审核批准入口。

U5共享状态流转、U6预检、共享检索和管理员审核统一留第4批，避免把预检提示当作完整审核流程。现有接口契约不新增端点；PaperEditPayload.categoryId补充null以表达清空分类。

输入边界：title<=255、authors 1–50项/每项<=120、keywords<=50项/每项<=120、abstract UTF-8<=60000字节、year 1000–9999、DOI<=120且符合规范化DOI格式、journal<=255、tagIds<=50且不重复、link<=500且为http/https。ID为正的JS安全整数；未知字段、错误数组类型和浮点整数均拒绝。Jackson 2.13原生字符串反序列化会把数组数字转为字符串，本批用严格反序列化器实际阻断。

## 存储、审计和补偿

- 默认存储目录为后端工作目录下data/storage，可用PAPERHUB_STORAGE_ROOT指定。必须使用持久化、服务账号专有的磁盘目录，不作静态资源映射。服务器只接受内部生成的papers/UUID.pdf和avatars/UUID.png键；旧绝对路径和路径穿越请求不可读。
- PDF<=100MiB，以真实读取字节再次检查；拒绝伪PDF、不能解析、加密、零页及>65535页的文档。解析使用PDFBox 2.0.35，限制内存并使用独立临时目录。
- 头像<=2MiB、最大2048×2048，只接受实际PNG/JPEG，去除原始元信息后重编码。数据库avatar只存内部短键；P3的MyProfile.avatar返回data:image/png;base64内容供现有img展示，不把dataURL存入VARCHAR，也不新增公开头像接口。第6批P1同样从受控存储生成该字段。
- 管理员私有元数据/预览/下载访问在REQUIRES_NEW事务中记录actor、paperId、ownerId、动作、时间和结果。审计失败不返回正文；外层回滚不撤销审计。日志没有PDF、摘要或笔记正文。
- 上传新文件前先在.cleanup写入持久化任务；数据库回滚则删除文件，失败保留任务。删除和头像替换同样先记录清理任务，提交后删除旧文件。后台每30秒重试，每轮最多处理100个成熟任务。
- 后台任务等待至少10分钟，业务写事务超时60秒；重新检查数据库引用后才清理，保留仍被有效论文/头像引用的文件。进程重启可继续处理；存储目录及.cleanup须一起保留。旧演示绝对路径不进行不受控文件删除。
- 逻辑删除后，文件即使尚未物理清理也不能重新授权。已开始的传输不能追溯撤回；下载计数代表获准开始传输，不保证客户端完整保存。敏感流传输中断另写失败审计；若此时审计系统也不可用，仅能写服务器故障日志，不能回滚已发字节。
- 当前为同一持久化目录的单实例部署。多实例共享存储、分布式任务领取、上传配额与孤儿scratch文件运维归后续部署设计。

## 本机旧库迁移

本机MySQL 8.0.43原为v1.0结构：12张表，缺少token_version、逻辑删除、快照、条件唯一键及CHECK等。已提供独立增量脚本，未重跑schema.sql：

- paperhub-api/db/migrations/v1.0_to_v1.1.sql
- scripts/migrate_local_mysql.py：精确旧结构指纹检查；在临时MySQL导入已存在快照，演练迁移；与全新schema逐项核对230项结构；比对原行数据后才允许apply。
- scripts/check_local_mysql.py：只读比较现有库与新空库结构，不输出账号凭据或行内容。
- target/migration-verification.json、target/local-database-check.json为本机验证结果。

本机后端已在http://127.0.0.1:8080启动，真实MySQL下的researcher登录及GET /papers/mine验证通过，结果记录在target/local-http-check.json。进程ID记录在target/local-application.pid，日志为target/local-application.log。示例账号口令沿用开发种子，数据库口令仅存在被忽略的外置配置中。

迁移保留5个账号、4篇论文、15分类、12标签、8条论文标签关联、收藏1、阅读2、笔记1、举报1及重复对1。旧私有论文审核值规范化为NULL；旧重复字段重命名；外键改为保留历史；新增一条迁移审计。

4条旧共享样例没有版权确认，保留记录并设为rejected，原因注明需确认版权后重新提交；不会推断已有授权。旧授权时间、文件哈希保持未知NULL。第4批resubmit对缺少授权的历史记录须要求copyrightAgreed=true，也可删除旧记录后重新进行真实上传。

旧数据缺少的历史标题使用迁移时当前标题捕获；duplicate_pair.submitted_at、report.created_at使用迁移捕获时间，不能解释为原始提交时间；原笔记updated_at保持不变。旧/storage/*.pdf只是演示路径，原始PDF不在工作区，受控文件接口返回404，真实文件需重新上传。

用户要求不再追加备份后，迁移复用了此前已完成的本机快照，没有再生成备份。MySQL DDL会隐式提交，普通ROLLBACK不能回退结构。失败须停止应用，核对已执行阶段后前向修复；需要回退时，在确认无新增业务写入的前提下将此前快照先恢复到隔离库验证，再人工回退数据库及应用版本。不要对部分迁移的库重跑此脚本或schema.sql。

## 验证结果

- JDK 1.8.0_471 / Maven Wrapper 3.9.9，11项单元测试、33项真实MySQL集成测试通过（含原认证13项）。
- 真正启动Java8可执行JAR，经Tomcat HTTP验证登录、匿名401、multipart上传、PDF预览/下载原字节、下载计数、逻辑删除后404。
- 重要失败路径覆盖：跨用户访问/修改、禁用用户、审计不可用拒绝敏感内容、独立审计不随外事务回滚、数据库失败清理新文件、清理失败重启重试、有效文件保护、并发18次下载、并发共享重复上传只留一份、DTO权限字段拒绝、共享编辑冲突回滚、真实100MiB流限制、非法图片及传输中断审计。
- 62个运行时JAR、17566个基础class的Java8字节码检查通过；API契约回归与TypeScript检查通过。
- OSV扫描完成：19个组件、113个去重公告，与第1批相同；本次新增PDFBox/fontbox/commons-logging未新增命中。固定旧框架的生产上线安全门禁仍未通过，见SECURITY_AUDIT.md。
- 集成数据和文件只写入临时MySQL/临时目录；本机结构迁移单独执行并逐行比较保留内容。凭据和JWT密钥仍在被Git忽略的外置配置中。

下一批：K1–K11平台/自有分类标签、树结构、防环、计数与关联权限。共享流转、个人阅读和页面真实接入继续按计划分批实施。

