<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import type { Paper } from '@/types/paper'
import { reviewStatusLabels, type ReviewStatus } from '@/types/paper'
import * as myLibrary from '@/services/myLibraryService'
import * as userData from '@/services/userDataService'
import { mockCategories, getCategory } from '@/mocks/categories'
import { getTag, mockTags } from '@/mocks/tags'
import TagChip from '@/components/common/TagChip.vue'

const route = useRoute()
const router = useRouter()

/* ---------------- Tab 数据 ---------------- */
const loading = ref(true)
const activeTab = ref<string>((route.query.tab as string) || 'private')
const privateList = ref<Paper[]>([])
const sharedList = ref<Paper[]>([])
const favoriteList = ref<Paper[]>([])
const keyword = ref('')

async function fetchAll() {
  loading.value = true
  const mine = await myLibrary.getMyPapers()
  privateList.value = mine.privatePapers
  sharedList.value = mine.sharedPapers
  favoriteList.value = await myLibrary.getFavoritePapers()
  loading.value = false
}
onMounted(fetchAll)

function filterByKeyword(list: Paper[]): Paper[] {
  const kw = keyword.value.trim().toLowerCase()
  if (!kw) return list
  return list.filter((p) => p.title.toLowerCase().includes(kw))
}

const filteredPrivate = computed(() => filterByKeyword(privateList.value))
const filteredShared = computed(() => filterByKeyword(sharedList.value))
const filteredFavorites = computed(() => filterByKeyword(favoriteList.value))

