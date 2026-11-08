<script setup lang="ts">
import { onMounted, ref } from 'vue'
import * as admin from '@/services/adminService'
import type { OperationLog } from '@/types/admin'

const loading = ref(true)
const logs = ref<OperationLog[]>([])
const action = ref('all')

const actionOptions = [
  { label: '全部类型', value: 'all' },
  { label: '用户禁用', value: '用户禁用' },
  { label: '用户恢复', value: '用户恢复' },
  { label: '重置密码', value: '重置密码' },
  { label: '论文下架', value: '论文下架' },
  { label: '论文恢复', value: '论文恢复' },
  { label: '删除违规资源', value: '删除违规资源' },
  { label: '论文审核', value: '论文审核' },
  { label: '重复论文处理', value: '重复论文处理' },
  { label: '举报处理', value: '举报处理' },
  { label: '敏感数据访问', value: '敏感数据访问' },
]

async function fetchLogs() {
  loading.value = true
  try {
    logs.value = await admin.getLogs(action.value)
  } finally {
    loading.value = false
  }
}
onMounted(fetchLogs)
</script>

<template>
  <div class="ph-page">
    <div class="ph-page-header">
      <div>
        <h2 class="ph-page-title">操作日志</h2>
        <p class="ph-page-desc">平台管理员操作的历史留痕</p>
      </div>
    </div>

    <div class="ph-card logs-card">
      <div class="filter-bar">
        <el-select v-model="action" style="width: 170px" @change="fetchLogs">
          <el-option
            v-for="opt in actionOptions"
            :key="opt.value"
            :label="opt.label"
            :value="opt.value"
          />
        </el-select>
        <div class="filter-count">共 {{ logs.length }} 条日志</div>
      </div>

      <div v-if="loading" v-loading="true" class="table-loading" />

      <el-table v-else :data="logs" empty-text="暂无符合条件的操作日志" style="width: 100%">
        <el-table-column prop="userName" label="操作用户" width="110" />
        <el-table-column prop="action" label="操作类型" width="140">
          <template #default="{ row }">
            <span class="action-chip">{{ row.action }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="target" label="操作对象" min-width="260" show-overflow-tooltip />
        <el-table-column label="操作结果" width="90" align="center">
          <template #default="{ row }">
            <span
              class="ph-status"
              :class="row.result === '成功' ? 'ph-status--success' : 'ph-status--danger'"
            >
              {{ row.result }}
            </span>
          </template>
        </el-table-column>
        <el-table-column prop="time" label="操作时间" width="150" />
      </el-table>
    </div>
  </div>
</template>

<style scoped>
.logs-card {
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
.action-chip {
  font-size: 12.5px;
  color: var(--ph-primary);
  background: var(--ph-primary-light);
  border-radius: 5px;
  padding: 2px 8px;
}
</style>
