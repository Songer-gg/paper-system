import type { Paper, ReviewStatus } from '@/types/paper'
import type {
  DuplicatePair,
  DuplicateRecord,
  OperationLog,
  ReportItem,
  ReviewRecord,
} from '@/types/admin'
import type { PlatformUser, UserStatus } from '@/types/user'
import { loadJSON, saveJSON } from '@/utils/storage'
import { mockUsers } from '@/mocks/users'
import { sharedPapers } from '@/mocks/papers/sharedPapers'
import { pendingPapers } from '@/mocks/papers/pendingPapers'
import { mockReviewRecords } from '@/mocks/admin/reviewRecords'
import { mockDuplicatePairs } from '@/mocks/admin/duplicates'
import { mockReports } from '@/mocks/admin/reports'
import { mockLogs } from '@/mocks/admin/logs'
import { getCategoryDescendantIds } from '@/mocks/categories'

/**
 * 管理员端服务：用户管理 / 共享论文管理 / 审核 / 重复 / 举报 / 日志。
 * 与用户端共用 ph_paper_overlay、ph_deleted_papers，使审核操作直接联动用户端展示。
 * 演示阶段所有写入通过 localStorage 持久化。
 */

const ADMIN_ID = 101
const USER_OVERLAY_KEY = 'ph_admin_user_overlay'
const PASSWORD_RESET_KEY = 'ph_admin_password_resets'
const REVIEW_RECORD_KEY = 'ph_admin_review_records'
const DUPLICATE_OVERLAY_KEY = 'ph_admin_duplicate_overlay'
const DUPLICATE_RECORD_KEY = 'ph_admin_duplicate_records'
const REPORT_OVERLAY_KEY = 'ph_admin_report_overlay'
const LOG_KEY = 'ph_admin_logs'

interface UserOverlay {
  status?: UserStatus
}

const userOverlay = loadJSON<Record<number, UserOverlay>>(USER_OVERLAY_KEY, {})
const passwordResets = loadJSON<number[]>(PASSWORD_RESET_KEY, [])
const dynamicReviewRecords = loadJSON<ReviewRecord[]>(REVIEW_RECORD_KEY, [])
const duplicateOverlay = loadJSON<
  Record<number, { status: DuplicatePair['status']; handledAt: string; handlerNote?: string }>
>(DUPLICATE_OVERLAY_KEY, {})
const dynamicDuplicateRecords = loadJSON<DuplicateRecord[]>(DUPLICATE_RECORD_KEY, [])
const reportOverlay = loadJSON<
  Record<number, { status: 'resolved'; result: string; handledAt: string }>
>(REPORT_OVERLAY_KEY, {})
const dynamicLogs = loadJSON<OperationLog[]>(LOG_KEY, [])

function delay<T>(value: T, ms = 120): Promise<T> {
  return new Promise((resolve) => setTimeout(() => resolve(value), ms))
}

