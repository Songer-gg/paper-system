# PaperHub RESTful API v1.1

Base URL：`/api/v1`。共 67 端点。业务决策以 [BUSINESS_RULES.md](../BUSINESS_RULES.md) 为准；DTO 以 [ts/types.ts](ts/types.ts) 为准。本版按现有前端实际结构修订，取代 v1.0 的字段及权限冲突。

## 1. 全局协议

除 A1 外全部验证 `Authorization: Bearer <JWT>`；role 来自数据库，登录请求 role 只用于校验入口。JWT 24 小时，用户状态与 token_version 每次验证；禁用、改密、重置密码使旧 token 失效。

JSON：`{code:0,message:'ok',data:...}`。失败 data=null，不返回堆栈。

| code | HTTP    | 含义                             |
| ---- | ------- | -------------------------------- |
| 0    | 200/201 | 成功                             |
| 1001 | 401     | 未登录、过期、撤销或禁用         |
| 1002 | 403     | 权限不足                         |
| 1003 | 404     | 不存在、已删除或不可见           |
| 1004 | 409     | 唯一冲突、并发状态冲突、重复处理 |
| 1005 | 422     | 参数校验失败                     |
| 1006 | 400     | 状态/版权等业务拒绝              |
| 1500 | 500     | 内部错误                         |

时间 `yyyy-MM-dd HH:mm:ss`，Asia/Shanghai。ID 为自增 number，服务端约束在 JS 安全整数范围。空 DOI/期刊响应为 `''`，空数组为 `[]`，未分类为 categoryId=null；私有论文 reviewStatus 省略，不返回字符串 private 作为审核状态。

U10 分页 page>=1，pageSize 默认10、范围1–100，返回 `{list,total}`。其他列表保持数组响应，不能无版本变更静默改分页。

文件：U8 POST 下载、U9 GET 预览；成功 application/pdf 流，不套 JSON，失败按上表 JSON。上传 file + payload(application/json)，头像 multipart 仅 file。ApiClient.upload/download 必须显式传 method。

## 2. 权限及可见性

| 资源                          | 上传者/本人  | 其他普通用户 | 管理员                       |
| ----------------------------- | ------------ | ------------ | ---------------------------- |
| 私有论文/PDF                  | 可访问       | 不可见       | 可访问，敏感读取先审计       |
| approved 共享                 | 可访问       | 可访问       | 可访问                       |
| pending/rejected/offline 共享 | 可访问       | 不可见       | 可访问                       |
| 收藏/阅读/笔记                | 仅本人       | 不可见       | 可经 M26–M28 查看并审计      |
| 已逻辑删除论文 PDF            | 不可访问     | 不可访问     | 不可访问                     |
| 平台分类/标签                 | 普通用户只读 | 普通用户只读 | 可维护                       |
| 自定义分类/标签               | 本人可维护   | 不可见       | 可维护并审计读取个人归属内容 |

/me 永远指 token 主体。普通用户不能通过 query/body.userId 切换数据归属。管理员查看他人数据通过显式 admin 路径，不提供修改他人私人笔记入口。

## 3. 认证

| 编号 | Method | Path        | 请求                                     | data                     |
| ---- | ------ | ----------- | ---------------------------------------- | ------------------------ |
| A1   | POST   | /auth/login | `{username,password,role}`；用户名或邮箱 | `{token,user:LoginUser}` |

失败统一“账号或密码错误，或当前登录入口不可用”，不暴露账号存在性。管理员随机重置密码一次响应展示，不记录明文。

## 4. 论文

