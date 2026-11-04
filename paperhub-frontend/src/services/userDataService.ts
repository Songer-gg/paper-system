import type { FavoriteRecord, Note, ReadingRecord, ReadingStatus } from '@/types/paper'
import { loadJSON, saveJSON } from '@/utils/storage'
import { seedFavoriteRecords, seedReadingRecords } from '@/mocks/user/reading'
import { seedNotes } from '@/mocks/user/notes'

/**
 * 当前用户个人数据服务：收藏 / 阅读状态 / 笔记。
 * 使用 localStorage 保存操作结果，刷新页面后演示状态仍保留。
 * 后续接入后端时，将本文件实现替换为真实 API 调用即可。
 */

const FAVORITE_KEY = 'ph_favorites'
const READING_KEY = 'ph_reading'
const NOTE_KEY = 'ph_notes'

const favoriteRecords = loadJSON(FAVORITE_KEY, seedFavoriteRecords)
const readingRecords = loadJSON(READING_KEY, seedReadingRecords)
const notes = loadJSON<Note[]>(NOTE_KEY, seedNotes)

function delay<T>(value: T, ms = 150): Promise<T> {
  return new Promise((resolve) => setTimeout(() => resolve(value), ms))
}

function now(): string {
  const d = new Date()
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
}

/* ---------------- 收藏 ---------------- */

export async function getFavorites(): Promise<FavoriteRecord[]> {
  return delay(favoriteRecords.map((f) => ({ ...f })))
}

export function getFavoritePaperIds(): number[] {
  return favoriteRecords.map((f) => f.paperId)
}

export async function toggleFavorite(
  paperId: number,
): Promise<{ paperId: number; favorited: boolean }> {
  const idx = favoriteRecords.findIndex((f) => f.paperId === paperId)
  let favorited: boolean
  if (idx >= 0) {
    favoriteRecords.splice(idx, 1)
    favorited = false
  } else {
    favoriteRecords.unshift({ paperId, favoritedAt: now() })
    favorited = true
  }
  saveJSON(FAVORITE_KEY, favoriteRecords)
  return delay({ paperId, favorited })
}

/* ---------------- 阅读状态 ---------------- */

export function getReadingRecords(): ReadingRecord[] {
  return readingRecords
}

export async function addToReading(paperId: number): Promise<ReadingRecord> {
  const existing = readingRecords.find((r) => r.paperId === paperId)
  if (existing) {
    return delay({ ...existing })
  }
  const record: ReadingRecord = { paperId, status: 'unread', lastReadAt: now() }
  readingRecords.unshift(record)
  saveJSON(READING_KEY, readingRecords)
  return delay({ ...record })
}

export async function updateReadingStatus(
  paperId: number,
  status: ReadingStatus,
): Promise<ReadingRecord> {
  const existing = readingRecords.find((r) => r.paperId === paperId)
  if (existing) {
    existing.status = status
    existing.lastReadAt = now()
  } else {
    readingRecords.unshift({ paperId, status, lastReadAt: now() })
  }
  saveJSON(READING_KEY, readingRecords)
  return delay({ ...readingRecords.find((r) => r.paperId === paperId)! })
}

/* ---------------- 笔记（供后续批次使用） ---------------- */

export async function getNotes(): Promise<Note[]> {
  return delay(notes.map((n) => ({ ...n })))
}

export async function addNote(input: {
  title: string
  paperId: number
  content: string
}): Promise<Note> {
  const maxId = notes.reduce((m, n) => Math.max(m, n.id), 5000)
  const note: Note = {
    id: maxId + 1,
    title: input.title.trim(),
    paperId: input.paperId,
    content: input.content.trim(),
    createdAt: now(),
    updatedAt: now(),
  }
  notes.unshift(note)
  saveJSON(NOTE_KEY, notes)
  return delay(note)
}

export async function updateNote(
  id: number,
  patch: { title?: string; paperId?: number; content?: string },
): Promise<Note | null> {
  const note = notes.find((n) => n.id === id)
  if (!note) return delay(null)
  if (patch.title !== undefined) note.title = patch.title.trim()
  if (patch.paperId !== undefined) note.paperId = patch.paperId
  if (patch.content !== undefined) note.content = patch.content.trim()
  note.updatedAt = now()
  saveJSON(NOTE_KEY, notes)
  return delay({ ...note })
}

export async function deleteNote(id: number): Promise<boolean> {
  const idx = notes.findIndex((n) => n.id === id)
  if (idx < 0) return delay(false)
  notes.splice(idx, 1)
  saveJSON(NOTE_KEY, notes)
  return delay(true)
}
