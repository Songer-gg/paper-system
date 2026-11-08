import type { LoginUser, RegisterRequest } from '../../../paperhub-api/ts/types'
import { register } from '../../../paperhub-api/ts/modules/auth'
import { useMock } from '@/config/runtime'
import { apiClient } from './api'

/** Registration always persists a real ordinary account; Mock cannot create login credentials. */
export function registerUser(input: RegisterRequest): Promise<LoginUser> {
  if (useMock) return Promise.reject(new Error('注册需要连接真实后端，请关闭Mock模式'))
  return register(apiClient, input)
}
