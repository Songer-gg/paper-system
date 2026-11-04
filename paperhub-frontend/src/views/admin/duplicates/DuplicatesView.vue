<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import * as admin from '@/services/adminService'
import type { DuplicatePair } from '@/types/admin'
import type { Paper } from '@/types/paper'
import { useRouter } from 'vue-router'

const router = useRouter()

const loading = ref(true)
const pairs = ref<DuplicatePair[]>([])
const paperMap = ref<Map<number, Paper>>(new Map())

async function fetchPairs() {
  loading.value = true
  const [list, papers] = await Promise.all([
    admin.getDuplicatePairs('all'),
    admin.getSharedPapers({ title: '', doi: '', uploader: '', categoryId: null, status: 'all' }),
  ])
  paperMap.value = new Map(papers.map((p) => [p.id, p]))
  pairs.value = list
  loading.value = false
}
onMounted(fetchPairs)

const pendingPairs = () => pairs.value.filter((p) => p.status === 'pending')
const resolvedPairs = () => pairs.value.filter((p) => p.status !== 'pending')
const activeTab = ref<'pending' | 'resolved'>('pending')

function paperOf(id: number): Paper | undefined {
  return paperMap.value.get(id)
}

const statusLabels: Record<DuplicatePair['status'], string> = {
  pending: '待处理',
  duplicate: '确认重复',
  distinct: '不同论文',
  linked: '已关联原文',
}
const statusClass: Record<DuplicatePair['status'], string> = {
  pending: 'ph-status--warning',
  duplicate: 'ph-status--danger',
  distinct: 'ph-status--success',
  linked: 'ph-status--primary',
}

function statusLabel(s: DuplicatePair['status']): string {
  return statusLabels[s]
}

function statusCls(s: DuplicatePair['status']): string {
  return statusClass[s]
}

async function handlePair(pair: DuplicatePair, action: 'duplicate' | 'distinct' | 'linked') {
  const actionLabels = {
    duplicate: '确认为重复',
    distinct: '确认为不同论文',
    linked: '关联到已有资源',
  } as const
  try {
    const { value } = await ElMessageBox.prompt(
      action === 'duplicate'
        ? '确认后新上传论文将被驳回（禁止重复创建共享资源），可填写处理说明。'
        : action === 'linked'
          ? '确认后新上传论文将被驳回，并引导上传者引用已有共享资源，可填写处理说明。'
          : '确认后新上传论文将继续正常审核流程，可填写处理说明。',
      actionLabels[action],
      {
        confirmButtonText: '确认',
        cancelButtonText: '取消',
        inputPlaceholder: '处理说明（可选）',
        type: action === 'distinct' ? 'info' : 'warning',
      },
    )
    const note = value.trim() || actionLabels[action]
    if (action === 'duplicate') await admin.confirmDuplicate(pair.id, note)
    else if (action === 'distinct') await admin.confirmDistinct(pair.id, note)
    else await admin.linkToOriginal(pair.id, note)
    ElMessage.success('处理完成')
    fetchPairs()
  } catch {
    /* 用户取消 */
  }
}

function goRecords() {
  router.push('/admin/duplicates/records')
}
</script>

