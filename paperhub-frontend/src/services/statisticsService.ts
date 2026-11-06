import * as myLibrary from '@/services/myLibraryService'
import * as userData from '@/services/userDataService'
import { mockCategories } from '@/mocks/categories'
import type { Paper } from '@/types/paper'

/**
 * 个人数据统计服务：聚合论文库、收藏与阅读数据，
 * 供数据统计页展示（规划书 5.12）。
 */

export interface MonthlyTrendPoint {
  month: string // '2026-06'
  label: string // '6月'
  count: number
}

export interface NameCount {
  name: string
  count: number
}

export async function getUserStatistics(): Promise<{
  totalPapers: number
  privateCount: number
  sharedCount: number
  favoriteCount: number
  readCount: number
  noteCount: number
  monthlyReadingTrend: MonthlyTrendPoint[]
  yearDistribution: NameCount[]
  categoryDistribution: NameCount[]
  journalDistribution: NameCount[]
}> {
  const mine = await myLibrary.getMyPapers()
  const myPapers = [...mine.privatePapers, ...mine.sharedPapers]
  const favorites = await userData.getFavorites()
  const reading = await userData.getReadingRecords()
  const notes = await userData.getNotes()

  /* 最近 12 个月的阅读趋势（按最近阅读时间归月） */
  const months: MonthlyTrendPoint[] = []
  const now = new Date()
  for (let i = 11; i >= 0; i--) {
    const d = new Date(now.getFullYear(), now.getMonth() - i, 1)
    const mm = `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}`
    months.push({ month: mm, label: `${d.getMonth() + 1}月`, count: 0 })
  }
  for (const r of reading) {
    const key = r.lastReadAt.slice(0, 7)
    const hit = months.find((m) => m.month === key)
    if (hit) hit.count++
  }

  const rootName = (p: Paper): string => {
    let cat = mockCategories.find((c) => c.id === p.categoryId)
    while (cat && cat.parentId !== null) {
      cat = mockCategories.find((c) => c.id === cat!.parentId)
    }
    return cat?.name ?? '未分类'
  }

  const countBy = (list: Paper[], keyOf: (p: Paper) => string): NameCount[] => {
    const map = new Map<string, number>()
    for (const p of list) {
      const k = keyOf(p) || '未投稿'
      map.set(k, (map.get(k) ?? 0) + 1)
    }
    return [...map.entries()]
      .map(([name, count]) => ({ name, count }))
      .sort((a, b) => b.count - a.count)
  }

  return {
    totalPapers: myPapers.length,
    privateCount: mine.privatePapers.length,
    sharedCount: mine.sharedPapers.length,
    favoriteCount: favorites.length,
    readCount: reading.filter((r) => r.status === 'read').length,
    noteCount: notes.length,
    monthlyReadingTrend: months,
    yearDistribution: countBy(myPapers, (p) => String(p.year)).sort(
      (a, b) => Number(a.name) - Number(b.name),
    ),
    categoryDistribution: countBy(myPapers, rootName),
    journalDistribution: countBy(myPapers, (p) => p.journal).slice(0, 8),
  }
}