| 编号 | Method | Path                          | 请求                                    | data / 规则                                                      |
| ---- | ------ | ----------------------------- | --------------------------------------- | ---------------------------------------------------------------- |
| U1   | GET    | /papers/mine                  | 无                                      | `{privatePapers:Paper[],sharedPapers:Paper[]}`，仅本人未删除资源 |
| U2   | GET    | /papers/{id}                  | id                                      | Paper；管理员访问私有论文审计                                    |
| U3   | PATCH  | /papers/{id}                  | PaperEditPayload                        | Paper；本人/管理员可编辑，approved 编辑自动转 pending            |
| U4   | DELETE | /papers/{id}                  | id                                      | true；本人/管理员逻辑删除，保留私人数据与历史                    |
| U5   | PATCH  | /papers/{id}/review-flow      | `{action,copyrightAgreed?}` | Paper；action为share/unshare/resubmit，版权及状态校验，撤回保留记录 |
| U6   | POST   | /papers/duplicate-check       | `{title?,doi?,fileSha256?}` | DuplicateCheckResult；level为doi/title/file/none，不可见命中省略paper |
| U7   | POST   | /papers                       | multipart file + payload(UploadPayload) | Paper；共享 pending，私有无审核状态；服务端最终查重              |
| U8   | POST   | /papers/{id}/download         | id                                      | PDF attachment；原子增加 downloads；管理员私有访问审计           |
| U9   | GET    | /papers/{id}/file             | id                                      | PDF inline；不增下载；同样鉴权及审计                             |
| U10  | GET    | /shared-papers                | SharedQuery                             | PageResult<Paper>，仅 shared/approved/未删除                     |
| U11  | GET    | /shared-papers/{id}           | id                                      | Paper；仅已通过共享，否则1003                                    |
| U12  | GET    | /shared-papers/filter-options | 无                                      | `{categories,tags,years,journals}`，仅来自可见共享池             |

Paper 含前端全部元数据和 `uploaderName`；不含 file_path、file_sha256、密码或生成列。reviewComment 仅本人及管理员返回。offlineReason=withdrawn|moderation。`link` 是 http/https 原文链接，不是服务器路径。

UploadPayload：title/authors[]/abstract/year/categoryId/tagIds[]/keywords[]/visibility/copyrightAgreed，doi/journal/link 可选。fileSizeMB/pageCount/fileSha256 由服务端计算，客户端预检不能替代；原始 File 不能只保存文件名。

U10 的 q 覆盖标题/作者/DOI/摘要/关键词/期刊；categoryId 含后代；tagIds 以逗号分隔，匹配任意标签；year/journal 精确筛选。sort 仅 uploaded-desc/downloads-desc/year-desc，并加 id 稳定排序。总数对论文去重。

状态机及并发：见业务规则第二、三节。禁止直接 PATCH reviewStatus。U6 的提示不能替代 U7/share/resubmit/approve/restore 最终验证。标题相似允许人工判断，精确 DOI/文件重复返回1004。

U5仅上传者可用，管理员不能冒充；share须显式copyrightAgreed=true，resubmit历史缺少确认时也须传true。显式false拒绝；省略参数可沿用已有有效确认。私人分类标签须先经U3调整为平台项。share/resubmit/approve/restore检查实际PDF及服务器SHA-256；旧演示路径、缺失或变化的文件不能发布。

U10的q最多255字符，tagIds最多50项；作者/关键词逐项检索，LIKE通配字符作为字面值。无匹配分类返回空结果。越界页返回空list及正确total。U12包含有公开论文的分类祖先，计数仅基于approved共享。

## 5. 分类、标签

| 编号 | Method | Path                    | 请求              | data                                         |
| ---- | ------ | ----------------------- | ----------------- | -------------------------------------------- |
| K1   | GET    | /categories             | 无                | Category[]，包含 parentId/custom/paperCount  |
| K2   | POST   | /categories             | `{name,parentId}` | Category，归属由真实角色决定                 |
| K3   | PATCH  | /categories/{id}        | `{name}`          | true                                         |
| K4   | DELETE | /categories/{id}        | id                | string[]，已删除分类名；前端确认在请求前完成 |
| K5   | POST   | /categories/{id}/move   | `{parentId}`，number或null | true，服务端防环/归属校验 |
| K6   | GET    | /categories/{id}/papers | id                | Paper[]，含后代且只返回调用者可见论文        |
| K7   | GET    | /tags                   | 无                | Tag[]，包含 custom/paperCount                |
| K8   | POST   | /tags                   | `{name,color}`    | Tag                                          |
| K9   | PATCH  | /tags/{id}              | `{name,color}`    | true                                         |
| K10  | DELETE | /tags/{id}              | id                | true，解除关联                               |
| K11  | GET    | /tags/{id}/papers       | id                | Paper[]，按可见性过滤                        |

同归属、同父级分类重名1004；标签同归属重名1004。Category.desc 映射 description。分类删除含子树、论文只解除关联；含其他用户自定义子节点时1004，不能未经授权级联。共享论文仅使用平台分类标签。

