# PaperHub 论文管理平台 — 前端（第一阶段）

基于 Vue 3 的论文管理平台前端。第一阶段无真实后端，所有数据由本地 Mock 数据 + localStorage 模拟服务提供，完整覆盖用户端与管理员端功能，并支持两端审核/下架/删除等操作的实时联动。

## 技术栈

| 类别      | 选型                                                                |
| --------- | ------------------------------------------------------------------- |
| 框架      | Vue 3（Composition API + `<script setup>`）                         |
| 语言      | TypeScript                                                          |
| 构建      | Vite 8（rolldown）                                                  |
| 路由      | Vue Router 4                                                        |
| 状态管理  | Pinia                                                               |
| UI 组件库 | Element Plus（中文语言包 + 全局图标注册）                           |
| 图表      | ECharts 6（按需分包）                                               |
| HTTP      | Axios（为第二阶段接入后端预留）                                     |
| 代码质量  | vue-tsc（`-b` 构建模式类型检查）、ESLint 9（flat config）、Prettier |

## 快速开始

```bash
# 安装依赖
npm install

# 启动开发服务器（默认 http://localhost:5173）
npm run dev

# 生产构建（含类型检查）
npm run build
```

其他命令：

```bash
npm run type-check   # vue-tsc -b 全量类型检查
npm run lint         # ESLint 检查并自动修复
npm run format       # Prettier 格式化 src/
npm run preview      # 预览生产构建产物
```

## 演示账号

登录页右上角可切换登录角色。

| 角色     | 用户名       | 密码       | 身份                      |
| -------- | ------------ | ---------- | ------------------------- |
| 普通用户 | `researcher` | `123456`   | 林知远（用户 ID 1）       |
| 管理员   | `admin`      | `admin123` | 平台管理员（用户 ID 101） |

## 目录结构

```
src/
├── components/            # 公共组件（TagChip、VChart 等）
├── layouts/               # 布局（UserLayout 用户端 / AdminLayout 管理员端）
├── mocks/                 # Mock 数据（papers / user / admin / users / tags / categories）
├── router/                # 路由（用户端 + 管理员端，登录守卫按角色隔离）
├── services/              # 模拟业务服务（检索、上传、审核、统计、知识管理等）
├── stores/                # Pinia（登录态）
├── styles/                # 全局样式与设计变量
├── types/                 # TS 类型定义
├── utils/                 # localStorage 工具
└── views/
    ├── login/            # 登录页（角色切换）
    ├── user/              # 用户端 14 页（首页 / 我的论文库 / 共享库 / 上传 / 详情 / 阅读器 / 分类 / 标签 / 阅读 / 笔记 / 统计 / 个人中心）
    └── admin/             # 管理员端 14 页（首页 / 用户 / 论文 / 下架 / 审核 / 重复 / 举报 / 分类 / 标签 / 日志 / 设置）
```

## 功能概览

- **用户端**：论文上传（含版权授权与查重提醒）、我的论文库（编辑 / 删除 / 共享流转）、共享论文库检索（关键词 / 分类 / 标签 / 年份 / 期刊 / 排序 / 分页）、在线阅读器、收藏、阅读管理、阅读笔记、分类与标签管理、统计图表、个人中心。
- **管理员端**：用户管理、共享论文管理与下架、论文审核（通过 / 驳回，需版权声明确认）、疑似重复处理、举报处理、平台分类与标签、操作日志、系统设置（只读展示）。
- **两端联动**：审核通过后论文立即出现在用户端共享库；驳回原因反馈给上传者；下架后用户端不可访问；删除操作全局生效。

## Mock 数据说明

- 所有写操作（上传、审核、下架、收藏、笔记、分类调整等）通过 localStorage 持久化，键统一以 `ph_` 前缀命名（如 `ph_paper_overlay`、`ph_uploaded_papers`）。
- 管理员端与用户端共享同一组 overlay 键，因此两端数据实时联动。
- 如需恢复初始演示数据，清除浏览器该站点的 localStorage 即可（或在控制台执行 `localStorage.clear()`）。

## 设计规范速查

- 主色 `#2563EB`，页面背景 `#F5F7FA`，边框 `#E5E7EB`
- 用户端：白色侧栏的学术 SaaS 风格；管理员端：深藏蓝侧栏
- 全局工具类：`ph-page` / `ph-card` / `ph-status` / `ph-ellipsis` 等（见 `src/styles/index.css`）

## 与后端对接说明

第二阶段保持 `src/services/` 的页面入口，由真实适配器绑定 ApiClient，处理登录 token、缓存选择器、文件流和错误空态。API 已提供无框架依赖的 `paperhub-api/ts/fetch-client.ts`，也可以依接口使用现有 Axios 实现。不能直接替换全部 import 后假定零改动。

现行规则见 [BUSINESS_RULES.md](../BUSINESS_RULES.md)，分批计划见 [后端开发计划](../paperhub-back/BACKEND_DEVELOPMENT_PLAN.md)。管理员已获授权查看全部论文和个人数据，敏感读取必须审计；本轮 Mock 只演示该交互，真实安全控制由 Java 实现。

回归：在工作区运行 `node paperhub-api/tests/contracts.cjs`；SQL 在独立临时实例运行 `python -B paperhub-api/tests/test_schema.py --bin-dir "MySQL安装目录/bin"`，不连接已有数据库。
