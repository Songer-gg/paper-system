<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import type { Paper } from '@/types/paper'
import * as myLibrary from '@/services/myLibraryService'

const route = useRoute()
const router = useRouter()
const paperId = Number(route.params.id)

const loading = ref(true)
const paper = ref<Paper | null>(null)

/* ---------------- 阅读状态 ---------------- */
const pageCount = ref(12)
const currentPage = ref(1)
const jumpPage = ref<number | null>(null)
const scale = ref(1)
const scaleOptions = [0.5, 0.75, 0.9, 1, 1.1, 1.25, 1.5, 1.75, 2]

onMounted(async () => {
  const p = await myLibrary.getPaperForDetail(paperId)
  paper.value = p
  if (p) {
    pageCount.value = Math.max(p.pageCount, 4)
    currentPage.value = 1
  }
  loading.value = false
})

const canPrev = computed(() => currentPage.value > 1)
const canNext = computed(() => currentPage.value < pageCount.value)

function goPrev() {
  if (canPrev.value) {
    currentPage.value--
    jumpPage.value = null
  }
}

function goNext() {
  if (canNext.value) {
    currentPage.value++
    jumpPage.value = null
  }
}

function goJump() {
  const target = jumpPage.value
  if (!target) return
  if (target < 1 || target > pageCount.value) {
    ElMessage.warning(`页码范围为 1 - ${pageCount.value}`)
    return
  }
  currentPage.value = target
  jumpPage.value = null
}

/* ---------------- 缩放 ---------------- */
function zoomIn() {
  const idx = scaleOptions.findIndex((s) => s >= scale.value)
  if (idx < scaleOptions.length - 1) scale.value = scaleOptions[idx + 1]
}

function zoomOut() {
  const idx = scaleOptions.findIndex((s) => s >= scale.value)
  scale.value = scaleOptions[Math.max(0, idx - 1)]
}

function fitPage() {
  scale.value = 1
  ElMessage.success('已适应页面宽度')
}

function onWheel(e: WheelEvent) {
  if (e.ctrlKey) {
    e.preventDefault()
    if (e.deltaY < 0) zoomIn()
    else zoomOut()
  }
}

function onKeyDown(e: KeyboardEvent) {
  if (e.key === 'ArrowLeft') goPrev()
  if (e.key === 'ArrowRight') goNext()
}

onMounted(() => window.addEventListener('keydown', onKeyDown))
onUnmounted(() => window.removeEventListener('keydown', onKeyDown))

/* ---------------- 其他操作 ---------------- */
function download() {
  ElMessage.success(`已开始下载《${paper.value?.title ?? '论文'}》（模拟）`)
}

function goBack() {
  if (window.history.length > 1) router.back()
  else router.push(`/user/papers/${paperId}`)
}

/* ---------------- 模拟页面内容 ---------------- */
interface Line {
  w: string
  h: number
}
const pageLines = computed<Line[]>(() => {
  const seed = paperId * 7 + currentPage.value * 13
  const lines: Line[] = []
  for (let i = 0; i < 16; i++) {
    const r = Math.abs(Math.sin(seed + i * 3.7) * 10000) % 100
    const isLast = i === 15
    lines.push({
      w: isLast ? '38%' : `${58 + (r % 40)}%`,
      h: 8,
    })
  }
  return lines
})

const hasFigure = computed(() => currentPage.value % 4 === 2)
const hasTable = computed(() => currentPage.value % 5 === 3)
</script>

