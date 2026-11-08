import * as requests from '../../../paperhub-api/ts/modules/profile'
import { useMock } from '@/config/runtime'
import { apiClient } from './api'
export type MonthlyTrendPoint = { month: string; label: string; count: number }
export type NameCount = { name: string; count: number }
export function getUserStatistics() {
  return useMock
    ? import('@/mocks/services/statisticsService').then((m) => m.getUserStatistics())
    : requests.getUserStatistics(apiClient)
}
