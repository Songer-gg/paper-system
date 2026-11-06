/** 管理员端类型定义（规划书第六章） */

/** 审核记录 */
export interface ReviewRecord {
  id: number
  paperId: number
  paperTitle: string
  uploaderId: number
  uploaderName?: string
  /** approved 通过 / rejected 驳回 */
  action: 'approved' | 'rejected'
  comment: string
  reviewerId: number
  reviewerName?: string
  reviewedAt: string
}

/** 疑似重复论文对（仅模拟比对结果，不实现真实查重算法） */
export interface DuplicatePair {
  id: number
  originalPaperId: number
  newPaperId: number
  /** DOI 比对：true 完全一致 */
  doiMatch: boolean
  /** 标题相似度（0-100） */
  titleSimilarity: number
  /** 文件哈希比对：true 完全一致 */
  fileHashMatch: boolean
  /** pending 待处理 / duplicate 确认重复 / distinct 确认不同 / linked 已关联 */
  status: 'pending' | 'duplicate' | 'distinct' | 'linked'
  submittedAt: string
  handledAt?: string
  handlerNote?: string
}

/** 举报类型 */
export type ReportType = 'copyright' | 'content' | 'duplicate' | 'other'

export const reportTypeLabels: Record<ReportType, string> = {
  copyright: '版权或授权问题',
  content: '内容错误',
  duplicate: '重复资源',
  other: '其他违规内容',
}

/** 举报 */
export interface ReportItem {
  id: number
  paperId: number
  reporterId: number
  reporterName?: string
  type: ReportType
  reason: string
  createdAt: string
  /** pending 待处理 / resolved 已处理 */
  status: 'pending' | 'resolved'
  result?: string
  handledAt?: string
}

/** 重复论文处理记录 */
export interface DuplicateRecord {
  id: number
  pairId: number
  paperTitle: string
  /** duplicate / distinct / linked */
  action: 'duplicate' | 'distinct' | 'linked'
  note: string
  handlerId: number
  handlerName?: string
  handledAt: string
}

/** 操作日志 */
export interface OperationLog {
  id: number
  /** 操作用户名 */
  userName: string
  /** 操作类型 */
  action: string
  /** 操作对象 */
  target: string
  /** 成功 / 失败 */
  result: '成功' | '失败'
  time: string
}
