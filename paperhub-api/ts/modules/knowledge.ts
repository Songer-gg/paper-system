/**
 * 分类 / 标签管理（K1–K11，用户端与管理员端共用）
 * 对应前端：knowledgeService.ts、用户端与管理员端 CategoriesView / TagsView
 * 管理员可操作任何平台级项；普通用户仅可操作 custom=true 的自建项（服务端校验）。
 */
import type { ApiClient } from '../http'
import type { Category, CategoryDeleteResult, Paper, Tag } from '../types'

/* ---------------- 分类 ---------------- */

/** K1 GET /categories — 全量分类（扁平含 parentId 与 paperCount） */
export function getCategories(client: ApiClient): Promise<Category[]> {
  return client.get('/categories')
}

/** K2 POST /categories — 新建分类（重名 1004） */
export function createCategory(
  client: ApiClient,
  name: string,
  parentId: number | null,
): Promise<Category> {
  return client.post('/categories', { name, parentId })
}

/** K3 PATCH /categories/{id} — 重命名 */
export function renameCategory(client: ApiClient, id: number, name: string): Promise<boolean> {
  return client.patch(`/categories/${id}`, { name })
}

/** K4 DELETE /categories/{id} — 删除分类（响应返回级联删除的分类名，供前端确认展示） */
export function deleteCategory(client: ApiClient, id: number): Promise<CategoryDeleteResult> {
  return client.delete(`/categories/${id}`)
}

/** K5 POST /categories/{id}/move — 移动层级（服务端防环校验，成环 1006） */
export function moveCategory(
  client: ApiClient,
  id: number,
  parentId: number | null,
): Promise<boolean> {
  return client.post(`/categories/${id}/move`, { parentId })
}

/** K6 GET /categories/{id}/papers — 分类下论文（含后代分类） */
export function getPapersOfCategory(client: ApiClient, id: number): Promise<Paper[]> {
  return client.get(`/categories/${id}/papers`)
}

/* ---------------- 标签 ---------------- */

/** K7 GET /tags — 全量标签（含 paperCount） */
export function getTags(client: ApiClient): Promise<Tag[]> {
  return client.get('/tags')
}

/** K8 POST /tags — 新建标签 */
export function createTag(client: ApiClient, name: string, color: string): Promise<Tag> {
  return client.post('/tags', { name, color })
}

/** K9 PATCH /tags/{id} — 修改标签 */
export function updateTag(
  client: ApiClient,
  id: number,
  name: string,
  color: string,
): Promise<boolean> {
  return client.patch(`/tags/${id}`, { name, color })
}

/** K10 DELETE /tags/{id} — 删除标签（论文仅解除关联） */
export function deleteTag(client: ApiClient, id: number): Promise<boolean> {
  return client.delete(`/tags/${id}`)
}

/** K11 GET /tags/{id}/papers — 标签下论文 */
export function getPapersOfTag(client: ApiClient, id: number): Promise<Paper[]> {
  return client.get(`/tags/${id}/papers`)
}
