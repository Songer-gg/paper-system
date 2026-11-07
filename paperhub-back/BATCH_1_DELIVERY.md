# 第1批交付：基础工程与认证

日期：2026-11-07。功能验收完成；生产上线仍受 [依赖安全门禁](SECURITY_AUDIT.md) 限制。未连接或修改现有业务数据库。

## 已实现

- Maven Wrapper 3.9.9、Spring Boot 2.7.18、Java 8编译/运行约束、MyBatis-Plus 3.5.7、MySQL驱动和JJWT依赖。
- A1 `POST /api/v1/auth/login`：用户名/邮箱、BCrypt、角色入口、账号状态校验，返回前端所需的`token + LoginUser`。
- HS256 JWT：issuer/audience、iat/exp/jti、24小时有效期；每次请求验证数据库账号状态、真实角色和token_version。
- 普通/管理员路径隔离，无状态认证，表单登录/HTTP Basic/默认自动生成账号关闭；测试探针不打入应用。
- 统一响应、校验、业务/数据库异常、401/403/404和防火墙拒绝的JSON响应；不在响应回显密码、SQL或堆栈。
- 显式CORS白名单、单实例IP+账号登录限流及容量上限；JWT/数据库凭据由环境变量提供。
- JDBC/应用采用Asia/Shanghai，MySQL会话设为+08:00，空实例无需安装命名时区表。
- 应用禁止自动执行结构和演示数据；专用验证脚本创建隔离MySQL实例和临时账号，结束后关闭清理。

## 验证证据

| 检查       | 结果                                                                                        |
| ---------- | ------------------------------------------------------------------------------------------- |
| JDK        | Oracle Java/javac 1.8.0_471，Maven Enforcer通过                                             |
| 单元       | 8项通过，0失败、0跳过                                                                       |
| MySQL集成  | MySQL 8.0.43，13项通过，0失败、0跳过                                                        |
| 打包与启动 | 可执行JAR打包成功，Java8启动并通过真实HTTP登录及匿名401验证                                 |
| 字节码     | 59个运行时JAR、16598个基础class兼容Java8；排除Java8不会加载的multi-release分支与module-info |
| Wrapper    | 官方分发包SHA-512核对后固定SHA-256，mvnw.cmd已验证运行                                      |
| 安全扫描   | 完整查询59个运行时坐标，19个组件命中113个去重OSV公告；尚未解除上线门禁                      |

集成场景包含：邮箱登录、角色伪造拒绝、禁用账号、恢复不复活旧token、版本递增撤销、角色变更撤销、过期及错误签名、未知JSON字段、重复Authorization头、CORS、登录限流、缺失路由、内部错误不泄露细节。

详细测试报告位于target/surefire-reports与target/failsafe-reports；验证日志在target/mysql-verification.log、target/application-smoke.log。依赖树、字节码报告和OSV原始结果也在target，均不纳入Git。

复现命令和环境配置见 [README.md](README.md)。`mvn test`只跑不需要数据库的单元测试；真实数据库与打包验证使用scripts/verify_mysql.py，不直接针对已有数据库执行集成测试。

用户提供的本机数据库凭据已保存在Git忽略的外置配置中。本机Windows MySQL服务处于停止状态，当前会话无法启动服务，因此本机root凭据和paperhub库状态尚未验证；这不等于账号密码错误。所有已通过的数据库测试来自独立临时实例，未修改已有库。

## 本批边界与下一批

本批仅实现生产A1端点及共用基础。前端仍为Mock，尚未将登录切到真实服务。其他业务接口按计划开发，不用固定成功响应冒充实现。

下一批实施文件与论文核心：真实PDF上传/服务端哈希和页数、论文CRUD与逻辑删除、受控预览/下载、头像上传及文件补偿；管理员私有PDF访问须先完成可靠审计。用户状态修改、改密和重置接口在第6批实现，本批已验证它们所需的token_version撤销基础。
