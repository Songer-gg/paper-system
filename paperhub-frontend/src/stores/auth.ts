import { computed } from 'vue'
import { defineStore } from 'pinia'
import type { LoginUser, Role } from '@/types/auth'
import { useMock } from '@/config/runtime'
import { apiClient } from '@/services/api'
import {
  clearSession,
  currentSession,
  saveSession,
  updateSessionUser,
  sessionGeneration,
} from '@/utils/session'
import { ApiError } from '@/services/api'
import { login as requestLogin } from '../../../paperhub-api/ts/modules/auth'

export const useAuthStore = defineStore('auth', () => {
  const user = computed<LoginUser | null>(() => currentSession.value?.user ?? null)
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
    clearSession()
    const generation = sessionGeneration()
    if (useMock) {
      const { mockVerifyLogin } = await import('@/mocks/auth')
      const loggedIn = await mockVerifyLogin(username, password, role)
      if (generation !== sessionGeneration()) throw new ApiError(1001, '登录操作已失效，请重试')
      saveSession('mock-' + crypto.randomUUID(), loggedIn, remember)
      return loggedIn
    }
    const result = await requestLogin(apiClient, { username, password, role })
    if (generation !== sessionGeneration()) throw new ApiError(1001, '登录操作已失效，请重试')
    saveSession(result.token, result.user, remember)
    return result.user
  }

  function logout() {
    clearSession()
  }

  return { user, isLoggedIn, homePath, login, logout, updateUser: updateSessionUser }
})
