<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import * as knowledge from '@/services/knowledgeService'
import * as admin from '@/services/adminService'
import type { Paper, Tag } from '@/types/paper'

const loading = ref(true)
const tags = ref<Tag[]>([])
const keyword = ref('')
const selectedId = ref<number | null>(null)

/** 全平台共享池中的标签使用数量 */
const counts = ref<Record<number, number>>({})

async function fetchAll() {
  loading.value = true
  const [list] = await Promise.all([knowledge.getTags()])
  tags.value = list
  /* 使用数量统计改为读取共享池 */
  const papers = await fetchSharedPapers()
  const count: Record<number, number> = {}
  for (const p of papers) {
    for (const tid of p.tagIds) count[tid] = (count[tid] ?? 0) + 1
  }
  counts.value = count
  loading.value = false
}

/** 拉取全平台共享论文（含各审核状态），供标签计数 */
async function fetchSharedPapers(): Promise<Paper[]> {
  return admin.getSharedPapers({
    title: '',
    doi: '',
    uploader: '',
    categoryId: null,
    status: 'all',
  })
}
onMounted(fetchAll)

const filteredTags = computed(() => {
  const kw = keyword.value.trim().toLowerCase()
  return kw ? tags.value.filter((t) => t.name.toLowerCase().includes(kw)) : tags.value
})

const selectedTag = computed(() =>
  selectedId.value !== null ? (tags.value.find((t) => t.id === selectedId.value) ?? null) : null,
)
const selectedPapers = ref<Paper[]>([])

async function selectTag(t: Tag) {
  selectedId.value = t.id
  /* 从共享池中筛选该标签的论文 */
  const papers = await fetchSharedPapers()
  selectedPapers.value = papers.filter((p) => p.tagIds.includes(t.id))
}

/* ---------------- 新建 / 编辑 / 删除 ---------------- */
const palette = [
  '#2563EB',
  '#7C3AED',
  '#0891B2',
  '#D97706',
  '#DB2777',
  '#16A34A',
  '#DC2626',
  '#475569',
]
const dialog = ref(false)
const dialogMode = ref<'create' | 'edit'>('create')
const tagName = ref('')
const tagColor = ref(palette[0])
const editId = ref<number | null>(null)

function openCreate() {
  dialogMode.value = 'create'
  tagName.value = ''
  tagColor.value = palette[0]
  dialog.value = true
}

function openEdit(t: Tag) {
  dialogMode.value = 'edit'
  editId.value = t.id
  tagName.value = t.name
  tagColor.value = t.color
  dialog.value = true
}

async function submitDialog() {
  const name = tagName.value.trim()
  if (!name) {
    ElMessage.warning('请输入标签名称')
    return
  }
  if (dialogMode.value === 'create') {
    if (tags.value.some((t) => t.name === name)) {
      ElMessage.error('标签名称已存在')
      return
    }
    await knowledge.createTag(name, tagColor.value)
    ElMessage.success('标签已创建')
  } else if (editId.value !== null) {
    await knowledge.updateTag(editId.value, name, tagColor.value)
    ElMessage.success('标签已更新')
  }
  dialog.value = false
  fetchAll()
}

async function removeTag(t: Tag) {
  try {
    const used = counts.value[t.id] ?? 0
    await ElMessageBox.confirm(
      used
        ? `标签「${t.name}」当前被 ${used} 篇论文使用，删除后将从这些论文上移除，确定删除吗？`
        : `确定删除标签「${t.name}」吗？`,
      '删除标签',
      { confirmButtonText: '删除', cancelButtonText: '取消', type: 'warning' },
    )
    await knowledge.deleteTag(t.id)
    ElMessage.success('标签已删除')
    if (selectedId.value === t.id) {
      selectedId.value = null
      selectedPapers.value = []
    }
    fetchAll()
  } catch {
    /* 用户取消 */
  }
}
</script>

