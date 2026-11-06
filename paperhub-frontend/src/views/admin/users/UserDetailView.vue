<script setup lang="ts">
import { reportActionError } from '@/utils/errors'
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { AdminUser } from '@/services/adminService'
import * as admin from '@/services/adminService'
import type { Paper } from '@/types/paper'
import { readingStatusLabels, reviewStatusLabels } from '@/types/paper'
import { getCategory } from '@/services/knowledgeService'

const route = useRoute()
const router = useRouter()

const userId = Number(route.params.id)
const loading = ref(true)
const user = ref<AdminUser | null>(null)
const papers = ref<Paper[]>([])
const favorites = ref<Awaited<ReturnType<typeof admin.getUserFavorites>>>([])
const reading = ref<Awaited<ReturnType<typeof admin.getUserReadingRecords>>>([])
const notes = ref<Awaited<ReturnType<typeof admin.getUserNotes>>>([])
const personalLoaded = ref(false)

async function loadPersonalData() {
  const result = await Promise.all([
    admin.getUserFavorites(userId),
    admin.getUserReadingRecords(userId),
    admin.getUserNotes(userId),
  ])
  favorites.value = result[0]
  reading.value = result[1]
  notes.value = result[2]
  personalLoaded.value = true
}

const statusClass = computed(() =>
  user.value?.status === 'active' ? 'ph-status--success' : 'ph-status--danger',
)

async function fetchDetail() {
  loading.value = true
  try {
    user.value = await admin.getUserDetail(userId)
    papers.value = await admin.getUserPapers(userId)
  } finally {
    loading.value = false
  }
}
onMounted(fetchDetail)

function statusLabel(p: Paper): string {
  if (p.visibility === 'private') return '私有 · 无需审核'
  return reviewStatusLabels[p.reviewStatus ?? 'pending']
}

function statusTagType(p: Paper) {
  switch (p.reviewStatus) {
    case 'approved':
      return 'success'
    case 'pending':
      return 'warning'
    case 'rejected':
      return 'danger'
    default:
      return 'info'
  }
}

async function toggleStatus() {
  if (!user.value) return
  const disabling = user.value.status === 'active'
  try {
    await ElMessageBox.confirm(
      disabling
        ? `禁用后「${user.value.name}」将无法登录平台，确定继续吗？`
        : `确定恢复用户「${user.value.name}」的登录权限吗？`,
      disabling ? '禁用用户' : '恢复用户',
      {
        confirmButtonText: disabling ? '禁用' : '恢复',
        cancelButtonText: '取消',
        type: disabling ? 'warning' : 'info',
      },
    )
    await admin.setUserStatus(userId, disabling ? 'disabled' : 'active')
    ElMessage.success(disabling ? '用户已禁用' : '用户已恢复')
    fetchDetail()
  } catch (error) {
    reportActionError(error)
  }
}

async function resetPassword() {
  if (!user.value) return
  try {
    await ElMessageBox.confirm(
      `确定重置「${user.value.name}」的密码吗？重置后原密码立即失效。`,
      '重置密码',
      { confirmButtonText: '重置', cancelButtonText: '取消', type: 'warning' },
    )
    const newPwd = await admin.resetUserPassword(userId)
    await ElMessageBox.alert(
      `「${user.value.name}」的密码已重置为：${newPwd}（演示环境，请提醒用户首次登录后修改）`,
      '重置成功',
      { confirmButtonText: '知道了', type: 'success' },
    )
    fetchDetail()
  } catch (error) {
    reportActionError(error)
  }
}

function goBack() {
  router.push('/admin/users')
}
</script>

