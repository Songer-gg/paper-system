# PaperHub RESTful API 设计文档

> 版本：v1.0 ｜ Base URL：`/api/v1` ｜ 依据：第一阶段前端 28+ 页面与 Service 层签名

## 一、全局约定

### 1.1 认证

| 项 | 约定 |
| --- | --- |
| 方式 | JWT Bearer Token，请求头 `Authorization: Bearer <token>` |
| 获取 | `POST /auth/login`，有效期 24 小时 |
| 角色分离 | 登录时必须携带 `role`（`user` / `admin`），普通账号不能登录管理员端，反之亦然 |
| 登录例外 | `POST /auth/login` 无需 token，其余全部接口需要 |

### 1.2 统一响应包

```json
{ "code": 0, "message": "ok", "data": { } }
```

| code | HTTP 状态 | 含义 |
| --- | --- | --- |
| 0 | 200 / 201 | 成功 |
| 1001 | 401 | 未登录或 token 失效 |
| 1002 | 403 | 无权限（角色不匹配 / 非本人资源） |
| 1003 | 404 | 资源不存在或不可见（未过审共享论文对非上传者等同不存在） |
| 1004 | 409 | 冲突（标题/DOI 重复、分类重名、存在子分类不允许该操作等） |
| 1005 | 422 | 参数校验失败，`message` 为具体原因 |
| 1006 | 400 | 业务规则拒绝（如驳回必须填原因、未勾选版权确认不允许通过） |
| 1500 | 500 | 服务器内部错误 |

约定：前端只依赖 `code` 判断业务结果；HTTP 状态码与 `code` 保持上表映射。

### 1.3 分页与时间

- 分页请求：`page`（从 1 起）、`pageSize`（默认 10）；分页响应统一为 `PageResult<T> = { list: T[]; total: number }`。
- 时间字符串格式 `yyyy-MM-dd HH:mm:ss`。
- 排序值：共享库检索 `sort ∈ uploaded-desc | downloads-desc | year-desc`。

### 1.4 数据可见性规则（权限矩阵）

| 资源 | 普通用户（本人） | 普通用户（他人） | 管理员 |
| --- | --- | --- | --- |
| 私有论文及 PDF | 可见可改 | 不可见 | 仅元数据可见，**不可读文件内容** |
| 共享论文（approved） | 可见 | 可见 | 可见 |
| 共享论文（pending/rejected） | 仅上传者可见 | 不可见 | 可见元数据 |
| 已下架论文 | 仅上传者可见（含驳回原因） | 不可见 | 可见 |
| 阅读记录 / 阅读笔记 / 收藏 | 仅本人 | 不可见 | **不可见**（规划书硬约束） |
| 分类 / 标签（平台级） | 只读 + 用户可建自定义 | — | 可改（含重命名/移动/删除） |
| 操作日志 | 不可见 | 不可见 | 可见 |

> 管理员"不可读私有 PDF、不可见私人笔记"是规划书的硬性权限边界，对应接口不提供文件流与笔记内容返回。

---

## 二、认证模块 `/auth`

| # | Method | Path | 入参 | 出参 data | 说明 |
| --- | --- | --- | --- | --- | --- |
| A1 | POST | `/auth/login` | `LoginRequest { username, password, role }`（username 支持用户名或邮箱） | `LoginResponse { token, user: LoginUser }` | 校验失败 code=1005，message 区分"账号不存在/密码错误/角色不符" |

`LoginUser`：`{ id, username, name, email, role, registeredAt }`。

---

## 三、用户端 · 论文中心

### 3.1 我的论文库 `/papers/mine`

| # | Method | Path | 入参 | 出参 data | 对应前端函数 |
| --- | --- | --- | --- | --- | --- |
| U1 | GET | `/papers/mine` | — | `{ privatePapers: Paper[], sharedPapers: Paper[] }` | `getMyPapers()` |
| U2 | GET | `/papers/{id}` | path id | `Paper` | `getPaperForDetail(id)`。可见性见权限矩阵；不可见返回 1003 |
| U3 | PATCH | `/papers/{id}` | `PaperEditPayload` | `Paper` | `updatePaper(id, patch)`。仅本人 |
| U4 | DELETE | `/papers/{id}` | path id | `true` | `deleteMyPaper(id)`。仅本人；级联清理收藏/阅读/笔记关联 |
| U5 | PATCH | `/papers/{id}/review-flow` | `{ action: 'share' \| 'unshare' \| 'resubmit' }` | `Paper` | `changeReviewStatus(id, status)`：`share`=私有转共享（进入 pending）；`unshare`=取消共享转私有；`resubmit`=驳回后重新提交（进入 pending） |

