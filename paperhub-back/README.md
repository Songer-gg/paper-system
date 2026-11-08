# PaperHub 后端

Java 8 / Spring Boot 2.7.18 / Spring Security 5 / MyBatis-Plus 3.5.7 / MySQL 8。

已完成第1–9批功能开发及真实前端联调，既定67/67端点全部实现。详见[第9批交付](BATCH_9_DELIVERY.md)及[分批计划](BACKEND_DEVELOPMENT_PLAN.md)。前端默认真实模式；220项后端测试、67端点HTTP、4组隔离浏览器流程及27页本机读取检查通过。生产上线门禁仍未通过。应用启动不自动建库或导入账号。

8080提供受保护的后端API，直接浏览器访问可能返回1001；Vue页面访问http://localhost:5173，启动方式见[前端说明](../paperhub-frontend/README.md)，部署配置见[DEPLOYMENT.md](DEPLOYMENT.md)。

用户要求优先补齐的第10批普通注册见[BATCH_10_DELIVERY.md](BATCH_10_DELIVERY.md)，当前共68个端点。网页测试账号：管理员入口admin/admin123；用户入口researcher/123456。新用户可在登录页点击“立即注册”或访问http://localhost:5173/register；注册成功后重新登录，只能创建普通用户。开发账号不等于数据库root账号，不用于生产。

## 环境

- JDK 8（必须同时有 java 和 javac），Maven 3.6.3+（已附Maven Wrapper，固定3.9.9并校验分发包SHA-256）。
- MySQL 8.0.16+，先按 `paperhub-api/db` 的说明为新空库手动初始化；已有库必须使用单独迁移。
- 数据库应用账号只需所需业务表的 SELECT/INSERT/UPDATE/DELETE，不使用 root 运行应用。

依赖使用pom.xml的Boot BOM和固定版本；已引入PDFBox 2.0.35并实际验证Java8。不要改用Boot3/Jakarta或Spring Security6。

## 配置与运行

`.env.example` 仅为变量说明，Spring Boot 不会自动读取 `.env`。把变量填写到 IDE 的环境变量或当前 PowerShell 会话中；不要将真实凭据写进 application.yml、Git 或命令行参数。

```powershell
$env:JAVA_HOME = '你的 JDK 8 安装目录'
$env:PAPERHUB_DB_URL = 'jdbc:mysql://127.0.0.1:3306/paperhub?connectionTimeZone=Asia/Shanghai'
$env:PAPERHUB_DB_USERNAME = 'paperhub'
$env:PAPERHUB_DB_PASSWORD = '填入数据库密码'
$env:PAPERHUB_ALLOWED_ORIGINS = 'http://localhost:5173,http://127.0.0.1:5173'

# 生成当前环境的随机签名密钥，不输出到控制台；正常重启应从安全配置复用同一密钥。
$jwtBytes = New-Object byte[] 32
$jwtRng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
$jwtRng.GetBytes($jwtBytes)
$env:PAPERHUB_JWT_SECRET = [Convert]::ToBase64String($jwtBytes)
$jwtRng.Dispose()

.\mvnw.cmd test package
java -jar target/paperhub-back-0.1.0-SNAPSHOT.jar
```

数据库会话由连接池设置为+08:00，JDBC/应用使用Asia/Shanghai，不要求MySQL预加载命名时区表。默认端口 8080；可设置 PAPERHUB_PORT。没有 JWT 密钥或密钥不足 32 个解码字节时启动失败，不提供默认签名密钥。CORS 默认不允许跨域，按环境明确配置源；不接受通配符。

## 本机外置配置

已根据本轮提供的账号创建 `config/local.properties`，其中包含本地数据库凭据和随机签名密钥。此文件被Git忽略，位于src/main/resources之外，不会打包；不要复制到资源目录。

本机旧库已于第2批通过独立增量迁移对齐v1.1。先确保MySQL服务为Running，再在paperhub-back目录执行：

```powershell
java -jar target/paperhub-back-0.1.0-SNAPSHOT.jar '--spring.config.additional-location=file:./config/local.properties'
```

环境变量仍可覆盖配置中的同名值。该配置指向127.0.0.1:3306/paperhub；保留原账号及业务记录，4条缺少版权确认的旧共享样例改为驳回，未替用户确认版权。原演示PDF不存在，文件接口返回404；真实文件须经U7上传。迁移检查及历史时间口径见BATCH_2_DELIVERY.md。

文件默认保存在工作目录下data/storage，可配置PAPERHUB_STORAGE_ROOT；该目录不公开映射且被Git忽略。.cleanup为持久化清理队列，需要随存储目录保留；PAPERHUB_CLEANUP_ENABLED默认true，每30秒检查成熟任务。头像响应沿用前端dataURL显示方式，数据库只存服务器生成的短键，不新增公开头像接口。

## 登录与安全语义

