<script setup lang="ts">
import { onUnmounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import type { Paper } from '@/types/paper'
import { getPaperForDetail } from '@/services/myLibraryService'
import { getPaperById } from '@/services/adminService'
import { getPaperFile, downloadPaper } from '@/services/paperFiles'
import { reportActionError } from '@/utils/errors'
const route = useRoute()
const router = useRouter()
const paper = ref<Paper | null>(null)
const pdfUrl = ref('')
const loading = ref(false)
const errorMessage = ref('')
let request = 0
function release() {
  if (pdfUrl.value) URL.revokeObjectURL(pdfUrl.value)
  pdfUrl.value = ''
}
async function load() {
  const current = ++request
  release()
  paper.value = null
  errorMessage.value = ''
  loading.value = true
  try {
    const id = Number(route.params.id)
    const item = route.path.startsWith('/admin/')
      ? await getPaperById(id)
      : await getPaperForDetail(id)
    if (current !== request) return
    if (!item) throw new Error('论文不存在或不可访问')
    paper.value = item
    const blob = await getPaperFile(id)
    if (current === request) pdfUrl.value = URL.createObjectURL(blob)
  } catch (error) {
    if (current === request)
      errorMessage.value = error instanceof Error ? error.message : 'PDF加载失败'
  } finally {
    if (current === request) loading.value = false
  }
}
async function download() {
  if (!paper.value) return
  try {
    await downloadPaper(paper.value)
  } catch (error) {
    reportActionError(error)
  }
}
function back() {
  if (window.history.length > 1) router.back()
  else void router.push(route.path.startsWith('/admin/') ? '/admin/papers' : '/user/library/mine')
}
watch(() => route.params.id, load, { immediate: true })
onUnmounted(() => {
  request++
  release()
})
</script>
<template>
  <div class="reader-page" v-loading="loading">
    <div class="reader-toolbar">
      <el-button :icon="'ArrowLeft'" @click="back">返回</el-button>
      <div class="reader-title">
        <strong>{{ paper?.title ?? 'PDF阅读器' }}</strong
        ><span v-if="paper">{{ paper.pageCount }} 页</span>
      </div>
      <a v-if="pdfUrl" :href="pdfUrl" target="_blank" rel="noopener noreferrer">新窗口阅读</a>
      <el-button v-if="pdfUrl" :icon="'Download'" @click="download">下载 PDF</el-button>
    </div>
    <el-empty v-if="errorMessage && !loading" :description="errorMessage"
      ><el-button @click="load">重新加载</el-button></el-empty
    >
    <iframe v-else-if="pdfUrl" :src="pdfUrl" title="论文PDF内容" class="pdf-frame" />
  </div>
</template>
<style scoped>
.reader-page {
  min-height: calc(100vh - 100px);
  background: #f1f5f9;
}
.reader-toolbar {
  display: flex;
  gap: 16px;
  align-items: center;
  padding: 16px 24px;
  background: white;
  border-bottom: 1px solid #e5e7eb;
}
.reader-title {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.reader-title strong {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.reader-title span {
  color: #64748b;
  font-size: 12px;
}
.pdf-frame {
  display: block;
  width: 100%;
  height: calc(100vh - 170px);
  min-height: 600px;
  border: 0;
}
</style>