名称1–50字符；标签color为#RRGGBB，响应规范为大写。K5必须显式提供parentId，null表示移动到根级。创建、移动的整棵子树最多10层；成环、超深及跨归属挂接返回1006。平台项普通用户只读（修改1002）；其他用户的自定义项不可见（1003）。

K1/K6/K7/K11使用同一论文可见范围：本人未删除论文及已通过共享；管理员为全部未删除论文。分类计数包含后代，每篇在每个祖先只计一次。管理员读取自定义项、私有论文计数或列表先写独立审计，失败拒绝返回。K4/K10同时递增受影响论文version并保留文件和历史；平台字典维护不改变论文审核状态。

## 6. 收藏、阅读、笔记

| 编号 | Method | Path                          | 请求                         | data                                                |
| ---- | ------ | ----------------------------- | ---------------------------- | --------------------------------------------------- |
| R1   | GET    | /me/favorites                 | 无                           | FavoriteRecord[]，含可见共享 paper；不可见资源隐藏  |
| R2   | POST   | /papers/{id}/favorite         | `{favorited?:boolean}`       | `{paperId,favorited}`；缺省toggle，有值设置目标状态 |
| R3   | GET    | /me/reading-records           | 无                           | ReadingRecord[]，当前可见论文的个人记录             |
| R4   | POST   | /papers/{id}/reading          | id                           | ReadingRecord，新建unread，已存在返回原记录         |
| R5   | PATCH  | /me/reading-records/{paperId} | `{status}`                   | ReadingRecord；不存在时创建，保留已有addedAt        |
| R6   | GET    | /me/notes                     | `paperId?,keyword?`          | Note[]，自己的全部笔记；含paperTitle/paperAvailable |
| R7   | POST   | /me/notes                     | `{paperId,title,content}`    | Note，关联当前可见论文                              |
| R8   | PATCH  | /me/notes/{id}                | `{title?,paperId?,content?}` | Note；支持前端更换关联论文，保存快照                |
| R9   | DELETE | /me/notes/{id}                | id                           | true；仅本人                                        |

ReadingRecord={paperId,status,addedAt,lastReadAt}；状态固定 unread/reading/read/paused。R4重复加入保留全部原状态和时间；R5创建或更新lastReadAt，保留已有addedAt。FavoriteRecord={paperId,paper:Paper|null,favoritedAt}；R1中paper非空，管理员历史查询可空。R2仅可新增已通过、未删除共享论文的收藏；设置true幂等且保留原收藏时间。设置false允许清理失去访问权的已有收藏，不能借此新增不可见资源；toggle不自动重试。

Note={id,paperId,paperTitle,title,content,paperAvailable,createdAt,updatedAt}。笔记在原论文失效后仍可编辑正文及删除，不泄露失效论文内容；新的paperId必须可见，显式传入未改变的原paperId仍可保存失效来源的笔记。paperTitle为创建/更换关联时的快照，不随论文改名更新。关键词搜索笔记标题和关联标题快照，%、_按字面匹配。标题去首尾空白，正文纯文本/Markdown保留原始缩进及换行，前端必须转义后渲染。PATCH只更新提供的字段，空对象、显式null及归属/快照/时间等未知字段拒绝。

## 7. 个人资料与统计

| 编号 | Method | Path           | 请求                                        | data                                                   |
| ---- | ------ | -------------- | ------------------------------------------- | ------------------------------------------------------ |
| P1   | GET    | /me/profile    | 无                                          | MyProfile，含avatar/uploadedCount，统计由后端计算      |
| P2   | PATCH  | /me/profile    | `{name?,email?}`                            | MyProfile，部分更新；提供的邮箱须非空且唯一            |
| P3   | PUT    | /me/avatar     | multipart file                              | MyProfile，avatar返回经校验重编码的dataURL；数据库仅存内部短键 |
| P4   | PUT    | /me/password   | `{oldPassword,newPassword,confirmPassword}` | `{ok:true,message}`；错误走统一非0 code                |
| P5   | GET    | /me/dashboard  | 无                                          | UserDashboard，保持前端counts及recent数据结构          |
| P6   | GET    | /me/statistics | 无                                          | UserStatistics，monthlyReadingTrend含month/label/count |

P5/P6所有统计按本人归属；月度趋势按lastReadAt归月，最近12个月补零。上传数=本人未删除私有+共享；共享数含各审核状态。收藏计数与R1可见列表一致，已读数与R3可见记录一致。