<template>
  <div class="ph-page">
    <div class="ph-page-header">
      <div>
        <h2 class="ph-page-title">平台标签管理</h2>
        <p class="ph-page-desc">维护平台统一标签库（使用数量统计范围为全平台共享论文）</p>
      </div>
      <el-button type="primary" @click="openCreate">新建标签</el-button>
    </div>

    <div v-if="loading" v-loading="true" class="page-loading" />

    <div v-else class="two-col">
      <!-- 左：标签列表 -->
      <div class="ph-card tags-card">
        <el-input v-model="keyword" placeholder="搜索标签" clearable class="tag-search">
          <template #prefix
            ><el-icon><Search /></el-icon
          ></template>
        </el-input>
        <div class="tag-grid">
          <div
            v-for="t in filteredTags"
            :key="t.id"
            class="tag-item"
            :class="{ 'is-selected': t.id === selectedId }"
            @click="selectTag(t)"
          >
            <span class="tag-dot" :style="{ background: t.color }" />
            <span class="tag-name">{{ t.name }}</span>
            <span class="tag-count">{{ counts[t.id] ?? 0 }}</span>
            <span class="tag-actions" @click.stop>
              <el-button text size="small" @click="openEdit(t)">编辑</el-button>
              <el-button text type="danger" size="small" @click="removeTag(t)">删除</el-button>
            </span>
          </div>
        </div>
        <el-empty v-if="!filteredTags.length" description="没有匹配的标签" :image-size="80" />
      </div>

      <!-- 右：标签论文面板 -->
      <div class="ph-card panel-card">
        <template v-if="selectedTag">
          <h3 class="ph-section-title">
            <span class="tag-dot" :style="{ background: selectedTag.color }" />
            {{ selectedTag.name }}（{{ selectedPapers.length }} 篇）
          </h3>
          <el-table
            v-if="selectedPapers.length"
            :data="selectedPapers.slice(0, 30)"
            style="width: 100%"
          >
            <el-table-column label="论文标题" min-width="240" show-overflow-tooltip>
              <template #default="{ row }">
                <span class="paper-title">{{ row.title }}</span>
              </template>
            </el-table-column>
            <el-table-column prop="journal" label="期刊" min-width="130" show-overflow-tooltip />
            <el-table-column prop="year" label="年份" width="70" align="center" />
          </el-table>
          <div v-if="selectedPapers.length > 30" class="more-hint">
            仅展示前 30 篇，共 {{ selectedPapers.length }} 篇
          </div>
        </template>
        <el-empty v-else description="点击左侧标签查看关联论文" :image-size="100" />
      </div>
    </div>

    <!-- 新建/编辑弹窗 -->
    <el-dialog
      v-model="dialog"
      :title="dialogMode === 'create' ? '新建标签' : '编辑标签'"
      width="420px"
    >
      <div class="dialog-body">
        <el-input v-model="tagName" placeholder="标签名称" maxlength="12" show-word-limit />
        <div class="color-row">
          <span
            v-for="c in palette"
            :key="c"
            class="color-dot"
            :class="{ 'is-active': c === tagColor }"
            :style="{ background: c }"
            @click="tagColor = c"
          />
        </div>
      </div>
      <template #footer>
        <el-button @click="dialog = false">取消</el-button>
        <el-button type="primary" @click="submitDialog">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.page-loading {
  height: 360px;
}
.two-col {
  display: grid;
  grid-template-columns: 1.15fr 1fr;
  gap: 20px;
  align-items: start;
}
.tags-card {
  padding: 18px 20px;
}
.tag-search {
  margin-bottom: 14px;
}

.tag-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(220px, 1fr));
  gap: 10px;
}
.tag-item {
  display: flex;
  align-items: center;
  gap: 8px;
  border: 1px solid var(--ph-border);
  border-radius: 8px;
  padding: 8px 10px;
  cursor: pointer;
  transition: border-color 0.15s;
}
.tag-item:hover {
  border-color: var(--ph-primary);
}
.tag-item.is-selected {
  border-color: var(--ph-primary);
  background: var(--ph-primary-light);
}
.tag-dot {
  width: 10px;
  height: 10px;
  border-radius: 50%;
  flex-shrink: 0;
}
.tag-name {
  font-size: 13px;
  color: var(--ph-text);
  font-weight: 500;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.tag-count {
  margin-left: auto;
  font-size: 12px;
  color: var(--ph-text-muted);
  font-variant-numeric: tabular-nums;
  flex-shrink: 0;
}
.tag-actions {
  display: none;
  flex-shrink: 0;
  white-space: nowrap;
}
.tag-item:hover .tag-actions {
  display: inline-flex;
}
.tag-item:hover .tag-count {
  display: none;
}
.tag-actions .el-button + .el-button {
  margin-left: 0;
}

.panel-card {
  padding: 20px 24px;
  min-height: 360px;
}
.panel-card .ph-section-title {
  display: flex;
  align-items: center;
  gap: 8px;
}
.paper-title {
  color: var(--ph-text);
  font-weight: 500;
  font-size: 13px;
}
.more-hint {
  margin-top: 10px;
  font-size: 12px;
  color: var(--ph-text-muted);
  text-align: center;
}

.dialog-body {
  display: flex;
  flex-direction: column;
  gap: 14px;
}
.color-row {
  display: flex;
  gap: 10px;
}
.color-dot {
  width: 22px;
  height: 22px;
  border-radius: 50%;
  cursor: pointer;
  border: 2px solid transparent;
}
.color-dot.is-active {
  border-color: var(--ph-text);
}

@media (max-width: 1080px) {
  .two-col {
    grid-template-columns: 1fr;
  }
}
</style>
