<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import type { Paper, ReadingStatus } from '@/types/paper'
import { readingStatusLabels, reviewStatusLabels } from '@/types/paper'
import * as myLibrary from '@/services/myLibraryService'
import * as userData from '@/services/userDataService'
import { getCategory } from '@/mocks/categories'
import { getTag } from '@/mocks/tags'
import { getUserName as getUploaderName } from '@/mocks/users'
import TagChip from '@/components/common/TagChip.vue'

const route = useRoute()
const router = useRouter()
const paperId = Number(route.params.id)

const loading = ref(true)
const paper = ref<Paper | null>(null)
const notFound = ref(false)

const isFavorited = ref(false)
const readingStatus = ref<ReadingStatus>('unread')
const hasReadingRecord = ref(false)

async function fetchAll() {
  loading.value = true
  const p = await myLibrary.getPaperForDetail(paperId)
  if (!p) {
    notFound.value = true
    loading.value = false
    return
  }
  paper.value = p
  isFavorited.value = userData.getFavoritePaperIds().includes(p.id)
  const record = userData.getReadingRecords().find((r) => r.paperId === p.id)
  hasReadingRecord.value = !!record
  readingStatus.value = record?.status ?? 'unread'
  loading.value = false
}
onMounted(fetchAll)

/* ---------------- 展示辅助 ---------------- */
function categoryPath(id: number): string {
  let cat = getCategory(id)
  const path: string[] = []
  while (cat) {
    path.unshift(cat.name)
    cat = cat.parentId !== null ? getCategory(cat.parentId) : undefined
  }
  return path.join(' / ')
}

const metaItems = computed(() => {
  const p = paper.value
  if (!p) return []
  return [
    { label: '作者', value: p.authors.join('，') },
    { label: '发表年份', value: `${p.year} 年` },
    { label: '期刊 / 会议', value: p.journal || '—' },
    { label: 'DOI', value: p.doi || '—' },
    { label: '上传者', value: getUploaderName(p.uploaderId) },
    { label: '上传时间', value: p.uploadedAt },
    { label: '文件大小', value: `${p.fileSizeMB} MB · ${p.pageCount} 页` },
    { label: '下载次数', value: `${p.downloads} 次` },
  ]
})

/* ---------------- 行为操作 ---------------- */
async function toggleFavorite() {
  if (!paper.value) return
  const res = await userData.toggleFavorite(paper.value.id)
  isFavorited.value = res.favorited
  ElMessage.success(res.favorited ? '已加入收藏' : '已取消收藏')
}

async function changeReadingStatus(status: ReadingStatus) {
  if (!paper.value) return
  await userData.updateReadingStatus(paper.value.id, status)
  hasReadingRecord.value = true
  readingStatus.value = status
  ElMessage.success(`阅读状态已更新为「${readingStatusLabels[status]}」`)
}

function download() {
  ElMessage.success(`已开始下载《${paper.value?.title}》（模拟）`)
}

function goReader() {
  router.push(`/user/papers/${paperId}/reader`)
}

/* ---------------- 笔记弹窗 ---------------- */
const noteDialogVisible = ref(false)
const noteForm = reactive({
  title: '',
  content: '',
})

function openNoteDialog() {
  noteForm.title = paper.value ? `《${paper.value.title}》阅读笔记` : ''
  noteForm.content = ''
  noteDialogVisible.value = true
}

async function saveNote() {
  if (!noteForm.title.trim()) {
    ElMessage.warning('请输入笔记标题')
    return
  }
  if (!noteForm.content.trim()) {
    ElMessage.warning('请输入笔记内容')
    return
  }
  await myLibrary.addNote({
    title: noteForm.title.trim(),
    paperId,
    content: noteForm.content.trim(),
  })
  noteDialogVisible.value = false
  ElMessage.success('笔记已保存，可在「阅读笔记」中查看')
}

function goNotes() {
  router.push('/user/notes')
}
</script>

