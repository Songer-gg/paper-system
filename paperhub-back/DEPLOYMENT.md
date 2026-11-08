# PaperHub 运行与部署说明

当前提供本机开发运行及可审阅的部署配置示例，不执行公网发布。生产依赖安全门禁尚未通过，见SECURITY_AUDIT.md。

## 本机开发

1. 启动MySQL8；现有库已完成迁移，不重跑schema.sql或种子脚本。
2. 在paperhub-back启动Java8可执行JAR，使用被Git忽略的外置config/local.properties；不要将真实凭据复制到资源目录或命令行。
3. 在paperhub-frontend执行npm ci、npm run dev，浏览器访问http://localhost:5173；8080为受保护API。
4. 开发代理/api默认指向127.0.0.1:8080。后端PAPERHUB_ALLOWED_ORIGINS须匹配真实前端源（本机localhost:5173及127.0.0.1:5173），不使用通配符。

旧样例元数据保留，但原/storage/*.pdf文件不在本机，访问返回404；请重新上传真实文件。不能为演示样例补造PDF或替用户确认版权。

## 构建与配置

后端需JDK8及Maven3.6.3+，固定Spring Boot2.7.18；前端需锁文件对应工具链（本机Node24），npm ci后npm run build。前端VITE_USE_MOCK=false、VITE_API_BASE_URL=/api/v1，dist不含数据库凭据或JWT签名密钥。

后端环境变量：PAPERHUB_DB_URL/USERNAME/PASSWORD、PAPERHUB_JWT_SECRET（至少32字节随机Base64，正常重启复用）、PAPERHUB_ALLOWED_ORIGINS、PAPERHUB_STORAGE_ROOT、PAPERHUB_PORT。应用账号只授予业务所需SELECT/INSERT/UPDATE/DELETE，正式环境不使用root或导入演示账号。

头像/论文存储在Web根目录外；data/storage及.cleanup持久化清理队列须一并保留，禁止公开映射/storage或data。所有PDF均经带鉴权的/api/v1/papers/{id}/file或download访问。当前文件存储按单实例设计；多实例应先统一存储方案，共享目录命名锁依赖同一MySQL主节点。

## 反向代理

示例见deploy/nginx.conf.example：静态dist及/api同源代理，history路由回退index.html；multipart总大小上限至少102MiB，读取超时覆盖大文件上传/下载。正式环境使用HTTPS，后端CORS白名单填写实际站点源。Nginx示例只供配置审阅，需替换域名、站点路径及证书配置；本轮不启动或发布Nginx。

JWT不放URL，不在代理/应用访问日志中输出Authorization；SPA浏览器保存的token与角色字段不替代服务端权限检查。改密/重置/禁用后的旧token不可复活，用户撤回资源不可由管理员恢复。

## 验收与运维

发布前在隔离环境运行scripts/verify_mysql.py --frontend-e2e及前端build/type-check/lint/契约测试，验证权限、文件与事务；执行Java运行时及npm依赖扫描，完成现有安全公告处理结论后再考虑上线。当前没有自动通过或豁免生产门禁。

启动不会自动建库、迁移或创建账号。新空库按paperhub-api/db说明手动初始化；已有库使用带前置检查的迁移。本轮不追加数据库备份；正式运行的数据保护/恢复策略须由部署负责人另行配置和演练。