<template>
  <div class="reader-shell" @wheel="onWheel">
    <!-- 顶部工具栏 -->
    <header class="reader-toolbar">
      <div class="toolbar-left">
        <el-button text :icon="'ArrowLeft'" @click="goBack">返回详情</el-button>
        <el-divider direction="vertical" />
        <span class="reader-title ph-ellipsis">
          {{ paper?.title ?? (loading ? '加载中…' : '论文预览') }}
        </span>
      </div>

      <div class="toolbar-center">
        <el-button text :icon="'ArrowLeft'" :disabled="!canPrev" @click="goPrev">
          上一页
        </el-button>
        <el-input-number
          v-model="jumpPage"
          :min="1"
          :max="pageCount"
          :controls="false"
          size="small"
          class="jump-input"
          :placeholder="`${currentPage}`"
          @change="goJump"
        />
        <span class="page-total">/ {{ pageCount }} 页</span>
        <el-button text :icon="'ArrowRight'" :disabled="!canNext" @click="goNext">
          下一页
        </el-button>
      </div>

      <div class="toolbar-right">
        <el-tooltip content="缩小（Ctrl+滚轮）" placement="bottom">
          <el-button text :icon="'ZoomOut'" @click="zoomOut" />
        </el-tooltip>
        <span class="scale-text">{{ Math.round(scale * 100) }}%</span>
        <el-tooltip content="放大（Ctrl+滚轮）" placement="bottom">
          <el-button text :icon="'ZoomIn'" @click="zoomIn" />
        </el-tooltip>
        <el-tooltip content="适应页面" placement="bottom">
          <el-button text :icon="'FullScreen'" @click="fitPage" />
        </el-tooltip>
        <el-divider direction="vertical" />
        <el-button text :icon="'Download'" @click="download">下载</el-button>
      </div>
    </header>

    <!-- 阅读区 -->
    <main class="reader-body">
      <div v-if="loading" v-loading="true" class="reader-loading" />

      <template v-else-if="paper">
        <div class="page-stage">
          <div class="paper-page" :style="{ transform: `scale(${scale})` }">
            <!-- 页眉：论文标题 + 期刊 -->
            <div class="sim-header">
              <div class="sim-journal">{{ paper.journal || 'Preprint' }}</div>
              <div class="sim-doi" v-if="paper.doi">DOI: {{ paper.doi }}</div>
            </div>

            <div v-if="currentPage === 1" class="sim-title-block">
              <div class="sim-title">{{ paper.title }}</div>
              <div class="sim-authors">{{ paper.authors.join('，') }}</div>
            </div>

            <!-- 模拟正文行 -->
            <div class="sim-content">
              <div
                v-for="(line, i) in pageLines"
                :key="i"
                class="sim-line"
                :style="{ width: line.w, height: line.h + 'px' }"
              />

              <!-- 模拟插图 -->
              <div v-if="hasFigure" class="sim-figure">
                <el-icon :size="30" color="#c4cbd8"><Picture /></el-icon>
                <span>Figure {{ Math.ceil(currentPage / 4) }} · 示意图（模拟内容）</span>
              </div>

              <!-- 模拟表格 -->
              <div v-if="hasTable" class="sim-table">
                <div v-for="r in 4" :key="r" class="sim-table-row">
                  <div v-for="c in 5" :key="c" class="sim-table-cell" />
                </div>
                <div class="sim-table-caption">
                  Table {{ Math.ceil(currentPage / 5) }} · 数据表（模拟内容）
                </div>
              </div>
            </div>

            <!-- 页脚：页码 -->
            <div class="sim-footer">
              <span>{{ currentPage }}</span>
            </div>
          </div>
        </div>

        <!-- 底部翻页 -->
        <div class="pager-bar">
          <el-button :disabled="!canPrev" @click="goPrev">上一页</el-button>
          <el-pagination
            layout="pager"
            :page-size="1"
            :total="pageCount"
            :current-page="currentPage"
            :pager-count="9"
            @current-change="(p: number) => (currentPage = p)"
          />
          <el-button :disabled="!canNext" @click="goNext">下一页</el-button>
        </div>
      </template>

      <div v-else class="reader-empty">
        <el-empty description="论文不存在或暂无访问权限">
          <el-button type="primary" @click="goBack">返回详情</el-button>
        </el-empty>
      </div>
    </main>

    <!-- 模拟预览声明 -->
    <div class="sim-banner">
      模拟预览模式：当前为第一阶段前端演示的示意页面，正式版将接入真实 PDF 渲染
    </div>
  </div>
</template>

<style scoped>
.reader-shell {
  height: 100vh;
  display: flex;
  flex-direction: column;
  background: #3b4256;
}

