import { loadJSON, saveJSON } from '@/utils/storage'
function identity(): { id?: number; role?: string } {
  const raw = localStorage.getItem('paperhub_auth') ?? sessionStorage.getItem('paperhub_auth')
  if (!raw) return { id: 1, role: 'user' }
  try {
    const value = JSON.parse(raw)
    return value.user ?? value
  } catch {
    return { id: 1, role: 'user' }
  }
}
export function currentUserId(): number {
  return identity().id ?? 1
}
export function currentRole(): 'user' | 'admin' {
  return identity().role === 'admin' ? 'admin' : 'user'
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
