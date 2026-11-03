import type { OperationLog } from '@/types/admin'

/** 操作日志 Mock（id 6001 起） */
export const mockLogs: OperationLog[] = [
  { id: 6001, userName: '平台管理员', action: '论文审核', target: '《面向联邦学习的隐私保护梯度压缩方法》', result: '成功', time: '2026-11-01 10:24' },
  { id: 6002, userName: '平台管理员', action: '论文审核', target: '《基于对比学习的工业图像缺陷检测方法》', result: '成功', time: '2026-10-30 09:41' },
  { id: 6003, userName: '平台管理员', action: '论文下架', target: '《面向智能电厂的多模态监测数据治理方法》', result: '成功', time: '2026-10-22 15:47' },
  { id: 6004, userName: '平台管理员', action: '举报处理', target: '举报单 #9001', result: '成功', time: '2026-10-22 15:47' },
  { id: 6005, userName: '平台管理员', action: '用户禁用', target: '孙无涯', result: '成功', time: '2026-10-19 11:30' },
  { id: 6006, userName: '平台管理员', action: '用户恢复', target: '孙无涯', result: '成功', time: '2026-10-21 09:05' },
  { id: 6007, userName: '平台管理员', action: '重置密码', target: '孙无涯', result: '成功', time: '2026-10-21 09:06' },
  { id: 6008, userName: '平台管理员', action: '标签管理', target: '新建标签「大模型」', result: '成功', time: '2026-10-18 14:22' },
  { id: 6009, userName: '平台管理员', action: '分类管理', target: '重命名分类「计算机视觉」', result: '成功', time: '2026-10-17 16:40' },
  { id: 6010, userName: '平台管理员', action: '重复论文处理', target: '疑似重复对 #7004', result: '成功', time: '2026-10-29 09:12' },
]
