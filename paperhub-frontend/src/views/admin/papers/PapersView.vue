<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import * as admin from '@/services/adminService'
import type { Paper, ReviewStatus } from '@/types/paper'
import { reviewStatusLabels } from '@/types/paper'
import { getCategory, mockCategories } from '@/mocks/categories'
import { getUserName } from '@/mocks/users'

/** 分类树（el-tree-select 数据） */
interface CategoryNode {
  value: number
  label: string
  children?: CategoryNode[]
}
const categoryTree = computed<CategoryNode[]>(() => {
  const build = (parentId: number | null): CategoryNode[] =>
    mockCategories
      .filter((c) => c.parentId === parentId)
      .map((c) => {
        const children = build(c.id)
        return { value: c.id, label: c.name, ...(children.length ? { children } : {}) }
      })
  return build(null)
})

const loading = ref(true)
const papers = ref<Paper[]>([])
const page = ref(1)
const pageSize = 10

const query = reactive({
  title: '',
  doi: '',
  uploader: '',
  categoryId: null as number | null,
  status: 'all' as ReviewStatus | 'all',
})

async function fetchPapers() {
  loading.value = true
  papers.value = await admin.getSharedPapers({
    title: query.title,
    doi: query.doi,
    uploader: query.uploader,
    categoryId: query.categoryId,
    status: query.status,
  })
  page.value = 1
  loading.value = false
}
onMounted(fetchPapers)

function resetFilters() {
  query.title = ''
  query.doi = ''
  query.uploader = ''
  query.categoryId = null
  query.status = 'all'
  fetchPapers()
}

const pagedPapers = computed(() =>
  papers.value.slice((page.value - 1) * pageSize, page.value * pageSize),
)

function statusTagType(p: Paper) {
  switch (p.reviewStatus) {
    case 'approved':
      return 'success'
    case 'pending':
      return 'warning'
    case 'rejected':
      return 'danger'
    default:
      return 'info'
  }
}

/* ---------------- 论文操作 ---------------- */
const drawer = ref(false)
const current = ref<Paper | null>(null)

function openDetail(p: Paper) {
  current.value = p
  drawer.value = true
}

async function offlinePaper(p: Paper) {
  try {
    await ElMessageBox.confirm(
      `下架后《${p.title}》将从共享论文库移除，用户无法继续访问，确定下架吗？`,
      '论文下架',
      { confirmButtonText: '下架', cancelButtonText: '取消', type: 'warning' },
    )
    await admin.offlinePaper(p.id)
    ElMessage.success('论文已下架')
    fetchPapers()
  } catch {
    /* 用户取消 */
  }
}

async function restorePaper(p: Paper) {
  await admin.restorePaper(p.id)
  ElMessage.success('论文已恢复上架')
  fetchPapers()
}

async function deletePaper(p: Paper) {
  try {
    await ElMessageBox.confirm(
      `删除违规资源《${p.title}》后不可恢复，确定删除吗？`,
      '删除违规资源',
      { confirmButtonText: '删除', cancelButtonText: '取消', type: 'error' },
    )
    await admin.deletePaper(p.id)
    ElMessage.success('违规资源已删除')
    fetchPapers()
  } catch {
    /* 用户取消 */
  }
}
</script>

