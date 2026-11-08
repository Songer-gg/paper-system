/**
 * 管理员端接口（M1–M28，全部需 admin 角色，服务端校验）
 * 对应前端：adminService.ts、管理员端 14 页
 */
import type { ApiClient } from "../http";
import type {
  AdminDashboard,
  AdminPaperQuery,
  AdminUser,
  AdminUserQuery,
  AdminReadingRecord,
  FavoriteRecord,
  Note,
  DuplicatePair,
  DuplicateRecord,
  OperationLog,
  Paper,
  PlatformSettings,
  ReportItem,
  ReviewGroup,
  ReviewRecord,
  UserStatus,
} from "../types";

/* ---------------- 7.1 管理首页 ---------------- */

/** M1 GET /admin/dashboard — 管理端仪表盘 */
export function getAdminDashboard(client: ApiClient): Promise<AdminDashboard> {
  return client.get("/admin/dashboard");
}

/* ---------------- 7.2 用户管理 ---------------- */

/** M2 GET /admin/users — 用户列表（关键字/状态/注册日期过滤） */
export function getUsers(
  client: ApiClient,
  query: AdminUserQuery,
): Promise<AdminUser[]> {
  const params: Record<string, unknown> = {
    keyword: query.keyword,
    status: query.status,
  };
  if (query.dateRange) {
    params.dateFrom = query.dateRange[0];
    params.dateTo = query.dateRange[1];
  }
  return client.get("/admin/users", params);
}

/** M3 GET /admin/users/{id} — 用户详情 */
export function getUserDetail(
  client: ApiClient,
  id: number,
): Promise<AdminUser | null> {
  return client.get(`/admin/users/${id}`);
}

/** M4 用户全部未删除论文含私有；私有元数据访问必须留审计日志。PDF 走 U8/U9。 */
export function getUserPapers(client: ApiClient, id: number): Promise<Paper[]> {
  return client.get(`/admin/users/${id}/papers`);
}

/** M26 管理员查看用户收藏（含不可用资源占位），服务端记录访问日志。 */
export function getUserFavorites(
  client: ApiClient,
  id: number,
): Promise<FavoriteRecord[]> {
  return client.get(`/admin/users/${id}/favorites`);
}

/** M27 管理员查看用户阅读记录，服务端记录访问日志。 */
export function getUserReadingRecords(
  client: ApiClient,
  id: number,
): Promise<AdminReadingRecord[]> {
  return client.get(`/admin/users/${id}/reading-records`);
}

/** M28 管理员查看用户笔记，服务端记录访问日志。 */
export function getUserNotes(client: ApiClient, id: number): Promise<Note[]> {
  return client.get(`/admin/users/${id}/notes`);
}

/** M5 PATCH /admin/users/{id}/status — 启用 / 禁用用户 */
export function setUserStatus(
  client: ApiClient,
  id: number,
  status: UserStatus,
): Promise<boolean> {
  return client.patch(`/admin/users/${id}/status`, { status });
}

/** M6 POST /admin/users/{id}/password-reset — 重置密码（服务端生成新密码） */
export function resetUserPassword(
  client: ApiClient,
  id: number,
): Promise<string> {
  return client.post(`/admin/users/${id}/password-reset`);
}

/* ---------------- 7.3 共享论文管理 ---------------- */

/** M7 GET /admin/papers — 共享论文池（含待审/驳回/下架，多条件过滤） */
export function getSharedPapers(
  client: ApiClient,
  query: AdminPaperQuery,
): Promise<Paper[]> {
  const params: Record<string, unknown> = {};
  if (query.title) params.title = query.title;
  if (query.doi) params.doi = query.doi;
  if (query.uploader) params.uploader = query.uploader;
  if (query.categoryId != null) params.categoryId = query.categoryId;
  if (query.status) params.status = query.status;
  return client.get("/admin/papers", params);
}

/** M8 GET /admin/papers/{id} — 论文详情（管理端视角） */
export function getPaperById(
  client: ApiClient,
  id: number,
): Promise<Paper | null> {
  return client.get(`/admin/papers/${id}`);
}

/** M9 PATCH /admin/papers/{id}/offline — 下架（用户端立即不可见） */
export function offlinePaper(client: ApiClient, id: number): Promise<boolean> {
  return client.patch(`/admin/papers/${id}/offline`);
}

/** M10 PATCH /admin/papers/{id}/restore — 恢复上架 */
export function restorePaper(client: ApiClient, id: number): Promise<boolean> {
  return client.patch(`/admin/papers/${id}/restore`);
}

