<script setup lang="ts">
import { reportActionError } from '@/utils/errors'
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useAuthStore } from '@/stores/auth'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()

interface MenuItem {
  label: string
  icon: string
  path?: string
}

interface MenuGroup {
  label?: string
  items: MenuItem[]
}

/* 管理员端导航（对应规划书 6.1 节） */
const menuGroups: MenuGroup[] = [
  {
    items: [{ label: '管理首页', icon: 'Odometer', path: '/admin/dashboard' }],
  },
  {
    label: '用户管理',
    items: [{ label: '用户列表', icon: 'UserFilled', path: '/admin/users' }],
  },
  {
    label: '论文资源管理',
    items: [
      { label: '共享论文列表', icon: 'Reading', path: '/admin/papers' },
      { label: '论文下架管理', icon: 'SoldOut', path: '/admin/papers/offline' },
    ],
  },
  {
    label: '内容审核',
    items: [
      { label: '待审核论文', icon: 'DocumentChecked', path: '/admin/review' },
      { label: '审核记录', icon: 'Tickets', path: '/admin/review/records' },
    ],
  },
  {
    label: '重复论文管理',
    items: [
      { label: '疑似重复论文', icon: 'CopyDocument', path: '/admin/duplicates' },
      { label: '处理记录', icon: 'List', path: '/admin/duplicates/records' },
    ],
  },
  {
    label: '分类与标签',
    items: [
      { label: '平台分类管理', icon: 'Files', path: '/admin/categories' },
      { label: '平台标签管理', icon: 'PriceTag', path: '/admin/tags' },
    ],
  },
  {
    label: '举报管理',
    items: [
      { label: '举报列表', icon: 'Warning', path: '/admin/reports' },
      { label: '举报处理记录', icon: 'Finished', path: '/admin/reports/records' },
    ],
  },
  {
    label: '系统管理',
    items: [
      { label: '操作日志', icon: 'Document', path: '/admin/logs' },
      { label: '系统设置', icon: 'Setting', path: '/admin/settings' },
    ],
  },
]

function isActive(path: string) {
  return route.path === path
}

const breadcrumbItems = computed(() => {
  const group = route.meta?.group as string | undefined
  const title = (route.meta?.title as string) ?? ''
  if (!group || group === title) return [title]
  return [group, title]
})

async function handleLogout() {
  try {
    await ElMessageBox.confirm('确定要退出管理员账号吗？', '退出登录', {
      confirmButtonText: '退出',
      cancelButtonText: '取消',
      type: 'warning',
    })
    auth.logout()
    ElMessage.success('已安全退出')
    router.push('/login')
  } catch (error) {
    reportActionError(error)
  }
}
</script>

<template>
  <div class="admin-layout">
    <!-- 深色侧边栏：与用户端白色侧栏形成明确的界面区分 -->
    <aside class="admin-aside">
      <div class="admin-brand">
        <div class="admin-mark">
          <el-icon :size="19"><Monitor /></el-icon>
        </div>
        <div class="admin-brand-text">
          <span class="admin-name">PaperHub 管理端</span>
          <span class="admin-sub">平台运营 · 内容治理</span>
        </div>
      </div>

      <nav class="admin-menu">
        <template v-for="(group, gi) in menuGroups" :key="gi">
          <div v-if="group.label" class="admin-group-label">{{ group.label }}</div>
          <router-link
            v-for="item in group.items"
            :key="item.path"
            :to="item.path!"
            class="admin-item"
            :class="{ active: isActive(item.path!) }"
          >
            <el-icon :size="16"><component :is="item.icon" /></el-icon>
            <span>{{ item.label }}</span>
          </router-link>
        </template>
      </nav>

      <div class="admin-aside-footer">
        <el-icon :size="13"><Lock /></el-icon>
        仅限授权管理员访问
      </div>
    </aside>

    <div class="admin-main">
      <header class="admin-header">
        <el-breadcrumb separator="/" class="admin-breadcrumb">
          <el-breadcrumb-item v-for="(item, i) in breadcrumbItems" :key="i">
            {{ item }}
          </el-breadcrumb-item>
        </el-breadcrumb>

        <div class="admin-header-right">
          <el-tag effect="dark" class="admin-role-tag" round>管理员</el-tag>
          <el-dropdown trigger="click" @command="handleLogout">
            <div class="admin-user-chip">
              <el-avatar :size="30" class="admin-avatar">
                {{ auth.user?.name?.charAt(0) }}
              </el-avatar>
              <span class="admin-user-name">{{ auth.user?.name }}</span>
              <el-icon :size="12" color="#94a3b8"><CaretBottom /></el-icon>
            </div>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item :icon="'SwitchButton'" command="logout">
                  退出登录
                </el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </header>

      <main class="admin-content">
        <router-view />
      </main>
    </div>
  </div>
