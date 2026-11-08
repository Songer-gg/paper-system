import { createFetchClient } from '../../../paperhub-api/ts/fetch-client'
import { ApiError } from '../../../paperhub-api/ts/http'
import { apiBase } from '@/config/runtime'
import { expireSession, getToken } from '@/utils/session'

export { ApiError }
export const apiClient = createFetchClient({
  baseUrl: apiBase,
  getToken,
  isSessionCurrent: (token) => token === getToken(),
  onUnauthorized: (token) => {
    if (token === getToken()) expireSession()
  },
  fetch: async (input, init) => {
    try {
      return await fetch(input, { ...init, signal: AbortSignal.timeout(120_000) })
    } catch {
      throw new ApiError(1500, '无法连接服务器或请求超时，请检查网络与后端服务')
    }
  },
})
export async function nullableDetail<T>(request: Promise<T>): Promise<T | null> {
  try {
    return await request
  } catch (error) {
    if (error instanceof ApiError && error.code === 1003) return null
    throw error
  }
}
