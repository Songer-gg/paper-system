/** 论文可见范围：私有（仅本人）/ 共享（审核通过后全员可见） */
export type PaperVisibility = 'private' | 'shared'

/** 共享论文审核状态 */
export type ReviewStatus = 'pending' | 'approved' | 'rejected' | 'offline'

/** 个人阅读状态 */
export type ReadingStatus = 'unread' | 'reading' | 'read' | 'paused'

export const readingStatusLabels: Record<ReadingStatus, string> = {
  unread: '未阅读',
  reading: '阅读中',
  read: '已阅读',
  paused: '暂不阅读',
}

export const reviewStatusLabels: Record<ReviewStatus, string> = {
  pending: '待审核',
  approved: '审核通过',
  rejected: '审核未通过',
  offline: '已下架',
}

export interface Paper {
  id: number
  title: string
  authors: string[]
  abstract: string
  doi: string
  journal: string
  year: number
  keywords: string[]
  categoryId: number | null
  tagIds: number[]
  visibility: PaperVisibility
  uploaderId: number
  uploaderName?: string
  uploadedAt: string
  downloads: number
  fileSizeMB: number
  pageCount: number
  link?: string
  /** 共享论文专用 */
  reviewStatus?: ReviewStatus
  reviewComment?: string
  offlineReason?: 'withdrawn' | 'moderation'
  copyrightAgreed?: boolean
  fileSha256?: string
}

export interface Category {
  id: number
  name: string
  parentId: number | null
  custom?: boolean
  ownerId?: number | null
  paperCount?: number
}

export interface Tag {
  id: number
  name: string
  color: string
  custom?: boolean
  ownerId?: number | null
  paperCount?: number
}

export interface ReadingRecord {
  paperId: number
  status: ReadingStatus
  lastReadAt: string
  addedAt?: string
}

export interface FavoriteRecord {
  paperId: number
  favoritedAt: string
}

export interface Note {
  id: number
  title: string
  paperId: number
  paperTitle?: string
  paperAvailable?: boolean
  content: string
  createdAt: string
  updatedAt: string
}

export interface PageResult<T> {
  list: T[]
  total: number
}
