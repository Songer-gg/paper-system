export type Role = 'user' | 'admin'

export interface LoginUser {
  id: number
  username: string
  name: string
  email: string
  role: Role
  registeredAt?: string
}
