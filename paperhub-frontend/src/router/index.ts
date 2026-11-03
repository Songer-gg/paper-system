import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

/**
 * 第一阶段路由表
 * - 业务页面按批次逐步实现，未实现的页面统一指向 PlaceholderView
 * - 登录页独立布局；用户端 / 管理员端分别使用独立 Layout
 */
const PlaceholderView = () => import('@/views/placeholder/PlaceholderView.vue')

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
          component: PlaceholderView,
          meta: { title: '首页', group: '首页' },
        },
        {
          path: 'library/shared',
          name: 'user-library-shared',
          component: PlaceholderView,
          meta: { title: '共享论文库', group: '论文中心' },
        },
        {
          path: 'library/mine',
          name: 'user-library-mine',
          component: PlaceholderView,
          meta: { title: '我的论文库', group: '论文中心' },
        },
        {
          path: 'papers/upload',
          name: 'user-paper-upload',
          component: PlaceholderView,
          meta: { title: '上传论文', group: '论文中心' },
        },
        {
          path: 'papers/:id',
          name: 'paper-detail',
          component: PlaceholderView,
          meta: { title: '论文详情', group: '论文中心' },
        },
        {
          path: 'categories',
          name: 'user-categories',
          component: PlaceholderView,
          meta: { title: '分类管理', group: '知识管理' },
        },
        {
          path: 'tags',
          name: 'user-tags',
          component: PlaceholderView,
          meta: { title: '标签管理', group: '知识管理' },
        },
        {
          path: 'reading',
          name: 'user-reading',
          component: PlaceholderView,
          meta: { title: '阅读管理', group: '知识管理' },
        },
        {
          path: 'notes',
          name: 'user-notes',
          component: PlaceholderView,
          meta: { title: '阅读笔记', group: '知识管理' },
        },
        {
          path: 'statistics',
          name: 'user-statistics',
          component: PlaceholderView,
          meta: { title: '数据统计', group: '数据分析' },
        },
        {
          path: 'profile',
          name: 'user-profile',
          component: PlaceholderView,
          meta: { title: '个人中心', group: '个人管理' },
        },
      ],
    },
    /* PDF 阅读器：独立全屏页，不套用侧边栏布局 */
    {
      path: '/user/papers/:id/reader',
      name: 'paper-reader',
      component: PlaceholderView,
      meta: { title: 'PDF 阅读器', group: '论文中心' },
    },
    /* ---------------- 管理员端 ---------------- */
    {
      path: '/admin',
      component: () => import('@/layouts/AdminLayout.vue'),
      redirect: '/admin/dashboard',
      children: [
        {
          path: 'dashboard',
          name: 'admin-dashboard',
          component: PlaceholderView,
          meta: { title: '管理首页', group: '平台概览' },
        },
        {
          path: 'users',
          name: 'admin-users',
          component: PlaceholderView,
          meta: { title: '用户列表', group: '用户管理' },
        },
        {
          path: 'users/:id',
          name: 'admin-user-detail',
          component: PlaceholderView,
          meta: { title: '用户详情', group: '用户管理' },
        },
        {
          path: 'papers',
          name: 'admin-papers',
          component: PlaceholderView,
          meta: { title: '共享论文列表', group: '论文资源管理' },
        },
        {
          path: 'papers/offline',
          name: 'admin-papers-offline',
          component: PlaceholderView,
          meta: { title: '论文下架管理', group: '论文资源管理' },
        },
        {
          path: 'review',
          name: 'admin-review',
          component: PlaceholderView,
          meta: { title: '待审核论文', group: '内容审核' },
        },
        {
          path: 'review/records',
          name: 'admin-review-records',
          component: PlaceholderView,
          meta: { title: '审核记录', group: '内容审核' },
        },
        {
          path: 'duplicates',
          name: 'admin-duplicates',
          component: PlaceholderView,
          meta: { title: '疑似重复论文', group: '重复论文管理' },
        },
        {
          path: 'duplicates/records',
          name: 'admin-duplicates-records',
          component: PlaceholderView,
          meta: { title: '重复论文处理记录', group: '重复论文管理' },
        },
        {
          path: 'categories',
          name: 'admin-categories',
          component: PlaceholderView,
          meta: { title: '平台分类管理', group: '分类与标签' },
        },
        {
          path: 'tags',
          name: 'admin-tags',
          component: PlaceholderView,
          meta: { title: '平台标签管理', group: '分类与标签' },
        },
        {
          path: 'reports',
          name: 'admin-reports',
          component: PlaceholderView,
          meta: { title: '举报列表', group: '举报管理' },
        },
        {
          path: 'reports/records',
          name: 'admin-reports-records',
          component: PlaceholderView,
          meta: { title: '举报处理记录', group: '举报管理' },
        },
        {
          path: 'logs',
          name: 'admin-logs',
          component: PlaceholderView,
          meta: { title: '操作日志', group: '系统管理' },
        },
        {
          path: 'settings',
          name: 'admin-settings',
          component: PlaceholderView,
          meta: { title: '系统设置', group: '系统管理' },
        },
      ],
    },
    { path: '/:pathMatch(.*)*', redirect: '/login' },
  ],
})

/* 路由守卫：未登录跳转登录页；角色与端不匹配时回到对应首页 */
router.beforeEach((to) => {
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

  return true
})

router.afterEach((to) => {
  const title = (to.meta?.title as string) ?? ''
  document.title = title ? `${title} - PaperHub` : 'PaperHub 论文管理平台'
})

export default router
