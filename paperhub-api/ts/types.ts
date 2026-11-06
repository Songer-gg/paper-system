/**
 * PaperHub API 契约类型定义（v1.1，按前端实际消费结构修订）
 *
 * 与 paperhub-frontend/src/types 保持字段一致，供前后端共同引用。
 * 后端（Java 8 + Spring Boot + MySQL 8）实现时以此为准生成 DTO。
 */

/* ================= 枚举 ================= */

export type Role = "user" | "admin";
export type UserStatus = "active" | "disabled";
export type PaperVisibility = "private" | "shared";
export type ReviewStatus = "pending" | "approved" | "rejected" | "offline";
export type ReadingStatus = "unread" | "reading" | "read" | "paused";
export type SharedSortKey = "uploaded-desc" | "downloads-desc" | "year-desc";
export type ReportType = "copyright" | "content" | "duplicate" | "other";
export type ReviewAction = "approved" | "rejected";
export type DuplicateAction = "duplicate" | "distinct" | "linked";
export type ReportStatus = "pending" | "resolved";

/* ================= 通用 ================= */

/** 统一分页结果 */
export interface PageResult<T> {
  list: T[];
  total: number;
}

/* ================= 认证 ================= */

export interface LoginRequest {
  /** 用户名或邮箱 */
  username: string;
  password: string;
  role: Role;
}

export interface LoginUser {
  id: number;
  username: string;
  name: string;
  email: string;
  role: Role;
  registeredAt: string;
}

export interface LoginResponse {
  token: string;
  user: LoginUser;
}

/* ================= 用户 ================= */

export interface PlatformUser {
  id: number;
  username: string;
  name: string;
  email: string;
  role: Role;
  status: UserStatus;
  registeredAt: string;
}

/** 管理员端用户视图（M2/M3） */
export interface AdminUser extends PlatformUser {
  /** 已上传共享论文数（含各审核状态） */
  sharedCount: number;
  /** 通过审核的贡献数量 */
  contributionCount: number;
  /** 最近一次管理员重置密码时间（null=未重置过，替代 mock 的 isPasswordReset） */
  passwordResetAt: string | null;
}

export interface MyProfile extends PlatformUser {
  avatar: string;
  uploadedCount: number;
}

/* ================= 论文 ================= */

export interface Paper {
  id: number;
  title: string;
  authors: string[];
  abstract: string;
  doi: string;
  journal: string;
  year: number;
  keywords: string[];
  categoryId: number | null;
  tagIds: number[];
  visibility: PaperVisibility;
  uploaderId: number;
  uploaderName: string;
  uploadedAt: string;
  downloads: number;
  fileSizeMB: number;
  pageCount: number;
  /** 私有论文不参与审核，省略此字段 */
  reviewStatus?: ReviewStatus;
  offlineReason?: "withdrawn" | "moderation";
  /** 驳回/下架原因，仅上传者与管理员可见 */
  reviewComment?: string;
  /** 原文链接（可选） */
  link?: string;
}

export interface PaperEditPayload {
  title?: string;
  authors?: string[];
  abstract?: string;
  journal?: string;
  year?: number;
  doi?: string;
  categoryId?: number;
  tagIds?: number[];
  keywords?: string[];
  link?: string;
}

/** 上传入参（U7 的 JSON part） */
export interface UploadPayload extends PaperEditPayload {
  title: string;
  authors: string[];
  abstract: string;
  year: number;
  categoryId: number;
  tagIds: number[];
  keywords: string[];
  visibility: PaperVisibility;
  /** 共享上传必须为 true，否则服务端 1006 拒绝 */
  copyrightAgreed: boolean;
}

export interface DuplicateCheckResult {
  level: "doi" | "title" | "file" | "none";
  message: string;
  /** 仅返回调用者有权查看的命中论文；不可见的重复只返回提示 */
  paper?: Paper;
}

export interface DuplicateCheckInput {
  title?: string;
  doi?: string;
  fileSha256?: string;
}

/** 共享流转动作（U5） */
export type ReviewFlowAction = "share" | "unshare" | "resubmit";

/* ================= 分类 / 标签 ================= */

export interface Category {
  id: number;
  name: string;
  /** null 表示根分类 */
  parentId: number | null;
  desc?: string;
  /** true 表示用户自建（仅本人与管理员可操作） */
  custom?: boolean;
  ownerId?: number | null;
  /** API 响应内嵌：该分类下论文数 */
  paperCount?: number;
}

export interface Tag {
  id: number;
  name: string;
  color: string;
  custom?: boolean;
  ownerId?: number | null;
  /** API 响应内嵌：该标签下论文数 */
  paperCount?: number;
}