<template>
  <div class="ph-page">
    <div class="ph-page-header">
      <div>
        <h2 class="ph-page-title">疑似重复论文</h2>
        <p class="ph-page-desc">
          基于 DOI、标题相似度与文件哈希的比对提示（模拟结果），需人工判断处理
        </p>
      </div>
      <el-button plain type="primary" @click="goRecords">查看处理记录</el-button>
    </div>

    <div class="ph-card dup-card">
      <div v-if="loading" v-loading="true" class="table-loading" />

      <template v-else>
        <el-tabs v-model="activeTab">
          <el-tab-pane :label="`待处理（${pendingPairs().length}）`" name="pending" />
          <el-tab-pane :label="`已处理（${resolvedPairs().length}）`" name="resolved" />
        </el-tabs>

        <template v-if="activeTab === 'pending'">
          <div v-if="pendingPairs().length" class="pair-list">
            <div v-for="pair in pendingPairs()" :key="pair.id" class="pair-item">
              <div class="pair-head">
                <span class="pair-id">比对对 #{{ pair.id }}</span>
                <span class="pair-time">提交于 {{ pair.submittedAt }}</span>
              </div>
              <div class="pair-compare">
                <!-- 原论文 -->
                <div class="paper-box paper-box--original">
                  <div class="paper-box-tag">原共享论文</div>
                  <div class="paper-box-title">
                    {{ paperOf(pair.originalPaperId)?.title ?? `论文 #${pair.originalPaperId}` }}
                  </div>
                  <div v-if="paperOf(pair.originalPaperId)" class="paper-box-meta">
                    {{ paperOf(pair.originalPaperId)!.authors.join('、') }}
                  </div>
                  <div v-if="paperOf(pair.originalPaperId)" class="paper-box-meta">
                    DOI：{{ paperOf(pair.originalPaperId)!.doi || '—' }}
                  </div>
                </div>

                <!-- 比对结果 -->
                <div class="compare-mid">
                  <div class="compare-item" :class="pair.doiMatch ? 'is-match' : 'is-diff'">
                    <span class="compare-label">DOI 比对</span>
                    <span class="compare-value">{{ pair.doiMatch ? '完全一致' : '不一致' }}</span>
                  </div>
                  <div
                    class="compare-item"
                    :class="pair.titleSimilarity >= 80 ? 'is-match' : 'is-mid'"
                  >
                    <span class="compare-label">标题相似度</span>
                    <span class="compare-value">{{ pair.titleSimilarity }}%</span>
                  </div>
                  <div class="compare-item" :class="pair.fileHashMatch ? 'is-match' : 'is-diff'">
                    <span class="compare-label">文件哈希</span>
                    <span class="compare-value">{{
                      pair.fileHashMatch ? '完全一致' : '不一致'
                    }}</span>
                  </div>
                </div>

                <!-- 新论文 -->
                <div class="paper-box paper-box--new">
                  <div class="paper-box-tag">新上传论文</div>
                  <div class="paper-box-title">
                    {{ paperOf(pair.newPaperId)?.title ?? `论文 #${pair.newPaperId}` }}
                  </div>
                  <div v-if="paperOf(pair.newPaperId)" class="paper-box-meta">
                    {{ paperOf(pair.newPaperId)!.authors.join('、') }}
                  </div>
                  <div v-if="paperOf(pair.newPaperId)" class="paper-box-meta">
                    DOI：{{ paperOf(pair.newPaperId)!.doi || '—' }}
                  </div>
                </div>
              </div>
              <div class="pair-actions">
                <el-button type="danger" plain size="small" @click="handlePair(pair, 'duplicate')">
                  确认为重复
                </el-button>
                <el-button type="success" plain size="small" @click="handlePair(pair, 'distinct')">
                  确认为不同论文
                </el-button>
                <el-button type="primary" plain size="small" @click="handlePair(pair, 'linked')">
                  关联到已有资源
                </el-button>
              </div>
            </div>
          </div>
          <el-empty v-else description="暂无待处理的疑似重复论文" :image-size="100" />
        </template>

        <template v-else>
          <el-table v-if="resolvedPairs().length" :data="resolvedPairs()" style="width: 100%">
            <el-table-column label="比对对" width="100">
              <template #default="{ row }">#{{ row.id }}</template>
            </el-table-column>
            <el-table-column label="原共享论文" min-width="200" show-overflow-tooltip>
              <template #default="{ row }">
                {{ paperOf(row.originalPaperId)?.title ?? `论文 #${row.originalPaperId}` }}
              </template>
            </el-table-column>
            <el-table-column label="新上传论文" min-width="200" show-overflow-tooltip>
              <template #default="{ row }">
                {{ paperOf(row.newPaperId)?.title ?? `论文 #${row.newPaperId}` }}
              </template>
            </el-table-column>
            <el-table-column label="处理结果" width="110">
              <template #default="{ row }">
                <span class="ph-status" :class="statusCls(row.status)">{{
                  statusLabel(row.status)
                }}</span>
              </template>
            </el-table-column>
            <el-table-column label="处理说明" min-width="160" show-overflow-tooltip>
              <template #default="{ row }">{{ row.handlerNote || '—' }}</template>
            </el-table-column>
            <el-table-column label="处理时间" width="140">
              <template #default="{ row }">{{ row.handledAt }}</template>
            </el-table-column>
          </el-table>
          <el-empty v-else description="暂无已处理的比对记录" :image-size="100" />
        </template>
      </template>
    </div>
  </div>
</template>

<style scoped>
.dup-card {
  padding: 20px 24px;
  margin-bottom: 24px;
}
.table-loading {
  height: 320px;
}

.pair-list {
  display: flex;
  flex-direction: column;
  gap: 16px;
}
.pair-item {
  border: 1px solid var(--ph-border);
  border-radius: 10px;
  padding: 16px 18px;
}
.pair-head {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 14px;
}
.pair-id {
  font-size: 13px;
  font-weight: 700;
  color: var(--ph-primary);
}
.pair-time {
  font-size: 12px;
  color: var(--ph-text-muted);
}

.pair-compare {
  display: grid;
  grid-template-columns: 1fr 170px 1fr;
  gap: 14px;
  align-items: stretch;
}
.paper-box {
  border-radius: 8px;
  padding: 14px 16px;
  background: var(--ph-bg);
}
.paper-box--original {
  border: 1px solid var(--ph-border);
}
.paper-box--new {
  border: 1px dashed var(--ph-warning);
  background: #fffbeb;
}
.paper-box-tag {
  font-size: 11.5px;
  font-weight: 600;
  margin-bottom: 8px;
}
.paper-box--original .paper-box-tag {
  color: var(--ph-text-muted);
}
.paper-box--new .paper-box-tag {
  color: var(--ph-warning);
}
.paper-box-title {
  font-size: 13.5px;
  font-weight: 600;
  color: var(--ph-text);
  line-height: 1.5;
  word-break: break-all;
}
.paper-box-meta {
  margin-top: 6px;
  font-size: 12px;
  color: var(--ph-text-muted);
}

.compare-mid {
  display: flex;
  flex-direction: column;
  justify-content: center;
  gap: 8px;
}
.compare-item {
  border-radius: 8px;
  padding: 8px 10px;
  text-align: center;
  border: 1px solid var(--ph-border);
  background: #fff;
}
.compare-item.is-match {
  border-color: #fca5a5;
  background: #fef2f2;
}
.compare-item.is-mid {
  border-color: #fcd34d;
  background: #fffbeb;
}
.compare-item.is-diff {
  border-color: var(--ph-border);
  background: #f0fdf4;
}
.compare-label {
  display: block;
  font-size: 11px;
  color: var(--ph-text-muted);
}
.compare-value {
  display: block;
  margin-top: 2px;
  font-size: 13px;
  font-weight: 700;
  color: var(--ph-text);
}

.pair-actions {
  display: flex;
  gap: 8px;
  margin-top: 14px;
}

@media (max-width: 960px) {
  .pair-compare {
    grid-template-columns: 1fr;
  }
}
</style>
