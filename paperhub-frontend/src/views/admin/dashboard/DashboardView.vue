<script setup lang="ts">
import { reportActionError } from '@/utils/errors'
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import type { EChartsOption } from 'echarts'
import VChart from '@/components/charts/VChart.vue'
import { getAdminDashboard, type AdminDashboard } from '@/services/dashboardService'
import { reviewStatusLabels, type ReviewStatus } from '@/types/paper'

const router = useRouter()
const loading = ref(true)
const data = ref<AdminDashboard | null>(null)

const statusClassMap: Record<ReviewStatus, string> = {
  pending: 'ph-status--warning',
  approved: 'ph-status--success',
  rejected: 'ph-status--danger',
  offline: 'ph-status--info',
}
function statusClass(status?: ReviewStatus): string {
  return status ? statusClassMap[status] : 'ph-status--info'
}

onMounted(async () => {
  try {
    data.value = await getAdminDashboard()
    loading.value = false
  } catch (error) {
    reportActionError(error)
  } finally {
    loading.value = false
  }
})

/* 统计条 */
const statItems = computed(() => {
  const t = data.value?.totals
  return [
    {
      label: '注册用户',
      value: t?.users ?? 0,
      caption: '包含已禁用账号',
      icon: 'User',
      path: '/admin/users',
    },
    {
      label: '共享论文',
      value: t?.papers ?? 0,
      caption: '审核通过',
      icon: 'Collection',
      path: '/admin/papers',
    },
    {
      label: '待审核论文',
      value: t?.pending ?? 0,
      caption: '需要及时处理',
      icon: 'DocumentChecked',
      highlight: true,
      path: '/admin/review',
    },
    {
      label: '今日新增论文',
      value: t?.papersToday ?? 0,
      caption: '今日上传的共享资源',
      icon: 'UploadFilled',
    },
    { label: '今日新增用户', value: t?.usersToday ?? 0, caption: '注册即计入', icon: 'Plus' },
  ]
})

/* 论文上传趋势 */
const uploadChartOption = computed<EChartsOption>(() => ({
  grid: { top: 30, left: 8, right: 16, bottom: 0, containLabel: true },
  tooltip: { trigger: 'axis' },
  xAxis: {
    type: 'category',
    boundaryGap: false,
    data: data.value?.uploadTrend.months.map((m) => m.slice(2)) ?? [],
    axisLine: { lineStyle: { color: '#E5E7EB' } },
    axisTick: { show: false },
    axisLabel: { color: '#6B7280' },
  },
  yAxis: {
    type: 'value',
    splitLine: { lineStyle: { color: '#F3F4F6' } },
    axisLabel: { color: '#9CA3AF' },
  },
  series: [
    {
      type: 'line',
      smooth: true,
      symbolSize: 7,
      data: data.value?.uploadTrend.papers ?? [],
      lineStyle: { width: 3, color: '#2563EB' },
      itemStyle: { color: '#2563EB' },
      areaStyle: {
        color: {
          type: 'linear',
          x: 0,
          y: 0,
          x2: 0,
          y2: 1,
          colorStops: [
            { offset: 0, color: 'rgba(37, 99, 235, 0.25)' },
            { offset: 1, color: 'rgba(37, 99, 235, 0)' },
          ],
        },
      },
    },
  ],
}))

/* 用户注册趋势 */
const registerChartOption = computed<EChartsOption>(() => ({
  grid: { top: 30, left: 8, right: 16, bottom: 0, containLabel: true },
  tooltip: { trigger: 'axis' },
  xAxis: {
    type: 'category',
    boundaryGap: false,
    data: data.value?.registerTrend.months.map((m) => m.slice(2)) ?? [],
    axisLine: { lineStyle: { color: '#E5E7EB' } },
    axisTick: { show: false },
    axisLabel: { color: '#6B7280' },
  },
  yAxis: {
    type: 'value',
    splitLine: { lineStyle: { color: '#F3F4F6' } },
    axisLabel: { color: '#9CA3AF' },
  },
  series: [
    {
      type: 'line',
      smooth: true,
      symbolSize: 7,
      data: data.value?.registerTrend.users ?? [],
      lineStyle: { width: 3, color: '#0891B2' },
      itemStyle: { color: '#0891B2' },
      areaStyle: {
        color: {
          type: 'linear',
          x: 0,
          y: 0,
          x2: 0,
          y2: 1,
          colorStops: [
            { offset: 0, color: 'rgba(8, 145, 178, 0.22)' },
            { offset: 1, color: 'rgba(8, 145, 178, 0)' },
          ],
        },
      },
    },
  ],
}))

