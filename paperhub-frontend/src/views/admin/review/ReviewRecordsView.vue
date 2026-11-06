<script setup lang="ts">
import { onMounted, ref } from 'vue'
import * as admin from '@/services/adminService'
import type { ReviewRecord } from '@/types/admin'
import { getUserName } from '@/mocks/users'

const loading = ref(true)
const records = ref<ReviewRecord[]>([])
const action = ref<'all' | 'approved' | 'rejected'>('all')

async function fetchRecords() {
  loading.value = true
  try {
    records.value = await admin.getReviewRecords(action.value)
  } finally {
    loading.value = false
  }
}
onMounted(fetchRecords)
</script>

<template>
  <div class="ph-page">
    <div class="ph-page-header">
      <div>
        <h2 class="ph-page-title">审核记录</h2>
        <p class="ph-page-desc">平台全部论文审核操作的历史记录</p>
      </div>
    </div>

    <div class="ph-card records-card">
      <div class="filter-bar">
        <el-radio-group v-model="action" @change="fetchRecords">
          <el-radio-button value="all">全部</el-radio-button>
          <el-radio-button value="approved">仅通过</el-radio-button>
          <el-radio-button value="rejected">仅驳回</el-radio-button>
        </el-radio-group>
        <div class="filter-count">共 {{ records.length }} 条记录</div>
      </div>

      <div v-if="loading" v-loading="true" class="table-loading" />

      <el-table v-else :data="records" empty-text="暂无符合条件的审核记录" style="width: 100%">
        <el-table-column label="论文标题" min-width="260" show-overflow-tooltip>
          <template #default="{ row }">
            <span class="paper-title">{{ row.paperTitle }}</span>
          </template>
        </el-table-column>
        <el-table-column label="上传者" width="100">
          <template #default="{ row }">{{
            row.uploaderName ?? getUserName(row.uploaderId)
          }}</template>
        </el-table-column>
        <el-table-column label="审核动作" width="90" align="center">
          <template #default="{ row }">
            <el-tag
              :type="row.action === 'approved' ? 'success' : 'danger'"
              size="small"
              effect="light"
            >
              {{ row.action === 'approved' ? '通过' : '驳回' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="审核意见" min-width="220" show-overflow-tooltip>
          <template #default="{ row }">
            <span class="comment">{{ row.comment || '—' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="审核人" width="90">
          <template #default="{ row }">{{
            row.reviewerName ?? getUserName(row.reviewerId)
          }}</template>
        </el-table-column>
        <el-table-column prop="reviewedAt" label="审核时间" width="140" />
      </el-table>
    </div>
  </div>
</template>

<style scoped>
.records-card {
  padding: 20px 24px;
  margin-bottom: 24px;
}
.table-loading {
  height: 320px;
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
.comment {
  font-size: 12.5px;
  color: var(--ph-text-muted);
}
</style>