/** M11 DELETE /admin/papers/{id} — 删除违规资源（不可恢复，全局生效） */
export function deletePaper(client: ApiClient, id: number): Promise<boolean> {
  return client.delete(`/admin/papers/${id}`);
}

/* ---------------- 7.4 内容审核 ---------------- */

/** M12 GET /admin/review/papers — 三态分组（待审核 / 已通过 / 已驳回） */
export function getReviewPapers(client: ApiClient): Promise<ReviewGroup> {
  return client.get("/admin/review/papers");
}

/** M13 POST /admin/review/papers/{id}/approve — 通过审核（copyrightConfirmed 必须 true，否则 1006；通过后立即进入共享库） */
export function approvePaper(
  client: ApiClient,
  paperId: number,
  comment: string,
  copyrightConfirmed: boolean,
): Promise<boolean> {
  return client.post(`/admin/review/papers/${paperId}/approve`, {
    comment,
    copyrightConfirmed,
  });
}

/** M14 POST /admin/review/papers/{id}/reject — 驳回（comment 必填，否则 1006；原因反馈给上传者） */
export function rejectPaper(
  client: ApiClient,
  paperId: number,
  comment: string,
): Promise<boolean> {
  return client.post(`/admin/review/papers/${paperId}/reject`, { comment });
}

/** M15 GET /admin/review/records — 审核记录（action=all|approved|rejected） */
export function getReviewRecords(
  client: ApiClient,
  action: "all" | "approved" | "rejected",
): Promise<ReviewRecord[]> {
  return client.get("/admin/review/records", { action });
}

/* ---------------- 7.5 疑似重复管理 ---------------- */

/** M16 GET /admin/duplicates — 疑似重复对（status=all|pending|resolved） */
export function getDuplicatePairs(
  client: ApiClient,
  status: "all" | "pending" | "resolved",
): Promise<DuplicatePair[]> {
  return client.get("/admin/duplicates", { status });
}

/** M17 POST /admin/duplicates/{pairId}/confirm — 确认重复（新论文自动驳回） */
export function confirmDuplicate(
  client: ApiClient,
  pairId: number,
  note: string,
): Promise<boolean> {
  return client.post(`/admin/duplicates/${pairId}/confirm`, { note });
}

/** M18 POST /admin/duplicates/{pairId}/distinct — 确认非重复 */
export function confirmDistinct(
  client: ApiClient,
  pairId: number,
  note: string,
): Promise<boolean> {
  return client.post(`/admin/duplicates/${pairId}/distinct`, { note });
}

/** M19 POST /admin/duplicates/{pairId}/link — 关联到原共享论文 */
export function linkToOriginal(
  client: ApiClient,
  pairId: number,
  note: string,
): Promise<boolean> {
  return client.post(`/admin/duplicates/${pairId}/link`, { note });
}

/** M20 GET /admin/duplicate-records — 重复处理记录 */
export function getDuplicateRecords(
  client: ApiClient,
): Promise<DuplicateRecord[]> {
  return client.get("/admin/duplicate-records");
}

/* ---------------- 7.6 举报管理 ---------------- */

/** M21 GET /admin/reports — 举报列表（status=all|pending|resolved） */
export function getReports(
  client: ApiClient,
  status: "all" | "pending" | "resolved",
): Promise<ReportItem[]> {
  return client.get("/admin/reports", { status });
}

/** M22 POST /admin/reports/{id}/resolve — 处理举报（offlinePaperToo=true 时同步下架被举报论文） */
export function resolveReport(
  client: ApiClient,
  reportId: number,
  result: string,
  offlinePaperToo: boolean,
): Promise<boolean> {
  return client.post(`/admin/reports/${reportId}/resolve`, {
    result,
    offlinePaperToo,
  });
}

/** M23 GET /admin/report-records — 举报处理记录 */
export function getReportRecords(client: ApiClient): Promise<ReportItem[]> {
  return client.get("/admin/report-records");
}

/* ---------------- 7.7 日志 / 设置 ---------------- */

/** M24 GET /admin/logs — 操作日志（action=操作类型或 all；写操作由服务端自动留痕） */
export function getLogs(
  client: ApiClient,
  action: string | "all",
): Promise<OperationLog[]> {
  return client.get("/admin/logs", { action });
}

/** M25 GET /admin/settings — 系统设置只读展示 */
export function getSettings(client: ApiClient): Promise<PlatformSettings> {
  return client.get("/admin/settings");
}