/** K4 删除响应：级联删除的分类名列表（供前端确认提示） */
export type CategoryDeleteResult = string[];

/* ================= 阅读 / 收藏 / 笔记 ================= */

export interface ReadingRecord {
  paperId: number;
  status: ReadingStatus;
  addedAt: string;
  lastReadAt: string;
}

export interface FavoriteRecord {
  paperId: number;
  paper: Paper | null;
  favoritedAt: string;
}

export interface Note {
  id: number;
  paperId: number;
  paperTitle?: string;
  paperAvailable: boolean;
  title: string;
  content: string;
  createdAt: string;
  updatedAt: string;
}

export interface NoteInput {
  paperId: number;
  title: string;
  content: string;
}

/* ================= 审核 / 重复 / 举报 / 日志（管理员端） ================= */

export interface ReviewRecord {
  id: number;
  paperId: number;
  paperTitle: string;
  uploaderId: number;
  uploaderName: string;
  action: ReviewAction;
  comment: string;
  reviewerId: number;
  reviewerName: string;
  reviewedAt: string;
}

export interface ReviewGroup {
  pending: Paper[];
  approved: Paper[];
  rejected: Paper[];
}

export interface DuplicatePair {
  id: number;
  originalPaperId: number;
  newPaperId: number;
  doiMatch: boolean;
  titleSimilarity: number;
  fileHashMatch: boolean;
  status: "pending" | DuplicateAction;
  submittedAt: string;
  handledAt?: string;
  handlerNote?: string;
}

export interface DuplicateRecord {
  id: number;
  pairId: number;
  paperTitle: string;
  action: DuplicateAction;
  note: string;
  handlerId: number;
  handlerName: string;
  handledAt: string;
}

export interface ReportItem {
  id: number;
  type: ReportType;
  paperId: number;
  paperTitle: string;
  reporterId: number;
  reporterName: string;
  reason: string;
  createdAt: string;
  status: ReportStatus;
  /** 处理结果说明（已处理时） */
  result?: string;
  handledAt?: string;
}

export interface OperationLog {
  id: number;
  userName: string;
  action: string;
  target: string;
  result: "成功" | "失败";
  time: string;
}

/* ================= 仪表盘 / 统计 ================= */

export interface UserDashboard {
  counts: {
    total: number;
    private: number;
    shared: number;
    favorites: number;
    read: number;
  };
  yearDist: { year: string; count: number }[];
  categoryDist: { name: string; value: number }[];
  readingDist: { status: ReadingStatus; label: string; count: number }[];
  recentUploads: Paper[];
  recentReadings: { paper: Paper; lastReadAt: string; status: ReadingStatus }[];
  recentFavorites: { paper: Paper; favoritedAt: string }[];
}

export interface AdminDashboard {
  totals: {
    users: number;
    papers: number;
    pending: number;
    papersToday: number;
    usersToday: number;
  };
  uploadTrend: { months: string[]; papers: number[] };
  registerTrend: { months: string[]; users: number[] };
  categoryDist: { name: string; value: number }[];
  recentUploads: Paper[];
  pendingTasks: Paper[];
}

export interface UserStatistics {
  totalPapers: number;
  privateCount: number;
  sharedCount: number;
  favoriteCount: number;
  readCount: number;
  noteCount: number;
  monthlyReadingTrend: { month: string; label: string; count: number }[];
  yearDistribution: { name: string; count: number }[];
  categoryDistribution: { name: string; count: number }[];
  journalDistribution: { name: string; count: number }[];
}

/* ================= 请求查询参数 ================= */

export interface SharedQuery {
  q?: string;
  categoryId?: number | null;
  tagIds?: number[];
  year?: number | null;
  journal?: string | null;
  sort?: SharedSortKey;
  page: number;
  pageSize: number;
}

export interface AdminUserQuery {
  keyword: string;
  status: UserStatus | "all";
  dateRange: [string, string] | null;
}

export interface PasswordResult {
  ok: boolean;
  message: string;
}

export interface PlatformSettings {
  platformName: string;
  slogan: string;
  logoText: string;
  version: string;
  maxUploadMB: number;
  allowedTypes: string;
  contactEmail: string;
}

/** 管理员敏感读取：响应与访问均须服务端审计；不接受客户端指定管理员身份。 */
export interface AdminReadingRecord extends ReadingRecord {
  paper: Paper | null;
}

export interface AdminPaperQuery {
  title?: string;
  doi?: string;
  uploader?: string;
  categoryId?: number | null;
  status?: ReviewStatus | "all";
}
