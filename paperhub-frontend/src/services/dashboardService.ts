import type { Paper, ReadingStatus } from '@/types/paper'
import { readingStatusLabels } from '@/types/paper'
import { sharedPapers } from '@/mocks/papers/sharedPapers'
import { privatePapers } from '@/mocks/papers/privatePapers'
import { pendingPapers } from '@/mocks/papers/pendingPapers'
import { mockUsers } from '@/mocks/users'
import { getCategory } from '@/mocks/categories'
import { registerTrend, uploadTrend } from '@/mocks/admin/stats'
import * as userData from '@/services/userDataService'

const CURRENT_USER_ID = 1

function delay<T>(value: T, ms = 300): Promise<T> {
  return new Promise((resolve) => setTimeout(() => resolve(value), ms))
}

export interface UserDashboard {
  counts: {
    total: number
    private: number
    shared: number
    favorites: number
    read: number
  }
  yearDist: { year: string; count: number }[]
  categoryDist: { name: string; value: number }[]
  readingDist: { status: ReadingStatus; label: string; count: number }[]
  recentUploads: Paper[]
  recentReadings: { paper: Paper; lastReadAt: string; status: ReadingStatus }[]
  recentFavorites: { paper: Paper; favoritedAt: string }[]
}

export async function getUserDashboard(): Promise<UserDashboard> {
  const myShared = sharedPapers.filter((p) => p.uploaderId === CURRENT_USER_ID)
  const myPapers = [...myShared, ...privatePapers]
  const favorites = await userData.getFavorites()
  const reading = userData.getReadingRecords()

  const readPaper = (paperId: number): Paper | undefined =>
    sharedPapers.find((p) => p.id === paperId)

  /* 论文发表年份分布 */
  const yearMap = new Map<number, number>()
  myPapers.forEach((p) => yearMap.set(p.year, (yearMap.get(p.year) ?? 0) + 1))
  const yearDist = [...yearMap.entries()]
    .sort((a, b) => a[0] - b[0])
    .map(([year, count]) => ({ year: String(year), count }))

  /* 论文分类分布（含子分类归属根分类聚合，Top 6 + 其他） */
  const categoryMap = new Map<string, number>()
  myPapers.forEach((p) => {
    let cat = getCategory(p.categoryId)
    while (cat && cat.parentId !== null) cat = getCategory(cat.parentId)
    const name = cat?.name ?? '未分类'
    categoryMap.set(name, (categoryMap.get(name) ?? 0) + 1)
  })
  const sorted = [...categoryMap.entries()].sort((a, b) => b[1] - a[1])
  const top = sorted.slice(0, 6).map(([name, value]) => ({ name, value }))
  const rest = sorted.slice(6).reduce((sum, [, v]) => sum + v, 0)
  if (rest > 0) top.push({ name: '其他', value: rest })

  /* 阅读状态分布 */
  const statusOrder: ReadingStatus[] = ['read', 'reading', 'unread', 'paused']
  const readingDist = statusOrder.map((status) => ({
    status,
    label: readingStatusLabels[status],
    count: reading.filter((r) => r.status === status).length,
  }))

  const recentUploads = [...myPapers]
    .sort((a, b) => b.uploadedAt.localeCompare(a.uploadedAt))
    .slice(0, 4)

  const recentReadings = reading
    .map((r) => ({ ...r, paper: readPaper(r.paperId)! }))
    .filter((r) => r.paper)
    .sort((a, b) => b.lastReadAt.localeCompare(a.lastReadAt))
    .slice(0, 4)

  const recentFavorites = favorites
    .map((f) => ({ paper: readPaper(f.paperId)!, favoritedAt: f.favoritedAt }))
    .filter((f) => f.paper)
    .sort((a, b) => b.favoritedAt.localeCompare(a.favoritedAt))
    .slice(0, 4)

  return delay({
    counts: {
      total: myPapers.length,
      private: privatePapers.length,
      shared: myShared.length,
      favorites: favorites.length,
      read: reading.filter((r) => r.status === 'read').length,
    },
    yearDist,
    categoryDist: top,
    readingDist,
    recentUploads,
    recentReadings,
    recentFavorites,
  })
}

export interface AdminDashboard {
  totals: {
    users: number
    papers: number
    pending: number
    papersToday: number
    usersToday: number
  }
  uploadTrend: typeof uploadTrend
  registerTrend: typeof registerTrend
  categoryDist: { name: string; value: number }[]
  recentUploads: Paper[]
  pendingTasks: Paper[]
}

const TODAY = '2026-11-03'

export async function getAdminDashboard(): Promise<AdminDashboard> {
  const normalUsers = mockUsers.filter((u) => u.role === 'user')
  const allShared = [...sharedPapers, ...pendingPapers]

  const categoryMap = new Map<string, number>()
  sharedPapers.forEach((p) => {
    let cat = getCategory(p.categoryId)
    while (cat && cat.parentId !== null) cat = getCategory(cat.parentId)
    const name = cat?.name ?? '未分类'
    categoryMap.set(name, (categoryMap.get(name) ?? 0) + 1)
  })
  const categoryDist = [...categoryMap.entries()]
    .sort((a, b) => b[1] - a[1])
    .map(([name, value]) => ({ name, value }))

  return delay({
    totals: {
      users: normalUsers.length,
      papers: sharedPapers.length,
      pending: pendingPapers.length,
      papersToday: allShared.filter((p) => p.uploadedAt.startsWith(TODAY)).length,
      usersToday: normalUsers.filter((u) => u.registeredAt.startsWith(TODAY)).length,
    },
    uploadTrend,
    registerTrend,
    categoryDist,
    recentUploads: [...allShared]
      .sort((a, b) => b.uploadedAt.localeCompare(a.uploadedAt))
      .slice(0, 6),
    pendingTasks: [...pendingPapers].sort((a, b) =>
      b.uploadedAt.localeCompare(a.uploadedAt),
    ),
  })
}
