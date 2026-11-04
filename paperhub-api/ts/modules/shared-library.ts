/**
 * 共享论文库检索（U10–U12）
 * 对应前端：paperService.ts、SharedLibraryView
 */
import type { ApiClient } from '../http'
import type { Category, PageResult, Paper, SharedQuery, Tag } from '../types'

/** U10 GET /shared-papers — 多条件检索（服务端仅返回 shared + approved） */
export function searchSharedPapers(client: ApiClient, query: SharedQuery): Promise<PageResult<Paper>> {
  const params: Record<string, unknown> = {
    page: query.page,
    pageSize: query.pageSize,
  }
  if (query.q) params.q = query.q
  if (query.categoryId != null) params.categoryId = query.categoryId
  if (query.tagIds?.length) params.tagIds = query.tagIds.join(',')
  if (query.year != null) params.year = query.year
  if (query.journal) params.journal = query.journal
  if (query.sort) params.sort = query.sort
  return client.get('/shared-papers', params)
}

/** U11 GET /shared-papers/{id} — 共享论文详情（非 approved 返回 1003） */
export function getSharedPaperById(client: ApiClient, id: number): Promise<Paper | undefined> {
  return client.get(`/shared-papers/${id}`)
}

/** U12 GET /shared-papers/filter-options — 筛选选项（基于当前可见共享池计算） */
export function getFilterOptions(client: ApiClient): Promise<{
  categories: Category[]
  tags: Tag[]
  years: number[]
  journals: string[]
}> {
  return client.get('/shared-papers/filter-options')
}
