<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import type { Paper } from '@/types/paper'
import { searchSharedPapers, getFilterOptions, type SharedSortKey } from '@/services/paperService'
import * as userData from '@/services/userDataService'
import { getUserName } from '@/mocks/users'
import { getCategory } from '@/services/knowledgeService'
import { getTag } from '@/services/knowledgeService'
import TagChip from '@/components/common/TagChip.vue'

const router = useRouter()

/* ---------------- 筛选状态 ---------------- */
const loading = ref(true)
const papers = ref<Paper[]>([])
const total = ref(0)
const viewMode = ref<'list' | 'card'>('list')

const filter = reactive({
  q: '',
  categoryId: null as number | null,
  tagIds: [] as number[],
  year: null as number | null,
  journal: null as string | null,
  sort: 'uploaded-desc' as SharedSortKey,
  page: 1,
  pageSize: 10,
})

const options = reactive<Awaited<ReturnType<typeof getFilterOptions>>>({
  categories: [],
  tags: [],
  years: [],
  journals: [],
})
const favoriteIds = ref<number[]>(userData.getFavoritePaperIds())

/* 分类树（供 el-tree-select 使用） */
interface TreeNodeOption {
  value: number
  label: string
  children: TreeNodeOption[]
}
const categoryTree = computed<TreeNodeOption[]>(() => {
  const build = (parentId: number | null): TreeNodeOption[] =>
    options.categories
      .filter((c) => c.parentId === parentId)
      .map((c) => ({
        value: c.id,
        label: c.name,
        children: build(c.id),
      }))
  return build(null)
})

/* 顶部搜索的初始关键词（来自全局搜索跳转） */
const route = useRoute()
onMounted(async () => {
  Object.assign(options, await getFilterOptions())
  if (typeof route.query.q === 'string') filter.q = route.query.q
  await fetchPapers()
})

