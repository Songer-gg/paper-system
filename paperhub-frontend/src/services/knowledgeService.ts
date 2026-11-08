import { shallowRef } from 'vue'
import * as requests from '../../../paperhub-api/ts/modules/knowledge'
import type { Category, Tag, Paper } from '@/types/paper'
import { useMock } from '@/config/runtime'
import { apiClient } from './api'
import { onSessionReset, sessionGeneration } from '@/utils/session'
const categories = shallowRef<Category[]>([])
const tags = shallowRef<Tag[]>([])
onSessionReset(() => {
  categories.value = []
  tags.value = []
})
export function getCategory(id: number | null) {
  return categories.value.find((row) => row.id === id)
}
export function getTag(id: number) {
  return tags.value.find((row) => row.id === id)
}
export function getCategoryDescendantIds(id: number): number[] {
  const result = new Set([id])
  let changed = true
  while (changed) {
    changed = false
    for (const row of categories.value)
      if (row.parentId !== null && result.has(row.parentId) && !result.has(row.id)) {
        result.add(row.id)
        changed = true
      }
  }
  return [...result]
}
export function countPapersByCategory(): Record<number, number> {
  return Object.fromEntries(categories.value.map((row) => [row.id, row.paperCount ?? 0]))
}
export function countPapersByTag(): Record<number, number> {
  return Object.fromEntries(tags.value.map((row) => [row.id, row.paperCount ?? 0]))
}
export async function getCategories(): Promise<Category[]> {
  const generation = sessionGeneration()
  const result = useMock
    ? await (await import('@/mocks/services/knowledgeService')).getCategories()
    : await requests.getCategories(apiClient)
  if (generation === sessionGeneration()) categories.value = result
  return result
}
export async function getTags(): Promise<Tag[]> {
  const generation = sessionGeneration()
  const result = useMock
    ? await (await import('@/mocks/services/knowledgeService')).getTags()
    : await requests.getTags(apiClient)
  if (generation === sessionGeneration()) tags.value = result
  return result
}
export async function warmKnowledge(): Promise<void> {
  await Promise.all([getCategories(), getTags()])
}
export async function createCategory(name: string, parentId: number | null) {
  const result = useMock
    ? await (await import('@/mocks/services/knowledgeService')).createCategory(name, parentId)
    : await requests.createCategory(apiClient, name, parentId)
  await getCategories()
  return result
}
export async function renameCategory(id: number, name: string) {
  const result = useMock
    ? await (await import('@/mocks/services/knowledgeService')).renameCategory(id, name)
    : await requests.renameCategory(apiClient, id, name)
  await getCategories()
  return result
}
export async function deleteCategory(id: number) {
  const result = useMock
    ? await (await import('@/mocks/services/knowledgeService')).deleteCategory(id)
    : await requests.deleteCategory(apiClient, id)
  await getCategories()
  return result
}
export async function moveCategory(id: number, parentId: number | null) {
  const result = useMock
    ? await (await import('@/mocks/services/knowledgeService')).moveCategory(id, parentId)
    : await requests.moveCategory(apiClient, id, parentId)
  await getCategories()
  return result
}
export function getPapersOfCategory(id: number) {
  return useMock
    ? import('@/mocks/services/knowledgeService').then((m) => m.getPapersOfCategory(id))
    : requests.getPapersOfCategory(apiClient, id)
}
export async function createTag(name: string, color: string) {
  const result = useMock
    ? await (await import('@/mocks/services/knowledgeService')).createTag(name, color)
    : await requests.createTag(apiClient, name, color)
  await getTags()
  return result
}
export async function updateTag(id: number, name: string, color: string) {
  const result = useMock
    ? await (await import('@/mocks/services/knowledgeService')).updateTag(id, name, color)
    : await requests.updateTag(apiClient, id, name, color)
  await getTags()
  return result
}
export async function deleteTag(id: number) {
  const result = useMock
    ? await (await import('@/mocks/services/knowledgeService')).deleteTag(id)
    : await requests.deleteTag(apiClient, id)
  await getTags()
  return result
}
export function getPapersOfTag(id: number) {
  return useMock
    ? import('@/mocks/services/knowledgeService').then((m) => m.getPapersOfTag(id))
    : requests.getPapersOfTag(apiClient, id)
}
export async function getVisiblePapers(): Promise<Paper[]> {
  if (useMock) return (await import('@/mocks/services/knowledgeService')).getVisiblePapers()
  const { getMyPapers } = await import('./myLibraryService')
  const { searchSharedPapers } = await import('./paperService')
  const mine = await getMyPapers()
  const papers = new Map(
    [...mine.privatePapers, ...mine.sharedPapers].map((paper) => [paper.id, paper]),
  )
  for (let page = 1; ; page++) {
    const result = await searchSharedPapers({ page, pageSize: 100 })
    for (const paper of result.list) papers.set(paper.id, paper)
    if (!result.list.length || page * 100 >= result.total) break
  }
  return [...papers.values()]
}
