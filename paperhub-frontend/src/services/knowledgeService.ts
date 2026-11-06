import { currentRole, currentUserId } from '@/utils/personalStorage'
import type { Category, Paper, Tag } from '@/types/paper'
import { loadJSON, saveJSON } from '@/utils/storage'
import { mockCategories } from '@/mocks/categories'
import { mockTags } from '@/mocks/tags'
import { sharedPapers } from '@/mocks/papers/sharedPapers'
import { privatePapers } from '@/mocks/papers/privatePapers'
import { pendingPapers } from '@/mocks/papers/pendingPapers'

/**
 * 知识管理服务：分类 / 标签的增删改查与论文计数。
 * Mock 阶段通过 localStorage overlay 持久化用户的自定义与修改，
 * 刷新后保留演示状态；接入后端后替换为真实 API 即可。
 */

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

const customCategories = loadJSON<CustomCategory[]>(CUSTOM_CATEGORY_KEY, [])
const renamedCategories = loadJSON<Record<number, string>>(RENAMED_CATEGORY_KEY, {})
const deletedCategoryIds = loadJSON<number[]>(DELETED_CATEGORY_KEY, [])
const movedCategories = loadJSON<Record<number, number | null>>(MOVED_CATEGORY_KEY, {})
const customTags = loadJSON<Tag[]>(CUSTOM_TAG_KEY, [])
const tagOverlay = loadJSON<Record<number, Partial<Tag>>>(TAG_OVERLAY_KEY, {})
const deletedTagIds = loadJSON<number[]>(DELETED_TAG_KEY, [])

function delay<T>(value: T, ms = 150): Promise<T> {
  return new Promise((resolve) => setTimeout(() => resolve(value), ms))
}

/** 应用改名与层级调整后的全量分类（含新建、过滤已删除） */
function effectiveCategories(): CustomCategory[] {
  return [...mockCategories, ...customCategories]
    .filter(
      (category) =>
        currentRole() === 'admin' ||
        !category.custom ||
        (category.ownerId ?? 1) === currentUserId(),
    )
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
  const mine = [...privatePapers, ...sharedPapers, ...pendingPapers, ...uploaded]
    .filter((p) => !deleted.includes(p.id))
    .map((p) => ({ ...p, ...(overlay[p.id] ?? {}) }))
    .filter((p) => currentRole() === 'admin' || p.uploaderId === currentUserId())
  const shared = [...sharedPapers, ...pendingPapers, ...uploaded]
    .filter((p) => !deleted.includes(p.id))
    .map((p) => ({ ...p, ...(overlay[p.id] ?? {}) }))
    .filter((p) => p.visibility === 'shared' && p.reviewStatus === 'approved')
  const seen = new Set<number>()
  return [...mine, ...shared].filter((p) => !seen.has(p.id) && seen.add(p.id))
}

/** 用户可见论文池（自己的论文 + 审核通过的共享库），供笔记关联等场景选择 */
export async function getVisiblePapers(): Promise<Paper[]> {
  return delay(visiblePapers(), 60)
}

/** 展示层字典查询。真实适配器加载 K1/K7 后维护相同缓存选择器。 */
export function getCategory(id: number | null): Category | undefined {
  return effectiveCategories().find((category) => category.id === id)
}

export function getCategoryDescendantIds(id: number): number[] {
  const result = new Set([id])
  const categories = effectiveCategories()
  let changed = true
  while (changed) {
    changed = false
    for (const category of categories) {
      if (category.parentId !== null && result.has(category.parentId) && !result.has(category.id)) {
        result.add(category.id)
        changed = true
      }
    }
  }
  return [...result]
}

export function getTag(id: number): Tag | undefined {
  const tag = [...mockTags, ...customTags].find((item) => item.id === id)
  if (!tag || deletedTagIds.includes(id)) return undefined
  if (tag.custom && currentRole() !== 'admin' && (tag.ownerId ?? 1) !== currentUserId())
    return undefined
  return { ...tag, ...(tagOverlay[id] ?? {}) }
}

/* ---------------- 分类 ---------------- */

export async function getCategories(): Promise<Category[]> {
  const counts = countPapersByCategory()
  return delay(
    effectiveCategories().map((category) => ({
      ...category,
      paperCount: counts[category.id] ?? 0,
    })),
  )
}

export async function createCategory(name: string, parentId: number | null): Promise<Category> {
  if (!name.trim() || name.trim().length > 50) throw new Error('分类名须为 1–50 字符')
  const custom = currentRole() !== 'admin'
  if (
    effectiveCategories().some(
      (category) =>
        !!category.custom === custom &&
        category.parentId === parentId &&
        category.name === name.trim(),
    )
  )
    throw new Error('同级分类已存在')
  const cat: CustomCategory = {
    id: Math.max(100, ...customCategories.map((c) => c.id)) + 1,
    name: name.trim(),
    parentId,
    custom,
    ownerId: custom ? currentUserId() : null,
  }
  customCategories.push(cat)
  saveJSON(CUSTOM_CATEGORY_KEY, customCategories)
  return delay(cat)
}

