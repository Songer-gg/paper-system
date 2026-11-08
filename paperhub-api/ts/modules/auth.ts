/**
 * 认证模块（A1）
 * 对应前端：mocks/auth.ts → paperhub-frontend/src/stores/auth.ts
 */
import type { ApiClient } from '../http'
import type { LoginRequest, LoginResponse, LoginUser, RegisterRequest } from '../types'

/** A1 POST /auth/login — 登录（username 支持用户名或邮箱；role 区分用户端/管理员端） */
export function login(client: ApiClient, body: LoginRequest): Promise<LoginResponse> {
  return client.post('/auth/login', body)
}

/** A2 POST /auth/register — 仅创建普通用户；成功后由用户重新登录。 */
export function register(client: ApiClient, body: RegisterRequest): Promise<LoginUser> {
  return client.post('/auth/register', body)
}
