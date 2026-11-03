<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import type { Paper, ReadingRecord, ReadingStatus } from '@/types/paper'
import { readingStatusLabels } from '@/types/paper'
import * as myLibrary from '@/services/myLibraryService'
import * as userData from '@/services/userDataService'
import { getCategory } from '@/mocks/categories'

const router = useRouter()

/* ---------------- 数据加载 ---------------- */
interface ReadingRow {
  record: ReadingRecord
  paper: Paper
}

const loading = ref(true)
const rows = ref<ReadingRow[]>([])

async function fetchRows() {
  loading.value = true
  const records = userData.getReadingRecords()
  const papers = await Promise.all(records.map((r) => myLibrary.getPaperForDetail(r.paperId)))
  rows.value = records
    .map((record, i) => ({ record, paper: papers[i] }))
    .filter((r): r is ReadingRow => !!r.paper)
  loading.value = false
}
onMounted(fetchRows)

/* ---------------- 筛选与搜索 ---------------- */
const statusFilter = ref<'all' | ReadingStatus>('all')
const keyword = ref('')

const filteredRows = computed(() => {
  let list = rows.value
  if (statusFilter.value !== 'all') {
    list = list.filter((r) => r.record.status === statusFilter.value)
  }
  const kw = keyword.value.trim().toLowerCase()
  if (kw) {
    list = list.filter((r) => r.paper.title.toLowerCase().includes(kw))
  }
  /* 按最近阅读时间倒序 */
  return [...list].sort((a, b) => b.record.lastReadAt.localeCompare(a.record.lastReadAt))
})

const statusTabs = computed(() => {
  const count = (s: ReadingStatus) => rows.value.filter((r) => r.record.status === s).length
  return [
    { key: 'all' as const, label: '全部', n: rows.value.length },
    ...(['unread', 'reading', 'read', 'shelved'] as ReadingStatus[]).map((s) => ({
      key: s,
      label: readingStatusLabels[s],
      n: count(s),
    })),
  ]
})

/* ---------------- 行为操作 ---------------- */
async function changeStatus(row: ReadingRow, status: ReadingStatus) {
  await userData.updateReadingStatus(row.record.paperId, status)
  row.record.status = status
  ElMessage.success(`「${row.paper.title.slice(0, 16)}…」已标记为${readingStatusLabels[status]}`)
}

function goDetail(id: number) {
  router.push(`/user/papers/${id}`)
}

function goShared() {
  router.push('/user/library/shared')
}
</script>

<template>
  <div class="ph-page">
    <div class="ph-page-header">
      <div>
        <h2 class="ph-page-title">阅读管理</h2>
        <p class="ph-page-desc">跟踪论文阅读进度：未读 / 在读 / 已读 / 搁置</p>
      </div>
      <el-button type="primary" :icon="'Collection'" @click="goShared">去共享库发现论文</el-button>
    </div>

    <div class="ph-card reading-card">
      <!-- 状态筛选 + 搜索 -->
      <div class="filter-bar">
        <div class="status-tabs">
          <button
            v-for="t in statusTabs"
            :key="t.key"
            class="status-tab"
            :class="{ active: statusFilter === t.key }"
            @click="statusFilter = t.key"
          >
            {{ t.label }}
            <span class="tab-count">{{ t.n }}</span>
          </button>
        </div>
        <el-input
          v-model="keyword"
          placeholder="搜索论文标题"
          clearable
          style="width: 240px"
        >
          <template #prefix><el-icon><Search /></el-icon></template>
        </el-input>
      </div>

      <div v-if="loading" v-loading="true" class="table-loading" />

      <template v-else-if="filteredRows.length">
        <el-table :data="filteredRows" style="width: 100%">
          <el-table-column label="论文标题" min-width="280">
            <template #default="{ row }">
              <div class="cell-title" @click="goDetail(row.record.paperId)">
                {{ row.paper.title }}
              </div>
              <div class="cell-sub">{{ row.paper.authors.slice(0, 2).join('，') }} 等</div>
            </template>
          </el-table-column>
          <el-table-column label="分类" width="150">
            <template #default="{ row }">
              {{ getCategory(row.paper.categoryId)?.name ?? '—' }}
            </template>
          </el-table-column>
          <el-table-column label="阅读状态" width="150">
            <template #default="{ row }">
              <el-select
                :model-value="row.record.status"
                size="small"
                class="status-select"
                :class="'is-' + row.record.status"
                @change="(s: ReadingStatus) => changeStatus(row, s)"
              >
                <el-option
                  v-for="(label, key) in readingStatusLabels"
                  :key="key"
                  :value="key"
                  :label="label"
                />
              </el-select>
            </template>
          </el-table-column>
          <el-table-column label="最近阅读" prop="record.lastReadAt" width="130" />
          <el-table-column label="加入时间" prop="record.addedAt" width="130" />
          <el-table-column label="操作" width="110" fixed="right">
            <template #default="{ row }">
              <el-button text type="primary" size="small" @click="goDetail(row.record.paperId)">
                查看详情
              </el-button>
            </template>
          </el-table-column>
        </el-table>
      </template>

      <el-empty v-else description="暂无匹配的阅读记录">
        <el-button type="primary" @click="goShared">去共享论文库添加阅读</el-button>
      </el-empty>
    </div>
  </div>
</template>

<style scoped>
.reading-card {
  padding: 20px 24px;
  margin-bottom: 24px;
}
.table-loading {
  height: 260px;
}

.filter-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  flex-wrap: wrap;
  margin-bottom: 16px;
}
.status-tabs {
  display: flex;
  gap: 4px;
  background: var(--ph-bg);
  padding: 4px;
  border-radius: 10px;
}
.status-tab {
  border: none;
  background: transparent;
  padding: 7px 14px;
  font-size: 13px;
  color: var(--ph-text-secondary);
  border-radius: 8px;
  cursor: pointer;
  display: flex;
  align-items: center;
  gap: 6px;
  transition: all 0.15s;
}
.status-tab:hover {
  color: var(--ph-primary);
}
.status-tab.active {
  background: #fff;
  color: var(--ph-primary);
  font-weight: 600;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.08);
}
.tab-count {
  font-size: 11px;
  color: var(--ph-text-muted);
}

.cell-title {
  font-size: 14px;
  font-weight: 600;
  color: var(--ph-text);
  cursor: pointer;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.cell-title:hover {
  color: var(--ph-primary);
}
.cell-sub {
  font-size: 12px;
  color: var(--ph-text-muted);
  margin-top: 3px;
}

.status-select {
  width: 110px;
}
</style>