async function fetchPapers() {
  loading.value = true
  try {
    const result = await searchSharedPapers({ ...filter })
    papers.value = result.list
    total.value = result.total
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  filter.page = 1
  fetchPapers()
}

function resetFilters() {
  filter.q = ''
  filter.categoryId = null
  filter.tagIds = []
  filter.year = null
  filter.journal = null
  filter.sort = 'uploaded-desc'
  filter.page = 1
  fetchPapers()
}

function handleSortChange() {
  filter.page = 1
  fetchPapers()
}

function handlePageChange() {
  fetchPapers()
}

/* ---------------- 行为操作 ---------------- */
async function toggleFavorite(paper: Paper) {
  const result = await userData.toggleFavorite(paper.id)
  favoriteIds.value = userData.getFavoritePaperIds()
  ElMessage.success(result.favorited ? '已加入收藏' : '已取消收藏')
}

async function addToReading(paper: Paper) {
  await userData.addToReading(paper.id)
  ElMessage.success('已加入阅读管理（未阅读）')
}

function goDetail(paper: Paper) {
  router.push(`/user/papers/${paper.id}`)
}

function goReader(paper: Paper) {
  router.push(`/user/papers/${paper.id}/reader`)
}

function downloadPaper(paper: Paper) {
  ElMessage.success(`已开始下载《${paper.title}》（模拟）`)
}

function isFavorited(paper: Paper): boolean {
  return favoriteIds.value.includes(paper.id)
}

function categoryNames(paper: Paper): string {
  let cat = getCategory(paper.categoryId)
  const path: string[] = []
  while (cat) {
    path.unshift(cat.name)
    cat = cat.parentId !== null ? getCategory(cat.parentId) : undefined
  }
  return path.join(' / ')
}
</script>

<template>
  <div class="ph-page library">
    <div class="ph-page-header">
      <div>
        <h2 class="ph-page-title">共享论文库</h2>
        <p class="ph-page-desc">平台用户共同维护的论文资源，经管理员审核通过后全员可查看与下载</p>
      </div>
      <div class="header-actions">
        <el-radio-group v-model="viewMode" size="default">
          <el-radio-button value="list">
            <el-icon><Menu /></el-icon>&nbsp;列表
          </el-radio-button>
          <el-radio-button value="card">
            <el-icon><Grid /></el-icon>&nbsp;卡片
          </el-radio-button>
        </el-radio-group>
        <el-button
          type="primary"
          :icon="'UploadFilled'"
          @click="router.push('/user/papers/upload')"
        >
          上传论文
        </el-button>
      </div>
    </div>

    <!-- 检索工具栏 -->
    <div class="ph-card toolbar">
      <div class="toolbar-row">
        <el-input
          v-model="filter.q"
          class="toolbar-search"
          placeholder="搜索论文标题、作者、DOI、摘要或关键词"
          clearable
          @keyup.enter="handleSearch"
          @clear="handleSearch"
        >
          <template #prefix>
            <el-icon><Search /></el-icon>
          </template>
        </el-input>
        <el-button type="primary" :icon="'Search'" @click="handleSearch">搜索</el-button>
        <el-button :icon="'RefreshLeft'" @click="resetFilters">重置</el-button>
        <span class="toolbar-count">共 {{ total }} 篇</span>
      </div>

      <div class="toolbar-row toolbar-filters">
        <el-tree-select
          v-model="filter.categoryId"
          :data="categoryTree"
          placeholder="全部分类"
          check-strictly
          clearable
          style="width: 170px"
          @change="handleSearch"
        />
        <el-select
          v-model="filter.tagIds"
          multiple
          collapse-tags
          collapse-tags-tooltip
          placeholder="标签筛选"
          clearable
          style="width: 200px"
          @change="handleSearch"
        >
          <el-option v-for="t in options.tags" :key="t.id" :value="t.id" :label="t.name">
            <span class="tag-option">
              <span class="tag-dot" :style="{ background: t.color }" />
              {{ t.name }}
            </span>
          </el-option>
        </el-select>
        <el-select
          v-model="filter.year"
          placeholder="发表年份"
          clearable
          style="width: 130px"
          @change="handleSearch"
        >
          <el-option v-for="y in options.years" :key="y" :value="y" :label="`${y} 年`" />
        </el-select>
        <el-select
          v-model="filter.journal"
          placeholder="期刊 / 会议"
          clearable
          filterable
          style="width: 220px"
          @change="handleSearch"
        >
          <el-option v-for="j in options.journals" :key="j" :value="j" :label="j" />
        </el-select>
        <div class="sort-box">
          <span class="sort-label">排序</span>
          <el-select v-model="filter.sort" style="width: 140px" @change="handleSortChange">
            <el-option value="uploaded-desc" label="最新上传" />
            <el-option value="downloads-desc" label="下载量最高" />
            <el-option value="year-desc" label="发表年份最新" />
          </el-select>
        </div>
      </div>
    </div>

    <!-- 列表视图 -->
    <div v-if="viewMode === 'list'" v-loading="loading" class="ph-card paper-list">
      <template v-if="papers.length">
        <div v-for="paper in papers" :key="paper.id" class="paper-row">
          <div class="paper-row-main" @click="goDetail(paper)">
            <div class="paper-title ph-ellipsis">{{ paper.title }}</div>
            <div class="paper-meta">
              <span class="paper-authors ph-ellipsis">{{ paper.authors.join('，') }}</span>
              <span class="meta-divider">·</span>
              <span>{{ paper.journal }}</span>
              <span class="meta-divider">·</span>
              <span>{{ paper.year }} 年</span>
            </div>
            <div class="paper-chips">
              <span class="cat-chip">{{ categoryNames(paper) }}</span>
              <TagChip
                v-for="tid in paper.tagIds.slice(0, 3)"
                :key="tid"
                :name="getTag(tid)?.name ?? ''"
                :color="getTag(tid)?.color"
              />
            </div>
          </div>

          <div class="paper-row-side">
            <div class="side-stats">
              <span
                ><el-icon><Download /></el-icon>{{ paper.downloads }}</span
              >
              <span class="side-uploader"
                >{{ paper.uploaderName ?? getUserName(paper.uploaderId) }} ·
                {{ paper.uploadedAt.slice(5, 16) }}</span
              >
            </div>
            <div class="side-actions">
              <el-tooltip content="PDF 预览" placement="top">
                <el-button text circle :icon="'View'" @click="goReader(paper)" />
              </el-tooltip>
              <el-tooltip content="下载" placement="top">
                <el-button text circle :icon="'Download'" @click="downloadPaper(paper)" />
              </el-tooltip>
              <el-tooltip :content="isFavorited(paper) ? '取消收藏' : '收藏'" placement="top">
                <el-button
                  text
                  circle
                  class="fav-btn"
                  :class="{ favorited: isFavorited(paper) }"
                  @click="toggleFavorite(paper)"
                >
                  <el-icon><StarFilled v-if="isFavorited(paper)" /><Star v-else /></el-icon>
                </el-button>
              </el-tooltip>
              <el-tooltip content="加入阅读管理" placement="top">
                <el-button text circle :icon="'Reading'" @click="addToReading(paper)" />
              </el-tooltip>
            </div>
          </div>
        </div>
      </template>
      <el-empty v-else description="没有找到匹配的论文，试试调整搜索条件" />
    </div>

    <!-- 卡片视图 -->
    <div v-else v-loading="loading" class="card-grid">
      <template v-if="papers.length">
        <div v-for="paper in papers" :key="paper.id" class="ph-card paper-card">
          <div class="card-top">
            <span class="cat-chip">{{ getCategory(paper.categoryId)?.name }}</span>
            <span class="card-year">{{ paper.year }}</span>
          </div>
          <div class="paper-title paper-title--card ph-ellipsis-2" @click="goDetail(paper)">
            {{ paper.title }}
          </div>
          <div class="card-authors ph-ellipsis">{{ paper.authors.join('，') }}</div>
          <div class="card-journal ph-ellipsis">{{ paper.journal }}</div>
          <div class="paper-chips">
            <TagChip
              v-for="tid in paper.tagIds.slice(0, 3)"
              :key="tid"
              :name="getTag(tid)?.name ?? ''"
              :color="getTag(tid)?.color"
            />
          </div>
          <div class="card-footer">
            <span class="side-uploader">
              <el-icon><Download /></el-icon>{{ paper.downloads }} ·
              {{ paper.uploaderName ?? getUserName(paper.uploaderId) }}
            </span>
            <div class="side-actions">
              <el-button text circle size="small" :icon="'View'" @click="goReader(paper)" />
              <el-button
                text
                circle
                size="small"
                :icon="'Download'"
                @click="downloadPaper(paper)"
              />
              <el-button
                text
                circle
                size="small"
                class="fav-btn"
                :class="{ favorited: isFavorited(paper) }"
                @click="toggleFavorite(paper)"
              >
                <el-icon><StarFilled v-if="isFavorited(paper)" /><Star v-else /></el-icon>
              </el-button>
              <el-button text circle size="small" :icon="'Reading'" @click="addToReading(paper)" />
            </div>
          </div>
        </div>
      </template>
      <el-empty v-else description="没有找到匹配的论文，试试调整搜索条件" class="card-empty" />
    </div>

    <!-- 分页 -->
    <div class="pagination-row">
      <el-pagination
        v-model:current-page="filter.page"
        v-model:page-size="filter.pageSize"
        :total="total"
        :page-sizes="[10, 20]"
        layout="total, sizes, prev, pager, next, jumper"
        background
        @current-change="handlePageChange"
        @size-change="handlePageChange"
      />
    </div>
  </div>
</template>

<style scoped>
.header-actions {
  display: flex;
  align-items: center;
  gap: 12px;
}

/* ---------- 工具栏 ---------- */
.toolbar {
  padding: 18px;
  margin-bottom: 16px;
}
.toolbar-row {
  display: flex;
  align-items: center;
  gap: 12px;
}
.toolbar-row + .toolbar-row {
  margin-top: 12px;
}
.toolbar-search {
  flex: 1;
}
.toolbar-count {
  margin-left: auto;
  font-size: 13px;
  color: var(--ph-text-secondary);
}
.sort-box {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-left: auto;
}
.sort-label {
  font-size: 13px;
  color: var(--ph-text-secondary);
}
.tag-option {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}
.tag-dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
}

