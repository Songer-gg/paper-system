import { shallowRef } from 'vue'
import type { LoginUser } from '@/types/auth'
import { useMock } from '@/config/runtime'

interface Session {
  token: string
  user: LoginUser
  mode: 'real' | 'mock'
}
const key = 'paperhub_auth'
const mode = useMock ? 'mock' : 'real'
const resets = new Set<() => void>()
let generation = 0
function storedSession(): Session | null {
  for (const storage of [localStorage, sessionStorage]) {
    try {
      const raw = storage.getItem(key)
      if (!raw) continue
      const value: unknown = JSON.parse(raw)
      if (
        typeof value === 'object' &&
        value !== null &&
        'token' in value &&
        typeof value.token === 'string' &&
        value.token.length > 8 &&
        'mode' in value &&
        value.mode === mode &&
        'user' in value &&
        typeof value.user === 'object' &&
        value.user !== null &&
        'id' in value.user &&
        Number.isSafeInteger(value.user.id) &&
        Number(value.user.id) > 0 &&
        'role' in value.user &&
        (value.user.role === 'user' || value.user.role === 'admin') &&
        'name' in value.user &&
        typeof value.user.name === 'string'
      )
        return value as Session
      storage.removeItem(key)
    } catch {
      storage.removeItem(key)
    }
  }
  return null
}
export const currentSession = shallowRef<Session | null>(storedSession())
export function getToken(): string | null {
  return currentSession.value?.token ?? null
}
export function sessionGeneration(): number {
  return generation
}
export function onSessionReset(callback: () => void): void {
  resets.add(callback)
}
export function clearSession(): void {
  generation++
  currentSession.value = null
  localStorage.removeItem(key)
  sessionStorage.removeItem(key)
  for (const reset of resets) reset()
}
export function saveSession(token: string, user: LoginUser, remember: boolean): void {
  clearSession()
  const value: Session = { token, user, mode }
  ;(remember ? localStorage : sessionStorage).setItem(key, JSON.stringify(value))
  currentSession.value = value
}
export function updateSessionUser(patch: Partial<Pick<LoginUser, 'name' | 'email'>>): void {
  const current = currentSession.value
  if (!current) return
  const next = { ...current, user: { ...current.user, ...patch } }
  const storage = localStorage.getItem(key) ? localStorage : sessionStorage
  storage.setItem(key, JSON.stringify(next))
  currentSession.value = next
}
export function expireSession(): void {
  clearSession()
  window.dispatchEvent(new Event('paperhub:unauthorized'))
}
// Other tabs must drop cached personal data when the shared remembered identity changes.
window.addEventListener('storage', (event) => {
  if (event.key !== key && event.key !== null) return
  generation++
  sessionStorage.removeItem(key)
  currentSession.value = storedSession()
  for (const reset of resets) reset()
  window.dispatchEvent(new Event('paperhub:unauthorized'))
})
