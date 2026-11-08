import { useMock } from '@/config/runtime'
import { currentSession } from '@/utils/session'
import { loadJSON, saveJSON } from '@/utils/storage'
export function currentUserId(): number {
  return currentSession.value?.user.id ?? 0
}
export function currentRole(): 'user' | 'admin' {
  return currentSession.value?.user.role ?? 'user'
}
export function loadPersonalJSONFor<T>(userId: number, key: string, fallback: T): T {
  return loadJSON(
    `${useMock ? 'mock' : 'real'}:${key}:${userId}`,
    useMock && userId === 1 ? loadJSON(key, fallback) : fallback,
  )
}
export function loadPersonalJSON<T>(key: string, fallback: T): T {
  return loadPersonalJSONFor(currentUserId(), key, fallback)
}
export function savePersonalJSON(key: string, value: unknown): void {
  if (!currentUserId()) throw new Error('请先登录')
  saveJSON(`${useMock ? 'mock' : 'real'}:${key}:${currentUserId()}`, value)
}