P5：yearDist按本人论文发表年份升序，categoryDist按根分类合并、数量倒序，保留Top6并把其余汇总为“其他”（已有同名项合并）。未分类/缺失分类归“未分类”。readingDist固定read/reading/unread/paused顺序，标签为已阅读/阅读中/未阅读/暂不阅读，无记录补0。recentUploads/recentReadings/recentFavorites各最多4项，分别按上传/最近阅读/收藏时间和对应记录id倒序；在SQL中先按本人及可见性过滤，最近阅读和收藏的paper非空。

P6：noteCount包含本人全部笔记，来源已删除也保留；yearDistribution按年份升序，categoryDistribution为全部根分类数量倒序，journalDistribution为Top8，空期刊归“未投稿”。分类/期刊数量并列时按名称稳定排序。monthlyReadingTrend覆盖应用Clock在Asia/Shanghai的当前月及前11个月，起始月首包含、下月月首排除，按每条当前可见阅读记录的lastReadAt归月（不限read状态），不是阅读次数或时长；month=yyyy-MM，label如1月。

P5/P6一次响应中的计数、图表和最近记录使用同一REPEATABLE READ只读快照，不能由客户端userId/ownerId切换归属。管理员使用/me仍只统计自己上传及自己的个人记录，但其阅读记录按管理员权限可关联他人未删除私有论文；此类私有来源和自己的私有上传聚合先独立持久化资源审计，失败不返回计数或内容。响应不返回笔记正文、密码或文件存储字段。

头像最大2MiB、2048×2048，只接受实际PNG/JPEG并重编码为PNG。P1/P2/P3的avatar字符串用于现有img展示；dataURL只在已认证响应中生成，不写入VARCHAR(255)，不新增公开文件端点。

P2只更新提供的name/email；空对象、显式null、归属/角色/头像/密码等未知字段拒绝。name去首尾空白后1–50个Unicode字符；email去首尾空白、转小写，最长120字符，并校验邮箱格式及唯一约束。邮箱不可占用其他账号的邮箱式username，避免用户名/邮箱登录歧义。uploadedCount由本人全部未删除论文计算；未设置或文件缺失的avatar为空字符串。

P4校验原密码，新密码8–72 UTF-8字节且与确认值一致，不得与原密码相同；密码不trim。成功同事务递增token_version，当前及其他旧token均失效，返回成功后前端须清理会话并重新登录，不自动重试改密请求。改密不覆盖最近一次管理员passwordResetAt。

## 8. 管理员

全部需真实 admin 角色。

