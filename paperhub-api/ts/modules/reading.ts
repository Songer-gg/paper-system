/**
 * 收藏 / 阅读管理 / 阅读笔记（R1–R9）
 * 对应前端：userDataService.ts、myLibraryService.addNote、ReadingView / NotesView
 * /me 仅指 token 本人；管理员查看他人数据使用 M26–M28，且须敏感访问审计。
 */
import type { ApiClient } from "../http";
import type {
  FavoriteRecord,
  Note,
  NoteInput,
  ReadingRecord,
  ReadingStatus,
} from "../types";

/* ---------------- 收藏 ---------------- */

/** R1 GET /me/favorites — 我的收藏（只返回当前仍可见的共享论文） */
export function getFavorites(client: ApiClient): Promise<FavoriteRecord[]> {
  return client.get("/me/favorites");
}

/** R2 POST /papers/{id}/favorite — 切换收藏状态 */
export function toggleFavorite(
  client: ApiClient,
  paperId: number,
  favorited?: boolean,
): Promise<{ paperId: number; favorited: boolean }> {
  return client.post(`/papers/${paperId}/favorite`, { favorited });
}

/* ---------------- 阅读管理 ---------------- */

/** R3 GET /me/reading-records — 我的阅读记录 */
export function getReadingRecords(client: ApiClient): Promise<ReadingRecord[]> {
  return client.get("/me/reading-records");
}

/** R4 POST /papers/{id}/reading — 加入阅读管理（初始 unread） */
export function addToReading(
  client: ApiClient,
  paperId: number,
): Promise<ReadingRecord> {
  return client.post(`/papers/${paperId}/reading`);
}

/** R5 PATCH /me/reading-records/{paperId} — 更新阅读状态 */
export function updateReadingStatus(
  client: ApiClient,
  paperId: number,
  status: ReadingStatus,
): Promise<ReadingRecord> {
  return client.patch(`/me/reading-records/${paperId}`, { status });
}

/* ---------------- 阅读笔记 ---------------- */

/** R6 GET /me/notes — 我的笔记（可按 paperId / keyword 过滤） */
export function getNotes(
  client: ApiClient,
  params?: { paperId?: number; keyword?: string },
): Promise<Note[]> {
  return client.get("/me/notes", params);
}

/** R7 POST /me/notes — 新建笔记（详情页快捷笔记与笔记页共用） */
export function addNote(client: ApiClient, input: NoteInput): Promise<Note> {
  return client.post("/me/notes", input);
}

/** R8 PATCH /me/notes/{id} — 修改笔记 */
export function updateNote(
  client: ApiClient,
  id: number,
  patch: { title?: string; paperId?: number; content?: string },
): Promise<Note> {
  return client.patch(`/me/notes/${id}`, patch);
}

/** R9 DELETE /me/notes/{id} — 删除笔记 */
export function deleteNote(client: ApiClient, id: number): Promise<boolean> {
  return client.delete(`/me/notes/${id}`);
}
