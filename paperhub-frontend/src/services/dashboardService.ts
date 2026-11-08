import * as requests from '../../../paperhub-api/ts/modules/profile'
import * as admin from '../../../paperhub-api/ts/modules/admin'
import { useMock } from '@/config/runtime'
import { apiClient } from './api'
export type { UserDashboard, AdminDashboard } from '@/mocks/services/dashboardService'
export function getUserDashboard() {
  return useMock
    ? import('@/mocks/services/dashboardService').then((m) => m.getUserDashboard())
    : requests.getUserDashboard(apiClient)
}
export function getAdminDashboard() {
  return useMock
    ? import('@/mocks/services/dashboardService').then((m) => m.getAdminDashboard())
    : admin.getAdminDashboard(apiClient)
}
