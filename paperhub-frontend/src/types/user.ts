import type { Role } from '@/types/auth'

export type UserStatus = 'active' | 'disabled'

export interface PlatformUser {
  id: number
  username: string
  name: string
  email: string
  role: Role
  status: UserStatus
  registeredAt: string
}
