import { loadJSON, saveJSON } from '@/utils/storage'
import { mockUsers } from '@/mocks/users'
import type { PlatformUser } from '@/types/user'

/**
 * 个人中心服务：查看/修改个人信息、头像、密码（演示阶段 localStorage 持久化）。
 * 演示账号原始密码为 123456。
 */

const CURRENT_USER_ID = 1
const PROFILE_KEY = 'ph_profile_overlay'

interface ProfileOverlay {
  name?: string
  email?: string
  /** 头像图片 dataURL；为空时展示姓名首字 */
  avatar?: string
}

let overlay = loadJSON<ProfileOverlay>(PROFILE_KEY, {})

export interface MyProfile extends PlatformUser {
  avatar: string
  uploadedCount: number
}

function delay<T>(value: T, ms = 150): Promise<T> {
  return new Promise((resolve) => setTimeout(() => resolve(value), ms))
}

export async function getMyProfile(uploadedCount: number): Promise<MyProfile> {
  const base = mockUsers.find((u) => u.id === CURRENT_USER_ID)!
  return delay({
    ...base,
    name: overlay.name ?? base.name,
    email: overlay.email ?? base.email,
    avatar: overlay.avatar ?? '',
    uploadedCount,
  })
}

export async function updateProfile(patch: { name?: string; email?: string }): Promise<MyProfile> {
  overlay = { ...overlay, ...patch }
  saveJSON(PROFILE_KEY, overlay)
  const uploadedCount = loadJSON<unknown[]>('ph_uploaded_papers', []).length
  return getMyProfile(uploadedCount)
}

export async function updateAvatar(dataUrl: string): Promise<MyProfile> {
  overlay.avatar = dataUrl
  saveJSON(PROFILE_KEY, overlay)
  const uploadedCount = loadJSON<unknown[]>('ph_uploaded_papers', []).length
  return getMyProfile(uploadedCount)
}

export interface PasswordResult {
  ok: boolean
  message: string
}

/** 修改密码：校验原密码（演示账号为 123456），新密码需 ≥ 6 位且两次一致 */
export async function changePassword(
  oldPassword: string,
  newPassword: string,
  confirmPassword: string,
): Promise<PasswordResult> {
  if (oldPassword !== '123456') {
    return delay({ ok: false, message: '原密码不正确' })
  }
  if (newPassword.length < 6) {
    return delay({ ok: false, message: '新密码长度不能少于 6 位' })
  }
  if (newPassword !== confirmPassword) {
    return delay({ ok: false, message: '两次输入的新密码不一致' })
  }
  if (newPassword === oldPassword) {
    return delay({ ok: false, message: '新密码不能与原密码相同' })
  }
  /* Mock 阶段：演示密码不真正失效，仅提示成功 */
  return delay({ ok: true, message: '密码修改成功（演示环境仍可用 123456 登录）' })
}
