<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useAuthStore } from '@/stores/auth'
import { mockNotifications } from '@/mocks/user/notifications'

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

/* 用户端导航（对应规划书 5.1 节） */
const menuGroups: MenuGroup[] = [
  {
    items: [
      { label: '首页', icon: 'HomeFilled', path: '/user/dashboard' },
    ],
  },
  {
    label: '论文中心',
    items: [
      { label: '共享论文库', icon: 'Collection', path: '/user/library/shared' },
      { label: '我的论文库', icon: 'FolderOpened', path: '/user/library/mine' },
      { label: '上传论文', icon: 'UploadFilled', path: '/user/papers/upload' },
    ],
  },
  {
    label: '知识管理',
    items: [
      { label: '分类管理', icon: 'Files', path: '/user/categories' },
      { label: '标签管理', icon: 'PriceTag', path: '/user/tags' },
      { label: '阅读管理', icon: 'Notebook', path: '/user/reading' },
      { label: '阅读笔记', icon: 'EditPen', path: '/user/notes' },
    ],
  },
  {
    label: '数据分析',
    items: [{ label: '数据统计', icon: 'TrendCharts', path: '/user/statistics' }],
  },
  {
    label: '个人管理',
    items: [{ label: '个人中心', icon: 'User', path: '/user/profile' }],
  },
]

const unreadCount = computed(
  () => mockNotifications.filter((n) => !n.read).length,
)

const searchKeyword = ref('')
function handleSearch() {
  const q = searchKeyword.value.trim()
  router.push({ path: '/user/library/shared', query: q ? { q } : undefined })
}

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
    await ElMessageBox.confirm('确定要退出当前账号吗？', '退出登录', {
      confirmButtonText: '退出',
      cancelButtonText: '取消',
      type: 'warning',
    })
    auth.logout()
    ElMessage.success('已安全退出')
    router.push('/login')
  } catch {
    /* 用户取消 */
  }
}
</script>

<template>
  <div class="user-layout">
    <!-- 左侧导航 -->
    <aside class="layout-aside">
      <router-link to="/user/dashboard" class="brand">
        <div class="brand-mark">
          <el-icon :size="20"><Reading /></el-icon>
        </div>
        <div class="brand-text">
          <span class="brand-name">PaperHub</span>
          <span class="brand-sub">论文管理平台</span>
        </div>
      </router-link>

      <nav class="side-menu">
        <template v-for="(group, gi) in menuGroups" :key="gi">
          <div v-if="group.label" class="menu-group-label">{{ group.label }}</div>
          <router-link
            v-for="item in group.items"
            :key="item.path"
            :to="item.path!"
            class="menu-item"
            :class="{ active: isActive(item.path!) }"
          >
            <el-icon :size="17"><component :is="item.icon" /></el-icon>
            <span>{{ item.label }}</span>
          </router-link>
        </template>
      </nav>

      <div class="aside-footer">PaperHub v0.1 · 演示阶段</div>
    </aside>

    <!-- 右侧主体 -->
    <div class="layout-main">
      <header class="layout-header">
        <div class="header-search">
          <el-input
            v-model="searchKeyword"
            placeholder="搜索论文标题、作者、DOI…"
            clearable
            @keyup.enter="handleSearch"
          >
            <template #prefix>
              <el-icon><Search /></el-icon>
            </template>
          </el-input>
        </div>

        <div class="header-right">
          <el-popover placement="bottom-end" width="360" trigger="click">
            <template #reference>
              <el-badge :value="unreadCount" :offset="[-2, 4]" class="notify-badge">
                <el-button :icon="'Bell'" text circle size="large" class="notify-btn" />
              </el-badge>
            </template>
            <div class="notify-panel">
              <div class="notify-title">消息通知</div>
              <div
                v-for="n in mockNotifications"
                :key="n.id"
                class="notify-item"
                :class="{ unread: !n.read }"
              >
                <div class="notify-item-title">{{ n.title }}</div>
                <div class="notify-item-content">{{ n.content }}</div>
                <div class="notify-item-time">{{ n.time }}</div>
              </div>
            </div>
          </el-popover>

          <el-divider direction="vertical" />

          <el-dropdown trigger="click" @command="handleLogout">
            <div class="user-chip">
              <el-avatar :size="32" class="user-avatar">
                {{ auth.user?.name?.charAt(0) }}
              </el-avatar>
              <span class="user-name">{{ auth.user?.name }}</span>
              <el-icon :size="12" color="#9ca3af"><CaretBottom /></el-icon>
            </div>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item :icon="'User'" disabled>
                  个人中心（后续批次开放）
                </el-dropdown-item>
                <el-dropdown-item :icon="'SwitchButton'" divided command="logout">
                  退出登录
                </el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </header>

      <main class="layout-content">
        <el-breadcrumb separator="/" class="content-breadcrumb">
          <el-breadcrumb-item v-for="(item, i) in breadcrumbItems" :key="i">
            {{ item }}
          </el-breadcrumb-item>
        </el-breadcrumb>
        <router-view />
      </main>
    </div>
  </div>