export async function renameCategory(id: number, name: string): Promise<boolean> {
  const category = effectiveCategories().find((item) => item.id === id)
  if (!category) throw new Error('分类不存在')
  if (currentRole() !== 'admin' && !category.custom) throw new Error('平台分类仅管理员可修改')
  if (
    effectiveCategories().some(
      (item) => item.id !== id && item.parentId === category.parentId && item.name === name.trim(),
    )
  )
    throw new Error('同级分类已存在')
  renamedCategories[id] = name.trim()
  saveJSON(RENAMED_CATEGORY_KEY, renamedCategories)
  return delay(true)
}

/** 删除分类（含其全部子分类），返回被删除的分类名称数组 */
export async function deleteCategory(id: number): Promise<string[]> {
  const all = effectiveCategories()
  const target = all.find((category) => category.id === id)
  if (!target || (currentRole() !== 'admin' && !target.custom)) throw new Error('无权删除该分类')
  const toDelete: number[] = [id]
  let frontier = [id]
  while (frontier.length) {
    const next = all
      .filter((c) => c.parentId !== null && frontier.includes(c.parentId))
      .map((c) => c.id)
    toDelete.push(...next)
    frontier = next
  }
  deletedCategoryIds.push(...toDelete)
  saveJSON(DELETED_CATEGORY_KEY, deletedCategoryIds)
  const overlay = loadJSON<Record<number, Partial<Paper>>>('ph_paper_overlay', {})
  const pool = [
    ...sharedPapers,
    ...privatePapers,
    ...pendingPapers,
    ...loadJSON<Paper[]>('ph_uploaded_papers', []),
  ]
  for (const paper of pool) {
    const categoryId = overlay[paper.id]?.categoryId ?? paper.categoryId
    if (categoryId !== null && toDelete.includes(categoryId))
      overlay[paper.id] = { ...overlay[paper.id], categoryId: null }
  }
  saveJSON('ph_paper_overlay', overlay)
  const names = toDelete.map((cid) => all.find((c) => c.id === cid)?.name ?? '').filter(Boolean)
  return delay(names)
}

/** 调整分类层级：不允许移动到自身或其后代分类下 */
export async function moveCategory(id: number, newParentId: number | null): Promise<boolean> {
  const target = effectiveCategories().find((category) => category.id === id)
  if (!target || (currentRole() !== 'admin' && !target.custom)) throw new Error('无权移动该分类')
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
  const papers = visiblePapers().filter((p) => p.categoryId !== null && ids.includes(p.categoryId))
  return delay(papers)
}

/* ---------------- 标签 ---------------- */

export async function getTags(): Promise<Tag[]> {
  const merged = [...mockTags, ...customTags]
    .filter(
      (tag) => currentRole() === 'admin' || !tag.custom || (tag.ownerId ?? 1) === currentUserId(),
    )
    .filter((t) => !deletedTagIds.includes(t.id))
    .map((t) => ({ ...t, ...(tagOverlay[t.id] ?? {}) }))
  const counts = countPapersByTag()
  return delay(merged.map((tag) => ({ ...tag, paperCount: counts[tag.id] ?? 0 })))
}

export async function createTag(name: string, color: string): Promise<Tag> {
  if (!name.trim() || name.trim().length > 50) throw new Error('标签名须为 1–50 字符')
  const custom = currentRole() !== 'admin'
  if ((await getTags()).some((tag) => !!tag.custom === custom && tag.name === name.trim()))
    throw new Error('标签已存在')
  const tag: Tag = {
    id: Math.max(100, ...customTags.map((t) => t.id)) + 1,
    name: name.trim(),
    color,
    custom,
    ownerId: custom ? currentUserId() : null,
  }
  customTags.push(tag)
  saveJSON(CUSTOM_TAG_KEY, customTags)
  return delay(tag)
}

export async function updateTag(id: number, name: string, color: string): Promise<boolean> {
  const target = (await getTags()).find((tag) => tag.id === id)
  if (!target || (currentRole() !== 'admin' && !target.custom)) throw new Error('无权修改该标签')
  tagOverlay[id] = { name: name.trim(), color }
  saveJSON(TAG_OVERLAY_KEY, tagOverlay)
  return delay(true)
}

export async function deleteTag(id: number): Promise<boolean> {
  const target = (await getTags()).find((tag) => tag.id === id)
  if (!target || (currentRole() !== 'admin' && !target.custom)) throw new Error('无权删除该标签')
  deletedTagIds.push(id)
  saveJSON(DELETED_TAG_KEY, deletedTagIds)
  const overlay = loadJSON<Record<number, Partial<Paper>>>('ph_paper_overlay', {})
  const pool = [
    ...sharedPapers,
    ...privatePapers,
    ...pendingPapers,
    ...loadJSON<Paper[]>('ph_uploaded_papers', []),
  ]
  for (const paper of pool) {
    const tagIds = overlay[paper.id]?.tagIds ?? paper.tagIds
    if (tagIds.includes(id))
      overlay[paper.id] = { ...overlay[paper.id], tagIds: tagIds.filter((tagId) => tagId !== id) }
  }
  saveJSON('ph_paper_overlay', overlay)
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
