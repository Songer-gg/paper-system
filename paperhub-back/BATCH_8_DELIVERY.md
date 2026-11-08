# 第8批交付与验收（2026-11-08）

本批完成P5/P6/M1三个聚合接口，既定67/67端点全部实现。复用原有表、索引及安全Paper VO，没有新增API、修改数据库SQL、增加运行时依赖或追加备份。前端仍使用Mock，第9批统一适配及页面回归；功能接口完成不等于生产上线门禁通过。

## 接口范围及结构

| 接口 | 响应 |
| --- | --- |
| P5 GET /me/dashboard | counts、yearDist、categoryDist、readingDist、recentUploads、recentReadings、recentFavorites |
| P6 GET /me/statistics | 六个计数、monthlyReadingTrend、yearDistribution、categoryDistribution、journalDistribution |
| M1 GET /admin/dashboard | totals、uploadTrend={months,papers}、registerTrend={months,users}、categoryDist、recentUploads、pendingTasks |

前缀/api/v1，字段严格匹配前端/API类型；空账户返回0、空数组及完整补零图表，不返回null图表。/me只按JWT主体，query.userId/ownerId不能切换归属；管理员使用/me也只统计自己的上传/个人记录。

## 个人统计口径

- 上传总数=本人未删除私有+全部审核状态共享；不计别人的论文或已删除资源，P1上传数与此一致。
- 收藏计数/最近收藏仅当前有效approved共享，与R1一致；已读数仅当前可见且status=read的本人记录，与R3一致。阅读分布和月度趋势包含当前可见记录的全部状态。
- 笔记计数包含本人所有笔记，原论文被删除后保留的笔记仍计数，不读取或返回笔记正文。
- 年份按本人论文发表年份升序；分类按根分类合并，未分类、失效分类及异常环归“未分类”。普通用户只读取平台/本人分类树，不使用他人私人分类名称。
- P5分类Top6，其余合并“其他”；已存在同名根分类时合并计数。P6返回全部根分类；期刊Top8，空值归“未投稿”。数量并列时采用名称稳定排序。
- readingDist固定read/reading/unread/paused，标签沿用前端：已阅读/阅读中/未阅读/暂不阅读，无记录补0。
- 最近上传、阅读、收藏各4项；先在SQL中过滤归属、删除及可见性，再按时间/记录ID倒序截断，paper非空。Paper使用既有安全映射，不暴露存储路径、哈希或内部版本。

## 时间、快照和管理员

月份来自Clock在Asia/Shanghai的当前月及前11个月，缺失月份补0。SQL使用[首月1日00:00,下月1日00:00)范围；每条当前阅读记录按lastReadAt归月，不声称记录多次阅读事件或时长。活动DATETIME通过JDBC直接映射LocalDateTime，不经JVM默认时区转换。

同一次响应中的计数、分组、最近记录使用REPEATABLE READ只读事务；并发删除提交后，当前响应保留一致的开始时快照，后续请求看到新状态。图表用COUNT/GROUP BY聚合，近期详情只加载所需论文；不为统计加载所有论文正文或个人笔记正文。

管理首页普通用户数包括禁用账号、排除管理员；共享卡片只计approved，待审只计pending。今日上传及上传趋势计全部状态未删除共享，排除私有；注册统计仅普通账号。今日使用上海[00:00,次日00:00)，趋势为最近12月补零。

M1分类只来自approved共享，全部返回；最近上传6条含全部共享状态，待审任务为全部未删除pending共享。全站私有论文不会进入管理首页计数或列表。

M1先独立持久化集合范围审计；管理员个人统计先记录本人范围，并记录聚合涉及的自己私有上传/本人阅读关联私有来源资源审计。任一审计失败不返回计数或内容；已提交的访问日志不随后续查询失败回滚。普通用户聚合始终按其现有访问权限过滤。

## 验证

- Java 1.8.0_471、Maven Wrapper 3.9.9；25项单元测试、195项真实MySQL集成测试，共220项全部通过。本批新增3项图表/时间单元测试、18项统计集成测试。
- 覆盖空账户图表、主体伪造、全部共享状态/逻辑删除、R1/R3/P1数量对齐、失效来源笔记计数、四种阅读状态及标签。
- 固定上海跨年时刻，覆盖当前及前11月补零、首尾秒边界、lastReadAt而非addedAt归月、今日起止、默认JVM时区为UTC时活动时间不偏移。
- 覆盖根分类/自有分类/未分类、Top6+其他、期刊Top8及空期刊、年份升序、近期列表过滤后截断和稳定排序、安全Paper字段。
- 覆盖管理员普通账号/禁用账号计数、全部状态共享趋势、approved分类、全部待审、私有排除、管理员个人数据归属及私有来源审计故障。
- 覆盖独立连接完成删除后，同一聚合响应计数/年份/近期详情仍在一致快照中，后续请求看到删除；管理员集合审计后查询故障日志仍保留。
- Java8打包应用通过真实HTTP成功调用既定67/67端点。脚本从api-spec解析端点，按method/path逐项匹配成功调用记录，结果见target/http-endpoint-coverage.json；包含本批三个聚合接口、P3真实PNG头像、U1个人库及U3编辑、全部既有业务/PDF流程。
- TypeScript及API契约检查通过。62个运行时JAR、17566个基础class兼容Java8；OSV扫描仍有19个组件命中113个去重公告，生产上线门禁继续未通过，详见SECURITY_AUDIT.md。

所有写测试仅在隔离临时MySQL及临时存储目录执行；统计测试的遗留元数据/时间边界夹具不导入本机库。原始报告在target/surefire-reports、target/failsafe-reports、target/mysql-verification.log、target/batch8-validation.json、target/http-endpoint-coverage.json及target/application-smoke.log。

## 本机与下一批

新版后端运行于http://127.0.0.1:8080/api/v1，PID见target/local-application.pid。本机230项结构只读核对一致；普通用户/管理员登录、P5/P6/M1、P1/R1/R3/R6/共享池计数对齐、主体归属、匿名401和普通用户访问管理首页403复查结果见target/batch8-local-http-check.json。现有账号密码、论文/阅读/收藏/笔记/举报记录未改动，管理员聚合读取依规则新增访问审计。

第9批接入真实前端：ApiClient及token/错误/缓存适配、全部Service、真实PDF预览/下载、名称及图表去Mock、改密后重新登录、页面回归和部署说明。管理首页“含1名已禁用”“较昨日+2”等固定文案须同步移除；本批没有声称浏览器页面已完成真实联调。
