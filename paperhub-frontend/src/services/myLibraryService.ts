import { currentUserId, loadPersonalJSON, savePersonalJSON } from '@/utils/personalStorage'
import type { Note, Paper } from '@/types/paper'
import { loadJSON, saveJSON } from '@/utils/storage'
import { sharedPapers } from '@/mocks/papers/sharedPapers'
import { privatePapers } from '@/mocks/papers/privatePapers'
import { pendingPapers } from '@/mocks/papers/pendingPapers'
import * as userData from '@/services/userDataService'

/**
 * 我的论文库服务：私有/共享论文管理、编辑 overlay、删除、
 * 审核状态流转（取消共享 / 重新提交）、模拟查重、上传提交、草稿。
 * 所有写操作通过 localStorage 持久化，刷新后保留演示状态。
 */

const OVERLAY_KEY = 'ph_paper_overlay'
const DELETED_KEY = 'ph_deleted_papers'
const UPLOADED_KEY = 'ph_uploaded_papers'
const DRAFT_KEY = 'ph_upload_draft'

type PaperOverlay = Partial<Paper>

function delay<T>(value: T, ms = 200): Promise<T> {
  return new Promise((resolve) => setTimeout(() => resolve(value), ms))
}

function now(): string {
  const d = new Date()
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
}

/** 合并 overlay 的单篇读取 */
function mergePaper(p: Paper): Paper {
  return { ...p, ...(loadJSON<Record<number, PaperOverlay>>(OVERLAY_KEY, {})[p.id] ?? {}) }
}

function currentPool(): Paper[] {
  const deleted = loadJSON<number[]>(DELETED_KEY, [])
  const uploaded = loadJSON<Paper[]>(UPLOADED_KEY, [])
  return [...sharedPapers, ...privatePapers, ...pendingPapers, ...uploaded]
    .filter((paper) => !deleted.includes(paper.id))
    .map(mergePaper)
}

function writePatch(id: number, patch: PaperOverlay): void {
  const latest = loadJSON<Record<number, PaperOverlay>>(OVERLAY_KEY, {})
  latest[id] = { ...(latest[id] ?? {}), ...patch }
  saveJSON(OVERLAY_KEY, latest)
}

/* ---------------- 我的论文 ---------------- */

export async function getMyPapers(): Promise<{
  privatePapers: Paper[]
  sharedPapers: Paper[]
}> {
  const all = currentPool().filter((p) => p.uploaderId === currentUserId())
  return delay({
    privatePapers: all.filter((p) => p.visibility === 'private'),
    sharedPapers: all.filter((p) => p.visibility === 'shared'),
  })
}

/** 收藏的论文（仅收藏共享论文；下架/删除/未过审的论文自动隐藏） */
export async function getFavoritePapers(): Promise<Paper[]> {
  const favIds = userData.getFavoritePaperIds()
  const pool = currentPool().filter(
    (p) => p.visibility === 'shared' && p.reviewStatus === 'approved',
  )
  const list = pool.filter((p) => favIds.includes(p.id))
  return delay(list)
}

/** 详情页统一取论文：我的论文，或共享库中审核通过的论文 */
export async function getPaperForDetail(id: number): Promise<Paper | null> {
  const pool = currentPool()
  const own = pool.find((p) => p.id === id && p.uploaderId === currentUserId())
  if (own) return delay(own)
  const shared = pool.find(
    (p) => p.id === id && p.visibility === 'shared' && p.reviewStatus === 'approved',
  )
  return delay(shared ?? null)
}

/* ---------------- 编辑 / 删除 / 状态流转 ---------------- */

export interface PaperEditPayload {
  title?: string
  authors?: string[]
  abstract?: string
  journal?: string
  year?: number
  doi?: string
  categoryId?: number
  tagIds?: number[]
  keywords?: string[]
  link?: string
}

export async function updatePaper(id: number, patch: PaperEditPayload): Promise<Paper | null> {
  const source = currentPool().find((p) => p.id === id)
  if (!source || source.uploaderId !== currentUserId()) return delay(null)
  const next: PaperOverlay = { ...patch }
  if (source.visibility === 'shared' && source.reviewStatus === 'approved') {
    next.reviewStatus = 'pending'
    next.reviewComment = undefined
  }
  writePatch(id, next)
  return delay(mergePaper(source))
}

export async function deleteMyPaper(id: number): Promise<boolean> {
  const source = currentPool().find((p) => p.id === id)
  if (!source || source.uploaderId !== currentUserId()) return delay(false)
  const deleted = loadJSON<number[]>(DELETED_KEY, [])
  saveJSON(DELETED_KEY, [...new Set([...deleted, id])])
  return delay(true)
}

/** 用户撤回保留在共享记录，管理员不能直接恢复；重新提交重新审核。 */
export async function changeReviewStatus(
  id: number,
  action: 'unshare' | 'resubmit',
): Promise<Paper | null> {
  const source = currentPool().find((p) => p.id === id && p.uploaderId === currentUserId())
  if (!source || source.visibility !== 'shared') throw new Error('无权操作此论文')
  if (action === 'unshare') {
    if (!['approved', 'pending'].includes(source.reviewStatus ?? ''))
      throw new Error('当前状态不能取消共享')
    writePatch(id, { reviewStatus: 'offline', offlineReason: 'withdrawn' })
  } else {
    if (
      source.reviewStatus !== 'rejected' &&
      !(source.reviewStatus === 'offline' && source.offlineReason === 'withdrawn')
    ) {
      throw new Error('当前状态不能重新提交')
    }
    writePatch(id, { reviewStatus: 'pending', offlineReason: undefined, reviewComment: undefined })
  }
  return delay(mergePaper(source))
}

/* ---------------- 上传 ---------------- */

