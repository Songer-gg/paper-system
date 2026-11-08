<script setup lang="ts">
import { onMounted, ref } from 'vue'
import * as admin from '@/services/adminService'
import type { DuplicateRecord } from '@/types/admin'

const loading = ref(true)
const records = ref<DuplicateRecord[]>([])

const actionLabels: Record<DuplicateRecord['action'], string> = {
  duplicate: '确认重复',
  distinct: '不同论文',
  linked: '关联原文',
}
const actionTag: Record<DuplicateRecord['action'], 'danger' | 'success' | 'primary'> = {
  duplicate: 'danger',
  distinct: 'success',
  linked: 'primary',
}

function actionLabel(a: DuplicateRecord['action']): string {
  return actionLabels[a]
}

function actionTagType(a: DuplicateRecord['action']): 'danger' | 'success' | 'primary' {
  return actionTag[a]
}

async function fetchRecords() {
  loading.value = true
  try {
    records.value = await admin.getDuplicateRecords()
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
        <h2 class="ph-page-title">重复论文处理记录</h2>
        <p class="ph-page-desc">疑似重复论文的人工处理历史</p>
      </div>
    </div>

    <div class="ph-card records-card">
      <div v-if="loading" v-loading="true" class="table-loading" />

      <el-table v-else-if="records.length" :data="records" style="width: 100%">
        <el-table-column label="比对对" width="90">
          <template #default="{ row }">#{{ row.pairId }}</template>
        </el-table-column>
        <el-table-column label="论文" min-width="240" show-overflow-tooltip>
          <template #default="{ row }">
            <span class="paper-title">{{ row.paperTitle }}</span>
          </template>
        </el-table-column>
        <el-table-column label="处理动作" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="actionTagType(row.action)" size="small" effect="light">
              {{ actionLabel(row.action) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="处理说明" min-width="200" show-overflow-tooltip>
          <template #default="{ row }">
            <span class="muted">{{ row.note || '—' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="处理人" width="90">
          <template #default="{ row }">{{ row.handlerName || '—' }}</template>
        </el-table-column>
        <el-table-column prop="handledAt" label="处理时间" width="140" />
      </el-table>
      <el-empty v-else description="暂无处理记录" :image-size="100" />
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
.paper-title {
  color: var(--ph-text);
  font-weight: 500;
}
.muted {
  font-size: 12.5px;
  color: var(--ph-text-muted);
}
</style>