/* 共享论文分类分布（横向条形图） */
const categoryChartOption = computed<EChartsOption>(() => {
  const dist = [...(data.value?.categoryDist ?? [])].sort((a, b) => a.value - b.value)
  return {
    grid: { top: 8, left: 8, right: 30, bottom: 0, containLabel: true },
    tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
    xAxis: {
      type: 'value',
      splitLine: { lineStyle: { color: '#F3F4F6' } },
      axisLabel: { color: '#9CA3AF' },
    },
    yAxis: {
      type: 'category',
      data: dist.map((d) => d.name),
      axisLine: { show: false },
      axisTick: { show: false },
      axisLabel: { color: '#4B5563' },
    },
    series: [
      {
        type: 'bar',
        barWidth: 14,
        data: dist.map((d) => d.value),
        itemStyle: {
          borderRadius: [0, 7, 7, 0],
          color: {
            type: 'linear',
            x: 0,
            y: 0,
            x2: 1,
            y2: 0,
            colorStops: [
              { offset: 0, color: '#93C5FD' },
              { offset: 1, color: '#1E40AF' },
            ],
          },
        },
        label: { show: true, position: 'right', color: '#4B5563', fontSize: 12 },
      },
    ],
  }
})
</script>

<template>
  <div v-loading="loading" class="ph-page admin-dashboard">
    <!-- 页头 -->
    <div class="ph-page-header">
      <div>
        <h2 class="ph-page-title">平台运营概览</h2>
        <p class="ph-page-desc">
          平台运行平稳，当前有
          <b class="pending-num">{{ data?.totals.pending ?? 0 }}</b>
          篇论文待审核，请及时处理
        </p>
      </div>
      <div class="header-actions">
        <el-button type="primary" :icon="'DocumentChecked'" @click="router.push('/admin/review')">
          前往审核
        </el-button>
      </div>
    </div>

    <template v-if="data">
      <!-- 统计条 -->
      <div class="ph-card stat-strip">
        <div
          v-for="item in statItems"
          :key="item.label"
          class="strip-item"
          :class="{ clickable: !!item.path }"
          @click="item.path && router.push(item.path)"
        >
          <div class="strip-icon" :class="{ 'strip-icon--warn': item.highlight }">
            <el-icon :size="20"><component :is="item.icon" /></el-icon>
          </div>
          <div>
            <div class="strip-value" :class="{ 'strip-value--warn': item.highlight }">
              {{ item.value }}
            </div>
            <div class="strip-label">{{ item.label }}</div>
            <div class="strip-caption">{{ item.caption }}</div>
          </div>
        </div>
      </div>

      <!-- 趋势图 -->
      <el-row :gutter="16" class="chart-row">
        <el-col :xs="24" :md="12">
          <div class="ph-card chart-card">
            <div class="chart-head">
              <div class="ph-section-title">论文上传趋势</div>
              <span class="chart-subtitle">近 12 个月 · 单位：篇</span>
            </div>
            <VChart :option="uploadChartOption" height="250px" />
          </div>
        </el-col>
        <el-col :xs="24" :md="12">
          <div class="ph-card chart-card">
            <div class="chart-head">
              <div class="ph-section-title">用户注册趋势</div>
              <span class="chart-subtitle">近 12 个月 · 单位：人</span>
            </div>
            <VChart :option="registerChartOption" height="250px" />
          </div>
        </el-col>
      </el-row>

      <!-- 分布 + 任务 -->
      <el-row :gutter="16">
        <el-col :xs="24" :md="10">
          <div class="ph-card chart-card">
            <div class="chart-head">
              <div class="ph-section-title">共享论文分类分布</div>
            </div>
            <VChart :option="categoryChartOption" height="306px" />
          </div>
        </el-col>
        <el-col :xs="24" :md="14">
          <div class="task-stack">
            <!-- 待处理审核任务 -->
            <div class="ph-card task-card">
              <div class="chart-head">
                <div class="ph-section-title">待处理审核任务</div>
                <el-link type="primary" underline="never" @click="router.push('/admin/review')">
                  全部待审（{{ data.pendingTasks.length }}）
                </el-link>
              </div>
              <div
                v-for="p in data.pendingTasks"
                :key="p.id"
                class="task-row"
                @click="router.push('/admin/review')"
              >
                <span class="ph-status ph-status--warning">待审核</span>
                <div class="task-main">
                  <div class="task-title ph-ellipsis">{{ p.title }}</div>
                  <div class="task-meta">
                    {{ p.uploaderName || '—' }} · {{ p.journal || '未填写期刊' }} · {{ p.year }} 年
                  </div>
                </div>
                <span class="task-time">{{ p.uploadedAt.slice(5, 16) }}</span>
              </div>
            </div>

            <!-- 最近上传记录 -->
            <div class="ph-card task-card">
              <div class="chart-head">
                <div class="ph-section-title">最近上传记录</div>
                <el-link type="primary" underline="never" @click="router.push('/admin/papers')">
                  查看全部
                </el-link>
              </div>
              <div v-for="p in data.recentUploads" :key="p.id" class="task-row">
                <span class="ph-status" :class="statusClass(p.reviewStatus)">
                  {{ reviewStatusLabels[p.reviewStatus as ReviewStatus] ?? '未知' }}
                </span>
                <div class="task-main">
                  <div class="task-title ph-ellipsis">{{ p.title }}</div>
                  <div class="task-meta">{{ p.uploaderName || '—' }} · 上传者</div>
                </div>
                <span class="task-time">{{ p.uploadedAt.slice(5, 16) }}</span>
              </div>
            </div>
          </div>
        </el-col>
      </el-row>
    </template>
  </div>
