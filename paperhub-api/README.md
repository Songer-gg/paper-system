# PaperHub API 契约 v1.1

后端以已完成的 Vue 3 页面和实际 Service 消费字段为基础实现。Mock 是交互原型，不是真实权限、文件存储或身份认证。

## 现行依据

- [业务规则](../BUSINESS_RULES.md)：包括用户确认的管理员最高读取权限、敏感访问审计、用户撤回及逻辑删除。
- [API 文档](api-spec.md)：70个端点（原64个 + M26–M28 + 普通注册A2 + 本机PDF解析U13 + DOI联网补全U14）。
- [TypeScript DTO](ts/types.ts)、[模块函数](ts/modules/)：代码级契约。
- [数据库结构](db/schema.sql)、[演示种子](db/seed-demo.sql)、[ER 说明](db/er-design.md)。
- [Java 开发规范](../paperhub-back/BACKEND_DEVELOPMENT_RULES.md)、[分批计划](../paperhub-back/BACKEND_DEVELOPMENT_PLAN.md)。

## 约定

Base URL `/api/v1`；JWT Bearer；JSON `{code,message,data}`；分页 `{list,total}`；时间 `yyyy-MM-dd HH:mm:ss`，Asia/Shanghai。文件成功返回流，失败返回 JSON。上传/下载显式指定 HTTP 方法。

结构脚本仅用于新空库，没有自动删库；不在应用启动时执行。演示种子含公开密码，只供开发空库手动导入。已有库需要另写增量迁移，不能重跑建表脚本。

已为本机v1.0旧库提供[增量迁移说明](db/migrations/README.md)与v1.0_to_v1.1.sql，包含隔离演练和原数据保留检查；不要对已有库直接执行schema.sql。

## 前端接入

`ts/modules` 是传输函数，每个函数接收 ApiClient；它们不是可以直接替换所有 Mock import 的零改动适配器。前端保持 Service 入口，由适配器绑定 client、处理登录响应、缓存选择器、404 空态与文件流。

第9批已完成真实接入，前端默认连接Java后端。`ts/fetch-client.ts`支持按请求token处理401和拒绝过期身份的迟到响应，下载Blob保留Content-Disposition文件名；具体会话和缓存由前端适配器负责。运行及验证见[前端说明](../paperhub-frontend/README.md)与[第9批交付](../paperhub-back/BATCH_9_DELIVERY.md)。

v1.1 已对齐前端的查重结果、重复对证据及状态、重复处理记录、管理员趋势、月度标签、密码结果、重置密码字符串和日期筛选。File、Promise、HTTP 方法及权限边界使用真实协议，具体接入步骤见开发计划。

普通注册A2已按用户后续要求优先开发；管理员不公开注册，开发账号见前后端README。通知、忘记密码、用户举报提交不自动新增。管理员个人数据只读端点来自用户明确授权，敏感访问必须服务端审计。

本机解析U13已按用户确认实现：multipart只传file，须登录，最多20MiB，读取元信息及前3页文字并返回PaperMetadata候选字段、来源及提示；不创建论文、不执行OCR或联网补全，正式提交仍调用U7。见[第11批交付](../paperhub-back/BATCH_11_DELIVERY.md)。

第12批增加独立U14，JSON只传doi，返回PaperMetadata及lookupStatus；sources增加crossref。只有固定Crossref端点会收到DOI，PDF正文仍本机处理。记录无摘要或上游故障需明确处理并保留已填内容，不能把HTTP200软失败当作摘要已取得。见[第12批交付](../paperhub-back/BATCH_12_DELIVERY.md)。
