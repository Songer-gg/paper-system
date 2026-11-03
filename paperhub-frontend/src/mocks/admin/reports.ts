import type { ReportItem } from '@/types/admin'

/** 举报列表 Mock（id 9001 起） */
export const mockReports: ReportItem[] = [
  {
    id: 9001,
    paperId: 1024,
    reporterId: 3,
    type: 'copyright',
    reason: '该论文为内部技术报告，未经授权共享至平台。',
    createdAt: '2026-10-22 14:36',
    status: 'resolved',
    result: '核实存在版权争议，已下架该论文并通知上传者。',
    handledAt: '2026-10-22 15:47',
  },
  {
    id: 9002,
    paperId: 1015,
    reporterId: 5,
    type: 'content',
    reason: '第 3 节公式推导存在明显笔误，可能误导读者。',
    createdAt: '2026-11-01 11:02',
    status: 'pending',
  },
  {
    id: 9003,
    paperId: 3002,
    reporterId: 2,
    type: 'duplicate',
    reason: '与共享库《基于图神经网络的联邦学习聚合优化方法》内容高度重复。',
    createdAt: '2026-11-02 09:40',
    status: 'pending',
  },
  {
    id: 9004,
    paperId: 1019,
    reporterId: 6,
    type: 'other',
    reason: '摘要中出现商业推广链接，疑似广告内容。',
    createdAt: '2026-10-30 20:15',
    status: 'pending',
  },
]