</template>

<style scoped>
.admin-layout {
  display: flex;
  height: 100vh;
  overflow: hidden;
}

/* ---------- 深色侧边栏 ---------- */
.admin-aside {
  width: 232px;
  flex-shrink: 0;
  background: linear-gradient(180deg, #16233f, #101828);
  display: flex;
  flex-direction: column;
}

.admin-brand {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 18px 16px;
  border-bottom: 1px solid rgba(148, 163, 184, 0.15);
}
.admin-mark {
  width: 36px;
  height: 36px;
  border-radius: 10px;
  background: rgba(59, 130, 246, 0.18);
  border: 1px solid rgba(59, 130, 246, 0.35);
  color: #93c5fd;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}
.admin-brand-text {
  display: flex;
  flex-direction: column;
  line-height: 1.3;
}
.admin-name {
  font-size: 15px;
  font-weight: 600;
  color: #f1f5f9;
}
.admin-sub {
  font-size: 11px;
  color: #64748b;
}

.admin-menu {
  flex: 1;
  overflow-y: auto;
  padding: 10px 12px 16px;
}
.admin-group-label {
  font-size: 11px;
  color: #5b6b85;
  padding: 14px 10px 6px;
  letter-spacing: 1px;
}
.admin-item {
  position: relative;
  display: flex;
  align-items: center;
  gap: 10px;
  height: 38px;
  padding: 0 12px;
  margin-bottom: 2px;
  border-radius: 8px;
  font-size: 13.5px;
  color: var(--ph-admin-aside-text);
  transition: all 0.18s ease;
}
.admin-item:hover {
  background: rgba(148, 163, 184, 0.08);
  color: #e2e8f0;
}
.admin-item.active {
  background: rgba(59, 130, 246, 0.16);
  color: #bfdbfe;
  font-weight: 600;
}
.admin-item.active::before {
  content: '';
  position: absolute;
  left: 0;
  top: 9px;
  bottom: 9px;
  width: 3px;
  border-radius: 2px;
  background: #3b82f6;
}

.admin-aside-footer {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 12px 16px;
  font-size: 11px;
  color: #5b6b85;
  border-top: 1px solid rgba(148, 163, 184, 0.15);
}

/* ---------- 主体 ---------- */
.admin-main {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  background: var(--ph-bg);
}
.admin-header {
  height: var(--ph-header-height);
  flex-shrink: 0;
  background: #fff;
  border-bottom: 1px solid var(--ph-border);
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 24px;
}
.admin-header-right {
  display: flex;
  align-items: center;
  gap: 14px;
}
.admin-role-tag {
  background: var(--ph-admin-accent);
  border-color: var(--ph-admin-accent);
  font-weight: 600;
}
.admin-user-chip {
  display: flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
  padding: 4px 6px;
  border-radius: 8px;
  transition: background 0.18s;
}
.admin-user-chip:hover {
  background: #f5f7fa;
}
.admin-avatar {
  background: #e0e7ff;
  color: var(--ph-admin-accent);
  font-weight: 600;
  font-size: 13px;
}
.admin-user-name {
  font-size: 13.5px;
  color: var(--ph-text);
}

.admin-content {
  flex: 1;
  overflow-y: auto;
  padding: 20px 24px 0;
}
</style>
