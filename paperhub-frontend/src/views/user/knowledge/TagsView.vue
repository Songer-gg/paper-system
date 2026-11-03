<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { Paper, Tag } from '@/types/paper'
import * as knowledge from '@/services/knowledgeService'

const router = useRouter()

const PRESET_COLORS = [
  '#2563EB', '#7C3AED', '#0891B2', '#D97706', '#DB2777',
  '#16A34A', '#475569', '#059669', '#EA580C', '#0D9488',
]

/* ---------------- 标签数据 ---------------- */
const loading = ref(true)
const tags = ref<Tag[]>([])
const counts = ref<Record<number, number>>({})
const keyword = ref('')
const selectedId = ref<number | null>(null)
const tagPapers = ref<Paper[]>([])
const papersLoading = ref(false)

async function fetchTags() {
  loading.value = true
  tags.value = await knowledge.getTags()
  counts.value = knowledge.countPapersByTag()
  loading.value = false
  if (selectedId.value && tags.value.some((t) => t.id === selectedId.value)) {
    await loadPapers(selectedId.value)
  } else {
    selectedId.value = null
  }
}

async function loadPapers(id: number) {
  papersLoading.value = true
  tagPapers.value = await knowledge.getPapersOfTag(id)
  papersLoading.value = false
}

function selectTag(tag: Tag) {
  selectedId.value = tag.id
  loadPapers(tag.id)
}

onMounted(fetchTags)

const filteredTags = computed(() => {
  const kw = keyword.value.trim().toLowerCase()
  if (!kw) return tags.value
  return tags.value.filter((t) => t.name.toLowerCase().includes(kw))
})

const selectedTag = computed(() => tags.value.find((t) => t.id === selectedId.value))

function goDetail(id: number) {
  router.push(`/user/papers/${id}`)
}

/* ---------------- 新建 / 编辑 / 删除 ---------------- */
const dialogVisible = ref(false)
const editingTag = ref<Tag | null>(null)
const form = reactive({
  name: '',
  color: PRESET_COLORS[0],
})

function openCreate() {
  editingTag.value = null
  form.name = ''
  form.color = PRESET_COLORS[Math.floor(Math.random() * PRESET_COLORS.length)]
  dialogVisible.value = true
}

function openEdit(tag: Tag) {
  editingTag.value = tag
  form.name = tag.name
  form.color = tag.color
  dialogVisible.value = true
}

async function saveDialog() {
  const name = form.name.trim()
  if (!name) {
    ElMessage.warning('请输入标签名称')
    return
  }
  if (editingTag.value) {
    await knowledge.updateTag(editingTag.value.id, name, form.color)
    ElMessage.success('标签已更新')
  } else {
    await knowledge.createTag(name, form.color)
    ElMessage.success(`标签「${name}」已创建`)
  }
  dialogVisible.value = false
  fetchTags()
}

async function handleDelete(tag: Tag) {
  const n = counts.value[tag.id] ?? 0
  try {
    await ElMessageBox.confirm(
      n
        ? `标签「${tag.name}」关联了 ${n} 篇论文，删除后关联将一并解除。确定删除吗？`
        : `确定删除标签「${tag.name}」吗？`,
      '删除标签',
      { confirmButtonText: '删除', cancelButtonText: '取消', type: 'warning' },
    )
    await knowledge.deleteTag(tag.id)
    ElMessage.success('标签已删除')
    if (selectedId.value === tag.id) selectedId.value = null
    fetchTags()
  } catch {
    /* 用户取消 */
  }
}
</script>

