import * as requests from '../../../paperhub-api/ts/modules/admin'
import type { AdminUserQuery, AdminPaperQuery, UserStatus } from '../../../paperhub-api/ts/types'
import { useMock } from '@/config/runtime'
import { apiClient, nullableDetail } from './api'
export type { AdminUser } from '../../../paperhub-api/ts/types'
export type UserQuery = AdminUserQuery
export type PaperQuery = AdminPaperQuery
export function getUsers(query: UserQuery) {
  return useMock
    ? import('@/mocks/services/adminService').then((m) => m.getUsers(query))
    : requests.getUsers(apiClient, query)
}
export function getUserDetail(id: number) {
  return useMock
    ? import('@/mocks/services/adminService').then((m) => m.getUserDetail(id))
    : nullableDetail(requests.getUserDetail(apiClient, id))
}
export function getUserPapers(id: number) {
  return useMock
    ? import('@/mocks/services/adminService').then((m) => m.getUserPapers(id))
    : requests.getUserPapers(apiClient, id)
}
export function getUserFavorites(id: number) {
  return useMock
    ? import('@/mocks/services/adminService').then((m) => m.getUserFavorites(id))
    : requests.getUserFavorites(apiClient, id)
}
export function getUserReadingRecords(id: number) {
  return useMock
    ? import('@/mocks/services/adminService').then((m) => m.getUserReadingRecords(id))
    : requests.getUserReadingRecords(apiClient, id)
}
export function getUserNotes(id: number) {
  return useMock
    ? import('@/mocks/services/adminService').then((m) => m.getUserNotes(id))
    : requests.getUserNotes(apiClient, id)
}
export function setUserStatus(id: number, status: UserStatus) {
  return useMock
    ? import('@/mocks/services/adminService').then((m) => m.setUserStatus(id, status))
    : requests.setUserStatus(apiClient, id, status)
}
export function resetUserPassword(id: number) {
  return useMock
    ? import('@/mocks/services/adminService').then((m) => m.resetUserPassword(id))
    : requests.resetUserPassword(apiClient, id)
}
export function getSharedPapers(query: PaperQuery) {
  return useMock
    ? import('@/mocks/services/adminService').then((m) =>
        m.getSharedPapers({
          title: '',
          doi: '',
          uploader: '',
          categoryId: null,
          status: 'all',
          ...query,
        }),
      )
    : requests.getSharedPapers(apiClient, query)
}
export function getPaperById(id: number) {
  return useMock
    ? import('@/mocks/services/adminService').then((m) => m.getPaperById(id))
    : nullableDetail(requests.getPaperById(apiClient, id))
}
export function offlinePaper(id: number) {
  return useMock
    ? import('@/mocks/services/adminService').then((m) => m.offlinePaper(id))
    : requests.offlinePaper(apiClient, id)
}
export function restorePaper(id: number) {
  return useMock
    ? import('@/mocks/services/adminService').then((m) => m.restorePaper(id))
    : requests.restorePaper(apiClient, id)
}
export function deletePaper(id: number) {
  return useMock
    ? import('@/mocks/services/adminService').then((m) => m.deletePaper(id))
    : requests.deletePaper(apiClient, id)
}
export function getReviewPapers() {
  return useMock
    ? import('@/mocks/services/adminService').then((m) => m.getReviewPapers())
    : requests.getReviewPapers(apiClient)
}
export function approvePaper(id: number, comment: string, copyrightConfirmed: boolean) {
  return useMock
    ? import('@/mocks/services/adminService').then((m) =>
        m.approvePaper(id, comment, copyrightConfirmed),
      )
    : requests.approvePaper(apiClient, id, comment, copyrightConfirmed)
}
export function rejectPaper(id: number, comment: string) {
  return useMock
    ? import('@/mocks/services/adminService').then((m) => m.rejectPaper(id, comment))
    : requests.rejectPaper(apiClient, id, comment)
}
export function getReviewRecords(action: 'all' | 'approved' | 'rejected') {
  return useMock
    ? import('@/mocks/services/adminService').then((m) => m.getReviewRecords(action))
    : requests.getReviewRecords(apiClient, action)
}
export function getDuplicatePairs(status: 'all' | 'pending' | 'resolved') {
  return useMock
    ? import('@/mocks/services/adminService').then((m) => m.getDuplicatePairs(status))
    : requests.getDuplicatePairs(apiClient, status)
}
export function confirmDuplicate(id: number, note: string) {
  return useMock
    ? import('@/mocks/services/adminService').then((m) => m.confirmDuplicate(id, note))
    : requests.confirmDuplicate(apiClient, id, note)
}
export function confirmDistinct(id: number, note: string) {
  return useMock
    ? import('@/mocks/services/adminService').then((m) => m.confirmDistinct(id, note))
    : requests.confirmDistinct(apiClient, id, note)
}
export function linkToOriginal(id: number, note: string) {
  return useMock
    ? import('@/mocks/services/adminService').then((m) => m.linkToOriginal(id, note))
    : requests.linkToOriginal(apiClient, id, note)
}
export function getDuplicateRecords() {
  return useMock
    ? import('@/mocks/services/adminService').then((m) => m.getDuplicateRecords())
    : requests.getDuplicateRecords(apiClient)
}
export function getReports(status: 'all' | 'pending' | 'resolved') {
  return useMock
    ? import('@/mocks/services/adminService').then((m) => m.getReports(status))
    : requests.getReports(apiClient, status)
}
export function resolveReport(id: number, result: string, offlinePaperToo: boolean) {
  return useMock
    ? import('@/mocks/services/adminService').then((m) =>
        m.resolveReport(id, result, offlinePaperToo),
      )
    : requests.resolveReport(apiClient, id, result, offlinePaperToo)
}
export function getLogs(action: string) {
  return useMock
    ? import('@/mocks/services/adminService').then((m) => m.getLogs(action))
    : requests.getLogs(apiClient, action)
}
export function getReportRecords() {
  return useMock ? getReports('resolved') : requests.getReportRecords(apiClient)
}
export function getSettings() {
  return useMock
    ? Promise.resolve({
        platformName: 'PaperHub 论文管理平台',
        slogan: '面向科研人员的学术论文管理与共享平台',
        logoText: 'PH',
        version: 'V1.0（Mock）',
        maxUploadMB: 100,
        allowedTypes: '.pdf',
        contactEmail: 'support@paperhub.example.cn',
      })
    : requests.getSettings(apiClient)
}