</template>

<style scoped>
.user-layout {
  display: flex;
  height: 100vh;
  overflow: hidden;
}

/* ---------- 左侧导航 ---------- */
.layout-aside {
  width: var(--ph-aside-width);
  flex-shrink: 0;
  background: #fff;
  border-right: 1px solid var(--ph-border);
  display: flex;
  flex-direction: column;
}

.brand {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 16px;
  border-bottom: 1px solid var(--ph-border);
}
.brand-mark {
  width: 38px;
  height: 38px;
  border-radius: 11px;
  background: linear-gradient(135deg, #2563eb, #1e40af);
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}
.brand-text {
  display: flex;
  flex-direction: column;
  line-height: 1.25;
}
.brand-name {
  font-size: 17px;
  font-weight: 700;
  letter-spacing: 0.2px;
  color: var(--ph-text);
}
.brand-sub {
  font-size: 11px;
  color: var(--ph-text-muted);
}

.side-menu {
  flex: 1;
  overflow-y: auto;
  padding: 10px 12px 16px;
}
.menu-group-label {
  font-size: 12px;
  color: var(--ph-text-muted);
  padding: 14px 10px 6px;
  letter-spacing: 1px;
}
.menu-item {
  display: flex;
  align-items: center;
  gap: 10px;
  height: 40px;
  padding: 0 12px;
  margin-bottom: 2px;
  border-radius: 10px;
  font-size: 14px;
  color: var(--ph-text-secondary);
  transition: all 0.18s ease;
}
.menu-item:hover {
  background: #f5f7fa;
  color: var(--ph-primary);
}
.menu-item.active {
  background: var(--ph-primary-light);
  color: var(--ph-primary);
  font-weight: 600;
}

.aside-footer {
  padding: 12px 16px;
  font-size: 11px;
  color: var(--ph-text-muted);
  border-top: 1px solid var(--ph-border);
}

/* ---------- 顶部栏 ---------- */
.layout-main {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
}
.layout-header {
  height: var(--ph-header-height);
  flex-shrink: 0;
  background: #fff;
  border-bottom: 1px solid var(--ph-border);
  display: flex;
  align-items: center;
  justify-content:space-between;
  padding: 0 24px;
  gap: 16px;
}
.header-search {
  width: 340px;
}
.header-search :deep(.el-input__wrapper) {
  border-radius: 999px;
  background: var(--ph-bg);
}
.header-right {
  display: flex;
  align-items: center;
  gap: 4px;
}
.notify-btn {
  color: var(--ph-text-secondary);
}
.user-chip {
  display: flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
  padding: 4px 6px;
  border-radius: 10px;
  transition: background 0.18s;
}
.user-chip:hover {
  background: #f5f7fa;
}
.user-avatar {
  background: var(--ph-primary-light);
  color: var(--ph-primary);
  font-weight: 600;
}
.user-name {
  font-size: 14px;
  color: var(--ph-text);
}

/* ---------- 内容区 ---------- */
.layout-content {
  flex: 1;
  overflow-y: auto;
  padding: 20px 24px 0;
}
.content-breadcrumb {
  margin-bottom: 16px;
}

/* ---------- 通知面板 ---------- */
.notify-panel {
  margin: -4px;
}
.notify-title {
  font-size: 14px;
  font-weight: 600;
  padding: 4px 4px 10px;
  border-bottom: 1px solid var(--ph-border);
  margin-bottom: 8px;
}
.notify-item {
  padding: 8px 6px;
  border-radius: 8px;
}
.notify-item.unread {
  background: var(--ph-primary-light);
}
.notify-item + .notify-item {
  margin-top: 4px;
}
.notify-item-title {
  font-size: 13px;
  font-weight: 600;
  color: var(--ph-text);
}
.notify-item-content {
  font-size: 12px;
  color: var(--ph-text-secondary);
  line-height: 1.6;
  margin: 4px 0;
}
.notify-item-time {
  font-size: 11px;
  color: var(--ph-text-muted);
}
</style>
