import type { DuplicatePair } from '@/types/admin'

/**
 * 疑似重复论文对 Mock（仅演示比对结果，不实现真实查重算法）。
 * newPaperId 指向待审核池（3001-3005），originalPaperId 指向共享库。
 */
export const mockDuplicatePairs: DuplicatePair[] = [
  {
    id: 7001,
    originalPaperId: 1002,
    newPaperId: 3002,
    doiMatch: false,
    titleSimilarity: 87,
    fileHashMatch: false,
    status: 'pending',
    submittedAt: '2026-11-02 09:18',
  },
  {
    id: 7002,
    originalPaperId: 1008,
    newPaperId: 3003,
    doiMatch: true,
    titleSimilarity: 64,
    fileHashMatch: false,
    status: 'pending',
    submittedAt: '2026-11-01 17:42',
  },
  {
    id: 7003,
    originalPaperId: 1011,
    newPaperId: 3004,
    doiMatch: false,
    titleSimilarity: 92,
    fileHashMatch: true,
    status: 'pending',
    submittedAt: '2026-10-31 10:05',
  },
  {
    id: 7004,
    originalPaperId: 1013,
    newPaperId: 3005,
    doiMatch: false,
    titleSimilarity: 41,
    fileHashMatch: false,
    status: 'distinct',
    submittedAt: '2026-10-28 16:30',
    handledAt: '2026-10-29 09:12',
    handlerNote: '仅研究领域相近，方法与实验均不同，判定为不同论文。',
  },
]
