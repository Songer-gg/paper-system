<script setup lang="ts">
import { onMounted, ref } from 'vue'
import * as echarts from 'echarts'
import VChart from '@/components/charts/VChart.vue'
import { getUserStatistics } from '@/services/statisticsService'
import type { MyProfile } from '@/services/profileService'
import { getMyProfile } from '@/services/profileService'
import { useAuthStore } from '@/stores/auth'

const auth = useAuthStore()

const loading = ref(true)
const stat = ref<Awaited<ReturnType<typeof getUserStatistics>> | null>(null)
const profile = ref<MyProfile | null>(null)

onMounted(async () => {
  stat.value = await getUserStatistics()
  profile.value = await getMyProfile()
  loading.value = false
})

/* ---------------- 图表配置 ---------------- */
function trendOption(data: { label: string; count: number }[]): echarts.EChartsOption {
  return {
    grid: { left: 40, right: 20, top: 30, bottom: 28 },
    tooltip: { trigger: 'axis' },
    xAxis: {
      type: 'category',
      data: data.map((d) => d.label),
      axisLine: { lineStyle: { color: '#e5e7eb' } },
      axisLabel: { color: '#9ca3af', fontSize: 11 },
    },
    yAxis: {
      type: 'value',
      minInterval: 1,
      splitLine: { lineStyle: { color: '#f3f4f6' } },
      axisLabel: { color: '#9ca3af', fontSize: 11 },
    },
    series: [
      {
        type: 'line',
        smooth: true,
        symbolSize: 6,
        data: data.map((d) => d.count),
        itemStyle: { color: '#2563eb' },
        lineStyle: { width: 3, color: '#2563eb' },
        areaStyle: {
          color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
            { offset: 0, color: 'rgba(37,99,235,0.22)' },
            { offset: 1, color: 'rgba(37,99,235,0.02)' },
          ]),
        },
      },
    ],
  }
}

function yearOption(data: { name: string; count: number }[]): echarts.EChartsOption {
  return {
    grid: { left: 40, right: 20, top: 30, bottom: 28 },
    tooltip: { trigger: 'axis' },
    xAxis: {
      type: 'category',
      data: data.map((d) => d.name),
      axisLine: { lineStyle: { color: '#e5e7eb' } },
      axisLabel: { color: '#9ca3af', fontSize: 11 },
    },
    yAxis: {
      type: 'value',
      minInterval: 1,
      splitLine: { lineStyle: { color: '#f3f4f6' } },
      axisLabel: { color: '#9ca3af', fontSize: 11 },
    },
    series: [
      {
        type: 'bar',
        data: data.map((d) => d.count),
        barWidth: 22,
        itemStyle: {
          borderRadius: [6, 6, 0, 0],
          color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
            { offset: 0, color: '#3b82f6' },
            { offset: 1, color: '#93c5fd' },
          ]),
        },
      },
    ],
  }
}

function categoryOption(data: { name: string; count: number }[]): echarts.EChartsOption {
  return {
    tooltip: { trigger: 'item', formatter: '{b}：{c} 篇（{d}%）' },
    legend: {
      orient: 'vertical',
      right: 10,
      top: 'center',
      itemWidth: 10,
      itemHeight: 10,
      textStyle: { color: '#6b7280', fontSize: 12 },
    },
    color: ['#2563eb', '#7c3aed', '#0891b2', '#d97706', '#db2777', '#16a34a', '#475569', '#059669'],
    series: [
      {
        type: 'pie',
        radius: ['45%', '68%'],
        center: ['36%', '50%'],
        avoidLabelOverlap: true,
        itemStyle: { borderRadius: 6, borderColor: '#fff', borderWidth: 2 },
        label: { show: false },
        data: data.map((d) => ({ name: d.name, value: d.count })),
      },
    ],
  }
}

function journalOption(data: { name: string; count: number }[]): echarts.EChartsOption {
  return {
    grid: { left: 150, right: 40, top: 20, bottom: 20 },
    tooltip: { trigger: 'axis' },
    xAxis: {
      type: 'value',
      minInterval: 1,
      splitLine: { lineStyle: { color: '#f3f4f6' } },
      axisLabel: { color: '#9ca3af', fontSize: 11 },
    },
    yAxis: {
      type: 'category',
      data: [...data].reverse().map((d) => d.name),
      axisLine: { lineStyle: { color: '#e5e7eb' } },
      axisLabel: {
        color: '#6b7280',
        fontSize: 11,
        width: 140,
        overflow: 'truncate',
      },
    },
    series: [
      {
        type: 'bar',
        data: [...data].reverse().map((d) => d.count),
        barWidth: 12,
        itemStyle: { borderRadius: [0, 6, 6, 0], color: '#0d9488' },
      },
    ],
  }
}
</script>

