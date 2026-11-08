<script setup lang="ts">
import { reportActionError } from '@/utils/errors'
import { onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import * as admin from '@/services/adminService'
import type { Paper } from '@/types/paper'
import { getCategory } from '@/services/knowledgeService'

const loading = ref(true)
const papers = ref<Paper[]>([])

async function fetchPapers() {
  loading.value = true
  try {
    papers.value = await admin.getSharedPapers({
      title: '',
      doi: '',
      uploader: '',
      categoryId: null,
      status: 'offline',
    })
  } finally {
    loading.value = false
  }
}
onMounted(fetchPapers)

async function restorePaper(p: Paper) {
  try {
    await ElMessageBox.confirm(
      `确定恢复《${p.title}》的共享状态吗？恢复后所有用户可再次访问。`,
      '恢复论文',
      { confirmButtonText: '恢复', cancelButtonText: '取消', type: 'info' },
    )
    await admin.restorePaper(p.id)
    ElMessage.success('论文已恢复上架')
    fetchPapers()
  } catch (error) {
    reportActionError(error)
  }
}

async function deletePaper(p: Paper) {
  try {
    await ElMessageBox.confirm(`删除《${p.title}》后不可恢复，确定删除吗？`, '删除违规资源', {
      confirmButtonText: '删除',
      cancelButtonText: '取消',
      type: 'error',
    })
    await admin.deletePaper(p.id)
    ElMessage.success('资源已删除')
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
        <h2 class="ph-page-title">论文下架管理</h2>
        <p class="ph-page-desc">已下架论文的查看、恢复与违规资源清理</p>
      </div>
    </div>

    <div class="ph-card offline-card">
      <el-alert
        v-if="!loading && papers.length"
        type="warning"
        :closable="false"
        show-icon
        :title="`当前共 ${papers.length} 篇已下架论文，用户端均不可访问`"
        class="banner"
      />

      <div v-if="loading" v-loading="true" class="table-loading" />

      <template v-else>
        <el-table v-if="papers.length" :data="papers" style="width: 100%">
          <el-table-column label="论文标题" min-width="280" show-overflow-tooltip>
            <template #default="{ row }">
              <span class="paper-title">{{ row.title }}</span>
            </template>
          </el-table-column>
          <el-table-column label="分类" width="120" show-overflow-tooltip>
            <template #default="{ row }">
              {{ getCategory(row.categoryId)?.name ?? '—' }}
            </template>
          </el-table-column>
          <el-table-column label="上传者" width="100">
            <template #default="{ row }">{{ row.uploaderName || '—' }}</template>
          </el-table-column>
          <el-table-column label="上传时间" width="110">
            <template #default="{ row }">{{ row.uploadedAt.slice(0, 10) }}</template>
          </el-table-column>
          <el-table-column label="状态" width="90">
            <template #default>
              <span class="ph-status ph-status--info">已下架</span>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="160" fixed="right">
            <template #default="{ row }">
              <el-button
                text
                type="success"
                size="small"
                :disabled="row.offlineReason === 'withdrawn'"
                @click="restorePaper(row)"
              >
                恢复上架
              </el-button>
              <el-button text type="danger" size="small" @click="deletePaper(row)">
                删除资源
              </el-button>
            </template>
          </el-table-column>
        </el-table>
        <el-empty v-else description="暂无已下架论文" :image-size="100" />
      </template>
    </div>
  </div>
</template>

<style scoped>
.offline-card {
  padding: 20px 24px;
  margin-bottom: 24px;
}
.banner {
  margin-bottom: 16px;
}
.table-loading {
  height: 280px;
}
.paper-title {
  color: var(--ph-text);
  font-weight: 500;
}
</style>