/* ---------- 工具栏 ---------- */
.reader-toolbar {
  height: 52px;
  flex-shrink: 0;
  background: #2b3245;
  color: #e5e9f2;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 16px;
  gap: 12px;
}
.toolbar-left,
.toolbar-center,
.toolbar-right {
  display: flex;
  align-items: center;
  gap: 6px;
  min-width: 0;
}
.toolbar-left {
  flex: 1.2;
}
.toolbar-center {
  flex: 1;
  justify-content: center;
}
.toolbar-right {
  flex: 0.9;
  justify-content: flex-end;
}
.reader-toolbar :deep(.el-button) {
  color: #c3cad9;
}
.reader-toolbar :deep(.el-button.is-disabled) {
  color: #5c6478;
}
.reader-title {
  font-size: 13.5px;
  color: #e5e9f2;
  max-width: 300px;
}
.page-total {
  font-size: 12.5px;
  color: #98a1b5;
}
.jump-input {
  width: 56px;
}
.jump-input :deep(.el-input__inner) {
  text-align: center;
  background: #222839;
  border-color: #454d63;
  color: #e5e9f2;
}
.scale-text {
  font-size: 12px;
  color: #98a1b5;
  width: 42px;
  text-align: center;
}
.reader-toolbar :deep(.el-divider) {
  background: #454d63;
}

/* ---------- 阅读区 ---------- */
.reader-body {
  flex: 1;
  overflow: auto;
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 26px 20px 20px;
}
.reader-loading,
.reader-empty {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 100%;
}
.reader-empty :deep(.el-empty__description p) {
  color: #98a1b5;
}

.page-stage {
  display: flex;
  justify-content: center;
  width: 100%;
}
.paper-page {
  width: 640px;
  min-height: 860px;
  background: #fff;
  border-radius: 4px;
  box-shadow: 0 8px 40px rgba(0, 0, 0, 0.45);
  padding: 44px 56px 36px;
  transform-origin: top center;
  transition: transform 0.15s ease;
  display: flex;
  flex-direction: column;
}

/* 模拟页面元素 */
.sim-header {
  display: flex;
  justify-content: space-between;
  font-size: 11px;
  color: #9ca3af;
  border-bottom: 1px solid #e5e7eb;
  padding-bottom: 8px;
  margin-bottom: 18px;
  font-family: 'Times New Roman', serif;
  font-style: italic;
}
.sim-title-block {
  text-align: center;
  margin-bottom: 22px;
}
.sim-title {
  font-size: 19px;
  font-weight: 700;
  color: #111827;
  line-height: 1.5;
  font-family: 'Times New Roman', 'Songti SC', serif;
}
.sim-authors {
  margin-top: 10px;
  font-size: 13px;
  color: #4b5563;
}
.sim-content {
  flex: 1;
}
.sim-line {
  background: #d7dbe4;
  border-radius: 3px;
  margin-bottom: 11px;
  animation: none;
}
.sim-line:first-child {
  background: #9ca3af;
  height: 12px !important;
  margin-bottom: 16px;
}
.sim-figure {
  margin: 18px 10px;
  border: 1.5px dashed #c4cbd8;
  border-radius: 6px;
  height: 150px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 8px;
  color: #9ca3af;
  font-size: 12px;
}
.sim-table {
  margin: 18px 4px;
}
.sim-table-row {
  display: flex;
}
.sim-table-row + .sim-table-row .sim-table-cell {
  border-top: none;
}
.sim-table-cell {
  flex: 1;
  height: 26px;
  border: 1px solid #d1d5db;
  background: #f8fafc;
}
.sim-table-row + .sim-table-row .sim-table-cell {
  border-top: none;
}
.sim-table-caption {
  text-align: center;
  font-size: 11.5px;
  color: #9ca3af;
  margin-top: 8px;
}
.sim-footer {
  text-align: center;
  font-size: 12px;
  color: #9ca3af;
  margin-top: 20px;
  font-family: 'Times New Roman', serif;
}

/* ---------- 底部翻页 ---------- */
.pager-bar {
  margin-top: 22px;
  display: flex;
  align-items: center;
  gap: 14px;
}
.pager-bar :deep(.el-button) {
  background: transparent;
  border-color: #454d63;
  color: #c3cad9;
}
.pager-bar :deep(.el-pagination) {
  --el-pagination-bg-color: transparent;
  --el-pagination-text-color: #98a1b5;
  --el-pagination-button-disabled-bg-color: transparent;
  --el-pagination-hover-color: #fff;
}
.pager-bar :deep(.el-pager li) {
  background: transparent;
  color: #98a1b5;
}
.pager-bar :deep(.el-pager li.is-active) {
  color: #fff;
  font-weight: 700;
}

/* ---------- 模拟声明 ---------- */
.sim-banner {
  flex-shrink: 0;
  text-align: center;
  font-size: 12px;
  color: #98a1b5;
  background: #2b3245;
  padding: 8px 16px;
  border-top: 1px solid #222839;
}
</style>
