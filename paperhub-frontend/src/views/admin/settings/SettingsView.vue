<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { getSettings } from '@/services/adminService'
import type { PlatformSettings } from '../../../../../paperhub-api/ts/types'
import { reportActionError } from '@/utils/errors'
import { ElMessage } from 'element-plus'

/**
 * 系统设置：从服务端读取平台基础信息。
 */
const settings = ref<PlatformSettings | null>(null)

onMounted(async () => {
  try {
    settings.value = await getSettings()
  } catch (error) {
    reportActionError(error)
  }
})

function onSave() {
  ElMessage.info('当前设置仅供查看')
}
</script>

<template>
  <div v-if="settings" class="ph-page">
    <div class="ph-page-header">
      <div>
        <h2 class="ph-page-title">系统设置</h2>
        <p class="ph-page-desc">平台基础信息与上传限制</p>
      </div>
    </div>

    <div class="settings-grid">
      <!-- 平台信息 -->
      <div class="ph-card section-card">
        <h3 class="ph-section-title">平台信息</h3>
        <div class="logo-row">
          <div class="logo-badge">{{ settings.logoText }}</div>
          <div>
            <div class="logo-name">{{ settings.platformName }}</div>
            <div class="logo-slogan">{{ settings.slogan }}</div>
          </div>
        </div>
        <el-descriptions :column="1" border class="desc">
          <el-descriptions-item label="平台名称">{{ settings.platformName }}</el-descriptions-item>
          <el-descriptions-item label="平台版本">{{ settings.version }}</el-descriptions-item>
          <el-descriptions-item label="联系邮箱">{{ settings.contactEmail }}</el-descriptions-item>
        </el-descriptions>
      </div>

      <!-- 上传限制 -->
      <div class="ph-card section-card">
        <h3 class="ph-section-title">上传限制</h3>
        <el-descriptions :column="1" border class="desc">
          <el-descriptions-item label="文件大小限制">
            单个文件不超过 {{ settings.maxUploadMB }} MB
          </el-descriptions-item>
          <el-descriptions-item label="允许上传类型">
            <el-tag type="primary" effect="plain" size="small"
              >PDF（{{ settings.allowedTypes }}）</el-tag
            >
          </el-descriptions-item>
        </el-descriptions>
        <el-alert type="info" :closable="false" show-icon title="系统设置仅供查看。" />
      </div>
    </div>

    <div class="ph-card section-card save-card">
      <el-button type="primary" @click="onSave">保存设置</el-button>
      <span class="save-hint">当前设置仅供查看</span>
    </div>
  </div>
  <el-empty v-else description="暂未获取到平台设置，请稍后重试" />
</template>

<style scoped>
.settings-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 20px;
  margin-bottom: 20px;
}
.section-card {
  padding: 20px 24px;
}
.section-card .ph-section-title {
  margin-bottom: 16px;
}

.logo-row {
  display: flex;
  align-items: center;
  gap: 14px;
  margin-bottom: 18px;
}
.logo-badge {
  width: 52px;
  height: 52px;
  border-radius: 12px;
  background: var(--ph-primary);
  color: #fff;
  font-size: 20px;
  font-weight: 800;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}
.logo-name {
  font-size: 16px;
  font-weight: 700;
  color: var(--ph-text);
}
.logo-slogan {
  margin-top: 2px;
  font-size: 12.5px;
  color: var(--ph-text-muted);
}
.desc {
  margin-bottom: 16px;
}

.save-card {
  display: flex;
  align-items: center;
  gap: 14px;
}
.save-hint {
  font-size: 12.5px;
  color: var(--ph-text-muted);
}

@media (max-width: 1080px) {
  .settings-grid {
    grid-template-columns: 1fr;
  }
}
</style>
