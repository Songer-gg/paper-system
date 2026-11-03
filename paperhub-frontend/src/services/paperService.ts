import type { PageResult, Paper } from '@/types/paper'
import { sharedPapers } from '@/mocks/papers/sharedPapers'
import { mockCategories, getCategoryDescendantIds } from '@/mocks/categories'
import { mockTags } from '@/mocks/tags'

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

export async function searchSharedPapers(
  query: SharedQuery,
): Promise<PageResult<Paper>> {
  const { q, categoryId, tagIds, year, journal, sort = 'uploaded-desc', page, pageSize } = query

  const kw = q?.trim().toLowerCase() ?? ''
  const categoryScope = categoryId ? getCategoryDescendantIds(categoryId) : null

  const list = sharedPapers
    .filter((p) => p.reviewStatus === 'approved')
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
  return sharedPapers.find((p) => p.id === id)
}

/** 筛选选项：分类树 / 标签 / 年份 / 期刊 */
export function getFilterOptions(): {
  categories: typeof mockCategories
  tags: typeof mockTags
  years: number[]
  journals: string[]
} {
  const years = [...new Set(sharedPapers.map((p) => p.year))].sort((a, b) => b - a)
  const journals = [...new Set(sharedPapers.map((p) => p.journal))].sort((a, b) =>
    a.localeCompare(b, 'zh-CN'),
  )
  return { categories: mockCategories, tags: mockTags, years, journals }
}