### 3.2 论文上传 `/papers`

| # | Method | Path | 入参 | 出参 data | 说明 |
| --- | --- | --- | --- | --- | --- |
| U6 | POST | `/papers/duplicate-check` | `{ title, doi? }` | `DuplicateCheckResult { duplicated: boolean; matched: Paper[] }` | 提交前查重提示（不阻断提交） |
| U7 | POST | `/papers` | `multipart/form-data`：`file`(PDF) + `UploadPayload`(JSON part：title/authors/abstract/doi/journal/year/keywords/link/categoryId/tagIds/visibility) | `Paper`（reviewStatus 初始 pending 或 private） | 上传共享论文需 `copyrightAgreed=true` 字段 |
| U8 | POST | `/papers/{id}/download` | path id | 文件流（PDF） | 记录下载计数并返回文件；管理员对私有论文调用返回 1002 |
| U9 | GET | `/papers/{id}/file` | path id | 文件流（PDF） | 在线阅读器预览用，权限同 U8 |

上传草稿：第一阶段为浏览器本地实现（`saveDraft/loadDraft/clearDraft`），**不进入 API 范围**；若后续要求多端同步，可扩展 `GET/PUT/DELETE /me/upload-draft`。

### 3.3 共享论文库 `/shared-papers`

| # | Method | Path | 入参 | 出参 data | 对应前端函数 |
| --- | --- | --- | --- | --- | --- |
| U10 | GET | `/shared-papers` | query：`q?`（模糊：标题/作者/DOI/摘要/关键词/期刊）、`categoryId?`（含后代）、`tagIds?`（逗号分隔）、`year?`、`journal?`、`sort?`、`page`、`pageSize` | `PageResult<Paper>` | `searchSharedPapers(query)`。服务端只返回 `visibility=shared && reviewStatus=approved` |
| U11 | GET | `/shared-papers/{id}` | path id | `Paper` | `getSharedPaperById(id)`；非 approved 返回 1003 |
| U12 | GET | `/shared-papers/filter-options` | — | `{ categories: Category[], tags: Tag[], years: number[], journals: string[] }` | `getFilterOptions()` |

---

## 四、用户端 · 知识管理

分类与标签为平台级树/集合，管理员端修改对全体用户生效；普通用户可创建自己的自定义分类/标签（`custom=true`），仅本人可见可改。

### 4.1 分类 `/categories`

| # | Method | Path | 入参 | 出参 data | 对应前端函数 |
| --- | --- | --- | --- | --- | --- |
| K1 | GET | `/categories` | — | `Category[]`（扁平含 `parentId`、`paperCount`） | `getCategories()` + `countPapersByCategory()` 合并 |
| K2 | POST | `/categories` | `{ name, parentId }` | `Category` | `createCategory(name, parentId)`；重名返回 1004 |
| K3 | PATCH | `/categories/{id}` | `{ name }` | `true` | `renameCategory(id, name)` |
| K4 | DELETE | `/categories/{id}` | path id | `{ deletedNames: string[] }` | `deleteCategory(id)`。服务端**先校验后删除**：返回级联删除的子分类名列表供确认展示；有子分类/关联论文时按"子分类一并删、论文仅解除关联"规则执行 |
| K5 | POST | `/categories/{id}/move` | `{ parentId: null \| number }` | `true` | `moveCategory(id, newParentId)`。服务端做防环校验，成环返回 1006 |
| K6 | GET | `/categories/{id}/papers` | path id | `Paper[]` | `getPapersOfCategory(id)`（含后代分类论文） |

### 4.2 标签 `/tags`

