<script setup lang="ts">
import { onMounted, ref } from 'vue'
import * as admin from '@/services/adminService'
import type { ReportItem } from '@/types/admin'
import { reportTypeLabels } from '@/types/admin'

const loading = ref(true)
const records = ref<ReportItem[]>([])

async function fetchRecords() {
  loading.value = true
  try {
    records.value = await admin.getReportRecords()
  } finally {
    loading.value = false
  }
}
onMounted(fetchRecords)

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
          <template #default="{ row }">{{ row.paperTitle || `论文 #${row.paperId}` }}</template>
        </el-table-column>
        <el-table-column label="举报人" width="90">
          <template #default="{ row }">{{ row.reporterName || '—' }}</template>
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