<template>
  <div class="ph-page">
    <div v-if="loading" v-loading="true" class="detail-loading" />

    <template v-else-if="notFound">
      <div class="ph-card notfound-card">
        <el-empty description="论文不存在、已删除或尚未通过审核">
          <el-button type="primary" @click="router.back()">返回上一页</el-button>
        </el-empty>
      </div>
    </template>

    <template v-else-if="paper">
      <div class="ph-page-header">
        <div>
          <el-button
            text
            :icon="'ArrowLeft'"
            class="back-btn"
            @click="router.back()"
          >
            返回
          </el-button>
          <h2 class="ph-page-title">{{ paper.title }}</h2>
          <p class="ph-page-desc">论文详情与个人阅读管理</p>
        </div>
        <div class="header-actions">
          <el-button type="primary" :icon="'View'" @click="goReader">在线预览</el-button>
          <el-button :icon="'Download'" @click="download">下载 PDF</el-button>
          <el-button
            :type="isFavorited ? 'warning' : 'default'"
            :icon="isFavorited ? 'StarFilled' : 'Star'"
            @click="toggleFavorite"
          >
            {{ isFavorited ? '已收藏' : '收藏' }}
          </el-button>
        </div>
      </div>

      <div class="detail-layout">
        <!-- 左侧：论文信息 -->
        <div class="detail-main">
          <div class="ph-card detail-card">
            <div class="detail-top">
              <span
                class="ph-status"
                :class="paper.visibility === 'shared' ? 'ph-status--primary' : 'ph-status--info'"
              >
                {{ paper.visibility === 'shared' ? '共享资源' : '私有资源' }}
              </span>
              <span
                v-if="paper.reviewStatus"
                class="ph-status"
                :class="{
                  approved: 'ph-status--success',
                  pending: 'ph-status--warning',
                  rejected: 'ph-status--danger',
                  offline: 'ph-status--info',
                }[paper.reviewStatus]"
              >
                {{ reviewStatusLabels[paper.reviewStatus] }}
              </span>
              <TagChip
                v-for="tid in paper.tagIds"
                :key="tid"
                :name="getTag(tid)?.name ?? ''"
                :color="getTag(tid)?.color"
              />
            </div>

            <div class="detail-section-title">摘要</div>
            <p class="abstract-text">
              {{ paper.abstract || '暂无摘要' }}
            </p>

            <div class="detail-section-title">元数据</div>
            <div class="meta-grid">
              <div v-for="item in metaItems" :key="item.label" class="meta-item">
                <div class="meta-label">{{ item.label }}</div>
                <div class="meta-value">{{ item.value }}</div>
              </div>
            </div>

            <template v-if="paper.keywords.length">
              <div class="detail-section-title">关键词</div>
              <div class="keyword-wrap">
                <span v-for="kw in paper.keywords" :key="kw" class="keyword-chip">{{ kw }}</span>
              </div>
            </template>

            <template v-if="paper.link">
              <div class="detail-section-title">原文链接</div>
              <el-link
                type="primary"
                :href="paper.link"
                target="_blank"
                underline="never"
              >
                {{ paper.link }}
              </el-link>
            </template>

            <div
              v-if="paper.reviewStatus === 'rejected' && paper.reviewComment"
              class="review-comment"
            >
              <div class="review-comment-title">
                <el-icon color="#dc2626"><WarningFilled /></el-icon>
                审核未通过原因
              </div>
              <p>{{ paper.reviewComment }}</p>
            </div>
          </div>
        </div>

        <!-- 右侧：个人阅读管理 -->
        <aside class="detail-aside">
          <div class="ph-card aside-card">
            <div class="aside-title">阅读管理</div>
            <div class="reading-row">
              <span class="reading-label">阅读状态</span>
              <el-select
                :model-value="readingStatus"
                style="width: 140px"
                @change="(s: ReadingStatus) => changeReadingStatus(s)"
              >
                <el-option
                  v-for="(label, key) in readingStatusLabels"
                  :key="key"
                  :value="key"
                  :label="label"
                />
              </el-select>
            </div>
            <p class="reading-tip">
              {{ hasReadingRecord
                ? '状态变更会同步到「阅读管理」页面'
                : '选择状态后自动加入「阅读管理」' }}
            </p>
            <el-divider />
            <el-button type="primary" plain :icon="'EditPen'" class="aside-btn" @click="openNoteDialog">
              添加阅读笔记
            </el-button>
            <el-button :icon="'Notebook'" class="aside-btn" @click="goNotes">
              查看全部笔记
            </el-button>
          </div>

          <div class="ph-card aside-card">
            <div class="aside-title">快捷操作</div>
            <el-button :icon="'View'" class="aside-btn" @click="goReader">在线预览</el-button>
            <el-button :icon="'Download'" class="aside-btn" @click="download">下载 PDF</el-button>
            <el-button
              :icon="isFavorited ? 'StarFilled' : 'Star'"
              class="aside-btn"
              @click="toggleFavorite"
            >
              {{ isFavorited ? '取消收藏' : '加入收藏' }}
            </el-button>
          </div>
        </aside>
      </div>
    </template>

    <!-- 添加笔记弹窗 -->
    <el-dialog v-model="noteDialogVisible" title="添加阅读笔记" width="520px">
      <el-form label-position="top">
        <el-form-item label="笔记标题" required>
          <el-input v-model="noteForm.title" placeholder="请输入笔记标题" maxlength="80" />
        </el-form-item>
        <el-form-item label="笔记内容" required>
          <el-input
            v-model="noteForm.content"
            type="textarea"
            :rows="6"
            placeholder="记录论文的核心观点、实验方法或你的思考…"
            maxlength="2000"
            show-word-limit
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="noteDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="saveNote">保存笔记</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.detail-loading {
  height: 320px;
}
.notfound-card {
  padding: 40px 0;
}

