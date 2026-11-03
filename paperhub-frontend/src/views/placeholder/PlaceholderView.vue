<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'

const route = useRoute()
const title = computed(() => (route.meta?.title as string) ?? '页面')
const group = computed(() => route.meta?.group as string | undefined)
</script>

<template>
  <div class="ph-page">
    <div class="ph-page-header">
      <div>
        <h2 class="ph-page-title">{{ title }}</h2>
        <p class="ph-page-desc">
          {{ group ? `${group} · ` : '' }}该页面属于后续开发批次，当前批次优先完成整体布局与导航结构。
        </p>
      </div>
    </div>
    <div class="ph-card placeholder-card">
      <div class="placeholder-icon">
        <el-icon :size="36"><Clock /></el-icon>
      </div>
      <h3>「{{ title }}」页面开发中</h3>
      <p>
        当前为第一阶段分批开发模式，此页面将在对应批次完成视觉与交互实现。
      </p>
      <el-tag type="info" effect="plain" round>PaperHub · 分批开发占位</el-tag>
    </div>
  </div>
</template>

<style scoped>
.placeholder-card {
  min-height: 420px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 14px;
  padding: 48px 24px;
  text-align: center;
}
.placeholder-icon {
  width: 72px;
  height: 72px;
  border-radius: 20px;
  background: var(--ph-primary-light);
  color: var(--ph-primary);
  display: flex;
  align-items: center;
  justify-content: center;
  margin-bottom: 4px;
}
.placeholder-card h3 {
  font-size: 17px;
  font-weight: 600;
  color: var(--ph-text);
}
.placeholder-card p {
  font-size: 13px;
  color: var(--ph-text-secondary);
  max-width: 380px;
  line-height: 1.7;
}
</style>
