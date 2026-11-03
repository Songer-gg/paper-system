import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import type { LoginUser, Role } from '@/types/auth'
import { mockVerifyLogin } from '@/mocks/auth'

const STORAGE_KEY = 'paperhub_auth'

function readStoredUser(): LoginUser | null {
  try {
    const raw =
      localStorage.getItem(STORAGE_KEY) ?? sessionStorage.getItem(STORAGE_KEY)
    return raw ? (JSON.parse(raw) as LoginUser) : null
  } catch {
    return null
  }
}

export const useAuthStore = defineStore('auth', () => {
  const user = ref<LoginUser | null>(readStoredUser())
  const isLoggedIn = computed(() => user.value !== null)

  const homePath = computed(() =>
    user.value?.role === 'admin' ? '/admin/dashboard' : '/user/dashboard',
  )

  async function login(
    username: string,
    password: string,
    role: Role,
    remember: boolean,
  ): Promise<LoginUser> {
    const loggedIn = await mockVerifyLogin(username, password, role)
    user.value = loggedIn
    // “记住登录状态”：localStorage 持久化；否则 sessionStorage 会话级保存
    const storage = remember ? localStorage : sessionStorage
    storage.setItem(STORAGE_KEY, JSON.stringify(loggedIn))
    return loggedIn
  }

  function logout() {
    user.value = null
    localStorage.removeItem(STORAGE_KEY)
    sessionStorage.removeItem(STORAGE_KEY)
  }

  return { user, isLoggedIn, homePath, login, logout }
})
