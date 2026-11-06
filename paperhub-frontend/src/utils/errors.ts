import { ElMessage } from 'element-plus'

/** 区分确认框取消与请求失败；仅记录消息，避免输出请求配置中的密码或 token。 */
export function reportActionError(error: unknown): void {
  if (error === 'cancel' || error === 'close') return
  const message = error instanceof Error ? error.message : '操作失败，请稍后重试'
  console.error('PaperHub:', message)
  ElMessage.error(message)
}
