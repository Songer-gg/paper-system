import type { FavoriteRecord, Note, ReadingRecord, ReadingStatus } from '@/types/paper'
import { seedFavoriteRecords, seedReadingRecords } from '@/mocks/user/reading'
import { seedNotes } from '@/mocks/user/notes'
import { currentUserId, loadPersonalJSON, savePersonalJSON } from '@/mocks/personalStorage'

const favorites = () =>
  loadPersonalJSON<FavoriteRecord[]>(
    'ph_favorites',
    currentUserId() === 1 ? seedFavoriteRecords : [],
  )
const readings = () =>
  loadPersonalJSON<ReadingRecord[]>('ph_reading', currentUserId() === 1 ? seedReadingRecords : [])
const notes = () => loadPersonalJSON<Note[]>('ph_notes', currentUserId() === 1 ? seedNotes : [])
const now = () => new Date().toLocaleString('sv-SE')
async function visiblePaper(id: number) {
  return (await import('@/mocks/services/myLibraryService')).getPaperForDetail(id)
}

export async function getFavorites(): Promise<FavoriteRecord[]> {
  const result: FavoriteRecord[] = []
  for (const record of favorites()) {
    const paper = await visiblePaper(record.paperId)
    if (paper?.visibility === 'shared' && paper.reviewStatus === 'approved')
      result.push({ ...record })
  }
  return result
}

/** 同步缓存选择器；真实接入须先由 R1 初始化缓存。 */
export function getFavoritePaperIds(): number[] {
  return favorites().map((item) => item.paperId)
}

export async function toggleFavorite(
  paperId: number,
  target?: boolean,
): Promise<{ paperId: number; favorited: boolean }> {
  const records = favorites()
  const index = records.findIndex((item) => item.paperId === paperId)
  const favorited = target ?? index < 0
  if (favorited && index < 0) {
    const paper = await visiblePaper(paperId)
    if (!paper || paper.visibility !== 'shared' || paper.reviewStatus !== 'approved')
      throw new Error('只能收藏可见共享论文')
    records.unshift({ paperId, favoritedAt: now() })
  } else if (!favorited && index >= 0) records.splice(index, 1)
  savePersonalJSON('ph_favorites', records)
  return { paperId, favorited }
}

export async function getReadingRecords(): Promise<ReadingRecord[]> {
  const result: ReadingRecord[] = []
  for (const record of readings()) {
    if (await visiblePaper(record.paperId))
      result.push({ ...record, addedAt: record.addedAt ?? record.lastReadAt })
  }
  return result
}

export async function addToReading(paperId: number): Promise<ReadingRecord> {
  if (!(await visiblePaper(paperId))) throw new Error('论文不可访问')
  const records = readings()
  const existing = records.find((item) => item.paperId === paperId)
  if (existing) return { ...existing, addedAt: existing.addedAt ?? existing.lastReadAt }
  const record: ReadingRecord = { paperId, status: 'unread', addedAt: now(), lastReadAt: now() }
  records.unshift(record)
  savePersonalJSON('ph_reading', records)
  return { ...record }
}

export async function updateReadingStatus(
  paperId: number,
  status: ReadingStatus,
): Promise<ReadingRecord> {
  if (!['unread', 'reading', 'read', 'paused'].includes(status)) throw new Error('无效阅读状态')
  if (!(await visiblePaper(paperId))) throw new Error('论文不可访问')
  const records = readings()
  let record = records.find((item) => item.paperId === paperId)
  if (!record) {
    record = { paperId, status, addedAt: now(), lastReadAt: now() }
    records.unshift(record)
  }
  record.addedAt ??= record.lastReadAt
  record.status = status
  record.lastReadAt = now()
  savePersonalJSON('ph_reading', records)
  return { ...record }
}

export async function getNotes(): Promise<Note[]> {
  return Promise.all(
    notes().map(async (note) => {
      const paper = await visiblePaper(note.paperId)
      return { ...note, paperTitle: note.paperTitle ?? paper?.title, paperAvailable: !!paper }
    }),
  )
}

function validateNote(title: string, content: string): void {
  if (!title.trim() || [...title.trim()].length > 300) throw new Error('笔记标题须为 1–300 字符')
  if (!content.trim() || new TextEncoder().encode(content).length > 60000)
    throw new Error('笔记正文为空或过长')
}

export async function addNote(input: {
  title: string
  paperId: number
  content: string
}): Promise<Note> {
  validateNote(input.title, input.content)
  const paper = await visiblePaper(input.paperId)
  if (!paper) throw new Error('关联论文不可访问')
  const records = notes()
  const note: Note = {
    ...input,
    id: Math.max(5000, ...records.map((item) => item.id)) + 1,
    title: input.title.trim(),
    content: input.content.trim(),
    paperTitle: paper.title,
    paperAvailable: true,
    createdAt: now(),
    updatedAt: now(),
  }
  records.unshift(note)
  savePersonalJSON('ph_notes', records)
  return { ...note }
}

export async function updateNote(
  id: number,
  patch: { title?: string; paperId?: number; content?: string },
): Promise<Note | null> {
  const records = notes()
  const note = records.find((item) => item.id === id)
  if (!note) return null
  validateNote(patch.title ?? note.title, patch.content ?? note.content)
  if (patch.paperId !== undefined && patch.paperId !== note.paperId) {
    const paper = await visiblePaper(patch.paperId)
    if (!paper) throw new Error('关联论文不可访问')
    note.paperId = patch.paperId
    note.paperTitle = paper.title
  }
  note.title = (patch.title ?? note.title).trim()
  note.content = (patch.content ?? note.content).trim()
  note.updatedAt = now()
  savePersonalJSON('ph_notes', records)
  return { ...note }
}

export async function deleteNote(id: number): Promise<boolean> {
  const records = notes()
  const index = records.findIndex((note) => note.id === id)
  if (index < 0) return false
  records.splice(index, 1)
  savePersonalJSON('ph_notes', records)
  return true
}
