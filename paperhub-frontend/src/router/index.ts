import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

/**
 * 第一阶段路由表
 * - 业务页面已按批次全部实现（用户端 + 管理员端）
 * - 登录页独立布局；用户端 / 管理员端分别使用独立 Layout
 */
const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', redirect: '/login' },
    {
      path: '/login',
      name: 'login',
      component: () => import('@/views/login/LoginView.vue'),
      meta: { title: '登录' },
    },
    /* ---------------- 用户端 ---------------- */
    {
      path: '/user',
      component: () => import('@/layouts/UserLayout.vue'),
      redirect: '/user/dashboard',
      children: [
        {
          path: 'dashboard',
          name: 'user-dashboard',
          component: () => import('@/views/user/dashboard/DashboardView.vue'),
          meta: { title: '首页', group: '首页' },
        },
        {
          path: 'library/shared',
          name: 'user-library-shared',
          component: () => import('@/views/user/library/SharedLibraryView.vue'),
          meta: { title: '共享论文库', group: '论文中心' },
        },
        {
          path: 'library/mine',
          name: 'user-library-mine',
          component: () => import('@/views/user/library/MyLibraryView.vue'),
          meta: { title: '我的论文库', group: '论文中心' },
        },
        {
          path: 'papers/upload',
          name: 'user-paper-upload',
          component: () => import('@/views/user/papers/UploadPaperView.vue'),
          meta: { title: '上传论文', group: '论文中心' },
        },
        {
          path: 'papers/:id',
          name: 'paper-detail',
          component: () => import('@/views/user/papers/PaperDetailView.vue'),
          meta: { title: '论文详情', group: '论文中心' },
        },
        {
          path: 'categories',
          name: 'user-categories',
          component: () => import('@/views/user/knowledge/CategoriesView.vue'),
          meta: { title: '分类管理', group: '知识管理' },
        },
        {
          path: 'tags',
          name: 'user-tags',
          component: () => import('@/views/user/knowledge/TagsView.vue'),
          meta: { title: '标签管理', group: '知识管理' },
        },
        {
          path: 'reading',
          name: 'user-reading',
          component: () => import('@/views/user/reading/ReadingView.vue'),
          meta: { title: '阅读管理', group: '知识管理' },
        },
        {
          path: 'notes',
          name: 'user-notes',
          component: () => import('@/views/user/notes/NotesView.vue'),
          meta: { title: '阅读笔记', group: '知识管理' },
        },
        {
          path: 'statistics',
          name: 'user-statistics',
          component: () => import('@/views/user/statistics/StatisticsView.vue'),
          meta: { title: '数据统计', group: '数据分析' },
        },
        {
          path: 'profile',
          name: 'user-profile',
          component: () => import('@/views/user/profile/ProfileView.vue'),
          meta: { title: '个人中心', group: '个人管理' },
        },
      ],
    },
    /* PDF 阅读器：独立全屏页，不套用侧边栏布局 */
    {
      path: '/user/papers/:id/reader',
      name: 'paper-reader',
      component: () => import('@/views/user/papers/PaperReaderView.vue'),
      meta: { title: 'PDF 阅读器', group: '论文中心' },
    },
    /* ---------------- 管理员端 ---------------- */
    {
      path: '/admin/papers/:id/reader',
      name: 'admin-paper-reader',
      component: () => import('@/views/user/papers/PaperReaderView.vue'),
      meta: { title: '论文预览', group: '论文资源管理' },
    },
    {
      path: '/admin',
      component: () => import('@/layouts/AdminLayout.vue'),
      redirect: '/admin/dashboard',
      children: [
        {
          path: 'dashboard',
          name: 'admin-dashboard',
          component: () => import('@/views/admin/dashboard/DashboardView.vue'),
          meta: { title: '管理首页', group: '平台概览' },
        },
        {
          path: 'users',
          name: 'admin-users',
          component: () => import('@/views/admin/users/UsersView.vue'),
          meta: { title: '用户列表', group: '用户管理' },
        },
        {
          path: 'users/:id',
          name: 'admin-user-detail',
          component: () => import('@/views/admin/users/UserDetailView.vue'),
          meta: { title: '用户详情', group: '用户管理' },
        },
        {
          path: 'papers',
          name: 'admin-papers',
          component: () => import('@/views/admin/papers/PapersView.vue'),
          meta: { title: '共享论文列表', group: '论文资源管理' },
        },
        {
          path: 'papers/offline',
          name: 'admin-papers-offline',
          component: () => import('@/views/admin/papers/OfflinePapersView.vue'),
          meta: { title: '论文下架管理', group: '论文资源管理' },
        },
        {
          path: 'review',
          name: 'admin-review',
          component: () => import('@/views/admin/review/ReviewView.vue'),
          meta: { title: '论文审核', group: '内容审核' },
        },
        {
          path: 'review/records',
          name: 'admin-review-records',
          component: () => import('@/views/admin/review/ReviewRecordsView.vue'),
          meta: { title: '审核记录', group: '内容审核' },
        },
        {
          path: 'duplicates',
          name: 'admin-duplicates',
          component: () => import('@/views/admin/duplicates/DuplicatesView.vue'),
          meta: { title: '疑似重复论文', group: '重复论文管理' },
        },
        {
          path: 'duplicates/records',
          name: 'admin-duplicates-records',
          component: () => import('@/views/admin/duplicates/DuplicateRecordsView.vue'),
          meta: { title: '重复论文处理记录', group: '重复论文管理' },
        },
        {
          path: 'categories',
          name: 'admin-categories',
          component: () => import('@/views/admin/categories/CategoriesView.vue'),
          meta: { title: '平台分类管理', group: '分类与标签' },
        },
        {
          path: 'tags',
          name: 'admin-tags',
          component: () => import('@/views/admin/tags/TagsView.vue'),
          meta: { title: '平台标签管理', group: '分类与标签' },
        },
        {
          path: 'reports',
          name: 'admin-reports',
          component: () => import('@/views/admin/reports/ReportsView.vue'),
          meta: { title: '举报列表', group: '举报管理' },
        },
        {
          path: 'reports/records',
          name: 'admin-reports-records',
          component: () => import('@/views/admin/reports/ReportRecordsView.vue'),
          meta: { title: '举报处理记录', group: '举报管理' },
        },
        {
          path: 'logs',
          name: 'admin-logs',
          component: () => import('@/views/admin/logs/LogsView.vue'),
          meta: { title: '操作日志', group: '系统管理' },
        },
        {
          path: 'settings',
          name: 'admin-settings',
          component: () => import('@/views/admin/settings/SettingsView.vue'),
          meta: { title: '系统设置', group: '系统管理' },
        },
      ],
    },
    { path: '/:pathMatch(.*)*', redirect: '/login' },
  ],
})

/* 路由守卫：未登录跳转登录页；角色与端不匹配时回到对应首页 */
router.beforeEach(async (to) => {
  const auth = useAuthStore()

  if (to.path === '/login') {
    return auth.isLoggedIn ? auth.homePath : true
  }

  if (!auth.isLoggedIn) {
    return { path: '/login', query: { redirect: to.fullPath } }
  }

  const requiredRole = to.path.startsWith('/admin') ? 'admin' : 'user'
  if (auth.user?.role !== requiredRole) {
    return auth.homePath
  }

  try {
    const [{ warmKnowledge }, { getFavorites }] = await Promise.all([
      import('@/services/knowledgeService'),
      import('@/services/userDataService'),
    ])
    await Promise.all([warmKnowledge(), getFavorites()])
  } catch (error) {
    const { reportActionError } = await import('@/utils/errors')
    reportActionError(error)
    if (!auth.isLoggedIn) return { path: '/login', query: { redirect: to.fullPath } }
  }

  return true
})

router.afterEach((to) => {
  const title = (to.meta?.title as string) ?? ''
  document.title = title ? `${title} - PaperHub` : 'PaperHub 论文管理平台'
})

export default router
