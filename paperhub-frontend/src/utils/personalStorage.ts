import type { LoginUser } from '@/types/auth'
import { loadJSON, saveJSON } from '@/utils/storage'

/** Mock 个人缓存按账号隔离；真实授权由后端实现。旧缓存只归演示用户 1。 */
export function currentUserId(): number {
  const session = localStorage.getItem('paperhub_auth') ?? sessionStorage.getItem('paperhub_auth')
  if (!session) return 1
  try {
    return (JSON.parse(session) as LoginUser).id
  } catch {
    return 1
  }
}

export function currentRole(): 'user' | 'admin' {
  const session = localStorage.getItem('paperhub_auth') ?? sessionStorage.getItem('paperhub_auth')
  try {
    return session && (JSON.parse(session) as LoginUser).role === 'admin' ? 'admin' : 'user'
  } catch {
    return 'user'
  }
}

export function loadPersonalJSONFor<T>(userId: number, key: string, fallback: T): T {
  return loadJSON(`${key}:${userId}`, userId === 1 ? loadJSON(key, fallback) : fallback)
}

export function loadPersonalJSON<T>(key: string, fallback: T): T {
  return loadPersonalJSONFor(currentUserId(), key, fallback)
}

export function savePersonalJSON(key: string, value: unknown): void {
  saveJSON(`${key}:${currentUserId()}`, value)
}
