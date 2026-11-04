export interface MockNotification {
  id: number
  type: 'review' | 'system' | 'resource'
  title: string
  content: string
  time: string
  read: boolean
}

export const mockNotifications: MockNotification[] = [
  {
    id: 1,
    type: 'review',
    title: '审核结果通知',
    content: '您共享的论文《联邦学习中的差分隐私机制研究综述》已审核通过，现已进入共享论文库。',
    time: '2026-11-02 14:20',
    read: false,
  },
  {
    id: 2,
    type: 'system',
    title: '平台维护公告',
    content: 'PaperHub 将于本周日 02:00-04:00 进行系统升级维护，期间服务可能间歇性不可用。',
    time: '2026-11-01 09:00',
    read: false,
  },
  {
    id: 3,
    type: 'resource',
    title: '收藏论文更新',
    content: '您收藏的论文《大语言模型幻觉现象综述》被上传者更新了元数据信息。',
    time: '2026-10-30 18:32',
    read: true,
  },
]
