<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import type { EChartsOption } from 'echarts'
import VChart from '@/components/charts/VChart.vue'
import TagChip from '@/components/common/TagChip.vue'
import { getTag } from '@/mocks/tags'
import { getCategory } from '@/mocks/categories'
import { useAuthStore } from '@/stores/auth'
import { getUserDashboard, type UserDashboard } from '@/services/dashboardService'
import { readingStatusLabels, type ReadingStatus } from '@/types/paper'

const router = useRouter()
const auth = useAuthStore()

const loading = ref(true)
const data = ref<UserDashboard | null>(null)

onMounted(async () => {
  data.value = await getUserDashboard()
  loading.value = false
})

const greeting = computed(() => {
  const h = new Date().getHours()
  if (h < 6) return '夜深了'
  if (h < 12) return '早上好'
  if (h < 14) return '中午好'
  if (h < 18) return '下午好'
  return '晚上好'
})

const todayText = computed(() => {
  const d = new Date()
  const week = ['日', '一', '二', '三', '四', '五', '六'][d.getDay()]
  return `${d.getFullYear()} 年 ${d.getMonth() + 1} 月 ${d.getDate()} 日 · 星期${week}`
})

/* 统计卡片 */
const statCards = computed(() => {
  const c = data.value?.counts
  return [
    {
      label: '我的论文',
      value: c?.total ?? 0,
      caption: '私有 + 我共享的论文',
      icon: 'Collection',
      iconBg: '#EFF6FF',
      iconColor: '#2563EB',
      path: '/user/library/mine',
    },
    {
      label: '私有论文',
      value: c?.private ?? 0,
      caption: '仅自己可见',
      icon: 'Lock',
      iconBg: '#F0FDF4',
      iconColor: '#16A34A',
      path: '/user/library/mine',
    },
    {
      label: '我共享的论文',
      value: c?.shared ?? 0,
      caption: '审核通过后全员可见',
      icon: 'Share',
      iconBg: '#EFF6FF',
      iconColor: '#2563EB',
      path: '/user/library/mine',
    },
    {
      label: '我的收藏',
      value: c?.favorites ?? 0,
      caption: '来自共享论文库',
      icon: 'Star',
      iconBg: '#FFF7ED',
      iconColor: '#D97706',
      path: '/user/library/mine',
    },
    {
      label: '已阅读论文',
      value: c?.read ?? 0,
      caption: '阅读状态为已读完',
      icon: 'CircleCheckFilled',
      iconBg: '#F0FDF4',
      iconColor: '#16A34A',
      path: '/user/reading',
    },
  ]
})

/* 论文发表年份分布（柱状图） */
const yearChartOption = computed<EChartsOption>(() => ({
  grid: { top: 24, left: 8, right: 8, bottom: 0, containLabel: true },
  tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
  xAxis: {
    type: 'category',
    data: data.value?.yearDist.map((d) => d.year) ?? [],
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
      type: 'bar',
      barWidth: 22,
      data: data.value?.yearDist.map((d) => d.count) ?? [],
      itemStyle: {
        borderRadius: [5, 5, 0, 0],
        color: {
          type: 'linear',
          x: 0,
          y: 0,
          x2: 0,
          y2: 1,
          colorStops: [
            { offset: 0, color: '#2563EB' },
            { offset: 1, color: '#93C5FD' },
          ],
        },
      },
    },
  ],
}))

/* 论文分类分布（环形图） */
const categoryChartOption = computed<EChartsOption>(() => ({
  tooltip: { trigger: 'item', formatter: '{b}: {c} 篇（{d}%）' },
  legend: { bottom: 0, icon: 'circle', itemWidth: 8, textStyle: { color: '#6B7280', fontSize: 12 } },
  color: ['#2563EB', '#7C3AED', '#0891B2', '#D97706', '#16A34A', '#DB2777', '#475569'],
  series: [
    {
      type: 'pie',
      radius: ['48%', '68%'],
      center: ['50%', '42%'],
      avoidLabelOverlap: true,
      itemStyle: { borderColor: '#fff', borderWidth: 2, borderRadius: 4 },
      label: { show: false },
      data: data.value?.categoryDist ?? [],
    },
  ],
}))

/* 阅读状态分布的自定义配色 */
const readingColors: Record<ReadingStatus, string> = {
  read: '#16A34A',
  reading: '#2563EB',
  unread: '#9CA3AF',
  paused: '#D97706',
}
const readingTotal = computed(
  () => data.value?.readingDist.reduce((s, r) => s + r.count, 0) ?? 0,
)

function paperCategoryName(id: number): string {
  return getCategory(id)?.name ?? '未分类'
}