<template>
  <div class="ph-page">
    <div class="ph-page-header">
      <div>
        <h2 class="ph-page-title">共享论文列表</h2>
        <p class="ph-page-desc">平台全部共享论文资源的管理与维护</p>
      </div>
    </div>

    <div class="ph-card papers-card">
      <!-- 筛选区 -->
      <div class="filter-bar">
        <el-input
          v-model="query.title"
          placeholder="按标题搜索"
          clearable
          style="width: 180px"
          @change="fetchPapers"
        >
          <template #prefix><el-icon><Search /></el-icon></template>
        </el-input>
        <el-input
          v-model="query.doi"
          placeholder="按 DOI 搜索"
          clearable
          style="width: 170px"
          @change="fetchPapers"
        />
        <el-input
          v-model="query.uploader"
          placeholder="按上传者搜索"
          clearable
          style="width: 150px"
          @change="fetchPapers"
        />
        <el-tree-select
          v-model="query.categoryId"
          :data="categoryTree"
          placeholder="全部分类"
          check-strictly
          clearable
          style="width: 160px"
          @change="fetchPapers"
        />
        <el-select
          v-model="query.status"
          placeholder="审核状态"
          style="width: 130px"
          @change="fetchPapers"
        >
          <el-option label="全部状态" value="all" />
          <el-option label="审核通过" value="approved" />
          <el-option label="待审核" value="pending" />
          <el-option label="审核未通过" value="rejected" />
          <el-option label="已下架" value="offline" />
        </el-select>
        <el-button text :icon="'RefreshLeft'" @click="resetFilters">重置</el-button>
        <div class="filter-count">共 {{ papers.length }} 篇</div>
      </div>

      <div v-if="loading" v-loading="true" class="table-loading" />

      <template v-else>
        <el-table :data="pagedPapers" style="width: 100%">
          <el-table-column label="论文标题" min-width="280" show-overflow-tooltip>
            <template #default="{ row }">
              <el-button text type="primary" class="title-btn" @click="openDetail(row)">
                {{ row.title }}
              </el-button>
            </template>
          </el-table-column>
          <el-table-column label="作者" min-width="150" show-overflow-tooltip>
            <template #default="{ row }">{{ row.authors.join('、') }}</template>
          </el-table-column>
          <el-table-column label="分类" width="120" show-overflow-tooltip>
            <template #default="{ row }">
              {{ getCategory(row.categoryId)?.name ?? '—' }}
            </template>
          </el-table-column>
          <el-table-column label="上传者" width="100">
            <template #default="{ row }">{{ getUserName(row.uploaderId) }}</template>
          </el-table-column>
          <el-table-column label="下载量" width="80" align="center">
            <template #default="{ row }">
              <span class="count-num">{{ row.downloads }}</span>
            </template>
          </el-table-column>
          <el-table-column label="状态" width="105">
            <template #default="{ row }">
              <el-tag :type="statusTagType(row)" size="small" effect="light">
                {{ reviewStatusLabels[row.reviewStatus ?? 'pending'] }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="170" fixed="right">
            <template #default="{ row }">
              <el-button
                v-if="row.reviewStatus === 'approved'"
                text
                type="warning"
                size="small"
                @click="offlinePaper(row)"
              >
                下架
              </el-button>
              <el-button
                v-if="row.reviewStatus === 'offline'"
                text
                type="success"
                size="small"
                @click="restorePaper(row)"
              >
                恢复
              </el-button>
              <el-button text type="danger" size="small" @click="deletePaper(row)">
                删除违规
              </el-button>
            </template>
          </el-table-column>
        </el-table>

        <div class="pager-row">
          <el-pagination
            v-model:current-page="page"
            layout="total, prev, pager, next"
            :total="papers.length"
            :page-size="pageSize"
          />
        </div>
      </template>
    </div>

    <!-- 论文详情抽屉 -->
    <el-drawer v-model="drawer" :title="current?.title ?? '论文详情'" size="480px">
      <template v-if="current">
        <div class="drawer-body">
          <el-descriptions :column="1" border>
            <el-descriptions-item label="标题">{{ current.title }}</el-descriptions-item>
            <el-descriptions-item label="作者">
              {{ current.authors.join('、') }}
            </el-descriptions-item>
            <el-descriptions-item label="摘要">
              <span class="abstract-text">{{ current.abstract }}</span>
            </el-descriptions-item>
            <el-descriptions-item label="DOI">{{ current.doi || '—' }}</el-descriptions-item>
            <el-descriptions-item label="期刊">{{ current.journal }}</el-descriptions-item>
            <el-descriptions-item label="年份">{{ current.year }}</el-descriptions-item>
            <el-descriptions-item label="关键词">
              {{ current.keywords.join('；') }}
            </el-descriptions-item>
            <el-descriptions-item label="分类">
              {{ getCategory(current.categoryId)?.name ?? '—' }}
            </el-descriptions-item>
            <el-descriptions-item label="上传者">
              {{ getUserName(current.uploaderId) }}
            </el-descriptions-item>
            <el-descriptions-item label="上传时间">{{ current.uploadedAt }}</el-descriptions-item>
            <el-descriptions-item label="下载量">{{ current.downloads }}</el-descriptions-item>
            <el-descriptions-item label="审核状态">
              <el-tag :type="statusTagType(current)" size="small" effect="light">
                {{ reviewStatusLabels[current.reviewStatus ?? 'pending'] }}
              </el-tag>
            </el-descriptions-item>
            <el-descriptions-item v-if="current.reviewComment" label="审核意见">
              <span class="comment-text">{{ current.reviewComment }}</span>
            </el-descriptions-item>
          </el-descriptions>

          <el-alert
            type="info"
            :closable="false"
            show-icon
            title="管理员管理的是共享资源，无权访问用户私有论文。"
          />
        </div>
      </template>
    </el-drawer>
  </div>
</template>

<style scoped>
.papers-card {
  padding: 20px 24px;
  margin-bottom: 24px;
}
.table-loading {
  height: 320px;
}

.filter-bar {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
  margin-bottom: 16px;
}
.filter-count {
  margin-left: auto;
  font-size: 12.5px;
  color: var(--ph-text-muted);
}

.title-btn {
  padding: 0;
  font-weight: 500;
  height: auto;
  white-space: normal;
  text-align: left;
  line-height: 1.4;
}
.count-num {
  font-size: 14px;
  font-weight: 700;
  color: var(--ph-text);
  font-variant-numeric: tabular-nums;
}

.pager-row {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}

.drawer-body {
  display: flex;
  flex-direction: column;
  gap: 16px;
}
.abstract-text,
.comment-text {
  font-size: 12.5px;
  color: var(--ph-text-muted);
  line-height: 1.6;
  white-space: pre-wrap;
}
.comment-text {
  color: var(--ph-error);
}
</style>
