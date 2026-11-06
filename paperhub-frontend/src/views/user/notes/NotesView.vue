<script setup lang="ts">
import { reportActionError } from '@/utils/errors'
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { Note, Paper } from '@/types/paper'
import * as userData from '@/services/userDataService'
import { getVisiblePapers } from '@/services/knowledgeService'

const router = useRouter()

/* ---------------- 数据加载 ---------------- */
const loading = ref(true)
const notes = ref<Note[]>([])
const paperMap = ref<Map<number, Paper>>(new Map())

async function fetchNotes() {
  loading.value = true
  try {
    const [list, papers] = await Promise.all([userData.getNotes(), getVisiblePapers()])
    notes.value = list
    paperMap.value = new Map(papers.map((p) => [p.id, p]))
    if (selectedId.value && !list.some((n) => n.id === selectedId.value)) {
      selectedId.value = null
    }
  } finally {
    loading.value = false
  }
}
onMounted(fetchNotes)

/* ---------------- 筛选与排序 ---------------- */
const keyword = ref('')
const sortBy = ref<'updated' | 'created'>('updated')

const filteredNotes = computed(() => {
  let list = [...notes.value]
  const kw = keyword.value.trim().toLowerCase()
  if (kw) {
    list = list.filter(
      (n) =>
        n.title.toLowerCase().includes(kw) ||
        (n.paperTitle ?? paperMap.value.get(n.paperId)?.title)?.toLowerCase().includes(kw),
    )
  }
  list.sort((a, b) =>
    (sortBy.value === 'updated' ? b.updatedAt : b.createdAt).localeCompare(
      sortBy.value === 'updated' ? a.updatedAt : a.createdAt,
    ),
  )
  return list
})

/* ---------------- 选中预览 ---------------- */
const selectedId = ref<number | null>(null)
const selectedNote = computed(() => notes.value.find((n) => n.id === selectedId.value))
const selectedPaper = computed(() =>
  selectedNote.value ? paperMap.value.get(selectedNote.value.paperId) : undefined,
)

function selectNote(id: number) {
  selectedId.value = id
}

/* ---------------- 简易 Markdown 渲染 ---------------- */
function escapeHtml(s: string): string {
  return s
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
}

function renderMarkdown(src: string): string {
  const lines = escapeHtml(src).split('\n')
  const out: string[] = []
  let listOpen = false
  const inline = (s: string) =>
    s.replace(/\*\*([^*]+)\*\*/g, '<strong>$1</strong>').replace(/`([^`]+)`/g, '<code>$1</code>')
  const closeList = () => {
    if (listOpen) {
      out.push('</ul>')
      listOpen = false
    }
  }
  for (const raw of lines) {
    const line = raw.trimEnd()
    if (/^#{3}\s+/.test(line)) {
      closeList()
      out.push(`<h4>${inline(line.replace(/^#{3}\s+/, ''))}</h4>`)
    } else if (/^#{1,2}\s+/.test(line)) {
      closeList()
      out.push(`<h3>${inline(line.replace(/^#{1,2}\s+/, ''))}</h3>`)
    } else if (/^[-*]\s+/.test(line)) {
      if (!listOpen) {
        out.push('<ul>')
        listOpen = true
      }
      out.push(`<li>${inline(line.replace(/^[-*]\s+/, ''))}</li>`)
    } else if (/^&gt;\s*/.test(line)) {
      closeList()
      out.push(`<blockquote>${inline(line.replace(/^&gt;\s*/, ''))}</blockquote>`)
    } else if (!line) {
      closeList()
    } else {
      closeList()
      out.push(`<p>${inline(line)}</p>`)
    }
  }
  closeList()
  return out.join('\n')
}

const renderedContent = computed(() =>
  selectedNote.value ? renderMarkdown(selectedNote.value.content) : '',
)

/* ---------------- 新建 / 编辑 / 删除 ---------------- */
const papers = computed(() => [...paperMap.value.values()])

const dialogVisible = ref(false)
const editingNote = ref<Note | null>(null)
const form = reactive({
  title: '',
  paperId: null as number | null,
  content: '',
})

function openCreate() {
  editingNote.value = null
  form.title = ''
  form.paperId = null
  form.content = ''
  dialogVisible.value = true
}

function openEdit(note: Note) {
  editingNote.value = note
  form.title = note.title
  form.paperId = note.paperId
  form.content = note.content
  dialogVisible.value = true
}

