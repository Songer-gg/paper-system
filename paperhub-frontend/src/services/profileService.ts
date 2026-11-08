import * as requests from '../../../paperhub-api/ts/modules/profile'
import { useMock } from '@/config/runtime'
import { apiClient } from './api'
export type { MyProfile, PasswordResult } from '../../../paperhub-api/ts/types'
export function getMyProfile() {
  return useMock
    ? import('@/mocks/services/profileService').then((m) => m.getMyProfile())
    : requests.getMyProfile(apiClient)
}
export function updateProfile(patch: { name?: string; email?: string }) {
  return useMock
    ? import('@/mocks/services/profileService').then((m) => m.updateProfile(patch))
    : requests.updateProfile(apiClient, patch)
}
export function updateAvatar(file: File) {
  return useMock
    ? import('@/mocks/services/profileService').then((m) => m.updateAvatar(file))
    : requests.updateAvatar(apiClient, file)
}
export function changePassword(oldPassword: string, newPassword: string, confirmPassword: string) {
  return useMock
    ? import('@/mocks/services/profileService').then((m) =>
        m.changePassword(oldPassword, newPassword, confirmPassword),
      )
    : requests.changePassword(apiClient, oldPassword, newPassword, confirmPassword)
}
