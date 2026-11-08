# paper-system

现行依据：[业务规则](BUSINESS_RULES.md)、[API 契约](paperhub-api/api-spec.md)、[后端开发规范](paperhub-back/BACKEND_DEVELOPMENT_RULES.md)、[分批开发计划](paperhub-back/BACKEND_DEVELOPMENT_PLAN.md)。

这是一个论文管理系统。

第1–9批功能开发及真实前后端联调已完成。页面访问 http://localhost:5173，8080提供后端API。运行步骤见[前端说明](paperhub-frontend/README.md)和[部署说明](paperhub-back/DEPLOYMENT.md)，当前验证及已知限制见[第9批交付](paperhub-back/BATCH_9_DELIVERY.md)。生产依赖安全门禁尚未通过。

第10批优先补齐普通用户注册：访问http://localhost:5173/register，注册成功后返回登录。开发管理员账号admin/admin123（需选择“管理员登录”），普通用户researcher/123456；详见[第10批交付](paperhub-back/BATCH_10_DELIVERY.md)。

- 可帮助用户管理自己的论文。
- 可辅助用户查找论文和学习自己上传的论文。
- 可找寻写内部公开的论文，实现论文的共享学习。
