import * as library from '../../../paperhub-api/ts/modules/library'
import { getDownloadFilename } from '../../../paperhub-api/ts/fetch-client'
import { apiClient } from './api'
import { useMock } from '@/config/runtime'
import { onSessionReset } from '@/utils/session'

const downloads = new Map<string, ReturnType<typeof setTimeout>>()
onSessionReset(() => {
  for (const [url, timer] of downloads) {
    clearTimeout(timer)
    URL.revokeObjectURL(url)
  }
  downloads.clear()
})
export async function getPaperFile(id: number): Promise<Blob> {
  if (useMock) throw new Error('Mock模式不提供PDF文件，请切换真实模式')
  return library.getPaperFile(apiClient, id)
}
export async function downloadPaper(paper: { id: number; title: string }): Promise<void> {
  if (useMock) throw new Error('Mock模式不提供PDF文件，请切换真实模式')
  const blob = await library.downloadPaper(apiClient, paper.id)
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  const name = getDownloadFilename(blob) || paper.title.replace(/[<>:"/\\|?*\x00-\x1f]/g, '_')
  link.download = name.toLowerCase().endsWith('.pdf') ? name : name + '.pdf'
  document.body.append(link)
  link.click()
  link.remove()
  downloads.set(
    url,
    setTimeout(() => {
      URL.revokeObjectURL(url)
      downloads.delete(url)
    }, 60_000),
  )
}