请求 JSON `{username,password,role}`，role=user/admin；username 支持用户名或邮箱。成功响应保持前端契约：

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "token": "签名 JWT",
    "user": {
      "id": 1,
      "username": "researcher",
      "name": "林知远",
      "email": "researcher@paperhub.cn",
      "role": "user",
      "registeredAt": "2025-03-12 09:30:00"
    }
  }
}
```

- 演示账号只存在于手动导入的 seed-demo.sql；正式环境不要导入。
- 失败不区分账号存在、密码错误、角色不符或账号禁用。角色来自数据库，请求 role 只决定登录入口。
- JWT 使用 HS256、固定 issuer/audience、24h 有效期及唯一 jti，只包含会话标识；每次请求重新查询账号，检查状态、role 和 token_version。
- 禁用/恢复的实际状态变化、改密及管理员重置同事务递增token_version；重复设置相同状态不递增。恢复账号不恢复旧token，P4成功后须重新登录。管理员重置返回一次随机密码，不得自动重试或写入日志。
- `/api/v1/admin/**` 需管理员；其他 `/api/v1/**` 需已认证。`/me` 的主体由 SessionPrincipal 决定，禁止从请求读取 userId 冒充。
- 默认单实例登录限流：每 IP 每分钟20次，每账号每分钟10次，最多10000个计数项；不信任客户端 X-Forwarded-For。多实例部署前须在受信网关或共享存储实现全局限流。
- 统一 401/403/404/422/409/400/500 JSON，失败 data=null；不向响应回显密码、SQL、异常堆栈或未知请求字段。
- 测试探针只在 src/test 中存在，不打入可执行应用。

## 验证

普通单元测试不连接数据库：

```powershell
.\mvnw.cmd test
```

真实 MySQL 集成与可执行 JAR 验证，从工作区根目录运行：

```powershell
python -B paperhub-back/scripts/verify_mysql.py `
  --mysql-bin '你的 MySQL 8/bin' `
  --maven '你的 Maven/bin/mvn.cmd' `
  --java-home '你的 JDK 8'
```

脚本创建临时 MySQL 数据目录、随机端口及临时应用账号；核对 @@datadir 后才导入结构和种子。随机数据库密码和 JWT 密钥仅通过子进程环境传入，测试完成关闭临时服务并清理目录。集成测试拒绝在没有隔离标记或数据目录不匹配时更新数据库。

增加`--frontend-e2e`可在该临时数据库上继续运行4组Chrome真实页面流程，需先在前端npm ci并安装本机Chrome。测试同时使用独立Vite端口及对应后端CORS白名单，拒绝缺少隔离标记的浏览器写测试；报告在target/frontend-e2e-report.json。不要在现有库执行写回归。

脚本执行mvn -Pmysql-it verify，并用Java8启动打包应用。累计25项单元测试、195项真实MySQL集成测试，共220项通过；真实HTTP按api-spec逐项记录67/67端点成功覆盖，结果在target/http-endpoint-coverage.json，包含聚合、头像、PDF与各业务流程。举报HTTP测试的待处理记录仅写已核对datadir的临时库，不新增用户提交接口。测试文件也在临时目录，不写本机存储；本批没有执行现有库的测试CRUD。报告在target/surefire-reports、target/failsafe-reports；应用验证日志在target/application-smoke.log。

依赖扫描：

```powershell
.\mvnw.cmd dependency:tree '-DoutputType=tgf' '-DoutputFile=target/dependencies.tgf'
python -B scripts/audit_dependencies.py
```

扫描只向 OSV 发送公开 Maven 坐标及版本，结果写 target/dependency-audit.json。扫描失败记为未完成，不能当作“无漏洞”；发现项需评估并形成上线处理结论，不擅自替换既定框架。

业务规则见[BUSINESS_RULES.md](../BUSINESS_RULES.md)。管理员私有论文元数据/PDF及他人收藏/阅读/笔记访问审计已落地；敏感审计不可用时不返回数据。

历史交付见[BATCH_1_DELIVERY.md](BATCH_1_DELIVERY.md)、[BATCH_2_DELIVERY.md](BATCH_2_DELIVERY.md)、[BATCH_3_DELIVERY.md](BATCH_3_DELIVERY.md)、[BATCH_4_DELIVERY.md](BATCH_4_DELIVERY.md)、[BATCH_5_DELIVERY.md](BATCH_5_DELIVERY.md)、[BATCH_6_DELIVERY.md](BATCH_6_DELIVERY.md)、[BATCH_7_DELIVERY.md](BATCH_7_DELIVERY.md)、[BATCH_8_DELIVERY.md](BATCH_8_DELIVERY.md)；当前交付及本机状态见[BATCH_9_DELIVERY.md](BATCH_9_DELIVERY.md)，依赖公告及上线门禁见[SECURITY_AUDIT.md](SECURITY_AUDIT.md)。
