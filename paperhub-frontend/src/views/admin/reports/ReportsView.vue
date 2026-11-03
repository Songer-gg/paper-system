<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import * as admin from '@/services/adminService'
import type { ReportItem } from '@/types/admin'
import { reportTypeLabels } from '@/types/admin'
import type { Paper } from '@/types/paper'
import { getUserName } from '@/mocks/users'

const router = useRouter()

const loading = ref(true)
const reports = ref<ReportItem[]>([])
const paperMap = ref<Map<number, Paper>>(new Map())
const status = ref<'all' | 'pending' | 'resolved'>('pending')

async function fetchReports() {
  loading.value = true
  const [list, papers] = await Promise.all([
    admin.getReports(status.value),
    admin.getSharedPapers({ title: '', doi: '', uploader: '', categoryId: null, status: 'all' }),
  ])
  paperMap.value = new Map(papers.map((p) => [p.id, p]))
  reports.value = list
  loading.value = false
}
onMounted(fetchReports)

function titleOf(id: number): string {
  return paperMap.value.get(id)?.title ?? `论文 #${id}`
}

/* ---------------- 处理举报弹窗 ---------------- */
const dialog = ref(false)
const current = ref<ReportItem | null>(null)
const resultText = ref('')
const offlineToo = ref(false)

function openResolve(r: ReportItem) {
  current.value = r
  resultText.value = ''
  offlineToo.value = false
  dialog.value = true
}

async function submitResolve() {
  if (!current.value) return
  const result = resultText.value.trim()
  if (!result) {
    ElMessage.warning('请填写处理结果')
    return
  }
  await admin.resolveReport(current.value.id, result, offlineToo.value)
  ElMessage.success('举报已处理')
  dialog.value = false
  fetchReports()
}

function goRecords() {
  router.push('/admin/reports/records')
}
</script>

<template>
  <div class="ph-page">
    <div class="ph-page-header">
      <div>
        <h2 class="ph-page-title">举报列表</h2>
        <p class="ph-page-desc">用户举报的受理与处理</p>
      </div>
      <el-button plain type="primary" @click="goRecords">处理记录</el-button>
    </div>

    <div class="ph-card reports-card">
      <div class="filter-bar">
        <el-radio-group v-model="status" @change="fetchReports">
          <el-radio-button value="pending">待处理</el-radio-button>
          <el-radio-button value="resolved">已处理</el-radio-button>
          <el-radio-button value="all">全部</el-radio-button>
        </el-radio-group>
        <div class="filter-count">共 {{ reports.length }} 条举报</div>
      </div>

      <div v-if="loading" v-loading="true" class="table-loading" />

      <template v-else>
        <el-table v-if="reports.length" :data="reports" style="width: 100%">
          <el-table-column label="举报单" width="80">
            <template #default="{ row }">#{{ row.id }}</template>
          </el-table-column>
          <el-table-column label="举报类型" width="140">
            <template #default="{ row }">
              <el-tag
                :type="row.type === 'copyright' ? 'danger' : row.type === 'content' ? 'warning' : 'info'"
                size="small"
                effect="light"
              >
                {{ reportTypeLabels[row.type] }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="被举报论文" min-width="220" show-overflow-tooltip>
            <template #default="{ row }">
              <span class="paper-title">{{ titleOf(row.paperId) }}</span>
            </template>
          </el-table-column>
          <el-table-column label="举报人" width="90">
            <template #default="{ row }">{{ getUserName(row.reporterId) }}</template>
          </el-table-column>
          <el-table-column label="举报原因" min-width="200" show-overflow-tooltip>
            <template #default="{ row }">{{ row.reason }}</template>
          </el-table-column>
          <el-table-column label="状态" width="90">
            <template #default="{ row }">
              <span
                class="ph-status"
                :class="row.status === 'pending' ? 'ph-status--warning' : 'ph-status--success'"
              >
                {{ row.status === 'pending' ? '待处理' : '已处理' }}
              </span>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="90" fixed="right">
            <template #default="{ row }">
              <el-button
                v-if="row.status === 'pending'"
                text
                type="primary"
                size="small"
                @click="openResolve(row)"
              >
                处理
              </el-button>
              <span v-else class="muted">已归档</span>
            </template>
          </el-table-column>
        </el-table>
        <el-empty v-else description="暂无举报" :image-size="100" />
      </template>
    </div>

    <!-- 处理举报弹窗 -->
    <el-dialog v-model="dialog" title="处理举报" width="480px">
      <template v-if="current">
        <div class="dialog-body">
          <el-descriptions :column="1" border size="small">
            <el-descriptions-item label="举报单">#{{ current.id }}</el-descriptions-item>
            <el-descriptions-item label="举报类型">
              {{ reportTypeLabels[current.type] }}
            </el-descriptions-item>
            <el-descriptions-item label="被举报论文">
              {{ titleOf(current.paperId) }}
            </el-descriptions-item>
            <el-descriptions-item label="举报原因">
              <span class="muted">{{ current.reason }}</span>
            </el-descriptions-item>
          </el-descriptions>

          <el-input
            v-model="resultText"
            type="textarea"
            :rows="3"
            placeholder="填写处理结果，如：核实举报属实 / 举报不成立等"
          />
          <el-checkbox v-model="offlineToo">
            同时下架被举报论文（从共享论文库移除）
          </el-checkbox>
        </div>
      </template>
      <template #footer>
        <el-button @click="dialog = false">取消</el-button>
        <el-button type="primary" @click="submitResolve">确认处理</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.reports-card {
  padding: 20px 24px;
  margin-bottom: 24px;
}
.table-loading {
  height: 300px;
}
.filter-bar {
  display: flex;
  align-items: center;
  margin-bottom: 16px;
}
.filter-count {
  margin-left: auto;
  font-size: 12.5px;
  color: var(--ph-text-muted);
}
.paper-title {
  color: var(--ph-text);
  font-weight: 500;
}
.muted {
  font-size: 12.5px;
  color: var(--ph-text-muted);
}

.dialog-body {
  display: flex;
  flex-direction: column;
  gap: 14px;
}
</style>
