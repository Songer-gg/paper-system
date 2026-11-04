import type { Note, Paper, ReviewStatus } from '@/types/paper'
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

const CURRENT_USER_ID = 1
const OVERLAY_KEY = 'ph_paper_overlay'
const DELETED_KEY = 'ph_deleted_papers'
const UPLOADED_KEY = 'ph_uploaded_papers'
const DRAFT_KEY = 'ph_upload_draft'

type PaperOverlay = Partial<Paper>

const overlay = loadJSON<Record<number, PaperOverlay>>(OVERLAY_KEY, {})
const deletedIds = loadJSON<number[]>(DELETED_KEY, [])
const uploadedPapers = loadJSON<Paper[]>(UPLOADED_KEY, [])
let draft = loadJSON<UploadDraft | null>(DRAFT_KEY, null)

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
  return { ...p, ...(overlay[p.id] ?? {}) }
}

/* ---------------- 我的论文 ---------------- */

export async function getMyPapers(): Promise<{
  privatePapers: Paper[]
  sharedPapers: Paper[]
}> {
  const myStatic = [...sharedPapers, ...privatePapers]
    .filter((p) => p.uploaderId === CURRENT_USER_ID)
    .map(mergePaper)
  const myUploaded = uploadedPapers.filter((p) => p.uploaderId === CURRENT_USER_ID)
  const all = [...myStatic, ...myUploaded].filter((p) => !deletedIds.includes(p.id))
  return delay({
    privatePapers: all.filter((p) => p.visibility === 'private'),
    sharedPapers: all.filter((p) => p.visibility === 'shared'),
  })
}

/** 收藏的论文（仅收藏共享论文；下架/删除/未过审的论文自动隐藏） */
export async function getFavoritePapers(): Promise<Paper[]> {
  const favIds = userData.getFavoritePaperIds()
  const pool = [...sharedPapers, ...pendingPapers, ...uploadedPapers]
    .filter((p) => !deletedIds.includes(p.id))
    .map(mergePaper)
    .filter((p) => p.visibility === 'shared' && p.reviewStatus === 'approved')
  const list = pool.filter((p) => favIds.includes(p.id))
  return delay(list)
}

/** 详情页统一取论文：我的论文，或共享库中审核通过的论文 */
export async function getPaperForDetail(id: number): Promise<Paper | null> {
  const pool = [...sharedPapers, ...privatePapers, ...pendingPapers, ...uploadedPapers]
    .filter((p) => !deletedIds.includes(p.id))
    .map(mergePaper)
  const own = pool.find((p) => p.id === id && p.uploaderId === CURRENT_USER_ID)
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
}

export async function updatePaper(id: number, patch: PaperEditPayload): Promise<Paper | null> {
  const source = [...sharedPapers, ...privatePapers, ...uploadedPapers].find((p) => p.id === id)
  if (!source) return delay(null)
  overlay[id] = { ...(overlay[id] ?? {}), ...patch }
  saveJSON(OVERLAY_KEY, overlay)
  return delay(mergePaper(source))
}

export async function deleteMyPaper(id: number): Promise<boolean> {
  const source = [...sharedPapers, ...privatePapers, ...uploadedPapers].find((p) => p.id === id)
  if (!source || source.uploaderId !== CURRENT_USER_ID) return delay(false)
  deletedIds.push(id)
  saveJSON(DELETED_KEY, deletedIds)
  return delay(true)
}

/** 审核状态流转：取消共享 approved→offline；重新提交 rejected→pending */
export async function changeReviewStatus(id: number, status: ReviewStatus): Promise<Paper | null> {
  return updatePaper(id, { reviewStatus: status } as PaperOverlay)
}

/* ---------------- 上传 ---------------- */

export interface UploadPayload extends PaperEditPayload {
  visibility: 'private' | 'shared'
  fileName: string
  fileSizeMB: number
  pageCount: number
}

let nextUploadedId = uploadedPapers.length ? Math.max(...uploadedPapers.map((p) => p.id)) + 1 : 4001

export async function submitPaper(payload: UploadPayload): Promise<Paper> {
  const paper: Paper = {
    id: nextUploadedId++,
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
    uploaderId: CURRENT_USER_ID,
    uploadedAt: now(),
    downloads: 0,
    fileSizeMB: payload.fileSizeMB,
    pageCount: payload.pageCount,
    ...(payload.visibility === 'shared' ? { reviewStatus: 'pending' as ReviewStatus } : {}),
    /* 记录文件名（模拟文件指纹），供文件级查重比对 */
    ...(payload.fileName ? { fileName: payload.fileName } : {}),
  }
  uploadedPapers.unshift(paper)
  saveJSON(UPLOADED_KEY, uploadedPapers)
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
 * - 文件名（模拟文件哈希）相同 → 文件重复（阻止创建）
 * - 标题完全相同 → 疑似重复（保留人工判断空间，可继续提交）
 */
export async function checkDuplicate(input: {
  doi?: string
  title?: string
  fileName?: string
}): Promise<DuplicateCheckResult> {
  const pool = [...sharedPapers, ...uploadedPapers].filter((p) => !deletedIds.includes(p.id))
  const doi = input.doi?.trim()
  if (doi) {
    const hit = pool.find((p) => p.doi && p.doi === doi)
    if (hit) {
      return delay({
        level: 'doi',
        message: `共享库中已存在 DOI 相同的论文，不允许重复创建共享资源。`,
        paper: mergePaper(hit),
      })
    }
  }
  if (input.fileName) {
    const hit = pool.find((p) => (p as Paper & { fileName?: string }).fileName === input.fileName)
    if (hit) {
      return delay({
        level: 'file',
        message: `已存在相同文件（文件指纹一致）的论文，不允许重复创建。`,
        paper: mergePaper(hit),
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
        paper: mergePaper(hit),
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
  draft = { ...form, savedAt: now() }
  saveJSON(DRAFT_KEY, draft)
}

export function loadDraft(): UploadDraft | null {
  return draft
}

export function clearDraft(): void {
  draft = null
  saveJSON(DRAFT_KEY, null)
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
