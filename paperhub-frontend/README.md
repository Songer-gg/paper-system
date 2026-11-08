# PaperHub 前端

Vue 3 / TypeScript / Pinia / Element Plus / ECharts。默认连接真实Java后端；页面继续通过src/services入口调用paperhub-api/ts/modules，统一Fetch客户端负责Bearer token、JSON错误及PDF文件流。

## 启动

先确保MySQL及paperhub-back运行，再在本目录执行：

```powershell
npm ci
npm run dev
```

页面：http://localhost:5173。8080为后端API。开发服务将/api代理到127.0.0.1:8080；后端CORS必须允许实际前端源。端口5173被占用时明确报错，不静默切换到其他端口。

默认无需创建.env。若需修改，可复制.env.example为.env.local并重启Vite；VITE_USE_MOCK=false、VITE_API_BASE_URL=/api/v1，PAPERHUB_API_PROXY只用于开发代理。VITE_*会进入浏览器构建，禁止放数据库密码、JWT签名密钥等秘密。

本地种子应用账号为researcher/123456、admin/admin123，仅适用于开发库；数据库账号与应用登录账号不同。真实登录页不显示自动填入凭据。现有旧样例PDF未导入时，阅读器明确显示文件不可用；请经上传页面提交真实PDF，不回退到模拟正文。

## 登录和数据

remember决定localStorage或sessionStorage。保存的是{token,user,mode}；旧Mock用户对象不能冒充真实登录。401清除会话、分类/收藏缓存并返回登录页；改密成功也退出重新登录。旧会话的迟到响应不能覆盖新账号缓存或使新账号退出。记住登录身份改变时，同源其他标签页清空个人缓存并重新导航。

姓名、上传者、审核/举报/重复处理人来自后端字段；不为普通用户调用管理员用户列表。分类/标签缓存来自K1/K7，计数由服务端提供。收藏缓存先由R1初始化，再用R2的目标状态更新；取消收藏明确传false。草稿只在本人、模式隔离的本地键中保存元数据；PDF不能恢复，需重新选择。真实业务数据不存本地overlay，不读取旧ph_uploaded_papers/ph_notes等Mock结果。

上传发送原File及application/json的payload。服务端计算页数、大小及哈希；浏览器哈希只用于共享提交前提示。PDF通过携带Bearer的U9获取Blob，再交给浏览器原生阅读器；U8下载解析Content-Disposition文件名，及时释放对象URL，不把token写进URL。原生阅读器控件取决于浏览器；不支持内嵌PDF时可在新窗口阅读或下载。

管理员可查看私有资源，访问必须审计；已删除论文的PDF对所有角色都不可访问。用户撤回后管理端恢复按钮禁用。笔记来源失效仍可编辑/导出正文；标题快照来自Note.paperTitle。举报页使用举报时paperTitle，不用当前论文标题重建历史。系统设置只读；通知、注册、忘记密码、用户举报提交未纳入本轮。

## Mock模式

仅在明确设置VITE_USE_MOCK=true并重启后启用。模拟Service集中在src/mocks/services，真实模式不会因网络失败回退到它们。两种模式的会话不能互用。Mock不提供真实PDF或安全授权保证，正式构建保持false。

## 验证

```powershell
npm run type-check
npm run build
node node_modules/eslint/bin/eslint.js src
node ../paperhub-api/tests/contracts.cjs
```

Chrome浏览器端到端测试只允许在受控临时库中运行：

```powershell
python -B ../paperhub-back/scripts/verify_mysql.py `
  --mysql-bin '你的MySQL8/bin' `
  --maven '后端mvnw.cmd绝对路径' `
  --java-home '你的JDK8' `
  --frontend-e2e
```

脚本创建临时MySQL、随机后端/前端端口和专用测试账号，测试数据不进入现有库；测试端口单独加入该临时后端CORS白名单。浏览器通过实际表单、审核、文件流、收藏/阅读/笔记和用户管理操作验证；Playwright使用本机Chrome。npm run test:e2e缺少隔离标记时拒绝执行写测试。报告在paperhub-back/target/frontend-e2e-report.json，失败截图/trace在本目录test-results（已忽略Git）。测试trace可能包含临时会话信息，勿公开上传。

scripts/read-only-check.mjs用于已启动的本机开发服务，只执行登录及页面读取；管理员读取仍按规则产生审计日志。生产部署说明见../paperhub-back/DEPLOYMENT.md，交付状态见../paperhub-back/BATCH_9_DELIVERY.md。

## 发布边界

构建产物为dist。后端既有依赖安全公告及上线门禁见../paperhub-back/SECURITY_AUDIT.md；前端构建、页面回归通过不代表该门禁已解除。