.back-btn {
  margin-left: -8px;
  margin-bottom: 6px;
  color: var(--ph-text-muted);
}

.header-actions {
  display: flex;
  gap: 12px;
  align-items: flex-start;
}

.detail-layout {
  display: flex;
  gap: 20px;
  align-items: flex-start;
  padding-bottom: 24px;
}
.detail-main {
  flex: 1;
  min-width: 0;
}
.detail-card {
  padding: 24px 28px;
}

.detail-top {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.detail-section-title {
  font-size: 14px;
  font-weight: 700;
  color: var(--ph-text);
  margin: 22px 0 10px;
}
.abstract-text {
  font-size: 14px;
  line-height: 1.9;
  color: var(--ph-text-secondary);
  margin: 0;
  text-align: justify;
}

.meta-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 14px 24px;
}
.meta-item {
  display: flex;
  gap: 10px;
  font-size: 13px;
  line-height: 1.6;
}
.meta-label {
  color: var(--ph-text-muted);
  flex-shrink: 0;
  width: 68px;
}
.meta-value {
  color: var(--ph-text);
  word-break: break-all;
}

.keyword-wrap {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}
.keyword-chip {
  display: inline-flex;
  align-items: center;
  height: 24px;
  padding: 0 10px;
  border-radius: 6px;
  background: var(--ph-bg);
  border: 1px solid var(--ph-border);
  font-size: 12.5px;
  color: var(--ph-text-secondary);
}

.review-comment {
  margin-top: 22px;
  border: 1px solid #fecaca;
  background: #fef2f2;
  border-radius: 10px;
  padding: 12px 16px;
}
.review-comment-title {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  font-weight: 700;
  color: var(--ph-danger);
  margin-bottom: 6px;
}
.review-comment p {
  margin: 0;
  font-size: 13px;
  line-height: 1.7;
  color: #7f1d1d;
}

.detail-aside {
  width: 280px;
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  gap: 16px;
}
.aside-card {
  padding: 18px;
}
.aside-title {
  font-size: 15px;
  font-weight: 700;
  color: var(--ph-text);
  margin-bottom: 14px;
}
.reading-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.reading-label {
  font-size: 13.5px;
  color: var(--ph-text-secondary);
}
.reading-tip {
  font-size: 12px;
  color: var(--ph-text-muted);
  margin: 10px 0 0;
  line-height: 1.6;
}
.aside-btn {
  width: 100%;
  margin-left: 0 !important;
}
.aside-card .aside-btn + .aside-btn {
  margin-top: 10px;
}

@media (max-width: 1080px) {
  .detail-layout {
    flex-direction: column;
  }
  .detail-aside {
    width: 100%;
    flex-direction: row;
    flex-wrap: wrap;
  }
  .detail-aside .aside-card {
    flex: 1;
    min-width: 260px;
  }
}
</style>
