<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { AdminUser } from '@/services/adminService'
import * as admin from '@/services/adminService'
import type { UserStatus } from '@/types/user'

const router = useRouter()

const loading = ref(true)
const users = ref<AdminUser[]>([])

const query = reactive({
  keyword: '',
  status: 'all' as UserStatus | 'all',
  dateRange: null as [string, string] | null,
})

async function fetchUsers() {
  loading.value = true
  users.value = await admin.getUsers({
    keyword: query.keyword,
    status: query.status,
    dateRange: query.dateRange,
  })
  loading.value = false
}
onMounted(fetchUsers)

function resetFilters() {
  query.keyword = ''
  query.status = 'all'
  query.dateRange = null
  fetchUsers()
}

/* ---------------- 用户操作 ---------------- */
async function toggleStatus(u: AdminUser) {
  const disabling = u.status === 'active'
  try {
    await ElMessageBox.confirm(
      disabling
        ? `禁用后「${u.name}」将无法登录平台，确定继续吗？`
        : `确定恢复用户「${u.name}」的登录权限吗？`,
      disabling ? '禁用用户' : '恢复用户',
      {
        confirmButtonText: disabling ? '禁用' : '恢复',
        cancelButtonText: '取消',
        type: disabling ? 'warning' : 'info',
      },
    )
    await admin.setUserStatus(u.id, disabling ? 'disabled' : 'active')
    ElMessage.success(disabling ? '用户已禁用' : '用户已恢复')
    fetchUsers()
  } catch {
    /* 用户取消 */
  }
}

async function resetPassword(u: AdminUser) {
  try {
    await ElMessageBox.confirm(
      `确定重置「${u.name}」的密码吗？重置后原密码立即失效。`,
      '重置密码',
      { confirmButtonText: '重置', cancelButtonText: '取消', type: 'warning' },
    )
    const newPwd = await admin.resetUserPassword(u.id)
    await ElMessageBox.alert(
      `「${u.name}」的密码已重置为：${newPwd}（演示环境，请提醒用户首次登录后修改）`,
      '重置成功',
      { confirmButtonText: '知道了', type: 'success' },
    )
  } catch {
    /* 用户取消 */
  }
}

function goDetail(id: number) {
  router.push(`/admin/users/${id}`)
}
</script>

<template>
  <div class="ph-page">
    <div class="ph-page-header">
      <div>
        <h2 class="ph-page-title">用户管理</h2>
        <p class="ph-page-desc">平台用户列表与账号状态管理</p>
      </div>
    </div>

    <div class="ph-card users-card">
      <!-- 筛选区 -->
      <div class="filter-bar">
        <el-input
          v-model="query.keyword"
          placeholder="搜索姓名 / 用户名"
          clearable
          style="width: 200px"
          @change="fetchUsers"
        >
          <template #prefix><el-icon><Search /></el-icon></template>
        </el-input>
        <el-select
          v-model="query.status"
          placeholder="账号状态"
          style="width: 130px"
          @change="fetchUsers"
        >
          <el-option label="全部状态" value="all" />
          <el-option label="正常" value="active" />
          <el-option label="已禁用" value="disabled" />
        </el-select>
        <el-date-picker
          v-model="query.dateRange"
          type="daterange"
          value-format="YYYY-MM-DD"
          range-separator="至"
          start-placeholder="注册起"
          end-placeholder="注册止"
          style="width: 240px"
          @change="fetchUsers"
        />
        <el-button text :icon="'RefreshLeft'" @click="resetFilters">重置</el-button>
        <div class="filter-count">共 {{ users.length }} 名用户</div>
      </div>

      <div v-if="loading" v-loading="true" class="table-loading" />

      <template v-else>
        <el-table :data="users" style="width: 100%">
          <el-table-column label="用户" min-width="200">
            <template #default="{ row }">
              <div class="user-cell">
                <el-avatar :size="34" class="user-avatar">{{ row.name.charAt(0) }}</el-avatar>
                <div class="user-cell-main">
                  <div class="user-cell-name">
                    {{ row.name }}
                    <el-tag
                      v-if="row.role === 'admin'"
                      type="danger"
                      size="small"
                      effect="plain"
                      class="role-tag"
                    >
                      管理员
                    </el-tag>
                  </div>
                  <div class="user-cell-sub">@{{ row.username }}</div>
                </div>
              </div>
            </template>
          </el-table-column>
          <el-table-column prop="email" label="邮箱" min-width="180" show-overflow-tooltip />
          <el-table-column prop="registeredAt" label="注册时间" width="120">
            <template #default="{ row }">{{ row.registeredAt.slice(0, 10) }}</template>
          </el-table-column>
          <el-table-column label="贡献" width="90" align="center">
            <template #default="{ row }">
              <span class="count-num">{{ row.contributionCount }}</span>
            </template>
          </el-table-column>
          <el-table-column label="共享论文" width="90" align="center">
            <template #default="{ row }">
              <span class="count-num">{{ row.sharedCount }}</span>
            </template>
          </el-table-column>
          <el-table-column label="状态" width="90">
            <template #default="{ row }">
              <span
                class="ph-status"
                :class="row.status === 'active' ? 'ph-status--success' : 'ph-status--danger'"
              >
                {{ row.status === 'active' ? '正常' : '已禁用' }}
              </span>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="220" fixed="right">
            <template #default="{ row }">
              <el-button text type="primary" size="small" @click="goDetail(row.id)">详情</el-button>
              <el-button
                text
                :type="row.status === 'active' ? 'danger' : 'success'"
                size="small"
                @click="toggleStatus(row)"
              >
                {{ row.status === 'active' ? '禁用' : '恢复' }}
              </el-button>
              <el-button text type="warning" size="small" @click="resetPassword(row)">
                重置密码
              </el-button>
            </template>
          </el-table-column>
        </el-table>
      </template>
    </div>
  </div>
</template>

<style scoped>
.users-card {
  padding: 20px 24px;
  margin-bottom: 24px;
}
.table-loading {
  height: 300px;
}

.filter-bar {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
  margin-bottom: 16px;
}
.filter-count {
  margin-left: auto;
  font-size: 12.5px;
  color: var(--ph-text-muted);
}

.user-cell {
  display: flex;
  align-items: center;
  gap: 10px;
}
.user-avatar {
  background: var(--ph-primary-light);
  color: var(--ph-primary);
  font-weight: 600;
  flex-shrink: 0;
}
.user-cell-name {
  font-size: 14px;
  font-weight: 600;
  color: var(--ph-text);
  display: flex;
  align-items: center;
  gap: 6px;
}
.role-tag {
  transform: scale(0.9);
}
.user-cell-sub {
  font-size: 12px;
  color: var(--ph-text-muted);
  margin-top: 2px;
}
.count-num {
  font-size: 15px;
  font-weight: 700;
  color: var(--ph-text);
  font-variant-numeric: tabular-nums;
}
</style>
