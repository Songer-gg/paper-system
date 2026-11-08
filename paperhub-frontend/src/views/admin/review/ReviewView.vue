<script setup lang="ts">
import { reportActionError } from '@/utils/errors'
import { computed, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import * as admin from '@/services/adminService'
import type { Paper } from '@/types/paper'
import { reviewStatusLabels } from '@/types/paper'
import { getCategory } from '@/services/knowledgeService'
import type { DuplicatePair } from '@/types/admin'

const loading = ref(true)
const activeTab = ref<'pending' | 'approved' | 'rejected'>('pending')
const groups = ref<{ pending: Paper[]; approved: Paper[]; rejected: Paper[] }>({
  pending: [],
  approved: [],
  rejected: [],
})
/** 疑似重复对，用于审核时提示 */
let duplicatePairs: DuplicatePair[] = []

async function fetchPapers() {
  loading.value = true
  try {
    groups.value = await admin.getReviewPapers()
    duplicatePairs = await admin.getDuplicatePairs('pending')
  } finally {
    loading.value = false
  }
}
onMounted(fetchPapers)

const currentList = computed(() => groups.value[activeTab.value])

function statusLabel(p: Paper): string {
  return reviewStatusLabels[p.reviewStatus ?? 'pending']
}

function pendingDuplicateOf(p: Paper): DuplicatePair | undefined {
  return duplicatePairs.find((d) => d.newPaperId === p.id)
}

/* ---------------- 审核抽屉 ---------------- */
const drawer = ref(false)
const current = ref<Paper | null>(null)
const copyrightConfirmed = ref(false)
const comment = ref('')

function openReview(p: Paper) {
  current.value = p
  copyrightConfirmed.value = false
  comment.value = ''
  drawer.value = true
}

const currentDuplicate = computed(() =>
  current.value ? pendingDuplicateOf(current.value) : undefined,
)

async function approve() {
  if (!current.value) return
  if (!copyrightConfirmed.value) {
    ElMessage.warning('请先阅读并确认版权声明后再通过审核')
    return
  }
  await admin.approvePaper(
    current.value.id,
    comment.value.trim() || '符合共享规范，审核通过。',
    copyrightConfirmed.value,
  )
  ElMessage.success('审核通过，论文已进入共享论文库')
  drawer.value = false
  fetchPapers()
}

async function reject() {
  if (!current.value) return
  const reason = comment.value.trim()
  if (!reason) {
    ElMessage.warning('请填写驳回原因')
    return
  }
  try {
    await ElMessageBox.confirm(
      `确定驳回《${current.value.title}》吗？驳回原因将反馈给上传者。`,
      '驳回论文',
      { confirmButtonText: '驳回', cancelButtonText: '取消', type: 'warning' },
    )
    await admin.rejectPaper(current.value.id, reason)
    ElMessage.success('已驳回该论文')
    drawer.value = false
    fetchPapers()
  } catch (error) {
    reportActionError(error)
  }
}
</script>

<template>
  <div class="ph-page">
    <div class="ph-page-header">
      <div>
        <h2 class="ph-page-title">论文审核</h2>
        <p class="ph-page-desc">审核用户提交的共享论文，通过后进入共享论文库</p>
      </div>
    </div>

    <div class="ph-card review-card">
      <div v-if="loading" v-loading="true" class="table-loading" />

      <template v-else>
        <el-tabs v-model="activeTab">
          <el-tab-pane :label="`待审核（${groups.pending.length}）`" name="pending" />
          <el-tab-pane :label="`已通过（${groups.approved.length}）`" name="approved" />
          <el-tab-pane :label="`已驳回（${groups.rejected.length}）`" name="rejected" />
        </el-tabs>

        <el-table v-if="currentList.length" :data="currentList" style="width: 100%">
          <el-table-column label="论文标题" min-width="260" show-overflow-tooltip>
            <template #default="{ row }">
              <span class="paper-title">
                {{ row.title }}
                <el-tag
                  v-if="pendingDuplicateOf(row)"
                  type="danger"
                  size="small"
                  effect="plain"
                  class="dup-flag"
                >
                  疑似重复
                </el-tag>
              </span>
            </template>
          </el-table-column>
          <el-table-column label="上传者" width="100">
            <template #default="{ row }">{{ row.uploaderName || '—' }}</template>
          </el-table-column>
          <el-table-column label="作者" min-width="150" show-overflow-tooltip>
            <template #default="{ row }">{{ row.authors.join('、') }}</template>
          </el-table-column>
          <el-table-column prop="journal" label="期刊" min-width="140" show-overflow-tooltip />
          <el-table-column prop="year" label="年份" width="70" align="center" />
          <el-table-column label="提交时间" width="110">
            <template #default="{ row }">{{ row.uploadedAt.slice(0, 10) }}</template>
          </el-table-column>
          <el-table-column label="状态" width="105">
            <template #default="{ row }">
              <el-tag
                :type="
                  row.reviewStatus === 'approved'
                    ? 'success'
                    : row.reviewStatus === 'rejected'
                      ? 'danger'
                      : 'warning'
                "
                size="small"
                effect="light"
              >
                {{ statusLabel(row) }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="100" fixed="right">
            <template #default="{ row }">
              <el-button
                v-if="row.reviewStatus === 'pending'"
                text
                type="primary"
                size="small"
                @click="openReview(row)"
              >
                审核
              </el-button>
              <span v-else-if="row.reviewComment" class="reject-reason" :title="row.reviewComment">
                驳回原因
              </span>
              <span v-else class="pass-mark">已入库</span>
            </template>
          </el-table-column>
        </el-table>
        <el-empty
          v-else
          :description="`暂无${activeTab === 'pending' ? '待审核' : activeTab === 'approved' ? '已通过' : '已驳回'}论文`"
          :image-size="100"
        />
      </template>
    </div>

    <!-- 审核详情抽屉 -->
    <el-drawer v-model="drawer" :title="current ? `审核：${current.title}` : '审核'" size="520px">
      <div v-if="current" class="drawer-body">
        <!-- 重复检测提示 -->
        <el-alert
          v-if="currentDuplicate"
          type="warning"
          :closable="false"
          show-icon
          title="重复检测提示"
          :description="`该论文与共享库已有资源疑似重复（标题相似度 ${currentDuplicate.titleSimilarity}%${currentDuplicate.doiMatch ? '，DOI 一致' : ''}${currentDuplicate.fileHashMatch ? '，文件哈希一致' : ''}）。请先前往“重复论文管理”处理后再决定是否通过。`"
        />

        <!-- 论文元数据 -->
        <div class="ph-section-title meta-title">论文元数据</div>
        <el-descriptions :column="1" border>
          <el-descriptions-item label="标题">{{ current.title }}</el-descriptions-item>
          <el-descriptions-item label="作者">
            {{ current.authors.join('、') }}
          </el-descriptions-item>
          <el-descriptions-item label="摘要">
            <span class="muted-text">{{ current.abstract }}</span>
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
            {{ current.uploaderName || '—' }}
          </el-descriptions-item>
          <el-descriptions-item label="提交时间">{{ current.uploadedAt }}</el-descriptions-item>
        </el-descriptions>

        <!-- PDF 预览（审核授权范围内） -->
        <div class="ph-section-title meta-title">PDF 预览</div>
        <div class="pdf-preview">
          <el-icon :size="30" class="pdf-icon"><Document /></el-icon>
          <div class="pdf-name">{{ current.title }}.pdf</div>
          <div class="pdf-meta">{{ current.pageCount }} 页 · {{ current.fileSizeMB }} MB</div>
          <el-button type="primary" @click="$router.push(`/admin/papers/${current.id}/reader`)"
            >预览 PDF</el-button
          >
          <div class="pdf-auth">仅限审核授权范围内预览，不得用于其他用途</div>
        </div>

        <!-- 版权声明确认 -->
        <el-checkbox v-model="copyrightConfirmed" class="copyright-check">
          我已确认上传者声明确认其拥有分享该文件的适当权利，且论文内容不违反平台共享规范。
        </el-checkbox>

        <!-- 审核意见 -->
        <div class="ph-section-title meta-title">审核意见</div>
        <el-input
          v-model="comment"
          type="textarea"
          :rows="3"
          placeholder="填写审核意见；驳回时必填原因，将反馈给上传者"
        />
      </div>

      <template #footer>
        <div v-if="current" class="drawer-footer">
          <el-button @click="drawer = false">取消</el-button>
          <el-button type="danger" plain @click="reject">驳回</el-button>
          <el-button type="primary" @click="approve">审核通过</el-button>
        </div>
      </template>
    </el-drawer>
  </div>
</template>

<style scoped>
.review-card {
  padding: 20px 24px;
  margin-bottom: 24px;
}
.table-loading {
  height: 360px;
}

.paper-title {
  color: var(--ph-text);
  font-weight: 500;
  display: inline-flex;
  align-items: center;
  gap: 6px;
}
.dup-flag {
  transform: scale(0.85);
}
.reject-reason {
  font-size: 12.5px;
  color: var(--ph-error);
  cursor: help;
}
.pass-mark {
  font-size: 12.5px;
  color: var(--ph-text-muted);
}

.drawer-body {
  display: flex;
  flex-direction: column;
  gap: 14px;
}
.meta-title {
  margin-top: 4px;
}
.muted-text {
  font-size: 12.5px;
  color: var(--ph-text-muted);
  line-height: 1.6;
}

.pdf-preview {
  border: 1px dashed var(--ph-border);
  border-radius: 8px;
  padding: 26px 16px;
  text-align: center;
  background: var(--ph-bg);
}
.pdf-icon {
  color: var(--ph-primary);
}
.pdf-name {
  margin-top: 8px;
  font-size: 13px;
  font-weight: 600;
  color: var(--ph-text);
  word-break: break-all;
}
.pdf-meta {
  margin-top: 4px;
  font-size: 12px;
  color: var(--ph-text-muted);
}
.pdf-auth {
  margin-top: 10px;
  font-size: 12px;
  color: var(--ph-warning);
}

.copyright-check {
  font-size: 13px;
  line-height: 1.6;
}

.drawer-footer {
  display: flex;
  justify-content: flex-end;
  gap: 4px;
}
</style>
