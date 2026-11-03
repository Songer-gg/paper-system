import type { Category, Paper, Tag } from '@/types/paper'
import { loadJSON, saveJSON } from '@/utils/storage'
import { mockCategories } from '@/mocks/categories'
import { mockTags } from '@/mocks/tags'
import { sharedPapers } from '@/mocks/papers/sharedPapers'
import { privatePapers } from '@/mocks/papers/privatePapers'

/**
 * 知识管理服务：分类 / 标签的增删改查与论文计数。
 * Mock 阶段通过 localStorage overlay 持久化用户的自定义与修改，
 * 刷新后保留演示状态；接入后端后替换为真实 API 即可。
 */

const CURRENT_USER_ID = 1
const CUSTOM_CATEGORY_KEY = 'ph_custom_categories'
const RENAMED_CATEGORY_KEY = 'ph_renamed_categories'
const DELETED_CATEGORY_KEY = 'ph_deleted_categories'
const MOVED_CATEGORY_KEY = 'ph_moved_categories'
const CUSTOM_TAG_KEY = 'ph_custom_tags'
const TAG_OVERLAY_KEY = 'ph_tag_overlay'
const DELETED_TAG_KEY = 'ph_deleted_tags'

interface CustomCategory extends Category {
  /** true 表示用户在演示阶段新建 */
  custom?: boolean
}

let customCategories = loadJSON<CustomCategory[]>(CUSTOM_CATEGORY_KEY, [])
let renamedCategories = loadJSON<Record<number, string>>(RENAMED_CATEGORY_KEY, {})
let deletedCategoryIds = loadJSON<number[]>(DELETED_CATEGORY_KEY, [])
let movedCategories = loadJSON<Record<number, number | null>>(MOVED_CATEGORY_KEY, {})
let customTags = loadJSON<Tag[]>(CUSTOM_TAG_KEY, [])
let tagOverlay = loadJSON<Record<number, Partial<Tag>>>(TAG_OVERLAY_KEY, {})
let deletedTagIds = loadJSON<number[]>(DELETED_TAG_KEY, [])

function delay<T>(value: T, ms = 150): Promise<T> {
  return new Promise((resolve) => setTimeout(() => resolve(value), ms))
}

/** 应用改名与层级调整后的全量分类（含新建、过滤已删除） */
function effectiveCategories(): CustomCategory[] {
  return [...mockCategories, ...customCategories]
    .filter((c) => !deletedCategoryIds.includes(c.id))
    .map((c) => ({
      ...c,
      name: renamedCategories[c.id] ?? c.name,
      parentId: movedCategories[c.id] !== undefined ? movedCategories[c.id] : c.parentId,
    }))
}

/* ---------------- 当前用户可见的论文池 ---------------- */

/** 用户可见论文池：自己的论文（私有 + 共享）+ 审核通过的共享库 */
function visiblePapers(): Paper[] {
  const uploaded = loadJSON<Paper[]>('ph_uploaded_papers', [])
  const overlay = loadJSON<Record<number, Partial<Paper>>>('ph_paper_overlay', {})
  const deleted = loadJSON<number[]>('ph_deleted_papers', [])
  const mine = [...privatePapers, ...sharedPapers, ...uploaded]
    .filter((p) => !deleted.includes(p.id))
    .map((p) => ({ ...p, ...(overlay[p.id] ?? {}) }))
    .filter((p) => p.uploaderId === CURRENT_USER_ID)
  const shared = sharedPapers.filter((p) => p.reviewStatus === 'approved')
  const seen = new Set<number>()
  return [...mine, ...shared].filter((p) => !seen.has(p.id) && seen.add(p.id))
}

/** 用户可见论文池（自己的论文 + 审核通过的共享库），供笔记关联等场景选择 */
export async function getVisiblePapers(): Promise<Paper[]> {
  return delay(visiblePapers(), 60)
}

/* ---------------- 分类 ---------------- */

export async function getCategories(): Promise<Category[]> {
  return delay(effectiveCategories())
}

export async function createCategory(name: string, parentId: number | null): Promise<Category> {
  const cat: CustomCategory = {
    id: Math.max(100, ...customCategories.map((c) => c.id)) + 1,
    name: name.trim(),
    parentId,
    custom: true,
  }
  customCategories.push(cat)
  saveJSON(CUSTOM_CATEGORY_KEY, customCategories)
  return delay(cat)
}

