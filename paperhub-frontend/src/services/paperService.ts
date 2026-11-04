import type { PageResult, Paper } from '@/types/paper'
import { sharedPapers } from '@/mocks/papers/sharedPapers'
import { pendingPapers } from '@/mocks/papers/pendingPapers'
import { mockCategories, getCategoryDescendantIds } from '@/mocks/categories'
import { mockTags } from '@/mocks/tags'
import { loadJSON } from '@/utils/storage'

/**
 * 论文服务：共享论文库检索（搜索/筛选/排序/分页）。
 * 模糊搜索覆盖标题、作者、DOI、摘要、关键词与期刊；后续接入后端时替换为真实 API。
 */

export type SharedSortKey = 'uploaded-desc' | 'downloads-desc' | 'year-desc'

export interface SharedQuery {
  q?: string
  categoryId?: number | null
  tagIds?: number[]
  year?: number | null
  journal?: string | null
  sort?: SharedSortKey
  page: number
  pageSize: number
}

function delay<T>(value: T, ms = 250): Promise<T> {
  return new Promise((resolve) => setTimeout(() => resolve(value), ms))
}

/**
 * 用户端可见的共享论文池：
 * 静态共享池 + 待审核池 + 用户上传池，过滤已删除，应用管理员端 overlay，
 * 仅保留 visibility=shared 且审核通过的论文（与 adminService 共用 localStorage 键，审核通过后立即可见）。
 */
function visibleSharedPool(): Paper[] {
  const overlay = loadJSON<Record<number, Partial<Paper>>>('ph_paper_overlay', {})
  const deleted = loadJSON<number[]>('ph_deleted_papers', [])
  const uploaded = loadJSON<Paper[]>('ph_uploaded_papers', [])
  return [...sharedPapers, ...pendingPapers, ...uploaded]
    .filter((p) => !deleted.includes(p.id))
    .map((p) => ({ ...p, ...(overlay[p.id] ?? {}) }))
    .filter((p) => p.visibility === 'shared' && p.reviewStatus === 'approved')
}

export async function searchSharedPapers(query: SharedQuery): Promise<PageResult<Paper>> {
  const { q, categoryId, tagIds, year, journal, sort = 'uploaded-desc', page, pageSize } = query

  const kw = q?.trim().toLowerCase() ?? ''
  const categoryScope = categoryId ? getCategoryDescendantIds(categoryId) : null

  const list = visibleSharedPool()
    .filter((p) => {
      if (kw) {
        const haystack = [
          p.title,
          p.authors.join(' '),
          p.doi,
          p.abstract,
          p.keywords.join(' '),
          p.journal,
        ]
          .join('\n')
          .toLowerCase()
        if (!haystack.includes(kw)) return false
      }
      if (categoryScope && !categoryScope.includes(p.categoryId)) return false
      if (year && p.year !== year) return false
      if (journal && p.journal !== journal) return false
      if (tagIds?.length && !tagIds.some((t) => p.tagIds.includes(t))) return false
      return true
    })
    .sort((a, b) => {
      switch (sort) {
        case 'downloads-desc':
          return b.downloads - a.downloads
        case 'year-desc':
          return b.year - a.year || b.uploadedAt.localeCompare(a.uploadedAt)
        default:
          return b.uploadedAt.localeCompare(a.uploadedAt)
      }
    })

  const total = list.length
  const start = (page - 1) * pageSize
  return delay({ list: list.slice(start, start + pageSize), total })
}

export function getSharedPaperById(id: number): Paper | undefined {
  return visibleSharedPool().find((p) => p.id === id)
}

/** 筛选选项：分类树 / 标签 / 年份 / 期刊（基于当前可见共享池） */
export function getFilterOptions(): {
  categories: typeof mockCategories
  tags: typeof mockTags
  years: number[]
  journals: string[]
} {
  const pool = visibleSharedPool()
  const years = [...new Set(pool.map((p) => p.year))].sort((a, b) => b - a)
  const journals = [...new Set(pool.map((p) => p.journal))].sort((a, b) =>
    a.localeCompare(b, 'zh-CN'),
  )
  return { categories: mockCategories, tags: mockTags, years, journals }
}