<template>
  <div class="ph-page">
    <div class="ph-page-header">
      <div>
        <h2 class="ph-page-title">用户详情</h2>
        <p class="ph-page-desc">查看用户资料、全部论文及个人阅读数据</p>
      </div>
      <el-button :icon="'ArrowLeft'" @click="goBack">返回列表</el-button>
    </div>

    <div v-if="loading" v-loading="true" class="page-loading" />

    <template v-else-if="user">
      <div class="detail-grid">
        <!-- 左列：资料卡 -->
        <div class="detail-left">
          <div class="ph-card profile-card">
            <el-avatar :size="64" class="profile-avatar">{{ user.name.charAt(0) }}</el-avatar>
            <div class="profile-name">
              {{ user.name }}
              <el-tag v-if="user.role === 'admin'" type="danger" size="small" effect="plain">
                管理员
              </el-tag>
            </div>
            <div class="profile-username">@{{ user.username }}</div>
            <span class="ph-status" :class="statusClass">
              {{ user.status === 'active' ? '正常' : '已禁用' }}
            </span>

            <el-descriptions :column="1" border class="profile-desc">
              <el-descriptions-item label="用户 ID">{{ user.id }}</el-descriptions-item>
              <el-descriptions-item label="邮箱">{{ user.email }}</el-descriptions-item>
              <el-descriptions-item label="注册时间">
                {{ user.registeredAt.slice(0, 10) }}
              </el-descriptions-item>
              <el-descriptions-item label="密码重置">
                <span v-if="user.passwordResetAt" class="reset-badge">已重置</span>
                <span v-else class="ph-text-muted">未重置</span>
              </el-descriptions-item>
            </el-descriptions>

            <div class="profile-actions">
              <el-button
                :type="user.status === 'active' ? 'danger' : 'success'"
                plain
                @click="toggleStatus"
              >
                {{ user.status === 'active' ? '禁用账号' : '恢复账号' }}
              </el-button>
              <el-button type="warning" plain @click="resetPassword">重置密码</el-button>
            </div>
          </div>

          <div class="contribution-row">
            <div class="ph-card contrib-card">
              <div class="contrib-num">{{ user.contributionCount }}</div>
              <div class="contrib-label">审核通过贡献</div>
            </div>
            <div class="ph-card contrib-card">
              <div class="contrib-num">{{ user.sharedCount }}</div>
              <div class="contrib-label">共享论文总数</div>
            </div>
          </div>

          <el-alert
            type="info"
            :closable="false"
            show-icon
            title="管理员数据访问"
            description="管理员可查看私有论文及个人数据。查看个人阅读数据时记录访问对象与时间；删除的论文文件不可访问。"
          />
        </div>

        <!-- 右列：共享论文列表 -->
        <div class="ph-card papers-card">
          <div class="papers-head">
            <h3 class="ph-section-title">全部论文（{{ papers.length }}）</h3>
          </div>
          <el-table v-if="papers.length" :data="papers" style="width: 100%">
            <el-table-column label="论文标题" min-width="260" show-overflow-tooltip>
              <template #default="{ row }">
                <span class="paper-title">{{ row.title }}</span>
              </template>
            </el-table-column>
            <el-table-column label="分类" width="130" show-overflow-tooltip>
              <template #default="{ row }">
                {{ getCategory(row.categoryId)?.name ?? '—' }}
              </template>
            </el-table-column>
            <el-table-column label="审核状态" width="110">
              <template #default="{ row }">
                <el-tag :type="statusTagType(row)" size="small" effect="light">
                  {{ statusLabel(row) }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="上传时间" width="110">
              <template #default="{ row }">{{ row.uploadedAt.slice(0, 10) }}</template>
            </el-table-column>
            <el-table-column label="操作" width="90">
              <template #default="{ row }">
                <el-button text @click="router.push(`/admin/papers/${row.id}/reader`)"
                  >预览</el-button
                >
              </template>
            </el-table-column>
          </el-table>
          <el-empty v-else description="该用户暂无论文" :image-size="90" />
          <el-divider />
          <el-button v-if="!personalLoaded" @click="loadPersonalData">查看个人阅读数据</el-button>
          <el-tabs v-else>
            <el-tab-pane :label="`收藏（${favorites.length}）`">
              <el-table :data="favorites">
                <el-table-column label="论文"
                  ><template #default="{ row }">{{
                    row.paper?.title ?? '原论文已删除'
                  }}</template></el-table-column
                >
                <el-table-column prop="favoritedAt" label="收藏时间" />
              </el-table>
            </el-tab-pane>
            <el-tab-pane :label="`阅读记录（${reading.length}）`">
              <el-table :data="reading">
                <el-table-column label="论文"
                  ><template #default="{ row }">{{
                    row.paper?.title ?? '原论文已删除'
                  }}</template></el-table-column
                >
                <el-table-column label="状态"
                  ><template #default="{ row }">{{
                    readingStatusLabels[row.status as keyof typeof readingStatusLabels]
                  }}</template></el-table-column
                >
                <el-table-column prop="lastReadAt" label="最近阅读" />
              </el-table>
            </el-tab-pane>
            <el-tab-pane :label="`私人笔记（${notes.length}）`">
              <el-collapse>
                <el-collapse-item
                  v-for="note in notes"
                  :key="note.id"
                  :title="note.title"
                  :name="note.id"
                >
                  <p>{{ note.paperTitle ?? `论文 #${note.paperId}` }}</p>
                  <div style="white-space: pre-wrap">{{ note.content }}</div>
                </el-collapse-item>
              </el-collapse>
            </el-tab-pane>
          </el-tabs>
        </div>
      </div>
    </template>

    <el-empty v-else description="未找到该用户" :image-size="100">
      <el-button type="primary" @click="goBack">返回用户列表</el-button>
    </el-empty>
  </div>
</template>

<style scoped>
.page-loading {
  height: 320px;
}

.detail-grid {
  display: grid;
  grid-template-columns: 320px 1fr;
  gap: 20px;
  align-items: start;
}
.detail-left {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.profile-card {
  padding: 28px 24px;
  text-align: center;
}
.profile-avatar {
  background: var(--ph-primary-light);
  color: var(--ph-primary);
  font-size: 26px;
  font-weight: 700;
}
.profile-name {
  margin-top: 14px;
  font-size: 18px;
  font-weight: 700;
  color: var(--ph-text);
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
}
.profile-username {
  margin-top: 4px;
  font-size: 13px;
  color: var(--ph-text-muted);
}
.profile-card .ph-status {
  margin-top: 12px;
}
.profile-desc {
  margin-top: 18px;
  text-align: left;
}
.reset-badge {
  color: var(--ph-warning);
  font-weight: 600;
  font-size: 12.5px;
}
.ph-text-muted {
  color: var(--ph-text-muted);
  font-size: 12.5px;
}
.profile-actions {
  display: flex;
  gap: 10px;
  margin-top: 18px;
}
.profile-actions .el-button {
  flex: 1;
}

.contribution-row {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
}
.contrib-card {
  padding: 18px 16px;
  text-align: center;
}
.contrib-num {
  font-size: 26px;
  font-weight: 800;
  color: var(--ph-primary);
  font-variant-numeric: tabular-nums;
}
.contrib-label {
  margin-top: 4px;
  font-size: 12.5px;
  color: var(--ph-text-muted);
}

.papers-card {
  padding: 20px 24px;
}
.papers-head {
  margin-bottom: 14px;
}
.paper-title {
  color: var(--ph-text);
  font-weight: 500;
}

@media (max-width: 1080px) {
  .detail-grid {
    grid-template-columns: 1fr;
  }
}
</style>