function now(): string {
  const d = new Date()
  const p = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())} ${p(d.getHours())}:${p(d.getMinutes())}`
}

function appendLog(action: string, target: string) {
  dynamicLogs.unshift({
    id: 6000 + (Date.now() % 100000),
    userName: '平台管理员',
    action,
    target,
    result: '成功',
    time: now(),
  })
  saveJSON(LOG_KEY, dynamicLogs)
}

/* ---------------- 论文池（与用户端共用 overlay） ---------------- */

function paperOverlay(): Record<number, Partial<Paper>> {
  return loadJSON<Record<number, Partial<Paper>>>('ph_paper_overlay', {})
}

/** 全部共享资源（含待审核/已驳回/已下架），供管理员管理页与审核页使用 */
function allSharedPapers(): Paper[] {
  const overlay = paperOverlay()
  const deleted = loadJSON<number[]>('ph_deleted_papers', [])
  const uploaded = loadJSON<Paper[]>('ph_uploaded_papers', [])
  const pool = [...sharedPapers, ...pendingPapers, ...uploaded]
  return pool
    .filter((p) => !deleted.includes(p.id))
    .map((p) => ({ ...p, ...(overlay[p.id] ?? {}) }))
}

function findPaper(id: number): Paper | undefined {
  return allSharedPapers().find((p) => p.id === id)
}

/** 修改论文（写共享 overlay，用户端立即联动） */
function patchPaper(id: number, patch: Partial<Paper>) {
  const overlay = paperOverlay()
  overlay[id] = { ...(overlay[id] ?? {}), ...patch }
  saveJSON('ph_paper_overlay', overlay)
}

/* ---------------- 用户管理 ---------------- */

export interface AdminUser extends PlatformUser {
  /** 已上传共享论文数（含各审核状态） */
  sharedCount: number
  /** 通过审核的贡献数量 */
  contributionCount: number
}

export interface UserQuery {
  keyword: string
  status: UserStatus | 'all'
  /** [起, 止]，YYYY-MM-DD */
  dateRange: [string, string] | null
}

export async function getUsers(query: UserQuery): Promise<AdminUser[]> {
  const papers = allSharedPapers()
  let list: AdminUser[] = mockUsers.map((u) => {
    const own = papers.filter((p) => p.uploaderId === u.id)
    return {
      ...u,
      status: userOverlay[u.id]?.status ?? u.status,
      sharedCount: own.length,
      contributionCount: own.filter((p) => p.reviewStatus === 'approved').length,
    }
  })
  const kw = query.keyword.trim().toLowerCase()
  if (kw) {
    list = list.filter(
      (u) => u.name.toLowerCase().includes(kw) || u.username.toLowerCase().includes(kw),
    )
  }
  if (query.status !== 'all') {
    list = list.filter((u) => u.status === query.status)
  }
  if (query.dateRange) {
    const [from, to] = query.dateRange
    list = list.filter((u) => {
      const day = u.registeredAt.slice(0, 10)
      return day >= from && day <= to
    })
  }
  return delay(list)
}

export async function getUserDetail(id: number): Promise<AdminUser | null> {
  const papers = allSharedPapers()
  const u = mockUsers.find((x) => x.id === id)
  if (!u) return delay(null, 0)
  const own = papers.filter((p) => p.uploaderId === id)
  return delay({
    ...u,
    status: userOverlay[id]?.status ?? u.status,
    sharedCount: own.length,
    contributionCount: own.filter((p) => p.reviewStatus === 'approved').length,
  })
}

export async function getUserPapers(id: number): Promise<Paper[]> {
  const papers = allSharedPapers().filter((p) => p.uploaderId === id)
  return delay(papers, 0)
}

export async function setUserStatus(id: number, status: UserStatus): Promise<boolean> {
  userOverlay[id] = { ...(userOverlay[id] ?? {}), status }
  saveJSON(USER_OVERLAY_KEY, userOverlay)
  const name = mockUsers.find((u) => u.id === id)?.name ?? `用户 ${id}`
  appendLog(status === 'disabled' ? '用户禁用' : '用户恢复', name)
  return delay(true, 0)
}

export async function resetUserPassword(id: number): Promise<string> {
  passwordResets.push(id)
  saveJSON(PASSWORD_RESET_KEY, passwordResets)
  const name = mockUsers.find((u) => u.id === id)?.name ?? `用户 ${id}`
  appendLog('重置密码', name)
  return delay('paper@2026', 0)
}

export function isPasswordReset(id: number): boolean {
  return passwordResets.includes(id)
}

/* ---------------- 共享论文管理 ---------------- */

export interface PaperQuery {
  title: string
  doi: string
  uploader: string
  categoryId: number | null
  status: ReviewStatus | 'all'
}

export async function getSharedPapers(query: PaperQuery): Promise<Paper[]> {
  let list = allSharedPapers()
  const kw = query.title.trim().toLowerCase()
  if (kw) list = list.filter((p) => p.title.toLowerCase().includes(kw))
  const doi = query.doi.trim().toLowerCase()
  if (doi) list = list.filter((p) => (p.doi ?? '').toLowerCase().includes(doi))
  const uploader = query.uploader.trim().toLowerCase()
  if (uploader) {
    list = list.filter((p) =>
      (mockUsers.find((u) => u.id === p.uploaderId)?.name ?? '').toLowerCase().includes(uploader),
    )
  }
  if (query.categoryId) {
    const descendants = getCategoryDescendantIds(query.categoryId)
    list = list.filter((p) => descendants.includes(p.categoryId))
  }
  if (query.status !== 'all') {
    list = list.filter((p) => p.reviewStatus === query.status)
  }
  return delay(list)
}

export async function getPaperById(id: number): Promise<Paper | null> {
  return delay(findPaper(id) ?? null, 0)
}

export async function offlinePaper(id: number): Promise<boolean> {
  const p = findPaper(id)
  if (!p) return delay(false, 0)
  patchPaper(id, { reviewStatus: 'offline' })
  appendLog('论文下架', `《${p.title}》`)
  return delay(true, 0)
}

export async function restorePaper(id: number): Promise<boolean> {
  const p = findPaper(id)
  if (!p) return delay(false, 0)
  patchPaper(id, { reviewStatus: 'approved' })
  appendLog('论文恢复', `《${p.title}》`)
  return delay(true, 0)
}

export async function deletePaper(id: number): Promise<boolean> {
  const p = findPaper(id)
  const deleted = loadJSON<number[]>('ph_deleted_papers', [])
  if (!p || deleted.includes(id)) return delay(false, 0)
  deleted.push(id)
  saveJSON('ph_deleted_papers', deleted)
  appendLog('删除违规资源', `《${p.title}》`)
  return delay(true, 0)
}

/* ---------------- 论文审核 ---------------- */

export async function getReviewPapers(): Promise<{
  pending: Paper[]
  approved: Paper[]
  rejected: Paper[]
}> {
  const papers = allSharedPapers().filter((p) => p.visibility === 'shared')
  return delay(
    {
      pending: papers.filter((p) => p.reviewStatus === 'pending'),
      approved: papers.filter((p) => p.reviewStatus === 'approved'),
      rejected: papers.filter((p) => p.reviewStatus === 'rejected'),
    },
    0,
  )
}

async function review(paperId: number, action: 'approved' | 'rejected', comment: string) {
  const p = findPaper(paperId)
  if (!p) return false
  patchPaper(paperId, {
    reviewStatus: action,
    reviewComment: action === 'rejected' ? comment : undefined,
  })
  const record: ReviewRecord = {
    id: 8000 + (Date.now() % 100000),
    paperId,
    paperTitle: p.title,
    uploaderId: p.uploaderId,
    action,
    comment,
    reviewerId: ADMIN_ID,
    reviewedAt: now(),
  }
  dynamicReviewRecords.unshift(record)
  saveJSON(REVIEW_RECORD_KEY, dynamicReviewRecords)
  appendLog('论文审核', `《${p.title}》`)
  return true
}

export async function approvePaper(paperId: number, comment: string): Promise<boolean> {
  return review(paperId, 'approved', comment)
}

export async function rejectPaper(paperId: number, comment: string): Promise<boolean> {
  return review(paperId, 'rejected', comment)
}

export async function getReviewRecords(
  action: 'all' | 'approved' | 'rejected',
): Promise<ReviewRecord[]> {
  const list = [...dynamicReviewRecords, ...mockReviewRecords].sort((a, b) =>
    b.reviewedAt.localeCompare(a.reviewedAt),
  )
  return delay(action === 'all' ? list : list.filter((r) => r.action === action), 0)
}

/* ---------------- 重复论文管理 ---------------- */

export async function getDuplicatePairs(
  status: 'all' | 'pending' | 'resolved',
): Promise<DuplicatePair[]> {
  const list = mockDuplicatePairs.map((p) => {
    const ov = duplicateOverlay[p.id]
    return ov
      ? { ...p, status: ov.status, handledAt: ov.handledAt, handlerNote: ov.handlerNote }
      : p
  })
  const filtered =
    status === 'all'
      ? list
      : status === 'pending'
        ? list.filter((p) => p.status === 'pending')
        : list.filter((p) => p.status !== 'pending')
  return delay(filtered, 0)
}

async function handleDuplicate(
  pairId: number,
  action: 'duplicate' | 'distinct' | 'linked',
  note: string,
) {
  const pair = mockDuplicatePairs.find((p) => p.id === pairId)
  if (!pair) return false
  duplicateOverlay[pairId] = { status: action, handledAt: now(), handlerNote: note }
  saveJSON(DUPLICATE_OVERLAY_KEY, duplicateOverlay)

  const newPaper = findPaper(pair.newPaperId)
  if (action === 'duplicate') {
    /* 确认重复：驳回新论文 */
    if (newPaper)
      patchPaper(pair.newPaperId, {
        reviewStatus: 'rejected',
        reviewComment: `与已有共享资源《${findPaper(pair.originalPaperId)?.title ?? ''}》重复，禁止重复创建共享资源。`,
      })
  } else if (action === 'distinct') {
    /* 确认不同论文：新论文维持原审核流程 */
    void newPaper
  } else if (action === 'linked' && newPaper) {
    /* 关联到已有共享资源：驳回新论文并指向原文 */
    patchPaper(pair.newPaperId, {
      reviewStatus: 'rejected',
      reviewComment: `已关联到共享库《${findPaper(pair.originalPaperId)?.title ?? ''}》，请直接引用已有资源。`,
    })
  }

  const record: DuplicateRecord = {
    id: 7000 + (Date.now() % 100000),
    pairId,
    paperTitle: newPaper?.title ?? `疑似重复对 #${pairId}`,
    action,
    note,
    handlerId: ADMIN_ID,
    handledAt: now(),
  }
  dynamicDuplicateRecords.unshift(record)
  saveJSON(DUPLICATE_RECORD_KEY, dynamicDuplicateRecords)
  appendLog('重复论文处理', `疑似重复对 #${pairId}`)
  return true
}

