import type { FavoriteRecord, ReadingRecord } from '@/types/paper'

/** 当前用户的阅读记录（个人数据，不影响其他用户） */
export const seedReadingRecords: ReadingRecord[] = [
  { paperId: 1005, status: 'reading', lastReadAt: '2026-11-02 21:40' },
  { paperId: 1020, status: 'read', lastReadAt: '2026-11-01 20:15' },
  { paperId: 1003, status: 'read', lastReadAt: '2026-10-28 15:22' },
  { paperId: 1011, status: 'read', lastReadAt: '2026-10-20 09:36' },
  { paperId: 1013, status: 'paused', lastReadAt: '2026-09-15 14:08' },
  { paperId: 1015, status: 'reading', lastReadAt: '2026-10-30 22:47' },
  { paperId: 1019, status: 'unread', lastReadAt: '2026-10-24 17:03' },
]

/** 当前用户的收藏（仅收藏共享论文，不复制文件） */
export const seedFavoriteRecords: FavoriteRecord[] = [
  { paperId: 1001, favoritedAt: '2026-10-18 09:12' },
  { paperId: 1005, favoritedAt: '2026-10-22 11:32' },
  { paperId: 1011, favoritedAt: '2026-10-06 11:28' },
  { paperId: 1019, favoritedAt: '2026-10-24 16:55' },
  { paperId: 1020, favoritedAt: '2026-10-27 09:08' },
]