function goPaper(id: number) {
  router.push(`/user/papers/${id}`)
}
</script>

<template>
  <div v-loading="loading" class="ph-page dashboard">
    <!-- 问候区 -->
    <div class="greeting-row">
      <div>
        <h2 class="greeting-title">{{ greeting }}，{{ auth.user?.name }}</h2>
        <p class="ph-page-desc">{{ todayText }} · 今天也继续积累知识吧</p>
      </div>
      <div class="greeting-actions">
        <el-button :icon="'TrendCharts'" @click="router.push('/user/statistics')">
          数据统计
        </el-button>
        <el-button type="primary" :icon="'UploadFilled'" @click="router.push('/user/papers/upload')">
          上传论文
        </el-button>
      </div>
    </div>

    <template v-if="data">
      <!-- 统计卡片 -->
      <div class="stat-grid">
        <div
          v-for="card in statCards"
          :key="card.label"
          class="ph-card stat-card"
          @click="router.push(card.path)"
        >
          <div
            class="stat-icon"
            :style="{ background: card.iconBg, color: card.iconColor }"
          >
            <el-icon :size="20"><component :is="card.icon" /></el-icon>
          </div>
          <div class="stat-info">
            <div class="stat-value">{{ card.value }}</div>
            <div class="stat-label">{{ card.label }}</div>
            <div class="stat-caption">{{ card.caption }}</div>
          </div>
        </div>
      </div>

      <!-- 图表区 -->
      <el-row :gutter="16" class="chart-row">
        <el-col :xs="24" :sm="24" :md="10">
          <div class="ph-card chart-card">
            <div class="ph-section-title">论文发表年份分布</div>
            <VChart :option="yearChartOption" height="260px" />
          </div>
        </el-col>
        <el-col :xs="24" :sm="24" :md="7">
          <div class="ph-card chart-card">
            <div class="ph-section-title">论文分类分布</div>
            <VChart :option="categoryChartOption" height="260px" />
          </div>
        </el-col>
        <el-col :xs="24" :sm="24" :md="7">
          <div class="ph-card chart-card">
            <div class="ph-section-title">阅读状态分布</div>
            <div class="reading-list">
              <div
                v-for="item in data.readingDist"
                :key="item.status"
                class="reading-item"
              >
                <div class="reading-item-head">
                  <span
                    class="reading-dot"
                    :style="{ background: readingColors[item.status] }"
                  />
                  <span class="reading-label">{{ item.label }}</span>
                  <span class="reading-count">{{ item.count }} 篇</span>
                </div>
                <div class="reading-bar-track">
                  <div
                    class="reading-bar"
                    :style="{
                      width: `${readingTotal ? (item.count / readingTotal) * 100 : 0}%`,
                      background: readingColors[item.status],
                    }"
                  />
                </div>
              </div>
            </div>
            <div class="reading-total">
              共追踪 <b>{{ readingTotal }}</b> 篇论文的阅读进度
            </div>
          </div>
        </el-col>
      </el-row>

      <!-- 最近动态 -->
      <el-row :gutter="16">
        <el-col :xs="24" :sm="24" :md="8">
          <div class="ph-card list-card">
            <div class="list-card-head">
              <div class="ph-section-title">最近上传</div>
              <el-link type="primary" underline="never" @click="router.push('/user/library/mine')">
                查看全部
              </el-link>
            </div>
            <div
              v-for="p in data.recentUploads"
              :key="p.id"
              class="list-item"
              @click="goPaper(p.id)"
            >
              <div class="list-item-main">
                <div class="list-item-title ph-ellipsis">{{ p.title }}</div>
                <div class="list-item-meta">
                  {{ p.visibility === 'private' ? '私有' : '共享' }} ·
                  {{ paperCategoryName(p.categoryId) }} · {{ p.year }} 年
                </div>
              </div>
              <span class="list-item-time">{{ p.uploadedAt.slice(5, 16) }}</span>
            </div>
          </div>
        </el-col>

        <el-col :xs="24" :sm="24" :md="8">
          <div class="ph-card list-card">
            <div class="list-card-head">
              <div class="ph-section-title">最近阅读</div>
              <el-link type="primary" underline="never" @click="router.push('/user/reading')">
                查看全部
              </el-link>
            </div>
            <div
              v-for="item in data.recentReadings"
              :key="item.paper.id"
              class="list-item"
              @click="goPaper(item.paper.id)"
            >
              <div class="list-item-main">
                <div class="list-item-title ph-ellipsis">{{ item.paper.title }}</div>
                <div class="list-item-meta">
                  {{ readingStatusLabels[item.status] }} · {{ item.paper.journal || '未投稿' }}
                </div>
              </div>
              <span class="list-item-time">{{ item.lastReadAt.slice(5, 16) }}</span>
            </div>
          </div>
        </el-col>

        <el-col :xs="24" :sm="24" :md="8">
          <div class="ph-card list-card">
            <div class="list-card-head">
              <div class="ph-section-title">最近收藏</div>
              <el-link type="primary" underline="never" @click="router.push('/user/library/mine')">
                查看全部
              </el-link>
            </div>
            <div
              v-for="item in data.recentFavorites"
              :key="item.paper.id"
              class="list-item"
              @click="goPaper(item.paper.id)"
            >
              <div class="list-item-main">
                <div class="list-item-title ph-ellipsis">{{ item.paper.title }}</div>
                <div class="list-item-meta">
                  <TagChip
                    v-for="tid in item.paper.tagIds.slice(0, 1)"
                    :key="tid"
                    :name="getTag(tid)?.name ?? ''"
                    :color="getTag(tid)?.color"
                  />
                </div>
              </div>
              <span class="list-item-time">{{ item.favoritedAt.slice(5, 16) }}</span>
            </div>
          </div>
        </el-col>
      </el-row>
    </template>
  </div>
