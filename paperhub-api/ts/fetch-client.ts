import { ApiError, type ApiClient, type ApiResponse } from "./http";

export interface FetchClientOptions {
  baseUrl?: string;
  getToken: () => string | null;
  onUnauthorized?: () => void;
  fetch?: typeof globalThis.fetch;
}

/** 与 UI/框架无关的实际传输实现；Service 适配器负责登录态、缓存及页面空态。 */
export function createFetchClient(options: FetchClientOptions): ApiClient {
  const base = (options.baseUrl ?? "/api/v1").replace(/\/$/, "");
  const send = options.fetch ?? globalThis.fetch.bind(globalThis);

  async function request<T>(
    method: string,
    path: string,
    body?: unknown,
    params?: Record<string, unknown>,
    binary = false,
  ): Promise<T> {
    const query = new URLSearchParams();
    for (const [key, value] of Object.entries(params ?? {})) {
      if (value !== undefined && value !== null) query.set(key, String(value));
    }
    const suffix = query.size ? `?${query}` : "";
    const headers = new Headers({
      Accept: binary ? "application/pdf, application/json" : "application/json",
    });
    const token = options.getToken();
    if (token) headers.set("Authorization", `Bearer ${token}`);
    let payload: BodyInit | undefined;
    if (body instanceof FormData) payload = body;
    else if (body !== undefined) {
      headers.set("Content-Type", "application/json");
      payload = JSON.stringify(body);
    }
    const response = await send(`${base}${path}${suffix}`, {
      method,
      headers,
      body: payload,
    });
    const isJson =
      response.headers.get("content-type")?.includes("json") ?? false;
    if (binary && response.ok && !isJson) {
      if (!response.headers.get("content-type")?.includes("application/pdf"))
        throw new ApiError(1500, "服务器未返回 PDF 文件");
      return (await response.blob()) as T;
    }
    if (!isJson) {
      if (response.status === 401) options.onUnauthorized?.();
      throw new ApiError(
        response.status === 401 ? 1001 : 1500,
        `请求失败（HTTP ${response.status}）`,
      );
    }
    const result: ApiResponse<T> = await response.json();
    if (!response.ok || result.code !== 0) {
      if (response.status === 401 || result.code === 1001)
        options.onUnauthorized?.();
      throw new ApiError(result.code || 1500, result.message || "请求失败");
    }
    if (binary) throw new ApiError(1500, "预期 PDF 文件，但服务器返回了 JSON");
    return result.data;
  }

  return {
    get: (path, params) => request("GET", path, undefined, params),
    post: (path, body, params) => request("POST", path, body, params),
    patch: (path, body) => request("PATCH", path, body),
    put: (path, body) => request("PUT", path, body),
    delete: (path) => request("DELETE", path),
    upload: (path, form, method) => request(method, path, form),
    download: (path, method) =>
      request(method, path, undefined, undefined, true),
  };
}