| # | Method | Path | 入参 | 出参 data | 对应前端函数 |
| --- | --- | --- | --- | --- | --- |
| K7 | GET | `/tags` | — | `Tag[]`（含 `paperCount`） | `getTags()` + `countPapersByTag()` 合并 |
| K8 | POST | `/tags` | `{ name, color }` | `Tag` | `createTag(name, color)` |
| K9 | PATCH | `/tags/{id}` | `{ name, color }` | `true` | `updateTag(id, name, color)` |
| K10 | DELETE | `/tags/{id}` | path id | `true` | `deleteTag(id)`；论文仅解除关联 |
| K11 | GET | `/tags/{id}/papers` | path id | `Paper[]` | `getPapersOfTag(id)` |

---

## 五、用户端 · 阅读 / 笔记 / 收藏

| # | Method | Path | 入参 | 出参 data | 对应前端函数 |
| --- | --- | --- | --- | --- | --- |
| R1 | GET | `/me/favorites` | — | `FavoriteRecord[]`（含 Paper） | `getFavorites()`；只返回当前仍可见（approved 且未删除）的共享论文 |
| R2 | POST | `/papers/{id}/favorite` | path id | `{ favorited: boolean }` | `toggleFavorite(paperId)`；重复调用为切换 |
| R3 | GET | `/me/reading-records` | — | `ReadingRecord[]` | `getReadingRecords()` |
| R4 | POST | `/papers/{id}/reading` | path id | `ReadingRecord`（status=unread） | `addToReading(paperId)` |
| R5 | PATCH | `/me/reading-records/{paperId}` | `{ status: ReadingStatus }` | `ReadingRecord` | `updateReadingStatus(paperId, status)` |
| R6 | GET | `/me/notes` | query：`paperId?`、`keyword?` | `Note[]` | `getNotes()` |
| R7 | POST | `/me/notes` | `{ paperId, title, content }` | `Note` | `addNote(input)`（详情页快捷笔记与笔记页共用） |
| R8 | PATCH | `/me/notes/{id}` | `{ title?, content? }` | `Note` | `updateNote(id, patch)` |
| R9 | DELETE | `/me/notes/{id}` | path id | `true` | `deleteNote(id)` |

---

## 六、用户端 · 个人管理

### 6.1 个人中心 `/me`

| # | Method | Path | 入参 | 出参 data | 对应前端函数 |
| --- | --- | --- | --- | --- | --- |
| P1 | GET | `/me/profile` | — | `MyProfile`（含 `uploadedCount`、`avatar`） | `getMyProfile()` |
| P2 | PATCH | `/me/profile` | `{ name?, email? }` | `MyProfile` | `updateProfile(patch)`；邮箱重复返回 1004 |
| P3 | PUT | `/me/avatar` | `multipart/form-data：file` | `MyProfile` | `updateAvatar(file)` |
| P4 | PUT | `/me/password` | `{ oldPassword, newPassword, confirmPassword }` | `{ success: true }`；旧密码错误 1006 | `changePassword(...)` |

### 6.2 仪表盘 `/me/dashboard`

| # | Method | Path | 入参 | 出参 data | 对应前端函数 |
| --- | --- | --- | --- | --- | --- |
| P5 | GET | `/me/dashboard` | — | `UserDashboard`（counts/yearDist/categoryDist/readingDist/recentUploads/recentReadings/recentFavorites） | `getUserDashboard()` |

### 6.3 数据统计 `/me/statistics`

| # | Method | Path | 入参 | 出参 data | 对应前端函数 |
| --- | --- | --- | --- | --- | --- |
| P6 | GET | `/me/statistics` | — | `UserStatistics`（totalPapers/privateCount/sharedCount/favoriteCount/readCount/noteCount/monthlyReadingTrend/yearDistribution/categoryDistribution/journalDistribution） | `getUserStatistics()` |

---

## 七、管理员端 `/admin`（全部需 admin 角色）

### 7.1 管理首页

| # | Method | Path | 入参 | 出参 data | 对应前端函数 |
| --- | --- | --- | --- | --- | --- |
| M1 | GET | `/admin/dashboard` | — | `AdminDashboard`（totals/uploadTrend/registerTrend/categoryDist/recentUploads/pendingTasks） | `getAdminDashboard()` |

### 7.2 用户管理