async function saveDialog() {
  if (!form.title.trim()) {
    ElMessage.warning('请输入笔记标题')
    return
  }
  if (!form.paperId) {
    ElMessage.warning('请选择关联论文')
    return
  }
  if (!form.content.trim()) {
    ElMessage.warning('请输入笔记内容')
    return
  }
  if (editingNote.value) {
    await userData.updateNote(editingNote.value.id, {
      title: form.title,
      ...(form.paperId !== editingNote.value.paperId ? { paperId: form.paperId } : {}),
      content: form.content,
    })
    ElMessage.success('笔记已更新')
  } else {
    await userData.addNote({
      title: form.title,
      paperId: form.paperId,
      content: form.content,
    })
    ElMessage.success('笔记已创建')
  }
  dialogVisible.value = false
  selectedId.value = null
  fetchNotes()
}

async function handleDelete(note: Note) {
  try {
    await ElMessageBox.confirm(`确定删除笔记「${note.title}」吗？删除后不可恢复。`, '删除笔记', {
      confirmButtonText: '删除',
      cancelButtonText: '取消',
      type: 'warning',
    })
    await userData.deleteNote(note.id)
    ElMessage.success('笔记已删除')
    if (selectedId.value === note.id) selectedId.value = null
    fetchNotes()
  } catch (error) {
    reportActionError(error)
  }
}

function goPaper(id: number) {
  router.push(`/user/papers/${id}`)
}

function fmtTime(t: string): string {
  return t.slice(0, 16)
}
</script>

<template>
  <div class="ph-page">
    <div class="ph-page-header">
      <div>
        <h2 class="ph-page-title">阅读笔记</h2>
        <p class="ph-page-desc">记录论文阅读心得，支持 Markdown 排版</p>
      </div>
      <el-button type="primary" :icon="'Plus'" @click="openCreate">新建笔记</el-button>
    </div>

    <div class="notes-layout">
      <!-- 左侧笔记列表 -->
      <div class="ph-card list-card">
        <div class="list-toolbar">
          <el-input v-model="keyword" placeholder="搜索笔记 / 论文标题" clearable>
            <template #prefix
              ><el-icon><Search /></el-icon
            ></template>
          </el-input>
          <el-radio-group v-model="sortBy" size="small">
            <el-radio-button value="updated">最近更新</el-radio-button>
            <el-radio-button value="created">创建时间</el-radio-button>
          </el-radio-group>
        </div>

        <div v-if="loading" v-loading="true" class="list-loading" />
        <template v-else-if="filteredNotes.length">
          <div
            v-for="n in filteredNotes"
            :key="n.id"
            class="note-item"
            :class="{ selected: n.id === selectedId }"
            @click="selectNote(n.id)"
          >
            <div class="note-item-title ph-ellipsis">{{ n.title }}</div>
            <div class="note-item-paper ph-ellipsis">
              <el-icon><Document /></el-icon>
              {{
                paperMap.get(n.paperId)?.title ?? `${n.paperTitle ?? '原论文'}（已删除或不可访问）`
              }}
            </div>
            <div class="note-item-time">更新于 {{ fmtTime(n.updatedAt) }}</div>
          </div>
        </template>
        <el-empty v-else description="暂无笔记" />
      </div>

      <!-- 右侧笔记预览 -->
      <div class="ph-card preview-card">
        <template v-if="selectedNote">
          <div class="preview-head">
            <div>
              <div class="preview-title">{{ selectedNote.title }}</div>
              <div class="preview-meta">
                关联论文：
                <el-link
                  :disabled="!selectedPaper"
                  type="primary"
                  underline="never"
                  @click="goPaper(selectedNote.paperId)"
                >
                  {{
                    selectedPaper?.title ??
                    `${selectedNote.paperTitle ?? '原论文'}（已删除或不可访问）`
                  }}
                </el-link>
                <span class="preview-time">
                  · 创建于 {{ fmtTime(selectedNote.createdAt) }}，更新于
                  {{ fmtTime(selectedNote.updatedAt) }}
                </span>
              </div>
            </div>
            <div class="preview-actions">
              <el-button text :icon="'EditPen'" @click="openEdit(selectedNote)">编辑</el-button>
              <el-button
                text
                :icon="'Delete'"
                class="danger-btn"
                @click="handleDelete(selectedNote)"
              >
                删除
              </el-button>
            </div>
          </div>
          <!-- eslint-disable-next-line vue/no-v-html -->
          <div class="note-content md-body" v-html="renderedContent" />
        </template>
        <el-empty v-else description="点击左侧笔记查看内容" />
      </div>
    </div>

    <!-- 新建 / 编辑弹窗 -->
    <el-dialog
      v-model="dialogVisible"
      :title="editingNote ? '编辑笔记' : '新建笔记'"
      width="560px"
      destroy-on-close
    >
      <el-form label-position="top">
        <el-form-item label="笔记标题" required>
          <el-input v-model="form.title" placeholder="请输入笔记标题" maxlength="80" />
        </el-form-item>
        <el-form-item label="关联论文" required>
          <el-select
            v-model="form.paperId"
            filterable
            placeholder="搜索并选择关联论文"
            style="width: 100%"
          >
            <el-option v-for="p in papers" :key="p.id" :value="p.id" :label="p.title" />
          </el-select>
        </el-form-item>
        <el-form-item label="笔记内容（支持 Markdown）" required>
          <el-input
            v-model="form.content"
            type="textarea"
            :rows="8"
            placeholder="## 标题&#10;- 列表项&#10;**加粗** 与 `代码`&#10;> 引用"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="saveDialog">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.notes-layout {
  display: flex;
  gap: 20px;
  align-items: flex-start;
  padding-bottom: 24px;
}
.list-card {
  width: 340px;
  flex-shrink: 0;
  padding: 16px;
}
.list-toolbar {
  display: flex;
  flex-direction: column;
  gap: 10px;
  margin-bottom: 12px;
}
.list-loading {
  height: 240px;
}