<template>
  <div class="ph-page">
    <div class="ph-page-header">
      <div>
        <h2 class="ph-page-title">标签管理</h2>
        <p class="ph-page-desc">
          标签与论文多对多关联，点击标签查看关联论文
        </p>
      </div>
      <div class="header-actions">
        <el-input v-model="keyword" placeholder="搜索标签" clearable style="width: 200px">
          <template #prefix><el-icon><Search /></el-icon></template>
        </el-input>
        <el-button type="primary" :icon="'Plus'" @click="openCreate">新建标签</el-button>
      </div>
    </div>

    <div class="tags-layout">
      <!-- 左侧标签卡片 -->
      <div class="ph-card tags-card">
        <div v-if="loading" v-loading="true" class="tags-loading" />
        <template v-else-if="filteredTags.length">
          <div class="tag-grid">
            <div
              v-for="t in filteredTags"
              :key="t.id"
              class="tag-card"
              :class="{ selected: t.id === selectedId }"
              :style="{
                borderColor: t.id === selectedId ? t.color : undefined,
                background: t.id === selectedId ? t.color + '14' : undefined,
              }"
              @click="selectTag(t)"
            >
              <div class="tag-head">
                <span class="tag-dot" :style="{ background: t.color }" />
                <span class="tag-name">{{ t.name }}</span>
                <span class="tag-actions" @click.stop>
                  <el-icon class="tag-action" @click="openEdit(t)"><EditPen /></el-icon>
                  <el-icon class="tag-action danger" @click="handleDelete(t)"><Delete /></el-icon>
                </span>
              </div>
              <div class="tag-count">{{ counts[t.id] ?? 0 }} 篇关联论文</div>
            </div>
          </div>
        </template>
        <el-empty v-else description="没有匹配的标签" />
      </div>

      <!-- 右侧关联论文 -->
      <div class="ph-card papers-card">
        <template v-if="selectedTag">
          <div class="papers-head">
            <div>
              <div class="papers-title">
                <span class="tag-dot" :style="{ background: selectedTag.color }" />
                {{ selectedTag.name }}
              </div>
              <div class="papers-sub">共 {{ tagPapers.length }} 篇关联论文</div>
            </div>
          </div>
          <div v-if="papersLoading" v-loading="true" class="papers-loading" />
          <template v-else-if="tagPapers.length">
            <div
              v-for="p in tagPapers"
              :key="p.id"
              class="paper-row"
              @click="goDetail(p.id)"
            >
              <div class="paper-row-main">
                <div class="paper-title ph-ellipsis">{{ p.title }}</div>
                <div class="paper-meta">
                  <span
                    class="ph-status"
                    :class="p.visibility === 'shared' ? 'ph-status--primary' : 'ph-status--info'"
                  >
                    {{ p.visibility === 'shared' ? '共享' : '私有' }}
                  </span>
                  <span>{{ p.authors.join('，') }}</span>
                  <span class="meta-dot">·</span>
                  <span>{{ p.year }} 年</span>
                </div>
              </div>
              <el-icon class="row-arrow"><ArrowRight /></el-icon>
            </div>
          </template>
          <el-empty v-else description="该标签下暂无关联论文" />
        </template>
        <el-empty v-else description="点击左侧标签，查看其关联的论文" />
      </div>
    </div>

    <!-- 新建 / 编辑标签弹窗 -->
    <el-dialog
      v-model="dialogVisible"
      :title="editingTag ? '编辑标签' : '新建标签'"
      width="440px"
      destroy-on-close
    >
      <el-form label-width="80px" label-position="left">
        <el-form-item label="标签名称" required>
          <el-input v-model="form.name" placeholder="请输入标签名称" maxlength="12" />
        </el-form-item>
        <el-form-item label="标签颜色">
          <div class="color-row">
            <span
              v-for="c in PRESET_COLORS"
              :key="c"
              class="color-dot"
              :class="{ active: form.color === c }"
              :style="{ background: c }"
              @click="form.color = c"
            />
          </div>
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
.header-actions {
  display: flex;
  gap: 12px;
}

.tags-layout {
  display: flex;
  gap: 20px;
  align-items: flex-start;
  padding-bottom: 24px;
}
.tags-card {
  flex: 1;
  min-width: 0;
  padding: 18px;
}
.tags-loading,
.papers-loading {
  height: 240px;
}

.tag-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(190px, 1fr));
  gap: 12px;
}
.tag-card {
  border: 1.5px solid var(--ph-border);
  border-radius: 12px;
  padding: 12px 14px;
  cursor: pointer;
  transition: all 0.15s;
}
.tag-card:hover {
  border-color: var(--ph-primary);
}
.tag-head {
  display: flex;
  align-items: center;
  gap: 8px;
}
.tag-dot {
  width: 10px;
  height: 10px;
  border-radius: 999px;
  flex-shrink: 0;
}
.tag-name {
  flex: 1;
  min-width: 0;
  font-size: 13.5px;
  font-weight: 600;
  color: var(--ph-text);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.tag-actions {
  display: flex;
  gap: 6px;
  opacity: 0;
  transition: opacity 0.15s;
}
.tag-card:hover .tag-actions {
  opacity: 1;
}
.tag-action {
  font-size: 14px;
  color: var(--ph-text-muted);
  cursor: pointer;
}
.tag-action:hover {
  color: var(--ph-primary);
}
.tag-action.danger:hover {
  color: var(--ph-danger);
}
.tag-count {
  margin-top: 8px;
  font-size: 11.5px;
  color: var(--ph-text-muted);
}

.papers-card {
  width: 380px;
  flex-shrink: 0;
  padding: 20px 24px;
}
.papers-head {
  padding-bottom: 14px;
  border-bottom: 1px solid var(--ph-border);
}
.papers-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 16px;
  font-weight: 700;
  color: var(--ph-text);
}
.papers-sub {
  font-size: 12.5px;
  color: var(--ph-text-muted);
  margin-top: 3px;
}
.paper-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 13px 4px;
  border-bottom: 1px solid #f3f4f6;
  cursor: pointer;
}
.paper-row:last-child {
  border-bottom: none;
}
.paper-row-main {
  flex: 1;
  min-width: 0;
}
.paper-title {
  font-size: 14px;
  font-weight: 600;
  color: var(--ph-text);
}
.paper-row:hover .paper-title {
  color: var(--ph-primary);
}
.paper-meta {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12.5px;
  color: var(--ph-text-secondary);
  margin-top: 5px;
  flex-wrap: wrap;
}
.meta-dot {
  color: #d4d7dc;
}
.row-arrow {
  color: #c7cdd8;
  flex-shrink: 0;
}

.color-row {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
}
.color-dot {
  width: 22px;
  height: 22px;
  border-radius: 999px;
  cursor: pointer;
  border: 2px solid transparent;
  transition: transform 0.12s;
}
.color-dot:hover {
  transform: scale(1.15);
}
.color-dot.active {
  border-color: var(--ph-text);
  box-shadow: 0 0 0 2px #fff inset;
}

@media (max-width: 1080px) {
  .tags-layout {
    flex-direction: column;
  }
  .papers-card {
    width: 100%;
  }
}
</style>
