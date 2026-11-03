<script setup lang="ts">
defineProps<{
  name: string
  color?: string
}>()
</script>

<template>
  <span class="tag-chip">
    <span class="tag-dot" :style="{ background: color ?? '#6B7280' }" />
    {{ name }}
  </span>
</template>

<style scoped>
.tag-chip {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  height: 22px;
  padding: 0 9px;
  border-radius: 6px;
  background: #f3f4f6;
  font-size: 12px;
  color: var(--ph-text-secondary);
  white-space: nowrap;
}
.tag-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  flex-shrink: 0;
}
</style>
