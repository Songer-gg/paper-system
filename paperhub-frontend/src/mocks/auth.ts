import type { LoginUser, Role } from '@/types/auth'

/**
 * 登录模拟服务
 * 第一阶段无真实后端，使用本地账号表模拟登录校验。
 * 后续接入 Spring Boot 后由 auth API 替换本文件。
 */
interface MockAccount extends LoginUser {
  password: string
}

export const mockUserAccounts: MockAccount[] = [
  {
    id: 1,
    username: 'researcher',
    password: '123456',
    name: '林知远',
    email: 'researcher@paperhub.cn',
    role: 'user',
    registeredAt: '2025-03-12 09:30:00',
  },
]

export const mockAdminAccounts: MockAccount[] = [
  {
    id: 101,
    username: 'admin',
    password: 'admin123',
    name: '平台管理员',
    email: 'admin@paperhub.cn',
    role: 'admin',
    registeredAt: '2025-01-01 08:00:00',
  },
]

export function mockVerifyLogin(
  username: string,
  password: string,
  role: Role,
): Promise<LoginUser> {
  return new Promise((resolve, reject) => {
    // 模拟网络延迟
    setTimeout(() => {
      const pool = role === 'admin' ? mockAdminAccounts : mockUserAccounts
      const account = pool.find(
        (a) => a.username === username.trim() || a.email === username.trim(),
      )
      if (!account) {
        reject(
          new Error(
            role === 'admin'
              ? '管理员账号不存在，请确认账号信息'
              : '用户名或邮箱不存在，请先注册',
          ),
        )
        return
      }
      if (account.password !== password) {
        reject(new Error('密码错误，请重新输入'))
        return
      }
      const { password: _ignored, ...user } = account
      resolve(user)
    }, 600)
  })
}
