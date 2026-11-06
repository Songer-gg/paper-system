/**
 * 我的论文库 / 论文上传 / 详情 / 阅读器（U1–U9）
 * 对应前端：myLibraryService.ts、PaperDetailView、PaperReaderView
 */
import type { ApiClient } from "../http";
import type {
  DuplicateCheckResult,
  DuplicateCheckInput,
  Paper,
  PaperEditPayload,
  ReviewFlowAction,
  UploadPayload,
} from "../types";

/** U1 GET /papers/mine — 我的论文（私有 + 共享两组） */
export function getMyPapers(client: ApiClient): Promise<{
  privatePapers: Paper[];
  sharedPapers: Paper[];
}> {
  return client.get("/papers/mine");
}

/** U2 GET /papers/{id} — 论文详情（本人或共享库中 approved；其余 1003） */
export function getPaperForDetail(
  client: ApiClient,
  id: number,
): Promise<Paper | null> {
  return client.get(`/papers/${id}`);
}

/** U3 PATCH /papers/{id} — 编辑论文元数据（仅本人） */
export function updatePaper(
  client: ApiClient,
  id: number,
  patch: PaperEditPayload,
): Promise<Paper | null> {
  return client.patch(`/papers/${id}`, patch);
}

/** U4 DELETE /papers/{id} — 删除我的论文（级联清理收藏/阅读/笔记关联） */
export function deleteMyPaper(client: ApiClient, id: number): Promise<boolean> {
  return client.delete(`/papers/${id}`);
}

/** U5 PATCH /papers/{id}/review-flow — 共享流转：share 私转共 / unshare 取消共享 / resubmit 驳回后重新提交 */
export function changeReviewStatus(
  client: ApiClient,
  id: number,
  action: ReviewFlowAction,
  copyrightAgreed?: boolean,
): Promise<Paper | null> {
  return client.patch(`/papers/${id}/review-flow`, { action, copyrightAgreed });
}

/** U6 提交前提示；U7 和共享流转仍须服务端最终查重，客户端哈希不可信 */
export function checkDuplicate(
  client: ApiClient,
  input: DuplicateCheckInput,
): Promise<DuplicateCheckResult> {
  return client.post("/papers/duplicate-check", input);
}

/** U7 POST /papers — 上传论文（multipart：file=PDF 文件 + 其余字段为 JSON part） */
export function submitPaper(
  client: ApiClient,
  file: File,
  payload: UploadPayload,
): Promise<Paper> {
  const form = new FormData();
  form.append("file", file);
  form.append(
    "payload",
    new Blob([JSON.stringify(payload)], { type: "application/json" }),
  );
  return client.upload("/papers", form, "POST");
}

/** U8 POST /papers/{id}/download — 下载 PDF（服务端累加 downloads 后返回文件流） */
export function downloadPaper(client: ApiClient, id: number): Promise<Blob> {
  return client.download(`/papers/${id}/download`, "POST");
}

/** U9 GET /papers/{id}/file — 在线阅读器取 PDF 流（权限同 U8） */
export function getPaperFile(client: ApiClient, id: number): Promise<Blob> {
  return client.download(`/papers/${id}/file`, "GET");
}