</template>

<style scoped>
/* ---------- 问候区 ---------- */
.greeting-row {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 20px;
}
.greeting-title {
  font-size: 22px;
  font-weight: 700;
  color: var(--ph-text);
}
.greeting-actions {
  display: flex;
  gap: 10px;
}

/* ---------- 统计卡片 ---------- */
.stat-grid {
  display: grid;
  grid-template-columns: repeat(5, 1fr);
  gap: 16px;
  margin-bottom: 16px;
}
.stat-card {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 18px;
  cursor: pointer;
  transition: transform 0.18s ease, box-shadow 0.18s ease;
}
.stat-card:hover {
  transform: translateY(-3px);
  box-shadow: 0 10px 24px rgba(15, 23, 42, 0.08);
}
.stat-icon {
  width: 46px;
  height: 46px;
  border-radius: 13px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}
.stat-value {
  font-size: 26px;
  font-weight: 700;
  color: var(--ph-text);
  line-height: 1.1;
}
.stat-label {
  font-size: 13px;
  color: var(--ph-text-secondary);
  margin-top: 3px;
}
.stat-caption {
  font-size: 11px;
  color: var(--ph-text-muted);
  margin-top: 2px;
}

/* ---------- 图表卡 ---------- */
.chart-row {
  margin-bottom: 16px;
}
.chart-card {
  height: 100%;
  padding: 18px 18px 10px;
  box-sizing: border-box;
}
.chart-card .ph-section-title {
  margin-bottom: 8px;
}

/* 阅读状态分布 */
.reading-list {
  display: flex;
  flex-direction: column;
  gap: 14px;
  padding: 8px 4px 0;
}
.reading-item-head {
  display: flex;
  align-items: center;
  gap: 7px;
  margin-bottom: 7px;
}
.reading-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
}
.reading-label {
  font-size: 13px;
  color: var(--ph-text);
}
.reading-count {
  margin-left: auto;
  font-size: 12.5px;
  color: var(--ph-text-secondary);
}
.reading-bar-track {
  height: 8px;
  border-radius: 4px;
  background: #f3f4f6;
  overflow: hidden;
}
.reading-bar {
  height: 100%;
  border-radius: 4px;
  transition: width 0.4s ease;
}
.reading-total {
  margin-top: 16px;
  padding-top: 12px;
  border-top: 1px dashed var(--ph-border);
  font-size: 12px;
  color: var(--ph-text-muted);
}
.reading-total b {
  color: var(--ph-primary);
}

/* ---------- 列表卡 ---------- */
.list-card {
  padding: 18px;
  margin-bottom: 16px;
}
.list-card-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 6px;
}
.list-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 4px;
  border-bottom: 1px solid #f3f4f6;
  cursor: pointer;
}
.list-item:last-child {
  border-bottom: none;
}
.list-item-main {
  flex: 1;
  min-width: 0;
}
.list-item-title {
  font-size: 13.5px;
  font-weight: 600;
  color: var(--ph-text);
  transition: color 0.15s;
}
.list-item:hover .list-item-title {
  color: var(--ph-primary);
}
.list-item-meta {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  color: var(--ph-text-muted);
  margin-top: 4px;
}
.list-item-time {
  font-size: 12px;
  color: var(--ph-text-muted);
  flex-shrink: 0;
}

@media (max-width: 1200px) {
  .stat-grid {
    grid-template-columns: repeat(2, 1fr);
  }
}
</style>
