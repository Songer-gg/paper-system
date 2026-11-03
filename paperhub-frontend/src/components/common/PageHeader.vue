<script setup lang="ts">
defineProps<{
  title: string
  description?: string
}>()
</script>

<template>
  <div class="ph-page-header">
    <div class="ph-page-header-text">
      <h2 class="ph-page-title">{{ title }}</h2>
      <p v-if="description" class="ph-page-desc">{{ description }}</p>
    </div>
    <div v-if="$slots.actions" class="ph-page-header-actions">
      <slot name="actions" />
    </div>
  </div>
</template>

<style scoped>
.ph-page-header-text {
  min-width: 0;
}
.ph-page-header-actions {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-shrink: 0;
}
</style>
