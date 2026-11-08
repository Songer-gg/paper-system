# 第5批交付与验收（2026-11-08）

本批实现R1–R9收藏、阅读状态与私人笔记，共9个端点，累计48/67。复用favorite、reading_record、note及现有论文、账号、操作日志表，没有新增API、运行时依赖或修改SQL。前端仍使用Mock，真实适配按第9批实施。

## 接口范围

| 接口 | 行为 |
| --- | --- |
| R1 GET /me/favorites | 本人仍有效的已通过共享收藏，含安全Paper VO |
| R2 POST /papers/{id}/favorite | 可省略body或传空对象toggle；显式favorited设置目标状态 |
| R3 GET /me/reading-records | 本人当前可见论文的阅读记录 |
| R4 POST /papers/{id}/reading | 新建unread；重复加入保持原状态和全部时间 |
| R5 PATCH /me/reading-records/{paperId} | 无记录则创建，已有保留addedAt并更新状态/lastReadAt |
| R6 GET /me/notes | 本人全部笔记，支持paperId及标题/标题快照关键词过滤 |
| R7 POST /me/notes | 关联当前可见论文，保存标题快照 |
| R8 PATCH /me/notes/{id} | 部分更新本人笔记，允许更换当前可见来源 |
| R9 DELETE /me/notes/{id} | 只删除本人笔记，不删除论文或其他个人记录 |

所有路径均以/api/v1为前缀。/me主体来自JWT，请求userId不能切换归属；管理员也不能借R8/R9修改他人的笔记。管理员查看他人数据的M26–M28仍属第6批。

## 业务行为

- 收藏仅限已通过、未删除共享；重复true保留原收藏时间，false幂等且允许清理失效收藏。取消收藏保留阅读与笔记。原记录在论文下架/删除时保留，R1隐藏失效项。
- 阅读允许本人私有论文或其他当前可见论文，四种状态为unread/reading/read/paused。R4重复调用不覆盖R5已设置的状态；不同用户数据独立。
- 笔记返回id/paperId/paperTitle/title/content/paperAvailable/createdAt/updatedAt，不返回userId或源论文内部字段。标题快照在创建和更换来源时保存，不随源论文改名而更新。
- 论文被撤回、改名或删除后，用户仍能搜索、编辑和删除自己的笔记。paperAvailable=false，保留原标题快照和正文；不读取失效来源当前标题、摘要或PDF给用户。
- 原来源失效时，PATCH省略paperId或显式传回相同paperId均可保存；更换paperId必须验证新来源。空对象、显式null、伪造归属/快照/时间字段均拒绝。
- 标题按Unicode字符限制300，正文按UTF-8限制60000字节，拒绝空白/非法编码。保留Markdown原始缩进和换行；关键词仅搜索标题与来源快照，转义LIKE中的%、_、!。

## 事务、并发与审计

个人数据写操作先锁关联论文，再锁本人记录。改关联按论文主键升序锁定新旧论文，然后读取当前笔记并核对关联；关联已被其他请求改变返回409。部分更新合并锁定后的当前行，避免旧快照覆盖其他字段的并发修改。个人数据操作不反向获取共享目录或字典锁。

论文当前读确保与撤回/删除竞争时，失去访问权的用户不能插入新的收藏、阅读或笔记。唯一键与行锁保证同一用户/论文一条收藏或阅读；并发toggle按顺序执行。个人数据和成功日志同事务，日志故障则整体回滚。

管理员通过自己的笔记/阅读访问私有来源时，先独立持久化资源审计；审计故障不返回数据。记录只包含资源ID、所属用户及动作，不含笔记正文或标题。现有删除流程继续保留个人数据及处理历史。

## 验证

- Java 1.8.0_471、Maven Wrapper 3.9.9；18项单元测试、123项真实MySQL集成测试，合计141项全部通过。本批新增3项输入校验单元测试及27项个人数据集成测试。
- 覆盖用户隔离、管理员不能修改他人笔记、幂等收藏、双toggle、重复加入阅读、阅读并发、源论文改名/撤回/删除、关联可见性、保留快照与Markdown。
- 覆盖部分更新并发合并、关联竞争409、两篇论文交叉关联锁序、撤回先提交时收藏/阅读/笔记拒绝、成功日志故障回滚和私有读取审计故障。
- Java8打包应用通过真实HTTP执行R1–R9，包括收藏幂等、阅读保留状态、笔记改关联、删除来源后继续编辑及清理；回归全部共享审核/重复命令、K1–K11及PDF流程。
- API契约及TypeScript检查通过；62个运行时JAR、17566个基础class通过Java8字节码检查。
- OSV扫描完成，仍有19个组件命中113个去重公告；既定框架未擅自升级，生产上线门禁继续未通过，见SECURITY_AUDIT.md。

测试仅写随机端口临时MySQL及临时文件目录。原始报告位于target/surefire-reports、target/failsafe-reports、target/mysql-verification.log、target/batch5-validation.json；HTTP日志为target/application-smoke.log。没有在用户现有库执行测试CRUD或结构迁移，没有追加备份。

## 本机与后续

本机结构只读核对230项一致；新版后端运行于http://127.0.0.1:8080/api/v1，PID记录在target/local-application.pid。普通用户及管理员登录、R1/R3/R6列表、匿名拒绝与/me归属复查结果见target/batch5-local-http-check.json。8080继续提供API，Vue页面需运行前端开发服务，当前仍使用Mock。

下一批为第6批：P1/P2/P4个人资料与改密，M2–M6用户管理，M26–M28管理员查看全部论文及个人数据；重点验证邮箱唯一、密码重置、旧JWT失效、自禁用/最后管理员保护，以及所有敏感读取先审计。
