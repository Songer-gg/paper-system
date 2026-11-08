import type { PaperMetadata } from '../../../paperhub-api/ts/types'
import { onUnmounted, ref } from 'vue'
import { useMock } from '@/config/runtime'
import { parsePaperMetadata, lookupPaperMetadata } from '@/services/myLibraryService'

interface MetadataForm {
  title: string
  authors: string
  abstract: string
  doi: string
  journal: string
  year: number | null
  keywords: string
}
type TextField = Exclude<keyof MetadataForm, 'year'>
const labels: Record<keyof MetadataForm, string> = {
  title: '题目',
  authors: '作者',
  abstract: '摘要',
  doi: 'DOI',
  journal: '期刊',
  year: '年份',
  keywords: '关键词',
}

/** Fill empty fields only; discard stale results and clear untouched automatic values on file changes. */
export function usePaperMetadata(form: MetadataForm) {
  const parsing = ref(false)
  const message = ref('')
  const warning = ref(false)
  const result = ref<PaperMetadata | null>(null)
  const networkEnabled = ref(true)
  const phase = ref<'local' | 'network' | 'idle'>('idle')
  const automatic = new Map<TextField, string>()
  let automaticYear: number | null = null
  let generation = 0

  function reset() {
    generation++
    parsing.value = false
    phase.value = 'idle'
    for (const [field, value] of automatic) if (form[field] === value) form[field] = ''
    if (automaticYear !== null && form.year === automaticYear) form.year = null
    automatic.clear()
    automaticYear = null
    result.value = null
    message.value = ''
    warning.value = false
  }

  function protectedFields() {
    return new Set(
      (Object.keys(labels) as (keyof MetadataForm)[]).filter((field) => {
        if (field === 'year') return form.year !== null && form.year !== automaticYear
        return !!form[field].trim() && form[field] !== automatic.get(field)
      }),
    )
  }
  function apply(
    parsed: PaperMetadata,
    protectedAtStart: Set<keyof MetadataForm>,
    replaceAutomatic: boolean,
    filled: Set<string>,
  ) {
    const values: Record<TextField, string> = {
      title: parsed.title,
      authors: parsed.authors.join('，'),
      abstract: parsed.abstract,
      doi: parsed.doi,
      journal: parsed.journal,
      keywords: parsed.keywords.join('，'),
    }
    for (const field of Object.keys(values) as TextField[]) {
      if (
        !protectedAtStart.has(field) &&
        values[field] &&
        (!form[field].trim() || (replaceAutomatic && form[field] === automatic.get(field)))
      ) {
        form[field] = values[field]
        automatic.set(field, values[field])
        filled.add(labels[field])
      }
    }
    if (
      !protectedAtStart.has('year') &&
      parsed.year !== null &&
      (form.year === null || (replaceAutomatic && form.year === automaticYear))
    ) {
      form.year = parsed.year
      automaticYear = parsed.year
      filled.add(labels.year)
    }
  }
  function describe(filled: Set<string>, notes: string[], online = false) {
    const currentNotes = notes.filter((note) => {
      if (
        note.includes('部分论文信息') &&
        form.title.trim() &&
        form.authors.trim() &&
        form.abstract.trim()
      )
        return false
      if (note.includes('未提供可用摘要') && form.abstract.trim()) return false
      return true
    })
    message.value =
      (filled.size
        ? `已自动填写：${[...filled].join('、')}。请核对后提交。`
        : '已保留你填写的内容；缺失信息请手动补充。') +
      (online ? ' 联网来源：Crossref。' : '') +
      (currentNotes.length ? ` ${currentNotes.join('；')}` : '')
    warning.value = currentNotes.length > 0
  }
  async function enrich(
    current: number,
    protectedAtStart: Set<keyof MetadataForm>,
    filled: Set<string>,
    notes: string[],
    automaticRequest: boolean,
  ) {
    const requested = form.doi.trim()
    if (!requested) return false
    phase.value = 'network'
    message.value = '正在按DOI联网补全，仅向Crossref发送DOI，不发送论文正文。'
    const remote = await lookupPaperMetadata(requested)
    if (
      current !== generation ||
      form.doi.trim() !== requested ||
      (automaticRequest && !networkEnabled.value)
    )
      return false
    apply(remote, protectedAtStart, true, filled)
    notes.push(...remote.warnings)
    if (remote.lookupStatus === 'found' && result.value) {
      result.value = {
        ...result.value,
        sources: { ...result.value.sources, ...remote.sources },
        lookupStatus: remote.lookupStatus,
      }
    }
    return remote.lookupStatus === 'found'
  }
  async function parse(file: File) {
    generation++
    message.value = ''
    warning.value = false
    if (useMock || file.size > 20 * 1024 * 1024) {
      warning.value = true
      message.value = useMock
        ? 'Mock模式不提供自动识别，请手动填写'
        : '自动识别支持20MiB以内的PDF，此文件可手动填写后提交'
      return
    }
    const current = generation
    const protectedAtStart = protectedFields()
    const filled = new Set<string>()
    const notes: string[] = []
    parsing.value = true
    phase.value = 'local'
    try {
      const parsed = await parsePaperMetadata(file)
      if (current !== generation) return
      result.value = parsed
      apply(parsed, protectedAtStart, false, filled)
      notes.push(...parsed.warnings)
      let online = false
      if (networkEnabled.value && form.doi.trim())
        online = await enrich(current, protectedAtStart, filled, notes, true)
      if (current === generation) describe(filled, notes, online)
    } catch (error) {
      if (current !== generation) return
      warning.value = true
      message.value = `${error instanceof Error ? error.message : '论文信息识别失败'}。有效PDF可手动填写后提交。`
    } finally {
      if (current === generation) {
        parsing.value = false
        phase.value = 'idle'
      }
    }
  }

  async function lookup() {
    if (parsing.value) return
    if (!form.doi.trim()) {
      warning.value = true
      message.value = '请先填写DOI'
      return
    }
    const current = ++generation
    const filled = new Set<string>(),
      notes: string[] = []
    parsing.value = true
    try {
      const online = await enrich(current, protectedFields(), filled, notes, false)
      if (current === generation) describe(filled, notes, online)
    } catch (error) {
      if (current !== generation) return
      warning.value = true
      message.value = `${error instanceof Error ? error.message : '联网补全失败'}。已保留现有内容，可手动填写。`
    } finally {
      if (current === generation) {
        parsing.value = false
        phase.value = 'idle'
      }
    }
  }
  function skip() {
    generation++
    parsing.value = false
    phase.value = 'idle'
    message.value = '已停止等待，现有识别结果已保留，可手动补充后提交。'
  }

  onUnmounted(reset)
  return { parsing, message, warning, result, networkEnabled, phase, parse, lookup, skip, reset }
}
