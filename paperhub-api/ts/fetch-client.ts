import { ApiError, type ApiClient, type ApiResponse } from "./http";

const fileNames = new WeakMap<Blob, string>();
export function getDownloadFilename(blob: Blob): string | undefined { return fileNames.get(blob); }
function responseFilename(value: string | null): string | undefined {
  if (!value) return undefined;
  const extended = /filename\*\s*=\s*UTF-8''([^;]+)/i.exec(value);
  let name = /filename\s*=\s*"([^"]+)"/i.exec(value)?.[1];
  if (extended) { try { name = decodeURIComponent(extended[1]!); } catch { /* Use the quoted fallback. */ } }
  return name?.replace(/[<>:"/\\|?*\x00-\x1f]/g, '_').trim().slice(0, 180) || undefined;
}

export interface FetchClientOptions {
  baseUrl?: string;
  getToken: () => string | null;
  onUnauthorized?: (requestToken: string | null) => void;
  isSessionCurrent?: (requestToken: string | null) => boolean;
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
    if (options.isSessionCurrent && !options.isSessionCurrent(token))
      throw new ApiError(1001, "登录状态已改变，请重新加载");
    const isJson =
      response.headers.get("content-type")?.includes("json") ?? false;
    if (binary && response.ok && !isJson) {
      if (!response.headers.get("content-type")?.includes("application/pdf"))
        throw new ApiError(1500, "服务器未返回 PDF 文件");
      const blob = await response.blob();
      if (options.isSessionCurrent && !options.isSessionCurrent(token)) throw new ApiError(1001, "登录状态已改变，请重新加载");
      const name = responseFilename(response.headers.get("content-disposition"));
      if (name) fileNames.set(blob, name);
      return blob as T;
    }
    if (!isJson) {
      if (response.status === 401) options.onUnauthorized?.(token);
      throw new ApiError(
        response.status === 401 ? 1001 : 1500,
        `请求失败（HTTP ${response.status}）`,
      );
    }
    const result: ApiResponse<T> = await response.json();
    if (options.isSessionCurrent && !options.isSessionCurrent(token)) throw new ApiError(1001, "登录状态已改变，请重新加载");
    if (!response.ok || result.code !== 0) {
      if (response.status === 401 || result.code === 1001)
        options.onUnauthorized?.(token);
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
