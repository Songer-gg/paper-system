/**
 * 个人中心 / 仪表盘 / 数据统计（P1–P6）
 * 对应前端：profileService.ts、dashboardService.getUserDashboard、statisticsService.ts
 */
import type { ApiClient } from '../http'
import type { MyProfile, UserDashboard, UserStatistics } from '../types'

/* ---------------- 个人中心 ---------------- */

/** P1 GET /me/profile — 我的资料（含 uploadedCount 与 avatar） */
export function getMyProfile(client: ApiClient): Promise<MyProfile> {
  return client.get('/me/profile')
}

/** P2 PATCH /me/profile — 修改姓名 / 邮箱（邮箱重复 1004） */
export function updateProfile(
  client: ApiClient,
  patch: { name?: string; email?: string },
): Promise<MyProfile> {
  return client.patch('/me/profile', patch)
}

/** P3 PUT /me/avatar — 上传头像（multipart：file） */
export function updateAvatar(client: ApiClient, file: File): Promise<MyProfile> {
  const form = new FormData()
  form.append('file', file)
  return client.upload('/me/avatar', form)
}

/** P4 PUT /me/password — 修改密码（旧密码错误返回 1006） */
export function changePassword(
  client: ApiClient,
  oldPassword: string,
  newPassword: string,
  confirmPassword: string,
): Promise<{ success: true }> {
  return client.put('/me/password', { oldPassword, newPassword, confirmPassword })
}

/* ---------------- 仪表盘 / 统计 ---------------- */

/** P5 GET /me/dashboard — 用户首页仪表盘（图表 + 最近动态） */
export function getUserDashboard(client: ApiClient): Promise<UserDashboard> {
  return client.get('/me/dashboard')
}

/** P6 GET /me/statistics — 数据统计页（四张图 + 六个计数） */
export function getUserStatistics(client: ApiClient): Promise<UserStatistics> {
  return client.get('/me/statistics')
}