/* ---------------- 状态样式 ---------------- */
const statusClassMap: Record<ReviewStatus, string> = {
  pending: 'ph-status--warning',
  approved: 'ph-status--success',
  rejected: 'ph-status--danger',
  offline: 'ph-status--info',
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

/* ---------------- 行为操作 ---------------- */
async function handleDelete(paper: Paper) {
  try {
    await ElMessageBox.confirm(`删除后《${paper.title}》将无法恢复，确定删除吗？`, '删除私有论文', {
      confirmButtonText: '删除',
      cancelButtonText: '取消',
      type: 'warning',
    })
    await myLibrary.deleteMyPaper(paper.id)
    ElMessage.success('论文已删除')
    fetchAll()
  } catch {
    /* 用户取消 */
  }
}

async function handleUnshare(paper: Paper) {
  try {
    await ElMessageBox.confirm(
      `取消共享后《${paper.title}》将从共享论文库下架，其他用户将无法访问。确定继续吗？`,
      '取消共享',
      { confirmButtonText: '取消共享', cancelButtonText: '再想想', type: 'warning' },
    )
    await myLibrary.changeReviewStatus(paper.id, 'offline')
    ElMessage.success('已取消共享，论文转为已下架状态')
    fetchAll()
  } catch {
    /* 用户取消 */
  }
}

async function handleResubmit(paper: Paper) {
  try {
    await ElMessageBox.confirm(
      `重新提交后《${paper.title}》将重新进入管理员审核队列。确定继续吗？`,
      '重新提交审核',
      { confirmButtonText: '提交', cancelButtonText: '取消' },
    )
    await myLibrary.changeReviewStatus(paper.id, 'pending')
    ElMessage.success('已重新提交审核')
    fetchAll()
  } catch {
    /* 用户取消 */
  }
}

async function viewReviewComment(paper: Paper) {
  ElMessageBox.alert(paper.reviewComment || '暂无审核意见', '审核意见', {
    confirmButtonText: '知道了',
  })
}

async function handleUnfavorite(paper: Paper) {
  await userData.toggleFavorite(paper.id)
  ElMessage.success('已取消收藏')
  fetchAll()
}

async function addToReading(paper: Paper) {
  await userData.addToReading(paper.id)
  ElMessage.success('已加入阅读管理（未阅读）')
}

function download(paper: Paper) {
  ElMessage.success(`已开始下载《${paper.title}》（模拟）`)
}

function goDetail(id: number) {
  router.push(`/user/papers/${id}`)
}

function goReader(id: number) {
  router.push(`/user/papers/${id}/reader`)
}

/* ---------------- 编辑弹窗 ---------------- */
const tags = mockTags
const yearOptions = Array.from({ length: 36 }, (_, i) => new Date().getFullYear() - i)

interface CascaderNode {
  value: number
  label: string
  children: CascaderNode[]
}
const categoryTree = computed<CascaderNode[]>(() => {
  const build = (parentId: number | null): CascaderNode[] =>
    mockCategories
      .filter((c) => c.parentId === parentId)
      .map((c) => ({ value: c.id, label: c.name, children: build(c.id) }))
  return build(null)
})

const editDialogVisible = ref(false)
const editingPaper = ref<Paper | null>(null)
const editFormRef = ref<FormInstance>()
const editForm = reactive({
  title: '',
  authors: '',
  journal: '',
  year: new Date().getFullYear(),
  abstract: '',
  categoryId: null as number | null,
  tagIds: [] as number[],
  keywords: '',
})

const editRules: FormRules = {
  title: [{ required: true, message: '请输入论文标题', trigger: 'blur' }],
  authors: [{ required: true, message: '请输入作者', trigger: 'blur' }],
  year: [{ required: true, message: '请选择发表年份', trigger: 'change' }],
  categoryId: [{ required: true, message: '请选择论文分类', trigger: 'change' }],
}

function openEdit(paper: Paper) {
  editingPaper.value = paper
  editForm.title = paper.title
  editForm.authors = paper.authors.join('，')
  editForm.journal = paper.journal
  editForm.year = paper.year
  editForm.abstract = paper.abstract
  editForm.categoryId = paper.categoryId
  editForm.tagIds = [...paper.tagIds]
  editForm.keywords = paper.keywords.join('，')
  editDialogVisible.value = true
}

async function saveEdit() {
  const valid = await editFormRef.value?.validate().catch(() => false)
  if (!valid || !editingPaper.value) return
  await myLibrary.updatePaper(editingPaper.value.id, {
    title: editForm.title,
    authors: editForm.authors.split(/[,，、\s]+/).filter(Boolean),
    journal: editForm.journal,
    year: editForm.year,
    abstract: editForm.abstract,
    categoryId: editForm.categoryId ?? undefined,
    tagIds: editForm.tagIds,
    keywords: editForm.keywords.split(/[,，、\s]+/).filter(Boolean),
  })
  ElMessage.success('论文信息已更新')
  editDialogVisible.value = false
  fetchAll()
}
</script>

<template>
  <div class="ph-page">
    <div class="ph-page-header">
      <div>
        <h2 class="ph-page-title">我的论文库</h2>
        <p class="ph-page-desc">
          管理你的私有论文、共享论文与收藏，所有操作状态在演示期间刷新后保留
        </p>
      </div>
      <div class="header-actions">
        <el-input v-model="keyword" placeholder="按标题搜索" clearable style="width: 220px">
          <template #prefix
            ><el-icon><Search /></el-icon
          ></template>
        </el-input>
        <el-button
          type="primary"
          :icon="'UploadFilled'"
          @click="router.push('/user/papers/upload')"
        >
          上传论文
        </el-button>
      </div>
    </div>

    <el-tabs v-model="activeTab" v-loading="loading" class="library-tabs">
      <!-- 私有论文 -->
      <el-tab-pane name="private">
        <template #label>
          我的私有论文
          <el-badge :value="filteredPrivate.length" class="tab-badge" type="info" />
        </template>
        <div class="ph-card list-card">
          <template v-if="filteredPrivate.length">
            <div v-for="p in filteredPrivate" :key="p.id" class="paper-row">
              <div class="paper-row-main" @click="goDetail(p.id)">
                <div class="paper-title ph-ellipsis">{{ p.title }}</div>
                <div class="paper-meta">
                  <span class="ph-status ph-status--primary">私有</span>
                  <span>{{ p.authors.join('，') }}</span>
                  <span class="meta-dot">·</span>
                  <span>{{ p.year }} 年</span>
                  <span class="meta-dot">·</span>
                  <span>{{ p.fileSizeMB }} MB</span>
                </div>
                <div class="paper-chips">
                  <span class="cat-chip">{{ categoryNames(p) }}</span>
                  <TagChip
                    v-for="tid in p.tagIds.slice(0, 3)"
                    :key="tid"
                    :name="getTag(tid)?.name ?? ''"
                    :color="getTag(tid)?.color"
                  />
                </div>
              </div>
              <div class="row-actions">
                <el-button text :icon="'View'" @click="goDetail(p.id)">查看</el-button>
                <el-button text :icon="'EditPen'" @click="openEdit(p)">编辑</el-button>
                <el-button text :icon="'Download'" @click="download(p)">下载</el-button>
                <el-button text :icon="'Delete'" class="danger-btn" @click="handleDelete(p)">
                  删除
                </el-button>
              </div>
            </div>
          </template>
          <el-empty v-else description="暂无私有论文，点击右上角上传" />
        </div>
      </el-tab-pane>

      <!-- 共享论文 -->
      <el-tab-pane name="shared">
        <template #label>
          我共享的论文
          <el-badge :value="filteredShared.length" class="tab-badge" type="info" />
        </template>
        <div class="ph-card list-card">
          <template v-if="filteredShared.length">
            <div v-for="p in filteredShared" :key="p.id" class="paper-row">
              <div class="paper-row-main" @click="goDetail(p.id)">
                <div class="paper-title ph-ellipsis">{{ p.title }}</div>
                <div class="paper-meta">
                  <span class="ph-status" :class="statusClassMap[p.reviewStatus!]">
                    {{ reviewStatusLabels[p.reviewStatus!] }}
                  </span>
                  <span>{{ p.year }} 年</span>
                  <span class="meta-dot">·</span>
                  <span>{{ p.journal || '未投稿' }}</span>
                  <template v-if="p.reviewStatus === 'approved'">
                    <span class="meta-dot">·</span>
                    <span>下载 {{ p.downloads }} 次</span>
                  </template>
                </div>
                <div class="paper-chips">
                  <span class="cat-chip">{{ categoryNames(p) }}</span>
                  <TagChip
                    v-for="tid in p.tagIds.slice(0, 3)"
                    :key="tid"
                    :name="getTag(tid)?.name ?? ''"
                    :color="getTag(tid)?.color"
                  />
                </div>
              </div>
              <div class="row-actions">
                <el-button text :icon="'View'" @click="goDetail(p.id)">详情</el-button>
                <el-button text :icon="'EditPen'" @click="openEdit(p)">编辑</el-button>
                <el-button
                  v-if="p.reviewComment && p.reviewStatus !== 'approved'"
                  text
                  :icon="'ChatLineSquare'"
                  @click="viewReviewComment(p)"
                >
                  审核意见
                </el-button>
                <el-button
                  v-if="p.reviewStatus === 'approved'"
                  text
                  :icon="'SoldOut'"
                  @click="handleUnshare(p)"
                >
                  取消共享
                </el-button>
                <el-button
                  v-if="p.reviewStatus === 'rejected'"
                  text
                  type="primary"
                  :icon="'RefreshRight'"
                  @click="handleResubmit(p)"
                >
                  重新提交
                </el-button>
              </div>
            </div>
          </template>
          <el-empty v-else description="暂无共享论文" />
        </div>
      </el-tab-pane>

      <!-- 我的收藏 -->
      <el-tab-pane name="favorites">
        <template #label>
          我的收藏
          <el-badge :value="filteredFavorites.length" class="tab-badge" type="info" />
        </template>
        <div class="ph-card list-card">
          <template v-if="filteredFavorites.length">
            <div v-for="p in filteredFavorites" :key="p.id" class="paper-row">
              <div class="paper-row-main" @click="goDetail(p.id)">
                <div class="paper-title ph-ellipsis">
                  <el-icon class="fav-star"><StarFilled /></el-icon>
                  {{ p.title }}
                </div>
                <div class="paper-meta">
                  <span>{{ p.authors.join('，') }}</span>
                  <span class="meta-dot">·</span>
                  <span>{{ p.journal }}</span>
                  <span class="meta-dot">·</span>
                  <span>{{ p.year }} 年</span>
                </div>
                <div class="paper-chips">
                  <span class="cat-chip">{{ categoryNames(p) }}</span>
                </div>
              </div>
              <div class="row-actions">
                <el-button text :icon="'View'" @click="goReader(p.id)">预览</el-button>
                <el-button text :icon="'Download'" @click="download(p)">下载</el-button>
                <el-button text :icon="'Reading'" @click="addToReading(p)">加入阅读</el-button>
                <el-button text :icon="'Star'" class="danger-btn" @click="handleUnfavorite(p)">
                  取消收藏
                </el-button>
              </div>
            </div>
          </template>
          <el-empty v-else description="暂无收藏，去共享论文库看看吧">
            <el-button type="primary" @click="router.push('/user/library/shared')">
              前往共享论文库
            </el-button>
          </el-empty>
        </div>
      </el-tab-pane>
    </el-tabs>

    <!-- 编辑弹窗 -->
    <el-dialog
      v-model="editDialogVisible"
      :title="`编辑论文信息${editingPaper?.visibility === 'shared' ? '（元数据）' : ''}`"
      width="620px"
      destroy-on-close
    >
      <el-form
        ref="editFormRef"
        :model="editForm"
        :rules="editRules"
        label-width="86px"
        label-position="left"
      >
        <el-form-item label="论文标题" prop="title">
          <el-input v-model="editForm.title" placeholder="请输入论文标题" />
        </el-form-item>
        <el-form-item label="作者" prop="authors">
          <el-input v-model="editForm.authors" placeholder="多个作者用逗号分隔" />
        </el-form-item>
        <el-form-item label="期刊 / 会议">
          <el-input v-model="editForm.journal" placeholder="可留空" />
        </el-form-item>
        <el-form-item label="发表年份" prop="year">
          <el-select v-model="editForm.year" placeholder="选择年份" style="width: 100%">
            <el-option v-for="y in yearOptions" :key="y" :value="y" :label="`${y} 年`" />
          </el-select>
        </el-form-item>
        <el-form-item label="摘要">
          <el-input
            v-model="editForm.abstract"
            type="textarea"
            :rows="3"
            placeholder="请输入论文摘要"
          />
        </el-form-item>
        <el-form-item label="论文分类" prop="categoryId">
          <el-tree-select
            v-model="editForm.categoryId"
            :data="categoryTree"
            placeholder="选择分类"
            check-strictly
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="标签">
          <el-select v-model="editForm.tagIds" multiple placeholder="选择标签" style="width: 100%">
            <el-option v-for="t in tags" :key="t.id" :value="t.id" :label="t.name" />
          </el-select>
        </el-form-item>
        <el-form-item label="关键词">
          <el-input v-model="editForm.keywords" placeholder="多个关键词用逗号分隔" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="saveEdit">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.header-actions {
  display: flex;
  gap: 12px;
}
.library-tabs {
  margin-bottom: 24px;
}
.tab-badge {
  margin-left: 6px;
  transform: translateY(-1px);
}
.tab-badge :deep(.el-badge__content) {
  background: #e5e7eb;
  color: #4b5563;
  border: none;
}

.list-card {
  padding: 6px 18px;
  min-height: 240px;
}
.paper-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 15px 4px;
  border-bottom: 1px solid #f3f4f6;
}
.paper-row:last-child {
  border-bottom: none;
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
  display: flex;
  align-items: center;
  gap: 6px;
  transition: color 0.15s;
}
.paper-row-main:hover .paper-title {
  color: var(--ph-primary);
}
.fav-star {
  color: #d97706;
  flex-shrink: 0;
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
.paper-chips {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-top: 8px;
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
.row-actions {
  display: flex;
  align-items: center;
  flex-shrink: 0;
}
.row-actions .el-button + .el-button {
  margin-left: 0;
}
.row-actions .el-button {
  padding: 8px 10px;
}
.danger-btn {
  color: var(--ph-danger);
}
.danger-btn:hover {
  color: #ef4444;
}
</style>