| 编号 | Method | Path                                | 请求                                                 | data / 规则                                                               |
| ---- | ------ | ----------------------------------- | ---------------------------------------------------- | ------------------------------------------------------------------------- |
| M1   | GET    | /admin/dashboard                    | 无                                                   | AdminDashboard；uploadTrend={months,papers}，registerTrend={months,users} |
| M2   | GET    | /admin/users                        | keyword,status=all/active/disabled,dateFrom?,dateTo? | AdminUser[]；TS适配器把dateRange拆成两个query参数                         |
| M3   | GET    | /admin/users/{id}                   | id                                                   | AdminUser，含passwordResetAt                                              |
| M4   | GET    | /admin/users/{id}/papers            | id                                                   | 用户全部未删除Paper[]，含私有，敏感访问审计                               |
| M5   | PATCH  | /admin/users/{id}/status            | `{status}`                                           | true；token_version递增，禁止自禁用及禁用最后管理员                       |
| M6   | POST   | /admin/users/{id}/password-reset    | id                                                   | string，随机新密码；与前端显示结构一致                                    |
| M7   | GET    | /admin/papers                       | title?,doi?,uploader?,categoryId?,status?            | 共享Paper[]；保留现有共享资源页                                           |
| M8   | GET    | /admin/papers/{id}                  | id                                                   | Paper；含私有，读取私有审计                                               |
| M9   | PATCH  | /admin/papers/{id}/offline          | id                                                   | true；仅approved共享变offline/moderation                                  |
| M10  | PATCH  | /admin/papers/{id}/restore          | id                                                   | true；仅offline/moderation，不能恢复用户撤回                              |
| M11  | DELETE | /admin/papers/{id}                  | id                                                   | true；逻辑删除且审计，可管理全部论文                                      |
| M12  | GET    | /admin/review/papers                | 无                                                   | `{pending,approved,rejected}`，仅共享未删除                               |
| M13  | POST   | /admin/review/papers/{id}/approve   | `{comment?,copyrightConfirmed:true}`                 | true；实际勾选值由页面传入，不自动伪造true                                |
| M14  | POST   | /admin/review/papers/{id}/reject    | `{comment}`                                          | true；原因必填，写审核流水                                                |
| M15  | GET    | /admin/review/records               | action=all/approved/rejected                         | ReviewRecord[]，使用审核时标题/上传者快照                                 |
| M16  | GET    | /admin/duplicates                   | status=all/pending/resolved                          | DuplicatePair[]，字段与前端三项比对一致                                   |
| M17  | POST   | /admin/duplicates/{pairId}/confirm  | `{note}`                                             | true，pending→duplicate，新论文驳回                                       |
| M18  | POST   | /admin/duplicates/{pairId}/distinct | `{note}`                                             | true，pending→distinct，不自动通过                                        |
| M19  | POST   | /admin/duplicates/{pairId}/link     | `{note}`                                             | true，pending→linked，新论文驳回并关联原文                                |
| M20  | GET    | /admin/duplicate-records            | 无                                                   | DuplicateRecord[]={id,pairId,paperTitle,action,note,handlerId,handlerName,handledAt} |
| M21  | GET    | /admin/reports                      | status=all/pending/resolved                          | ReportItem[]，含createdAt及paperTitle快照                                 |
| M22  | POST   | /admin/reports/{id}/resolve         | `{result,offlinePaperToo}`                           | true；举报处理与可选下架同事务，重复处理1004                              |
| M23  | GET    | /admin/report-records               | 无                                                   | 已处理ReportItem[]                                                        |
| M24  | GET    | /admin/logs                         | action=all或具体类型                                 | OperationLog[]，含敏感数据访问记录                                        |
| M25  | GET    | /admin/settings                     | 无                                                   | PlatformSettings，maxUploadMB=100，只读                                   |
| M26  | GET    | /admin/users/{id}/favorites         | id                                                   | FavoriteRecord[]，敏感读取先审计；失效paper=null                          |
| M27  | GET    | /admin/users/{id}/reading-records   | id                                                   | AdminReadingRecord[]，paper可为null，敏感读取先审计 |
| M28  | GET    | /admin/users/{id}/notes             | id                                                   | Note[]，敏感读取先审计，原论文失效仍返回私人笔记                          |

DuplicatePair={id,originalPaperId,newPaperId,doiMatch,titleSimilarity,fileHashMatch,status,submittedAt,handledAt?,handlerNote?}。status=pending/duplicate/distinct/linked；DuplicateRecord.action=duplicate/distinct/linked。已处理对不可覆盖；关联ID通过逻辑删除保留。

M13要求用户版权已确认及请求copyrightConfirmed=true；M14驳回原因不得为空白。M9没有原因输入，使用固定“管理员下架”原因；M10不能恢复withdrawn，且重新检查文件、版权、精确重复和待处理疑似对。重复审核/状态冲突返回1004。

M2关键词匹配姓名或用户名，%、_、!按字面匹配；注册日期为严格yyyy-MM-dd，起止日均包含，from不可晚于to。sharedCount为该用户未删除共享数（含全部审核状态），contributionCount为其中approved数量；不得统计私有或已删除论文。

M5状态实际改变时递增token_version；重复设置相同状态不重复递增，恢复账号不恢复旧token。禁止自禁用及禁用最后一个可用管理员；并发管理操作锁定后再次验证操作者仍为可用管理员。M6不接受客户端指定新密码，生成24字符安全随机密码（144位随机源），BCrypt存储并更新passwordResetAt/token_version，与成功日志同事务；密码只在本次string响应显示，不写日志。M6不可自动重试。

M2/M3用户资料和M4/M26–M28个人数据读取先持久化访问审计，失败不返回数据；个人列表为空也记录用户范围访问。M4返回该用户全部未删除论文；M26/M27保留全部个人记录，管理员能查看未删除的私有/下架来源，已删除来源paper=null。M28保留本人笔记的历史paperTitle/正文，paperAvailable按管理员权限及删除状态计算；每条笔记及私有来源均留审计。各接口均不返回password、tokenVersion、文件键等内部字段。