<template>
  <div class="ph-page">
    <div class="ph-page-header">
      <div>
        <h2 class="ph-page-title">数据统计</h2>
        <p class="ph-page-desc">{{ auth.user?.name }} 的论文库、收藏与阅读数据可视化</p>
      </div>
    </div>

    <div v-if="loading" v-loading="true" class="stat-loading" />

    <template v-else-if="stat && profile">
      <!-- 统计卡 -->
      <div class="stat-cards">
        <div class="ph-card stat-card">
          <div class="stat-label">论文总数</div>
          <div class="stat-value">{{ stat.totalPapers }}</div>
          <div class="stat-sub">
            含私有 {{ stat.privateCount }} 篇 · 共享 {{ stat.sharedCount }} 篇
          </div>
        </div>
        <div class="ph-card stat-card">
          <div class="stat-label">收藏论文</div>
          <div class="stat-value warn">{{ stat.favoriteCount }}</div>
          <div class="stat-sub">来自共享库的收藏</div>
        </div>
        <div class="ph-card stat-card">
          <div class="stat-label">已阅读</div>
          <div class="stat-value success">{{ stat.readCount }}</div>
          <div class="stat-sub">阅读状态为「已阅读」的论文</div>
        </div>
        <div class="ph-card stat-card">
          <div class="stat-label">阅读笔记</div>
          <div class="stat-value info">{{ stat.noteCount }}</div>
          <div class="stat-sub">累计记录的阅读心得</div>
        </div>
        <div class="ph-card stat-card">
          <div class="stat-label">注册时间</div>
          <div class="stat-value time">{{ profile.registeredAt.slice(0, 10) }}</div>
          <div class="stat-sub">已上传 {{ profile.uploadedCount }} 篇论文</div>
        </div>
      </div>

      <!-- 图表区 -->
      <div class="chart-grid">
        <div class="ph-card chart-card span-2">
          <div class="ph-section-title">月度阅读趋势（近 12 个月）</div>
          <VChart :option="trendOption(stat.monthlyReadingTrend)" height="300px" />
        </div>
        <div class="ph-card chart-card">
          <div class="ph-section-title">论文年份分布</div>
          <VChart :option="yearOption(stat.yearDistribution)" height="300px" />
        </div>
        <div class="ph-card chart-card">
          <div class="ph-section-title">论文分类分布</div>
          <VChart :option="categoryOption(stat.categoryDistribution)" height="300px" />
        </div>
        <div class="ph-card chart-card">
          <div class="ph-section-title">发表期刊分布（Top 8）</div>
          <VChart :option="journalOption(stat.journalDistribution)" height="300px" />
        </div>
      </div>
    </template>
  </div>
</template>

<style scoped>
.stat-loading {
  height: 400px;
}

.stat-cards {
  display: grid;
  grid-template-columns: repeat(5, 1fr);
  gap: 16px;
  margin-bottom: 20px;
}
.stat-card {
  padding: 18px 20px;
}
.stat-label {
  font-size: 12.5px;
  color: var(--ph-text-muted);
}
.stat-value {
  font-size: 30px;
  font-weight: 800;
  color: var(--ph-primary);
  margin-top: 8px;
  font-variant-numeric: tabular-nums;
}
.stat-value.warn {
  color: var(--ph-warning);
}
.stat-value.success {
  color: var(--ph-success);
}
.stat-value.info {
  color: #0d9488;
}
.stat-value.time {
  font-size: 22px;
  line-height: 38px;
}
.stat-sub {
  font-size: 11.5px;
  color: var(--ph-text-muted);
  margin-top: 6px;
}

.chart-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 16px;
  padding-bottom: 24px;
}
.chart-card {
  padding: 18px 20px;
  min-width: 0;
}
.chart-card.span-2 {
  grid-column: span 2;
}

@media (max-width: 1200px) {
  .stat-cards {
    grid-template-columns: repeat(3, 1fr);
  }
  .chart-grid {
    grid-template-columns: repeat(2, 1fr);
  }
  .chart-card.span-2 {
    grid-column: span 2;
  }
}
@media (max-width: 900px) {
  .stat-cards,
  .chart-grid {
    grid-template-columns: 1fr;
  }
  .chart-card.span-2 {
    grid-column: span 1;
  }
}
</style>
