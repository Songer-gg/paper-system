import type { Tag } from '@/types/paper'

/** 平台标签（与论文多对多关联） */
export const mockTags: Tag[] = [
  { id: 1, name: '差分隐私', color: '#2563EB' },
  { id: 2, name: '模型压缩', color: '#7C3AED' },
  { id: 3, name: '可解释性', color: '#0891B2' },
  { id: 4, name: '注意力机制', color: '#D97706' },
  { id: 5, name: '多模态', color: '#DB2777' },
  { id: 6, name: '系统综述', color: '#16A34A' },
  { id: 7, name: '实验评估', color: '#475569' },
  { id: 8, name: '能耗优化', color: '#059669' },
  { id: 9, name: '分布式训练', color: '#2563EB' },
  { id: 10, name: '数据增强', color: '#EA580C' },
  { id: 11, name: '迁移学习', color: '#9333EA' },
  { id: 12, name: '图神经网络', color: '#0D9488' },
  { id: 13, name: '时序分析', color: '#4F46E5' },
  { id: 14, name: '故障诊断', color: '#DC2626' },
  { id: 15, name: '强化学习', color: '#2563EB' },
  { id: 16, name: '人机交互', color: '#0891B2' },
  { id: 17, name: '自动驾驶', color: '#0284C7' },
  { id: 18, name: 'SLAM', color: '#65A30D' },
  { id: 19, name: '云边协同', color: '#0369A1' },
  { id: 20, name: '大规模预训练', color: '#7C3AED' },
  { id: 21, name: '安全防护', color: '#B45309' },
  { id: 22, name: '基准测试', color: '#52525B' },
]

export function getTag(id: number): Tag | undefined {
  return mockTags.find((t) => t.id === id)
}
