import * as requests from '../../../paperhub-api/ts/modules/reading'
import type { NoteInput, ReadingStatus, FavoriteRecord } from '../../../paperhub-api/ts/types'
import { useMock } from '@/config/runtime'
import { apiClient } from './api'
import { onSessionReset, sessionGeneration } from '@/utils/session'
const favoriteIds = new Set<number>()
onSessionReset(() => favoriteIds.clear())
export function getFavoritePaperIds(): number[] {
  return [...favoriteIds]
}
export async function getFavorites() {
  const generation = sessionGeneration()
  const result = useMock
    ? await (await import('@/mocks/services/userDataService')).getFavorites()
    : await requests.getFavorites(apiClient)
  if (generation === sessionGeneration()) {
    favoriteIds.clear()
    for (const row of result) favoriteIds.add(row.paperId)
  }
  return result
}
export async function getFavoriteRecords(): Promise<FavoriteRecord[]> {
  if (!useMock) return requests.getFavorites(apiClient)
  const records = await getFavorites()
  const { getPaperForDetail } = await import('./myLibraryService')
  return Promise.all(
    records.map(async (row) => {
      const paper = await getPaperForDetail(row.paperId)
      return { ...row, paper: paper ? { ...paper, uploaderName: paper.uploaderName ?? '' } : null }
    }),
  )
}
export async function toggleFavorite(paperId: number, target?: boolean) {
  const generation = sessionGeneration()
  const result = useMock
    ? await (await import('@/mocks/services/userDataService')).toggleFavorite(paperId, target)
    : await requests.toggleFavorite(apiClient, paperId, target)
  if (generation === sessionGeneration()) {
    if (result.favorited) favoriteIds.add(paperId)
    else favoriteIds.delete(paperId)
  }
  return result
}
export function getReadingRecords() {
  return useMock
    ? import('@/mocks/services/userDataService').then((m) => m.getReadingRecords())
    : requests.getReadingRecords(apiClient)
}
export function addToReading(id: number) {
  return useMock
    ? import('@/mocks/services/userDataService').then((m) => m.addToReading(id))
    : requests.addToReading(apiClient, id)
}
export function updateReadingStatus(id: number, status: ReadingStatus) {
  return useMock
    ? import('@/mocks/services/userDataService').then((m) => m.updateReadingStatus(id, status))
    : requests.updateReadingStatus(apiClient, id, status)
}
export function getNotes() {
  return useMock
    ? import('@/mocks/services/userDataService').then((m) => m.getNotes())
    : requests.getNotes(apiClient)
}
export function addNote(input: NoteInput) {
  return useMock
    ? import('@/mocks/services/userDataService').then((m) => m.addNote(input))
    : requests.addNote(apiClient, input)
}
export function updateNote(
  id: number,
  patch: { title?: string; paperId?: number; content?: string },
) {
  return useMock
    ? import('@/mocks/services/userDataService').then((m) => m.updateNote(id, patch))
    : requests.updateNote(apiClient, id, patch)
}
export function deleteNote(id: number) {
  return useMock
    ? import('@/mocks/services/userDataService').then((m) => m.deleteNote(id))
    : requests.deleteNote(apiClient, id)
}
