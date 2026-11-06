import { currentUserId } from '@/utils/personalStorage'
import type { Paper, ReadingStatus } from '@/types/paper'
import { readingStatusLabels } from '@/types/paper'
import { sharedPapers } from '@/mocks/papers/sharedPapers'
import { privatePapers } from '@/mocks/papers/privatePapers'
import { pendingPapers } from '@/mocks/papers/pendingPapers'
import { mockUsers } from '@/mocks/users'
import { getCategory } from '@/services/knowledgeService'
import { registerTrend, uploadTrend } from '@/mocks/admin/stats'
import * as userData from '@/services/userDataService'
import { loadJSON } from '@/utils/storage'

function delay<T>(value: T, ms = 300): Promise<T> {
  return new Promise((resolve) => setTimeout(() => resolve(value), ms))
}

/** 合并 overlay / 删除 / 用户上传后的全量论文池（静态 + 待审 + 上传） */
function allPapers(): Paper[] {
  const overlay = loadJSON<Record<number, Partial<Paper>>>('ph_paper_overlay', {})
  const deleted = loadJSON<number[]>('ph_deleted_papers', [])
  const uploaded = loadJSON<Paper[]>('ph_uploaded_papers', [])
  return [...sharedPapers, ...pendingPapers, ...privatePapers, ...uploaded]
    .filter((p) => !deleted.includes(p.id))
    .map((p) => ({ ...p, ...(overlay[p.id] ?? {}) }))
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
  const mine = allPapers().filter((p) => p.uploaderId === currentUserId())
  const myShared = mine.filter((p) => p.visibility === 'shared')
  const myPrivate = mine.filter((p) => p.visibility === 'private')
  const myPapers = [...myShared, ...myPrivate]
  const favorites = await userData.getFavorites()
  const reading = await userData.getReadingRecords()

  /* 论文解析：自己的论文，或共享库中审核通过的论文 */
  const readPaper = (paperId: number): Paper | undefined =>
    allPapers().find(
      (p) =>
        p.id === paperId &&
        (p.uploaderId === currentUserId() ||
          (p.visibility === 'shared' && p.reviewStatus === 'approved')),
    )

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
      private: myPrivate.length,
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

export async function getAdminDashboard(): Promise<AdminDashboard> {
  const today = new Date().toLocaleDateString('sv-SE')
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
      papersToday: allShared.filter((p) => p.uploadedAt.startsWith(today)).length,
      usersToday: normalUsers.filter((u) => u.registeredAt.startsWith(today)).length,
    },
    uploadTrend,
    registerTrend,
    categoryDist,
    recentUploads: [...allShared]
      .sort((a, b) => b.uploadedAt.localeCompare(a.uploadedAt))
      .slice(0, 6),
    pendingTasks: [...pendingPapers].sort((a, b) => b.uploadedAt.localeCompare(a.uploadedAt)),
  })
}