| # | Method | Path | 入参 | 出参 data | 对应前端函数 |
| --- | --- | --- | --- | --- | --- |
| M2 | GET | `/admin/users` | query：`keyword`（姓名/用户名/邮箱）、`status ∈ all\|active\|disabled`、`dateFrom?`、`dateTo?` | `AdminUser[]`（含 sharedCount、contributionCount、passwordResetAt） | `getUsers(query)`；`isPasswordReset` 由 `passwordResetAt != null` 表达 |
| M3 | GET | `/admin/users/{id}` | path id | `AdminUser` | `getUserDetail(id)` |
| M4 | GET | `/admin/users/{id}/papers` | path id | `Paper[]`（仅论文元数据，含各审核状态；**不含私有论文文件访问权**） | `getUserPapers(id)` |
| M5 | PATCH | `/admin/users/{id}/status` | `{ status: 'active' \| 'disabled' }` | `true` | `setUserStatus(id, status)` |
| M6 | POST | `/admin/users/{id}/password-reset` | path id | `{ newPassword: string }` | `resetUserPassword(id)`（服务端生成随机密码） |

### 7.3 共享论文管理

| # | Method | Path | 入参 | 出参 data | 对应前端函数 |
| --- | --- | --- | --- | --- | --- |
| M7 | GET | `/admin/papers` | query：`title?`、`doi?`、`uploader?`、`categoryId?`、`status ∈ all\|approved\|pending\|rejected\|offline` | `Paper[]` | `getSharedPapers(query)` |
| M8 | GET | `/admin/papers/{id}` | path id | `Paper` | `getPaperById(id)` |
| M9 | PATCH | `/admin/papers/{id}/offline` | path id | `true` | `offlinePaper(id)`（用户端立即不可见） |
| M10 | PATCH | `/admin/papers/{id}/restore` | path id | `true` | `restorePaper(id)` |
| M11 | DELETE | `/admin/papers/{id}` | path id | `true` | `deletePaper(id)`（不可恢复，全局生效） |

### 7.4 内容审核

| # | Method | Path | 入参 | 出参 data | 对应前端函数 |
| --- | --- | --- | --- | --- | --- |
| M12 | GET | `/admin/review/papers` | — | `{ pending: Paper[], approved: Paper[], rejected: Paper[] }` | `getReviewPapers()` |
| M13 | POST | `/admin/review/papers/{id}/approve` | `{ comment?, copyrightConfirmed: true }` | `true` | `approvePaper(id, comment)`；未带 `copyrightConfirmed=true` 返回 1006；通过后论文立即进入共享库 |
| M14 | POST | `/admin/review/papers/{id}/reject` | `{ comment }`（必填） | `true` | `rejectPaper(id, comment)`；驳回原因反馈给上传者详情页 |
| M15 | GET | `/admin/review/records` | query：`action ∈ all\|approved\|rejected` | `ReviewRecord[]` | `getReviewRecords(action)` |

### 7.5 疑似重复管理

| # | Method | Path | 入参 | 出参 data | 对应前端函数 |
| --- | --- | --- | --- | --- | --- |
| M16 | GET | `/admin/duplicates` | query：`status ∈ all\|pending\|resolved` | `DuplicatePair[]` | `getDuplicatePairs(status)` |
| M17 | POST | `/admin/duplicates/{pairId}/confirm` | `{ note }` | `true` | `confirmDuplicate(pairId, note)`（确认重复：新论文自动驳回） |
| M18 | POST | `/admin/duplicates/{pairId}/distinct` | `{ note }` | `true` | `confirmDistinct(pairId, note)`（确认非重复） |
| M19 | POST | `/admin/duplicates/{pairId}/link` | `{ note }` | `true` | `linkToOriginal(pairId, note)`（关联到原文） |
| M20 | GET | `/admin/duplicate-records` | — | `DuplicateRecord[]` | `getDuplicateRecords()` |

### 7.6 举报管理

| # | Method | Path | 入参 | 出参 data | 对应前端函数 |
| --- | --- | --- | --- | --- | --- |
| M21 | GET | `/admin/reports` | query：`status ∈ all\|pending\|resolved` | `ReportItem[]` | `getReports(status)` |
| M22 | POST | `/admin/reports/{id}/resolve` | `{ result, offlinePaperToo: boolean }` | `true` | `resolveReport(id, result, offlinePaperToo)`；`offlinePaperToo=true` 时同步下架被举报论文 |
| M23 | GET | `/admin/report-records` | — | `ReportItem[]`（已处理） | 处理记录页数据源 |

