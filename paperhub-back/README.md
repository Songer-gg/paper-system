# PaperHub 后端

Java 8 / Spring Boot 2.7.18 / Spring Security 5 / MyBatis-Plus 3.5.7 / MySQL 8。

本批实现 A1 `POST /api/v1/auth/login`，以及后续接口共用的认证、授权、响应和异常基础。其他业务端点按 [分批计划](BACKEND_DEVELOPMENT_PLAN.md) 开发；前端仍使用 Mock。本批不会自动创建数据库或导入账号。

## 环境

- JDK 8（必须同时有 java 和 javac），Maven 3.6.3+（已附Maven Wrapper，固定3.9.9并校验分发包SHA-256）。
- MySQL 8.0.16+，先按 `paperhub-api/db` 的说明为新空库手动初始化；已有库必须使用单独迁移。
- 数据库应用账号只需所需业务表的 SELECT/INSERT/UPDATE/DELETE，不使用 root 运行应用。

依赖使用 pom.xml 的 Boot BOM 和固定版本。PDFBox 尚未引入，将在文件批次加入。不要改用 Boot 3/Jakarta 或 Spring Security 6。

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

本机MySQL服务启动后，在paperhub-back目录执行：

```powershell
java -jar target/paperhub-back-0.1.0-SNAPSHOT.jar '--spring.config.additional-location=file:./config/local.properties'
```

环境变量仍可覆盖配置中的同名值。该配置指向127.0.0.1:3306/paperhub；本批没有创建或重建该库，也没有自动导入演示账号。先检查已有库状态，再决定是否需要空库初始化或独立迁移。

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
- 后续禁用、改密、重置事务必须递增 token_version；恢复账号不恢复旧 token。相关修改接口属于第6批，本批使用真实数据库状态变更验证撤销基础。
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

脚本执行 `mvn -Pmysql-it verify`，并用 Java 8 启动打包应用、通过真实 HTTP 验证登录与匿名401。报告在 target/surefire-reports、target/failsafe-reports；应用验证日志在 target/application-smoke.log。

依赖扫描：

```powershell
.\mvnw.cmd dependency:tree '-DoutputType=tgf' '-DoutputFile=target/dependencies.tgf'
python -B scripts/audit_dependencies.py
```

扫描只向 OSV 发送公开 Maven 坐标及版本，结果写 target/dependency-audit.json。扫描失败记为未完成，不能当作“无漏洞”；发现项需评估并形成上线处理结论，不擅自替换既定框架。

业务规则见 [BUSINESS_RULES.md](../BUSINESS_RULES.md)，管理员查看全部个人数据和私有 PDF 的可靠审计将在相关业务批次落地，不能仅凭角色过滤器视为已完成审计。

第1批交付及验收见 [BATCH_1_DELIVERY.md](BATCH_1_DELIVERY.md)，依赖公告及上线门禁见 [SECURITY_AUDIT.md](SECURITY_AUDIT.md)。