export interface UploadPayload extends PaperEditPayload {
  visibility: 'private' | 'shared'
  fileName: string
  fileSizeMB: number
  pageCount: number
  copyrightAgreed: boolean
  fileSha256: string
}

export async function submitPaper(file: File, payload: UploadPayload): Promise<Paper> {
  if (file.size === 0 || file.size > 100 * 1024 * 1024)
    throw new Error('PDF 文件必须在 0 至 100 MB 之间')
  const knowledge = await import('@/services/knowledgeService')
  const [categories, tags] = await Promise.all([knowledge.getCategories(), knowledge.getTags()])
  const category = categories.find((item) => item.id === payload.categoryId)
  if (!category || (payload.visibility === 'shared' && category.custom))
    throw new Error('共享论文必须选择平台分类')
  if (
    (payload.tagIds ?? []).some(
      (id) =>
        !tags.some((tag) => tag.id === id && (payload.visibility === 'private' || !tag.custom)),
    )
  ) {
    throw new Error('标签不存在或不适用于当前论文可见范围')
  }
  if (payload.visibility === 'shared') {
    if (!payload.copyrightAgreed) throw new Error('共享资源需确认版权授权')
    const duplicate = await checkDuplicate(payload)
    if (duplicate.level === 'doi' || duplicate.level === 'file') throw new Error(duplicate.message)
  }
  const paper: Paper = {
    id: Math.max(4000, ...currentPool().map((item) => item.id)) + 1,
    title: payload.title!,
    authors: payload.authors!,
    abstract: payload.abstract ?? '',
    doi: payload.doi ?? '',
    journal: payload.journal ?? '',
    year: payload.year!,
    keywords: payload.keywords ?? [],
    categoryId: payload.categoryId!,
    tagIds: payload.tagIds ?? [],
    visibility: payload.visibility,
    uploaderId: currentUserId(),
    uploadedAt: now(),
    downloads: 0,
    fileSizeMB: payload.fileSizeMB,
    pageCount: payload.pageCount,
    link: payload.link,
    copyrightAgreed: payload.copyrightAgreed,
    fileSha256: payload.fileSha256,
    ...(payload.visibility === 'shared' ? { reviewStatus: 'pending' as const } : {}),
    ...(payload.fileName ? { fileName: payload.fileName } : {}),
  }
  const latest = loadJSON<Paper[]>(UPLOADED_KEY, [])
  saveJSON(UPLOADED_KEY, [paper, ...latest])
  return delay(paper)
}

/* ---------------- 模拟查重 ---------------- */

export interface DuplicateCheckResult {
  level: 'doi' | 'title' | 'file' | 'none'
  message: string
  paper?: Paper
}

/**
 * 模拟查重（规划书 7.5 / 5.5）：
 * - DOI 完全相同 → 已存在相同论文（阻止创建）
 * - SHA-256 相同 → 文件重复（阻止创建）；真实服务端仍须重新计算
 * - 标题完全相同 → 疑似重复（保留人工判断空间，可继续提交）
 */
export async function checkDuplicate(input: {
  doi?: string
  title?: string
  fileSha256?: string
}): Promise<DuplicateCheckResult> {
  const pool = currentPool().filter(
    (paper) => paper.visibility === 'shared' && paper.reviewStatus !== 'rejected',
  )
  const visibleMatch = (paper: Paper): Paper | undefined =>
    paper.uploaderId === currentUserId() || paper.reviewStatus === 'approved' ? paper : undefined
  const normalizeDoi = (value: string) =>
    value
      .trim()
      .toLowerCase()
      .replace(/^https?:\/\/(?:dx\.)?doi\.org\//, '')
      .replace(/^doi:\s*/, '')
  const doi = input.doi ? normalizeDoi(input.doi) : ''
  if (doi) {
    const hit = pool.find((p) => p.doi && normalizeDoi(p.doi) === doi)
    if (hit) {
      return delay({
        level: 'doi',
        message: `共享库中已存在 DOI 相同的论文，不允许重复创建共享资源。`,
        paper: visibleMatch(hit),
      })
    }
  }
  if (input.fileSha256) {
    const hit = pool.find((p) => p.fileSha256 === input.fileSha256)
    if (hit) {
      return delay({
        level: 'file',
        message: `已存在相同文件（文件指纹一致）的论文，不允许重复创建。`,
        paper: visibleMatch(hit),
      })
    }
  }
  const title = input.title?.trim()
  if (title) {
    const hit = pool.find((p) => p.title === title)
    if (hit) {
      return delay({
        level: 'title',
        message: `共享库中已存在标题相同的论文，可能是重复资源。请确认是否为不同版本。`,
        paper: visibleMatch(hit),
      })
    }
  }
  return delay({ level: 'none', message: '未检测到重复论文。' })
}

/* ---------------- 草稿 ---------------- */

export interface UploadDraft {
  title?: string
  authors?: string
  abstract?: string
  doi?: string
  journal?: string
  year?: number
  keywords?: string
  link?: string
  categoryId?: number
  tagIds?: number[]
  visibility?: 'private' | 'shared'
  savedAt: string
}

export function saveDraft(form: Omit<UploadDraft, 'savedAt'>): void {
  savePersonalJSON(DRAFT_KEY, { ...form, savedAt: now() })
}

export function loadDraft(): UploadDraft | null {
  return loadPersonalJSON<UploadDraft | null>(DRAFT_KEY, null)
}

export function clearDraft(): void {
  savePersonalJSON(DRAFT_KEY, null)
}

/* ---------------- 笔记 ---------------- */

/** 新增阅读笔记（委托 userDataService 统一管理缓存） */
export async function addNote(input: {
  title: string
  paperId: number
  content: string
}): Promise<Note> {
  return userData.addNote(input)
}
