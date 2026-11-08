# 第7批交付与验收（2026-11-08）

本批实现M21–M25，共5个端点，累计64/67。复用report、operation_log及既有论文/用户表；没有新增API、修改数据库SQL、增加运行时依赖或追加备份。前端仍使用Mock，第9批统一接入。

## 接口范围

| 接口 | 行为 |
| --- | --- |
| M21 GET /admin/reports | 默认all，可按pending/resolved筛选，返回标题快照/举报人姓名/创建时间 |
| M22 POST /admin/reports/{id}/resolve | 一次性处理举报，可选同事务下架已公开共享论文 |
| M23 GET /admin/report-records | resolved举报，按处理时间/id倒序，保留处理结果和标题快照 |
| M24 GET /admin/logs | 全部或action精确筛选，按time/id倒序，操作人姓名取历史快照 |
| M25 GET /admin/settings | 平台基础信息，只读，PDF限制取实际100MiB |

路径前缀/api/v1，均需真实管理员；普通用户403，匿名或禁用/版本失效账号401。用户举报提交不属于本轮，未新增提交接口。举报处理记录直接由report终态生成，不新增历史表。

## 举报处理规则

result和offlinePaperToo都必须提供。处理结果去首尾Unicode空白后非空、最多500个Unicode字符；拒绝null、类型冒充、非法编码、NUL和handlerId/status等未知字段。只允许pending→resolved一次，保存处理人、时间和结果；重复请求409/1004，不覆盖原处理结果。

勾选同时下架时，按当前锁定状态处理：

- 未删除approved共享：改为offline/moderation，使用固定“举报核实后下架”说明，操作日志记录实际下架。
- 已撤回或管理员已下架：只关闭举报，保留原offlineReason、reviewComment和版本，管理员不能因此恢复用户撤回。
- 已删除：只关闭举报，保留deleted_at和原状态，PDF对所有角色继续不可访问。
- 未删除私有/pending/rejected：返回400/1006且不关闭举报，提示取消同时下架后处理。未勾选时只归档，不改变任何论文状态。

同一论文多条举报均可分别归档；第一条实际下架后，其余处理不重复修改论文版本或生成下架成功日志。处理不改写paperTitle，论文更名或逻辑删除后仍返回举报时快照；reporterName由用户表关联取得。

## 事务与审计

统一顺序为共享目录命名锁→字典共享锁→论文主键→举报行。关联预读后核对当前paperId/status，使用pending条件更新；论文为完整FOR UPDATE当前读，不用早期一致性快照覆盖并发下载量。

处理结果、处理人/时间、实际论文下架及成功日志同事务；任一失败整体回滚。处理说明和举报原因只在已授权举报响应中返回，不复制到操作日志；成功日志仅包含举报ID、论文ID和请求/实际下架标记。

举报列表/历史先独立持久化集合访问审计，再记录逐条举报及私有论文来源（包括删除来源快照）的审计；无结果也记录集合访问。读取日志仅追加一次集合审计后查询，不逐条递归记录。设置读取也留访问记录。任一访问审计失败均不返回数据，已提交的独立访问记录不随后续查询或写事务失败回滚。

M24的具体action值按最多50个Unicode字符校验、去首尾空白，参数绑定精确匹配，未知类型为空数组；%、_、SQL片段均作普通值。只返回id/userName/action/target/result/time，保留操作人姓名快照，不暴露user_id或其他内部字段。不提供任何日志修改/删除接口。

## 只读设置与前端后续

M25返回platformName/slogan/logoText/version/maxUploadMB/allowedTypes/contactEmail七个字段，版本展示V1.0，PDF限制由LocalFileStorage.PDF_LIMIT计算。平台文案及邮箱沿用前端占位信息，不表示已配置邮件服务；不返回数据库、JWT或存储配置，不提供保存接口。

第9批须让举报两页直接显示ReportItem.paperTitle及reporterName，不再用共享池当前标题或Mock用户名重建历史；设置页读取M25并保持只读；日志页接入M24。当前页面仍为Mock，本批没有声称页面已经联调完成。

## 验证

- Java 1.8.0_471、Maven Wrapper 3.9.9；22项单元测试、177项真实MySQL集成测试，合计199项全部通过。本批增加2项文本边界单元测试和25项举报/日志/设置集成测试。
- 覆盖状态筛选、安全字段、标题改名/删除后快照、500个Unicode字符边界、JSON白名单及类型、管理员/普通/其他/禁用/匿名权限。
- 覆盖同步下架原子性、重复/并发处理只一次成功、多举报只实际下架一次、撤回与下架竞争、已撤回/管理员下架/删除状态保护、未公开来源拒绝下架。
- 覆盖下架日志、最终日志、举报条件更新失败回滚；访问审计独立保留、私有来源审计失败拒绝、查询失败无数据；早期举报快照不覆盖随后完成的下载计数。
- 覆盖日志精确绑定筛选、中文类型、SQL/通配符字面值、姓名快照、稳定排序、每次读取仅一次访问日志；设置字段与真实PDF上限一致，写入方法拒绝。
- Java8可执行JAR经真实Tomcat HTTP执行全部5个新端点，包含同步下架、重复409、恢复、删除后举报快照保留、日志和设置；回归前6批真实HTTP流程。测试举报通过SQL写入已验证datadir的临时库，没有新增提交接口或污染现有库。
- TypeScript及API契约回归通过。62个运行时JAR、17566个基础class兼容Java8；OSV扫描仍有19个组件命中113个去重公告，生产上线安全门禁继续未通过，见SECURITY_AUDIT.md。

原始报告位于target/surefire-reports、target/failsafe-reports、target/mysql-verification.log、target/batch7-validation.json及target/application-smoke.log。所有写测试仅在隔离临时MySQL与临时文件目录执行。

## 本机状态及下一批

新版后端已运行于http://127.0.0.1:8080/api/v1，PID见target/local-application.pid。本机230项结构只读核对一致；用户/管理员登录、M21/M23/M24/M25、中文日志类型及权限只读检查通过，详见target/batch7-local-http-check.json。本机已有举报仍为1条pending、0条resolved，未用测试处理；管理员读取按规则新增访问审计，现有业务记录/账号密码未改动。

剩余3个端点为第8批P5/P6/M1：用户首页、个人统计与管理首页聚合；之后第9批真实前端适配、页面回归及部署准备。数组查询仍按现有契约不分页，数据规模增长后再版本化调整。
