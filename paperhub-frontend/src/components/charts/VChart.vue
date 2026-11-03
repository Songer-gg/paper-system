<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'
import * as echarts from 'echarts'

/**
 * ECharts 通用封装：初始化、随容器自适应缩放、option 响应式更新、自动销毁。
 */
const props = defineProps<{
  option: echarts.EChartsOption
  height?: string
}>()

const el = ref<HTMLDivElement>()
let chart: echarts.ECharts | null = null
let observer: ResizeObserver | null = null

onMounted(() => {
  if (!el.value) return
  chart = echarts.init(el.value)
  chart.setOption(props.option)
  observer = new ResizeObserver(() => chart?.resize())
  observer.observe(el.value)
})

watch(
  () => props.option,
  (opt) => chart?.setOption(opt, true),
  { deep: true },
)

onBeforeUnmount(() => {
  observer?.disconnect()
  observer = null
  chart?.dispose()
  chart = null
})
</script>

<template>
  <div ref="el" class="v-chart" :style="{ height: height ?? '300px' }" />
</template>

<style scoped>
.v-chart {
  width: 100%;
}
</style>