.note-item {
  border: 1.5px solid var(--ph-border);
  border-radius: 10px;
  padding: 12px 14px;
  cursor: pointer;
  transition: all 0.15s;
}
.note-item + .note-item {
  margin-top: 10px;
}
.note-item:hover {
  border-color: var(--ph-primary);
}
.note-item.selected {
  border-color: var(--ph-primary);
  background: var(--ph-primary-light);
}
.note-item-title {
  font-size: 13.5px;
  font-weight: 600;
  color: var(--ph-text);
}
.note-item-paper {
  display: flex;
  align-items: center;
  gap: 5px;
  font-size: 12px;
  color: var(--ph-text-muted);
  margin-top: 5px;
}
.note-item-time {
  font-size: 11px;
  color: var(--ph-text-muted);
  margin-top: 6px;
}

.preview-card {
  flex: 1;
  min-width: 0;
  padding: 24px 28px;
}
.preview-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  padding-bottom: 14px;
  border-bottom: 1px solid var(--ph-border);
}
.preview-title {
  font-size: 18px;
  font-weight: 700;
  color: var(--ph-text);
}
.preview-meta {
  display: flex;
  align-items: center;
  gap: 4px;
  flex-wrap: wrap;
  font-size: 12.5px;
  color: var(--ph-text-secondary);
  margin-top: 8px;
}
.preview-actions {
  flex-shrink: 0;
  display: flex;
}
.danger-btn {
  color: var(--ph-danger);
}

.note-content {
  margin-top: 18px;
}
.md-body :deep(h3) {
  font-size: 16px;
  font-weight: 700;
  color: var(--ph-text);
  margin: 18px 0 10px;
}
.md-body :deep(h4) {
  font-size: 14.5px;
  font-weight: 600;
  color: var(--ph-text);
  margin: 14px 0 8px;
}
.md-body :deep(p) {
  font-size: 14px;
  line-height: 1.9;
  color: var(--ph-text-secondary);
  margin: 8px 0;
}
.md-body :deep(ul) {
  padding-left: 22px;
  margin: 8px 0;
}
.md-body :deep(li) {
  font-size: 14px;
  line-height: 1.8;
  color: var(--ph-text-secondary);
}
.md-body :deep(blockquote) {
  border-left: 3px solid var(--ph-primary);
  background: var(--ph-bg);
  padding: 8px 14px;
  margin: 10px 0;
  border-radius: 0 8px 8px 0;
  font-size: 13.5px;
  color: var(--ph-text-secondary);
}
.md-body :deep(code) {
  background: #eef1f6;
  border-radius: 4px;
  padding: 1px 6px;
  font-size: 13px;
  color: #d6336c;
}
.md-body :deep(strong) {
  color: var(--ph-text);
}

@media (max-width: 1080px) {
  .notes-layout {
    flex-direction: column;
  }
  .list-card {
    width: 100%;
  }
}
</style>