任一侧有pending疑似对时不能通过；最终扫描新发现证据时先提交证据及失败日志，再返回1006，审核状态和流水不变。M17/M19驳回pending/approved新论文并写流水；withdrawn/moderation/已删除论文仅关闭历史疑似对，保留原状态。M19原文须为已通过、未删除共享。相同DOI/文件不能标记distinct；distinct不自动批准。M20直接由已处理duplicate_pair生成稳定记录，paperTitle为首次比对快照。

管理员查询用户论文不会赋予普通用户权限。审核/下架/删除/用户管理/重复处理/举报处理自动写操作日志；敏感读取的日志必须在内容返回前成功持久化。paperTitle使用快照，不能因为论文改名或删除丢失含义。审核记录返回uploaderName/reviewerName，重复处理记录返回handlerName，举报返回reporterName，均由服务端关联用户取得，普通页面不靠管理员用户列表解析名字。

M21默认status=all，仅接受all/pending/resolved；按createdAt/id倒序。M23仅resolved，按handledAt/id倒序。两者paperTitle均取report保存的快照，不连接当前论文标题替换；pending响应省略result/handledAt，resolved返回处理说明和时间，handlerId仅内部保存。

M22的result及offlinePaperToo必须提供，结果去首尾空白后非空、最多500个Unicode字符；拒绝显式null、类型冒充和handlerId/status等未知字段。仅pending可处理；第二次返回409/1004，保留原结果。offlinePaperToo=true时：未删除approved共享原子转offline/moderation，使用固定“举报核实后下架”说明；已offline（含withdrawn）或已删除资源保持现状，仅关闭举报；未删除私有/pending/rejected资源返回400/1006且不关闭举报，须取消下架选项。false只关闭举报，不改变论文。处理人、时间、说明、实际下架与成功日志同事务。

M21/M23读取先记录集合范围、逐条举报及私有来源审计；原论文已删除也保留举报快照。M24读取先审计，默认action=all，具体类型去首尾空白、最多50个Unicode字符，绑定参数精确匹配；未知类型返回空数组，%、_不作为通配符。按time/id倒序，userName为操作当时快照；不提供日志编辑/删除接口。读取日志只追加一次访问审计，不递归记录每条日志。

M25仅返回platformName/slogan/logoText/version/maxUploadMB/allowedTypes/contactEmail，版本展示V1.0，maxUploadMB取实际100MiB限制，allowedTypes=.pdf；平台文案/联系邮箱沿用前端占位信息。只读并记录访问，不提供保存接口，不返回任何密钥、数据库地址或文件路径。

M1沿用前端管理首页口径：totals.users为role=user的全部普通账号（含disabled，不含管理员）；papers为未删除approved共享，pending为未删除pending共享。papersToday和uploadTrend按全部未删除共享上传统计（含全部审核状态，排除私有），usersToday和registerTrend按普通账号注册统计。今日按Asia/Shanghai的[当日00:00,次日00:00)，两条趋势均为当前月及前11个月补零，分别返回{months,papers}/{months,users}。

M1的categoryDist只聚合已通过共享的根分类，不截断；recentUploads为全部状态未删除共享中最新6项，pendingTasks为全部未删除pending共享，均按uploadedAt/id倒序，不包含私有。管理首页聚合读取先独立持久化集合审计，失败不返回数据；统计、趋势、分类和列表在同一只读快照中查询。

## 9. 输入限制

标题1–255字符、作者非空数组、摘要非空且UTF-8<=60000字节、年份1000–当前年+1；DOI<=120 ASCII字符、期刊<=255、link<=500且http/https。分类/标签名1–50、标签色值#RRGGBB；笔记标题1–300、正文非空且UTF-8<=60000字节；意见/原因/处理说明<=500。

邮箱必填且<=120；昵称1–50；新密码8–72 UTF-8字节。以上由Java校验，数据库长度只做兜底。JSON数组元素类型也须校验。PDF 0<字节<=100 MiB、头像<=2 MiB。

## 10. 接入及范围

保留Service入口，绑定ApiClient并适配token、缓存、404空态；详见后端计划“前端接入清单”。不能直接复制模块后声称所有页面零改动。

草稿本地保存，不发送API；文件不能恢复时要求重新选择。通知、注册、忘记密码、用户举报提交本轮不新增接口。管理员M26–M28来自用户确认的最高读取权限。
