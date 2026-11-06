/**
 * PaperHub API 传输层约定（v1.0）
 *
 * 定义统一响应包、错误码与 ApiClient 接口。
 * 前端接入：用 Axios 实现 ApiClient（baseURL=/api/v1、token 注入、响应包解包）。
 * 后端接入：Spring Boot 全局返回 ApiResponse，异常处理器按错误码表映射。
 */

/* ================= 统一响应包 ================= */

export interface ApiResponse<T> {
  /** 0 = 成功；非 0 见 ErrorCode */
  code: number;
  message: string;
  data: T;
}

/* ================= 错误码 ================= */

export const ErrorCode = {
  OK: 0,
  UNAUTHORIZED: 1001,
  FORBIDDEN: 1002,
  NOT_FOUND: 1003,
  CONFLICT: 1004,
  INVALID_PARAM: 1005,
  BIZ_REJECTED: 1006,
  SERVER_ERROR: 1500,
} as const;

export type ErrorCode = (typeof ErrorCode)[keyof typeof ErrorCode];

/* ================= 请求客户端约定 ================= */

/**
 * HTTP 客户端接口契约：
 * - baseURL 固定 /api/v1
 * - 自动携带 Authorization: Bearer <token>
 * - 收到非 0 code 时抛出携带 code/message 的 ApiError
 */
export interface ApiClient {
  get<T>(url: string, params?: Record<string, unknown>): Promise<T>;
  post<T>(
    url: string,
    body?: unknown,
    params?: Record<string, unknown>,
  ): Promise<T>;
  /** PATCH 用于部分更新 */
  patch<T>(url: string, body?: unknown): Promise<T>;
  put<T>(url: string, body?: unknown): Promise<T>;
  delete<T>(url: string): Promise<T>;
  /** multipart/form-data 上传 */
  upload<T>(url: string, form: FormData, method: "POST" | "PUT"): Promise<T>;
  /** 文件流下载（返回 Blob，由调用方触发浏览器保存） */
  download(url: string, method: "GET" | "POST"): Promise<Blob>;
}

export class ApiError extends Error {
  constructor(
    public code: number,
    message: string,
  ) {
    super(message);
  }
}