export async function renameCategory(id: number, name: string): Promise<boolean> {
  renamedCategories[id] = name.trim()
  saveJSON(RENAMED_CATEGORY_KEY, renamedCategories)
  return delay(true)
}

/** 删除分类（含其全部子分类），返回被删除的分类名称数组 */
export async function deleteCategory(id: number): Promise<string[]> {
  const all = effectiveCategories()
  const toDelete: number[] = [id]
  let frontier = [id]
  while (frontier.length) {
    const next = all.filter((c) => c.parentId !== null && frontier.includes(c.parentId)).map((c) => c.id)
    toDelete.push(...next)
    frontier = next
  }
  deletedCategoryIds.push(...toDelete)
  saveJSON(DELETED_CATEGORY_KEY, deletedCategoryIds)
  const names = toDelete
    .map((cid) => all.find((c) => c.id === cid)?.name ?? '')
    .filter(Boolean)
  return delay(names)
}

/** 调整分类层级：不允许移动到自身或其后代分类下 */
export async function moveCategory(id: number, newParentId: number | null): Promise<boolean> {
  if (newParentId === id) return delay(false, 60)
  const all = effectiveCategories()
  /* 收集 id 及其全部后代，防止形成环 */
  const forbidden = new Set<number>([id])
  let frontier = [id]
  while (frontier.length) {
    const next = all
      .filter((c) => c.parentId !== null && frontier.includes(c.parentId))
      .map((c) => c.id)
    next.forEach((nid) => forbidden.add(nid))
    frontier = next
  }
  if (newParentId !== null && forbidden.has(newParentId)) return delay(false, 60)
  movedCategories[id] = newParentId
  saveJSON(MOVED_CATEGORY_KEY, movedCategories)
  return delay(true)
}

/** 分类（含子分类）下的论文数量 */
export function countPapersByCategory(): Record<number, number> {
  const papers = visiblePapers()
  const all = effectiveCategories()
  const count: Record<number, number> = {}
  for (const cat of all) {
    // 每篇论文计数归到自身分类及其全部祖先
    for (const p of papers) {
      let cur: Category | undefined = all.find((c) => c.id === p.categoryId)
      while (cur) {
        if (cur.id === cat.id) {
          count[cat.id] = (count[cat.id] ?? 0) + 1
          break
        }
        cur = cur.parentId !== null ? all.find((c) => c.id === cur!.parentId) : undefined
      }
    }
  }
  return count
}

/** 分类（含子分类）下的相关论文，按更新时间倒序 */
export async function getPapersOfCategory(id: number): Promise<Paper[]> {
  const all = effectiveCategories()
  const ids = [id]
  let frontier = [id]
  while (frontier.length) {
    const next = all
      .filter((c) => c.parentId !== null && frontier.includes(c.parentId))
      .map((c) => c.id)
    ids.push(...next)
    frontier = next
  }
  const papers = visiblePapers().filter((p) => ids.includes(p.categoryId))
  return delay(papers)
}

/* ---------------- 标签 ---------------- */

export async function getTags(): Promise<Tag[]> {
  const merged = [...mockTags, ...customTags]
    .filter((t) => !deletedTagIds.includes(t.id))
    .map((t) => ({ ...t, ...(tagOverlay[t.id] ?? {}) }))
  return delay(merged)
}

export async function createTag(name: string, color: string): Promise<Tag> {
  const tag: Tag = {
    id: Math.max(100, ...customTags.map((t) => t.id)) + 1,
    name: name.trim(),
    color,
  }
  customTags.push(tag)
  saveJSON(CUSTOM_TAG_KEY, customTags)
  return delay(tag)
}

export async function updateTag(id: number, name: string, color: string): Promise<boolean> {
  tagOverlay[id] = { name: name.trim(), color }
  saveJSON(TAG_OVERLAY_KEY, tagOverlay)
  return delay(true)
}

export async function deleteTag(id: number): Promise<boolean> {
  deletedTagIds.push(id)
  saveJSON(DELETED_TAG_KEY, deletedTagIds)
  return delay(true)
}

export function countPapersByTag(): Record<number, number> {
  const count: Record<number, number> = {}
  for (const p of visiblePapers()) {
    for (const tid of p.tagIds) {
      count[tid] = (count[tid] ?? 0) + 1
    }
  }
  return count
}

export async function getPapersOfTag(id: number): Promise<Paper[]> {
  const papers = visiblePapers().filter((p) => p.tagIds.includes(id))
  return delay(papers)
}
