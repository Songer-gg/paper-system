import * as requests from '../../../paperhub-api/ts/modules/shared-library'
import type { SharedQuery } from '../../../paperhub-api/ts/types'
import { useMock } from '@/config/runtime'
import { apiClient, nullableDetail } from './api'
export type { SharedQuery, SharedSortKey } from '../../../paperhub-api/ts/types'
export function searchSharedPapers(query: SharedQuery) {
  return useMock
    ? import('@/mocks/services/paperService').then((m) => m.searchSharedPapers(query))
    : requests.searchSharedPapers(apiClient, query)
}
export async function getSharedPaperById(id: number) {
  if (useMock) return (await import('@/mocks/services/paperService')).getSharedPaperById(id)
  return (await nullableDetail(requests.getSharedPaperById(apiClient, id))) ?? undefined
}
export function getFilterOptions() {
  return useMock
    ? import('@/mocks/services/paperService').then((m) => m.getFilterOptions())
    : requests.getFilterOptions(apiClient)
}