### 7.7 分类 / 标签 / 日志 / 设置

管理员对分类标签复用第四模块端点（K1–K11），区别在于管理员可修改**任何**平台级分类/标签（含移动层级），普通用户仅能操作 `custom=true` 的自有项。

| # | Method | Path | 入参 | 出参 data | 对应前端函数 |
| --- | --- | --- | --- | --- | --- |
| M24 | GET | `/admin/logs` | query：`action`（操作类型或 all） | `OperationLog[]` | `getLogs(action)`。审核/下架/删除/重复处理/举报处理/用户管理等写操作由服务端自动留痕 |
| M25 | GET | `/admin/settings` | — | 只读键值集合（平台信息、存储容量等） | 设置页只读展示，暂不支持修改保存 |

---

## 八、核心 DTO 说明

完整类型定义见 `ts/types.ts`，关键字段说明：

- **Paper**：`id, title, authors[], abstract, doi, journal, year, keywords[], categoryId, tagIds[], visibility(private|shared), uploaderId, uploadedAt, downloads, fileSizeMB, pageCount, reviewStatus(pending|approved|rejected|offline), reviewComment?, link?`
- **Category**：`id, name, parentId(null 为根), desc?, custom?`；API 响应附 `paperCount`
- **Tag**：`id, name, color, custom?`；API 响应附 `paperCount`
- **ReviewRecord**：`id, paperId, paperTitle, uploaderId, action(approved|rejected), comment, reviewerId, reviewedAt`
- **DuplicatePair**：`id, oldPaperId, newPaperId, similarity, reason, status(pending|confirmed|distinct|linked), handledAt?, handlerNote?`
- **ReportItem**：`id, type(copyright|content|duplicate|other), paperId, paperTitle, reporterId, reason, status(pending|resolved), result?, handledAt?`
- **OperationLog**：`id, userName, action, target, result(成功|失败), time`

## 九、Mock Service → API 映射表（前端接入清单）

| 前端 mock 函数 | API 端点 | 备注 |
| --- | --- | --- |
| `mockVerifyLogin` | A1 | |
| `getMyPapers / getPaperForDetail / updatePaper / deleteMyPaper / changeReviewStatus` | U1–U5 | |
| `submitPaper / checkDuplicate` | U7 / U6 | 上传增加 multipart 文件 |
| `download`（toast 模拟） | U8 / U9 | 真实下载与在线预览 |
| `searchSharedPapers / getSharedPaperById / getFilterOptions` | U10–U12 | |
| `getCategories / createCategory / renameCategory / deleteCategory / moveCategory / getPapersOfCategory` | K1–K6 | paperCount 内嵌响应 |
| `getTags / createTag / updateTag / deleteTag / getPapersOfTag` | K7–K11 | paperCount 内嵌响应 |
| `countPapersByCategory / countPapersByTag` | K1 / K7 | 由响应内嵌字段替代，删除本地函数 |
| `getFavorites / toggleFavorite` | R1 / R2 | |
| `getReadingRecords / addToReading / updateReadingStatus` | R3–R5 | |
| `getNotes / addNote / updateNote / deleteNote` | R6–R9 | |
| `getMyProfile / updateProfile / updateAvatar / changePassword` | P1–P4 | |
| `getUserDashboard / getUserStatistics` | P5 / P6 | |
| `getAdminDashboard` | M1 | |
| `getUsers / getUserDetail / getUserPapers / setUserStatus / resetUserPassword / isPasswordReset` | M2–M6 | isPasswordReset 由字段替代 |
| `getSharedPapers / getPaperById / offlinePaper / restorePaper / deletePaper` | M7–M11 | |
| `getReviewPapers / approvePaper / rejectPaper / getReviewRecords` | M12–M15 | |
| `getDuplicatePairs / confirmDuplicate / confirmDistinct / linkToOriginal / getDuplicateRecords` | M16–M20 | |
| `getReports / resolveReport` + 处理记录页 | M21–M23 | |
| `getLogs` | M24 | |
| `saveDraft / loadDraft / clearDraft` | 不入 API | 保持浏览器 localStorage 实现 |