export async function confirmDuplicate(pairId: number, note: string): Promise<boolean> {
  return handleDuplicate(pairId, 'duplicate', note)
}

export async function confirmDistinct(pairId: number, note: string): Promise<boolean> {
  return handleDuplicate(pairId, 'distinct', note)
}

export async function linkToOriginal(pairId: number, note: string): Promise<boolean> {
  return handleDuplicate(pairId, 'linked', note)
}

export async function getDuplicateRecords(): Promise<DuplicateRecord[]> {
  const handled = mockDuplicatePairs
    .filter((p) => p.status !== 'pending' && p.handlerNote)
    .map((p) => ({
      id: 7000 + p.id,
      pairId: p.id,
      paperTitle: `疑似重复对 #${p.id}`,
      action: p.status as 'duplicate' | 'distinct' | 'linked',
      note: p.handlerNote ?? '',
      handlerId: ADMIN_ID,
      handledAt: p.handledAt ?? '',
    }))
  const list = [...dynamicDuplicateRecords, ...handled].sort((a, b) =>
    b.handledAt.localeCompare(a.handledAt),
  )
  return delay(list, 0)
}

/* ---------------- 举报管理 ---------------- */

export async function getReports(status: 'all' | 'pending' | 'resolved'): Promise<ReportItem[]> {
  const list = mockReports.map((r) => {
    const ov = reportOverlay[r.id]
    return ov ? { ...r, status: ov.status, result: ov.result, handledAt: ov.handledAt } : r
  })
  const filtered =
    status === 'all'
      ? list
      : status === 'pending'
        ? list.filter((r) => r.status === 'pending')
        : list.filter((r) => r.status === 'resolved')
  return delay(filtered, 0)
}

export async function resolveReport(
  reportId: number,
  result: string,
  offlinePaperToo: boolean,
): Promise<boolean> {
  const report = mockReports.find((r) => r.id === reportId)
  if (!report) return delay(false, 0)
  reportOverlay[reportId] = { status: 'resolved', result, handledAt: now() }
  saveJSON(REPORT_OVERLAY_KEY, reportOverlay)
  if (offlinePaperToo) {
    const p = findPaper(report.paperId)
    if (p) {
      patchPaper(report.paperId, { reviewStatus: 'offline' })
      appendLog('论文下架', `《${p.title}》（举报处理）`)
    }
  }
  appendLog('举报处理', `举报单 #${reportId}`)
  return delay(true, 0)
}

/* ---------------- 操作日志 ---------------- */

export async function getLogs(action: string | 'all'): Promise<OperationLog[]> {
  const list = [...dynamicLogs, ...mockLogs].sort((a, b) => b.time.localeCompare(a.time))
  return delay(action === 'all' ? list : list.filter((l) => l.action === action), 0)
}
