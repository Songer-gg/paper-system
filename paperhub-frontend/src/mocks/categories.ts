import type { Category } from '@/types/paper'

/** 平台分类体系（树形结构，parentId 为 null 表示根分类） */
export const mockCategories: Category[] = [
  { id: 1, name: '人工智能', parentId: null },
  { id: 2, name: '联邦学习', parentId: 1 },
  { id: 3, name: '大语言模型', parentId: 1 },
  { id: 4, name: '具身智能', parentId: 1 },
  { id: 5, name: '计算机视觉', parentId: 1 },
  { id: 6, name: '强化学习', parentId: 1 },
  { id: 7, name: '工业智能', parentId: null },
  { id: 8, name: '预测性维护', parentId: 7 },
  { id: 9, name: '工业互联网', parentId: 7 },
  { id: 10, name: '数字孪生', parentId: 7 },
  { id: 11, name: '机器人', parentId: null },
  { id: 12, name: '移动机器人', parentId: 11 },
  { id: 13, name: '机械臂控制', parentId: 11 },
  { id: 14, name: '数据隐私与安全', parentId: null },
  { id: 15, name: '边缘计算', parentId: null },
  { id: 16, name: '知识图谱', parentId: null },
]

export function getCategory(id: number): Category | undefined {
  return mockCategories.find((c) => c.id === id)
}

/** 获取分类自身及其全部后代 id（用于分类筛选时包含子分类） */
export function getCategoryDescendantIds(id: number): number[] {
  const ids = [id]
  let frontier = [id]
  while (frontier.length) {
    const next = mockCategories
      .filter((c) => c.parentId !== null && frontier.includes(c.parentId))
      .map((c) => c.id)
    ids.push(...next)
    frontier = next
  }
  return ids
}