/* ---------- 列表视图 ---------- */
.paper-list {
  padding: 6px 18px;
  min-height: 300px;
}
.paper-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 20px;
  padding: 16px 4px;
  border-bottom: 1px solid #f3f4f6;
  transition: background 0.15s;
}
.paper-row:last-child {
  border-bottom: none;
}
.paper-row:hover {
  background: #fafbfc;
}
.paper-row-main {
  flex: 1;
  min-width: 0;
  cursor: pointer;
}
.paper-title {
  font-size: 15px;
  font-weight: 600;
  color: var(--ph-text);
  transition: color 0.15s;
}
.paper-row-main:hover .paper-title {
  color: var(--ph-primary);
}
.paper-meta {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12.5px;
  color: var(--ph-text-secondary);
  margin-top: 5px;
}
.paper-authors {
  max-width: 320px;
}
.meta-divider {
  color: #d4d7dc;
}
.paper-chips {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-top: 9px;
}
.cat-chip {
  display: inline-flex;
  align-items: center;
  height: 22px;
  padding: 0 9px;
  border-radius: 6px;
  background: var(--ph-primary-light);
  color: var(--ph-primary);
  font-size: 12px;
  white-space: nowrap;
}
.paper-row-side {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 8px;
  flex-shrink: 0;
}
.side-stats {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 12.5px;
  color: var(--ph-text-secondary);
}
.side-stats span:first-child {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-weight: 600;
  color: var(--ph-text);
}
.side-uploader {
  font-size: 12px;
  color: var(--ph-text-muted);
  display: inline-flex;
  align-items: center;
  gap: 4px;
}
.side-actions {
  display: flex;
  gap: 2px;
}
.fav-btn.favorited {
  color: #d97706;
}

/* ---------- 卡片视图 ---------- */
.card-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 16px;
  min-height: 300px;
}
.paper-card {
  padding: 18px;
  display: flex;
  flex-direction: column;
  transition:
    transform 0.18s ease,
    box-shadow 0.18s ease;
}
.paper-card:hover {
  transform: translateY(-3px);
  box-shadow: 0 10px 24px rgba(15, 23, 42, 0.08);
}
.card-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 10px;
}
.card-year {
  font-size: 12px;
  color: var(--ph-text-muted);
}
.paper-title--card {
  font-size: 14.5px;
  cursor: pointer;
  min-height: 44px;
}
.paper-title--card:hover {
  color: var(--ph-primary);
}
.card-authors {
  font-size: 12.5px;
  color: var(--ph-text-secondary);
  margin-top: 8px;
}
.card-journal {
  font-size: 12px;
  color: var(--ph-text-muted);
  margin-top: 3px;
  font-style: italic;
}
.card-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 14px;
  padding-top: 12px;
  border-top: 1px solid #f3f4f6;
}
.card-empty {
  grid-column: 1 / -1;
}

/* ---------- 分页 ---------- */
.pagination-row {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}

@media (max-width: 1100px) {
  .card-grid {
    grid-template-columns: repeat(2, 1fr);
  }
}
</style>
