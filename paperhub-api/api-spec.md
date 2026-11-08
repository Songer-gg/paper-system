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
| U5   | PATCH  | /papers/{id}/review-flow      | `{action:share                          | unshare                                                          | resubmit,copyrightAgreed?}` | Paper；share 必须版权确认；unshare=offline/withdrawn，保留在共享记录 |
| U6   | POST   | /papers/duplicate-check       | `{title?,doi?,fileSha256?}`             | `{level:doi                                                      | title                       | file                                                                 | none,message,paper?}`；不可见命中不返回 paper |
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

ReadingRecord={paperId,status,addedAt,lastReadAt}；状态固定 unread/reading/read/paused。FavoriteRecord={paperId,paper:Paper|null,favoritedAt}；R1中paper非空，管理员历史查询可空。R2设置false允许清理失去访问权的已有收藏，不能借此新增不可见资源；toggle不自动重试。

笔记在原论文失效后仍可编辑正文及删除，不泄露失效论文内容；新的paperId必须可见。关键词搜索笔记标题和关联标题快照。正文纯文本/Markdown，前端必须转义后渲染。

## 7. 个人资料与统计

| 编号 | Method | Path           | 请求                                        | data                                                   |
| ---- | ------ | -------------- | ------------------------------------------- | ------------------------------------------------------ |
| P1   | GET    | /me/profile    | 无                                          | MyProfile，含avatar/uploadedCount，统计由后端计算      |
| P2   | PATCH  | /me/profile    | `{name?,email?}`                            | MyProfile，邮箱必填且唯一                              |
| P3   | PUT    | /me/avatar     | multipart file                              | MyProfile，avatar返回经校验重编码的dataURL；数据库仅存内部短键 |
| P4   | PUT    | /me/password   | `{oldPassword,newPassword,confirmPassword}` | `{ok:true,message}`；错误走统一非0 code                |
| P5   | GET    | /me/dashboard  | 无                                          | UserDashboard，保持前端counts及recent数据结构          |
| P6   | GET    | /me/statistics | 无                                          | UserStatistics，monthlyReadingTrend含month/label/count |

P5/P6所有统计按本人归属；月度趋势按lastReadAt归月，最近12个月补零。上传数=本人未删除私有+共享；共享数含各审核状态。收藏计数与R1可见列表一致，已读数与R3可见记录一致。

头像最大2MiB、2048×2048，只接受实际PNG/JPEG并重编码为PNG。P1/P2/P3的avatar字符串用于现有img展示；dataURL只在已认证响应中生成，不写入VARCHAR(255)，不新增公开文件端点。

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
| M20  | GET    | /admin/duplicate-records            | 无                                                   | DuplicateRecord[]={id,pairId,paperTitle,action,note,handlerId,handledAt}  |
| M21  | GET    | /admin/reports                      | status=all/pending/resolved                          | ReportItem[]，含createdAt及paperTitle快照                                 |
| M22  | POST   | /admin/reports/{id}/resolve         | `{result,offlinePaperToo}`                           | true；举报处理与可选下架同事务，重复处理1004                              |
| M23  | GET    | /admin/report-records               | 无                                                   | 已处理ReportItem[]                                                        |
| M24  | GET    | /admin/logs                         | action=all或具体类型                                 | OperationLog[]，含敏感数据访问记录                                        |
| M25  | GET    | /admin/settings                     | 无                                                   | PlatformSettings，maxUploadMB=100，只读                                   |
| M26  | GET    | /admin/users/{id}/favorites         | id                                                   | FavoriteRecord[]，敏感读取先审计；失效paper=null                          |
| M27  | GET    | /admin/users/{id}/reading-records   | id                                                   | AdminReadingRecord[]，含paper:Paper                                       | null，敏感读取先审计 |
| M28  | GET    | /admin/users/{id}/notes             | id                                                   | Note[]，敏感读取先审计，原论文失效仍返回私人笔记                          |

DuplicatePair={id,originalPaperId,newPaperId,doiMatch,titleSimilarity,fileHashMatch,status,submittedAt,handledAt?,handlerNote?}。status=pending/duplicate/distinct/linked；DuplicateRecord.action=duplicate/distinct/linked。已处理对不可覆盖；关联ID通过逻辑删除保留。

管理员查询用户论文不会赋予普通用户权限。审核/下架/删除/用户管理/重复处理/举报处理自动写操作日志；敏感读取的日志必须在内容返回前成功持久化。paperTitle使用快照，不能因为论文改名或删除丢失含义。审核记录返回uploaderName/reviewerName，重复处理记录返回handlerName，举报返回reporterName，均由服务端关联用户取得，普通页面不靠管理员用户列表解析名字。

## 9. 输入限制

标题1–255字符、作者非空数组、摘要非空且UTF-8<=60000字节、年份1000–当前年+1；DOI<=120 ASCII字符、期刊<=255、link<=500且http/https。分类/标签名1–50、标签色值#RRGGBB；笔记标题1–300、正文非空且UTF-8<=60000字节；意见/原因/处理说明<=500。

邮箱必填且<=120；昵称1–50；新密码8–72 UTF-8字节。以上由Java校验，数据库长度只做兜底。JSON数组元素类型也须校验。PDF 0<字节<=100 MiB、头像<=2 MiB。

## 10. 接入及范围

保留Service入口，绑定ApiClient并适配token、缓存、404空态；详见后端计划“前端接入清单”。不能直接复制模块后声称所有页面零改动。

草稿本地保存，不发送API；文件不能恢复时要求重新选择。通知、注册、忘记密码、用户举报提交本轮不新增接口。管理员M26–M28来自用户确认的最高读取权限。
