/**
 * 认证模块（A1）
 * 对应前端：mocks/auth.ts → paperhub-frontend/src/stores/auth.ts
 */
import type { ApiClient } from '../http'
import type { LoginRequest, LoginResponse } from '../types'

/** A1 POST /auth/login — 登录（username 支持用户名或邮箱；role 区分用户端/管理员端） */
export function login(client: ApiClient, body: LoginRequest): Promise<LoginResponse> {
  return client.post('/auth/login', body)
}