</template>

<style scoped>
.header-actions {
  display: flex;
}
.pending-num {
  color: var(--ph-warning);
  font-size: 15px;
}

/* ---------- 统计条 ---------- */
.stat-strip {
  display: grid;
  grid-template-columns: repeat(5, 1fr);
  padding: 10px 0;
  margin-bottom: 16px;
}
.strip-item {
  display: flex;
  align-items: center;
  gap: 13px;
  padding: 16px 24px;
  border-right: 1px solid #f3f4f6;
}
.strip-item:last-child {
  border-right: none;
}
.strip-item.clickable {
  cursor: pointer;
  transition: background 0.15s;
}
.strip-item.clickable:hover {
  background: #f8fafc;
}
.strip-icon {
  width: 44px;
  height: 44px;
  border-radius: 12px;
  background: #eff6ff;
  color: #1e40af;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}
.strip-icon--warn {
  background: #fffbeb;
  color: #d97706;
}
.strip-value {
  font-size: 24px;
  font-weight: 700;
  color: var(--ph-text);
  line-height: 1.1;
}
.strip-value--warn {
  color: var(--ph-warning);
}
.strip-label {
  font-size: 13px;
  color: var(--ph-text-secondary);
  margin-top: 2px;
}
.strip-caption {
  font-size: 11px;
  color: var(--ph-text-muted);
  margin-top: 1px;
}

/* ---------- 图表卡 ---------- */
.chart-row {
  margin-bottom: 16px;
}
.chart-card {
  padding: 18px 18px 12px;
  height: 100%;
  box-sizing: border-box;
}
.chart-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8px;
}
.chart-subtitle {
  font-size: 12px;
  color: var(--ph-text-muted);
}

.task-stack {
  display: flex;
  flex-direction: column;
  gap: 16px;
  height: 100%;
}
.task-card {
  padding: 18px;
  flex: 1;
}
.task-row {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 11px 4px;
  border-bottom: 1px solid #f3f4f6;
  cursor: default;
}
.task-row:last-child {
  border-bottom: none;
}
.task-card .task-row:hover .task-title {
  color: var(--ph-primary);
}
.task-main {
  flex: 1;
  min-width: 0;
}
.task-title {
  font-size: 13.5px;
  font-weight: 600;
  color: var(--ph-text);
  transition: color 0.15s;
}
.task-meta {
  font-size: 12px;
  color: var(--ph-text-muted);
  margin-top: 3px;
}
.task-time {
  font-size: 12px;
  color: var(--ph-text-muted);
  flex-shrink: 0;
}
</style>
