import * as requests from '../../../paperhub-api/ts/modules/library'
import type {
  UploadPayload,
  Paper,
  PaperEditPayload,
  ReviewFlowAction,
  DuplicateCheckInput,
  NoteInput,
} from '../../../paperhub-api/ts/types'
import type { Paper as DisplayPaper } from '@/types/paper'
import { useMock } from '@/config/runtime'
import { apiClient, nullableDetail } from './api'
import { loadPersonalJSON, savePersonalJSON } from '@/utils/personalStorage'
import * as userData from './userDataService'
export type { UploadPayload, PaperEditPayload } from '../../../paperhub-api/ts/types'
export interface DuplicateCheckResult {
  level: 'doi' | 'title' | 'file' | 'none'
  message: string
  paper?: DisplayPaper
}
export function getMyPapers() {
  return useMock
    ? import('@/mocks/services/myLibraryService').then((m) => m.getMyPapers())
    : requests.getMyPapers(apiClient)
}
export async function getFavoritePapers(): Promise<Paper[]> {
  return (await userData.getFavoriteRecords())
    .map((row) => row.paper)
    .filter((paper): paper is Paper => paper !== null)
}
export function getPaperForDetail(id: number) {
  return useMock
    ? import('@/mocks/services/myLibraryService').then((m) => m.getPaperForDetail(id))
    : nullableDetail(requests.getPaperForDetail(apiClient, id))
}
export function updatePaper(id: number, patch: PaperEditPayload) {
  return useMock
    ? import('@/mocks/services/myLibraryService').then((m) => m.updatePaper(id, patch))
    : requests.updatePaper(apiClient, id, patch)
}
export function deleteMyPaper(id: number) {
  return useMock
    ? import('@/mocks/services/myLibraryService').then((m) => m.deleteMyPaper(id))
    : requests.deleteMyPaper(apiClient, id)
}
export async function changeReviewStatus(id: number, action: ReviewFlowAction, agreed?: boolean) {
  if (!useMock) return requests.changeReviewStatus(apiClient, id, action, agreed)
  if (action === 'share') throw new Error('Mock模式不支持私有转共享，请使用真实模式')
  return (await import('@/mocks/services/myLibraryService')).changeReviewStatus(id, action)
}
export function checkDuplicate(input: DuplicateCheckInput) {
  return useMock
    ? import('@/mocks/services/myLibraryService').then((m) => m.checkDuplicate(input))
    : requests.checkDuplicate(apiClient, input)
}
export async function submitPaper(file: File, payload: UploadPayload) {
  if (!useMock) return requests.submitPaper(apiClient, file, payload)
  const hash = await crypto.subtle.digest('SHA-256', await file.arrayBuffer())
  return (await import('@/mocks/services/myLibraryService')).submitPaper(file, {
    ...payload,
    fileName: file.name,
    fileSha256: Array.from(new Uint8Array(hash), (b) => b.toString(16).padStart(2, '0')).join(''),
    fileSizeMB: file.size / 1024 / 1024,
    pageCount: 1,
  })
}
export function addNote(input: NoteInput) {
  return userData.addNote(input)
}
export interface UploadDraft {
  title?: string
  authors?: string
  abstract?: string
  doi?: string
  journal?: string
  year?: number
  keywords?: string
  link?: string
  categoryId?: number
  tagIds?: number[]
  visibility?: 'private' | 'shared'
  savedAt: string
}
export function saveDraft(form: Omit<UploadDraft, 'savedAt'>) {
  savePersonalJSON('ph_upload_draft', { ...form, savedAt: new Date().toLocaleString('zh-CN') })
}
export function loadDraft() {
  return loadPersonalJSON<UploadDraft | null>('ph_upload_draft', null)
}
export function clearDraft() {
  savePersonalJSON('ph_upload_draft', null)
}
