# PaperHub 后端开发规范

## 依据及范围

优先级：用户明确指令 > 根目录 BUSINESS_RULES.md > paperhub-api/api-spec.md 与 ts/types.ts > db/schema.sql > 第一阶段规划书。冲突先统一契约，不在 Controller 临时发明行为。

后端围绕现有Vue3功能开发，保留页面流程及已消费字段。Mock的身份、数据和文件模拟不能作为生产实现。2026-11-08已完成第1–3批认证、文件论文核心及分类标签，后续按分批计划实施。

## 技术基线

- Java 8（source/target=1.8，最终使用 Java 8 实际运行验证）。Spring Boot 固定 2.7.18，Spring Framework/Security/Jackson 使用该 BOM；默认 Security 5.7.11。
- MyBatis-Plus 固定 3.5.7，使用 mybatis-plus-boot-starter；该版本的分页依赖保持其受控版本。禁止同时引入独立 mybatis-spring-boot-starter；不使用 Boot 3 starter、Spring 6 或 Jakarta Servlet。
- MySQL >=8.0.16，InnoDB/utf8mb4；Connector/J 使用 Boot BOM 的 com.mysql:mysql-connector-j:8.0.33；Maven >=3.6.3。
- JWT使用JJWT 0.11.5的api/impl/jackson；PDF校验使用PDFBox 2.0.35。第2批已实际引入并通过Java8构建/运行、运行时字节码及OSV扫描；全栈当前62个运行时JAR，已知公告门禁见SECURITY_AUDIT.md。
- Boot 2.7 社区支持已结束；上线前需要依赖安全扫描和补丁安排，不擅自升级用户指定主框架。
- MyBatis-Plus ID 明确 IdType.AUTO，对应 SQL AUTO_INCREMENT，不使用默认雪花 ID。API number 型 ID 必须 <= Number.MAX_SAFE_INTEGER，越界先迁移契约为字符串。

## 结构

版本核对参考：[Spring Boot 2.7.18 要求](https://docs.spring.io/spring-boot/docs/2.7.18/reference/html/getting-started.html)、[Boot 依赖表](https://docs.spring.io/spring-boot/docs/2.7.18/reference/html/dependency-versions.html)、[MyBatis-Plus 发布记录](https://baomidou.com/en/resources/changlog/)、[JJWT 版本说明](https://github.com/jwtk/jjwt/blob/main/CHANGELOG.md)、[Apache PDFBox 发布记录](https://www.apache.org/foundation/board/minutes/PDFBox.html)。这些资料不能替代第一批实际解析依赖和构建。

按 auth、paper、knowledge、reading、profile、admin、audit 分业务包，各包分 controller/service/mapper/entity/dto/vo。文件存储、认证、异常处理放公共基础设施。

Controller 负责绑定和校验，Service 负责权限、状态机及事务，Mapper 负责查询。实体不直接作为响应；防止密码、token_version、file_path、生成列泄露。不以通用 updateById 实现外部 PATCH。

## 协议与映射

- Base URL /api/v1，JSON 成功统一 {code:0,message,data}；异常、Security EntryPoint、AccessDeniedHandler 都按契约返回 JSON。
- LocalDateTime 统一 yyyy-MM-dd HH:mm:ss，应用/JDBC/数据库会话统一 Asia/Shanghai；统计按该时区计算。
- authors/keywords 显式 JSON TypeHandler；category.description 映射 desc，file_size_mb 映射 fileSizeMB；doi/journal NULL 在 VO 层规范化为空字符串，数组无值为 []。
- categoryId 可 null；私有 reviewStatus 省略。所有查询覆盖 deleted_at 和可见性，不能在分页后才过滤。
- 多标签检索采用 OR（匹配任意标签），总数按论文 DISTINCT。分类递归含后代，最大深度 10；SQL 参数绑定、排序白名单。
- 默认只 U10 服务端分页，其余按现有数组契约返回。规模增长后再版本化迁移分页，不静默改变响应形状。
- multipart payload 必须 application/json；PDF 流不套统一响应。下载与预览分别使用 POST/GET，错误仍返回 JSON，不把异常堆栈写入响应。

## 安全与审计

所有非登录接口都验证 JWT 签名、过期、真实角色、账号状态和 token_version。JWT 密钥从环境变量获取，禁止仓库硬编码或在日志输出 Authorization。

普通用户只能操作本人资源。管理员最高权限按 BUSINESS_RULES.md：可查看私有 PDF 及他人个人数据，敏感访问先审计后返回；审计不可用则拒绝访问。管理员读取他人数据使用显式 admin 路径；不改变 /me 的主体语义。

文件存于 Web 根目录外。访问按论文 ID 查询，禁止任意路径；按真实 PDF 解析、大小、页数生成元数据。禁止公开映射存储目录；CORS 采用配置白名单，JWT 无 Cookie 模式按实际设计处理 CSRF，不使用 allowAll 掩盖问题。

登录限流、统一失败提示；账号禁止自禁用和禁用最后一个可用管理员；普通用户不能用请求体设置 uploaderId、ownerId、role 或审核状态。日志只记录资源标识与操作结果，不能包含笔记正文、密码或文件字节。

## 事务及文件补偿

- 上传、论文编辑与标签、状态流转、审核流水、重复处理、举报同步下架及相关成功日志，同事务提交。
- 状态转换使用版本/条件 UPDATE；同一重复对仅处理一次。精确重复由 SQL 生成列唯一键兜底，转为 1004；不能只先查后插。
- 分类树移动/删除用事务及锁序防并发成环；平台节点含他人自定义子节点时禁止直接级联删除。
- 分类/标签锁先于论文锁：分类树写入按主键锁整树；论文分类/标签写入先取字典共享锁，删除关联按升序锁论文。后续共享流转如修改分类标签也必须沿用。平台字典维护递增论文version但不自动重送审；显式论文编辑保持原送审规则。
- 删除设 deleted_at，保留个人数据和历史；文件清理在提交后执行并可重试，清理任务不可导致 PDF 重获访问权限。
- 敏感读取审计用独立持久化事务（包含被拒绝访问），外层只读事务不得使审计回滚。流开始后传输失败单独记录结果，不声称数据库与文件流可整体回滚。

## 数据库交付

schema.sql 仅创建新空库，不含 DROP DATABASE；seed-demo.sql 只用于开发测试。应用启动禁止自动执行结构脚本。已有库先备份、审计存量重复，再编写带前置条件和回滚策略的增量迁移，不直接重跑 schema。

逻辑删除的 SQL 列、生成列、索引和 CHECK 属于数据库约束，不暴露为可写 DTO。不直接物理删除论文或覆盖审计/处理终态。

## 每批完成标准

1. Java 8 编译及实际运行，mvn test/package、依赖树与依赖安全扫描。
2. 接口契约与前端一致；关键失败路径有集成测试；不为纯 Getter 编写无意义测试。
3. 使用真实 MySQL 8 测试事务、JSON、唯一键、CHECK 和并发；不能仅用 H2 替代。
4. 正常用户、其他用户、管理员、禁用账号四组权限验证；敏感访问可查审计。
5. 更新开发计划状态和接口联调说明；未验证项明确列出，不将“代码已写”记为“已验收”。
