<script setup lang="ts">
import { onMounted, ref } from 'vue'
import * as admin from '@/services/adminService'
import type { ReportItem } from '@/types/admin'
import { reportTypeLabels } from '@/types/admin'
import type { Paper } from '@/types/paper'
import { getUserName } from '@/mocks/users'

const loading = ref(true)
const records = ref<ReportItem[]>([])
const paperMap = ref<Map<number, Paper>>(new Map())

async function fetchRecords() {
  loading.value = true
  const [list, papers] = await Promise.all([
    admin.getReports('resolved'),
    admin.getSharedPapers({ title: '', doi: '', uploader: '', categoryId: null, status: 'all' }),
  ])
  paperMap.value = new Map(papers.map((p) => [p.id, p]))
  records.value = list
  loading.value = false
}
onMounted(fetchRecords)

function titleOf(id: number): string {
  return paperMap.value.get(id)?.title ?? `论文 #${id}`
}

function typeLabel(t: ReportItem['type']): string {
  return reportTypeLabels[t]
}
</script>

<template>
  <div class="ph-page">
    <div class="ph-page-header">
      <div>
        <h2 class="ph-page-title">举报处理记录</h2>
        <p class="ph-page-desc">已处理举报的结果归档记录</p>
      </div>
    </div>

    <div class="ph-card records-card">
      <div v-if="loading" v-loading="true" class="table-loading" />

      <el-table v-else-if="records.length" :data="records" style="width: 100%">
        <el-table-column label="举报单" width="80">
          <template #default="{ row }">#{{ row.id }}</template>
        </el-table-column>
        <el-table-column label="举报类型" width="140">
          <template #default="{ row }">{{ typeLabel(row.type) }}</template>
        </el-table-column>
        <el-table-column label="被举报论文" min-width="220" show-overflow-tooltip>
          <template #default="{ row }">{{ titleOf(row.paperId) }}</template>
        </el-table-column>
        <el-table-column label="举报人" width="90">
          <template #default="{ row }">{{ getUserName(row.reporterId) }}</template>
        </el-table-column>
        <el-table-column label="举报原因" min-width="180" show-overflow-tooltip>
          <template #default="{ row }">{{ row.reason }}</template>
        </el-table-column>
        <el-table-column label="处理结果" min-width="180" show-overflow-tooltip>
          <template #default="{ row }">
            <span class="result-text">{{ row.result || '—' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="处理时间" width="140">
          <template #default="{ row }">{{ row.handledAt }}</template>
        </el-table-column>
      </el-table>
      <el-empty v-else description="暂无已处理的举报" :image-size="100" />
    </div>
  </div>
</template>

<style scoped>
.records-card {
  padding: 20px 24px;
  margin-bottom: 24px;
}
.table-loading {
  height: 280px;
}
.result-text {
  color: var(--ph-text);
  font-size: 12.5px;
}
</style>
