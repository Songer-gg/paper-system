# PaperHub API 中间层接口（契约层）

本目录是前端与后端之间的**正式 RESTful API 契约**，依据第一阶段已完成的前端页面与 Service 层签名反向设计。第二阶段 Java 8 + Spring Boot 后端与 MySQL 8 数据库设计必须遵循本契约实现。

## 目录结构

```
paperhub-api/
├── README.md        # 本文件：定位、约定速览、前端接入指南
├── api-spec.md       # RESTful API 设计文档（端点清单、入参出参、权限、错误码）★核心
└── ts/               # TypeScript 代码级契约（可直接编译，无运行时依赖）
    ├── types.ts      # 全部 DTO / 枚举定义
    ├── http.ts       # 统一响应包、错误码、分页约定、ApiClient 接口
    └── modules/      # 按业务模块拆分的请求函数签名
        ├── auth.ts           # 登录 / 会话
        ├── library.ts        # 我的论文库 / 上传 / 详情 / 阅读器
        ├── shared-library.ts # 共享论文库检索
        ├── knowledge.ts      # 分类 / 标签管理
        ├── reading.ts        # 收藏 / 阅读管理 / 阅读笔记
        ├── profile.ts        # 个人中心 / 仪表盘 / 数据统计
        └── admin.ts          # 管理员端全部接口
```

## 核心约定速览

| 约定 | 内容 |
| --- | --- |
| Base URL | `/api/v1` |
| 认证 | `Authorization: Bearer <JWT>`；登录响应返回 token，过期时间 24h |
| 统一响应包 | `{ code, message, data }`；`code = 0` 成功，非 0 见错误码表 |
| 分页 | 请求 `page`（1 起）+ `pageSize`；响应 `PageResult<T> = { list, total }` |
| 时间格式 | `yyyy-MM-dd HH:mm:ss`（与前端现有 mock 展示一致） |
| 管理员端前缀 | `/api/v1/admin/**`，需 `admin` 角色 |
| 文件传输 | 上传/下载均走 `multipart/form-data` 或文件流，元数据走 JSON |

## 前端接入方式

`ts/modules/` 中每个函数的第一个参数均为 `ApiClient`（见 `http.ts`），**函数签名与现有 `src/services/` 的 mock 实现保持一致**（个别纯本地函数除外，见 api-spec.md 映射表）。接入步骤：

1. 在 `paperhub-frontend/src/api/` 下用 Axios 实现 `ApiClient`（baseURL、token 注入、响应包解包、错误提示）。
2. 将 `ts/` 目录内容复制进 `src/api/`，为每个模块函数提供真实实现。
3. 各页面将 `import ... from '@/services/xxx'` 替换为 `@/api/modules/xxx`，页面代码无需改动。

## 设计原则

- **只覆盖前端已实现的功能**，不预设未开发的能力（通知、站内信等留待后续版本扩展）。
- **权限边界内置**：管理员接口不暴露用户私有论文内容与私人阅读笔记（见 api-spec.md 权限矩阵）。
- **前端零改动**：契约函数签名 = 现有 service 签名，替换实现即可。
