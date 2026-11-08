<script setup lang="ts">
import { computed, onMounted, onUnmounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance, FormRules, UploadFile, UploadInstance } from 'element-plus'
import type { Category, Paper, Tag } from '@/types/paper'
import * as myLibrary from '@/services/myLibraryService'
import * as userData from '@/services/userDataService'
import { getCategories, getTags } from '@/services/knowledgeService'

const router = useRouter()
const categories = ref<Category[]>([])
const tags = ref<Tag[]>([])

/* ---------------- 文件区（选择后随表单提交） ---------------- */
const uploadRef = ref<UploadInstance>()
interface SelectedPdf {
  raw: File
  name: string
  sizeMB: number
  sha256: string
}
const file = ref<SelectedPdf | null>(null)
const uploadProgress = ref(0)
const uploading = ref(false)

async function handleFileChange(uploadFile: UploadFile) {
  const selection = ++fileSelection
  file.value = null
  uploadProgress.value = 0
  uploading.value = false
  const raw = uploadFile.raw
  if (!raw) return
  if (!raw.name.toLowerCase().endsWith('.pdf')) {
    ElMessage.error('仅支持 PDF 格式文件')
    uploadRef.value?.clearFiles()
    return
  }
  const sizeMB = Math.round((raw.size / 1024 / 1024) * 10) / 10
  if (raw.size === 0 || raw.size > 100 * 1024 * 1024) {
    ElMessage.error('文件大小不能超过 100 MB')
    uploadRef.value?.clearFiles()
    return
  }
  try {
    const buffer = await raw.arrayBuffer()
    if (!new TextDecoder().decode(buffer.slice(0, 5)).startsWith('%PDF-')) {
      throw new Error('文件内容不是有效的 PDF')
    }
    const digest = await crypto.subtle.digest('SHA-256', buffer)
    if (selection !== fileSelection) return
    file.value = {
      raw,
      name: raw.name,
      sizeMB: sizeMB || 0.1,
      sha256: Array.from(new Uint8Array(digest), (byte) => byte.toString(16).padStart(2, '0')).join(
        '',
      ),
    }
    uploadProgress.value = 100
  } catch (error) {
    if (selection !== fileSelection) return
    uploadRef.value?.clearFiles()
    ElMessage.error(error instanceof Error ? error.message : '无法读取文件，请重新选择')
  }
}

let fileSelection = 0

onUnmounted(() => {
  fileSelection++
})

function removeFile() {
  fileSelection++
  file.value = null
  uploadProgress.value = 0
  uploading.value = false
  uploadRef.value?.clearFiles()
}

/* ---------------- 表单 ---------------- */
const formRef = ref<FormInstance>()
const form = reactive({
  title: '',
  authors: '',
  abstract: '',
  doi: '',
  journal: '',
  year: null as number | null,
  keywords: '',
  link: '',
  categoryId: null as number | null,
  tagIds: [] as number[],
  visibility: 'private' as 'private' | 'shared',
})
const copyrightAgreed = ref(false)

const yearOptions = Array.from({ length: 36 }, (_, i) => new Date().getFullYear() - i)

interface CascaderNode {
  value: number
  label: string
  children: CascaderNode[]
}
const categoryTree = computed<CascaderNode[]>(() => {
  const build = (parentId: number | null): CascaderNode[] =>
    categories.value
      .filter((category) => form.visibility === 'private' || !category.custom)
      .filter((c) => c.parentId === parentId)
      .map((c) => ({ value: c.id, label: c.name, children: build(c.id) }))
  return build(null)
})

const rules: FormRules = {
  title: [{ required: true, message: '请输入论文标题', trigger: 'blur' }],
  authors: [{ required: true, message: '请输入作者，多个作者用逗号分隔', trigger: 'blur' }],
  abstract: [{ required: true, message: '请输入论文摘要', trigger: 'blur' }],
  year: [{ required: true, message: '请选择发表年份', trigger: 'change' }],
  categoryId: [{ required: true, message: '请选择论文分类', trigger: 'change' }],
}

/* 共享时必须勾选版权授权，否则禁止提交 */
const copyrightRule = computed(() => form.visibility === 'shared' && !copyrightAgreed.value)

/* ---------------- 草稿 ---------------- */
const draftInfo = ref<string | null>(null)

onMounted(async () => {
  ;[categories.value, tags.value] = await Promise.all([getCategories(), getTags()])
  const draft = myLibrary.loadDraft()
  if (!draft) return
  draftInfo.value = draft.savedAt
  try {
    await ElMessageBox.confirm(
      `检测到 ${draft.savedAt} 保存的未完成草稿「${draft.title || '无标题'}」，是否恢复继续填写？`,
      '恢复草稿',
      { confirmButtonText: '恢复草稿', cancelButtonText: '放弃草稿', type: 'info' },
    )
    form.title = draft.title ?? ''
    form.authors = draft.authors ?? ''
    form.abstract = draft.abstract ?? ''
    form.doi = draft.doi ?? ''
    form.journal = draft.journal ?? ''
    form.year = draft.year ?? null
    form.keywords = draft.keywords ?? ''
    form.link = draft.link ?? ''
    form.categoryId = draft.categoryId ?? null
    form.tagIds = draft.tagIds ?? []
    form.visibility = draft.visibility ?? 'private'
    ElMessage.success('草稿已恢复')
  } catch {
    myLibrary.clearDraft()
  }
})

function buildDraftPayload() {
  return {
    title: form.title,
    authors: form.authors,
    abstract: form.abstract,
    doi: form.doi,
    journal: form.journal,
    year: form.year ?? undefined,
    keywords: form.keywords,
    link: form.link,
    categoryId: form.categoryId ?? undefined,
    tagIds: form.tagIds,
    visibility: form.visibility,
  }
}

function handleSaveDraft() {
  myLibrary.saveDraft(buildDraftPayload())
  draftInfo.value = new Date().toLocaleString('zh-CN')
  ElMessage.success('草稿已保存，下次进入页面可恢复')
}

/* ---------------- 提交前查重 ---------------- */
const dupDialogVisible = ref(false)
const dupResult = ref<myLibrary.DuplicateCheckResult | null>(null)
const submitting = ref(false)

function goPaper(id: number) {
  dupDialogVisible.value = false
  router.push(`/user/papers/${id}`)
}

async function favoriteDup(paper: Paper) {
  await userData.toggleFavorite(paper.id, true)
  ElMessage.success('已收藏该论文')
}

/* ---------------- 提交 ---------------- */
async function doSubmit() {
  submitting.value = true
  try {
    const paper = await myLibrary.submitPaper(file.value!.raw, {
      title: form.title,
      authors: form.authors.split(/[,，、\s]+/).filter(Boolean),
      abstract: form.abstract,
      doi: form.doi,
      journal: form.journal,
      year: form.year!,
      keywords: form.keywords.split(/[,，、\s]+/).filter(Boolean),
      categoryId: form.categoryId!,
      tagIds: form.tagIds,
      visibility: form.visibility,
      link: form.link,
      copyrightAgreed: copyrightAgreed.value,
    })
    myLibrary.clearDraft()
    if (form.visibility === 'private') {
      ElMessage.success(`《${paper.title}》已上传至私有论文库`)
      router.push('/user/library/mine?tab=private')
    } else {
      ElMessage.success(`《${paper.title}》已提交，等待管理员审核`)
      router.push('/user/library/mine?tab=shared')
    }
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '上传失败，请重试')
  } finally {
    submitting.value = false
  }
}

async function handleSubmit() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  if (!file.value) {
    ElMessage.warning('请先上传 PDF 文件')
    return
  }
  if (uploading.value) {
    ElMessage.warning('文件正在上传，请稍候')
    return
  }
  if (copyrightRule.value) {
    ElMessage.warning('共享资源需先确认拥有上传与授权权利')
    return
  }

  /* 共享提交前触发提交前查重（规划书 7.5） */
  if (form.visibility === 'shared') {
    const result = await myLibrary.checkDuplicate({
      doi: form.doi,
      title: form.title,
      fileSha256: file.value.sha256,
    })
    if (result.level === 'doi' || result.level === 'file') {
      dupResult.value = result
      dupDialogVisible.value = true
      return
    }
    if (result.level === 'title') {
      dupResult.value = result
      dupDialogVisible.value = true
      return
    }
  }
  doSubmit()
}

/* 疑似重复（仅标题相同）：人工判断后可继续提交 */
async function forceSubmit() {
  dupDialogVisible.value = false
  await doSubmit()
}

function handleCancel() {
  router.push('/user/library/mine')
}
</script>

<template>
  <div class="ph-page">
    <div class="ph-page-header">
      <div>
        <h2 class="ph-page-title">上传论文</h2>
        <p class="ph-page-desc">上传 PDF 文件并完善元数据；选择共享前请确认拥有相应授权</p>
      </div>
      <el-tag v-if="draftInfo" type="info" effect="plain">草稿保存于 {{ draftInfo }}</el-tag>
    </div>

    <div class="upload-layout">
      <div class="upload-main">
        <!-- ① 文件上传区 -->
        <section class="upload-section">
          <div class="section-head">
            <span class="section-index">1</span>
            <div>
              <div class="section-title">论文文件</div>
              <div class="section-desc">仅支持 PDF 格式，单文件不超过 100 MB</div>
            </div>
          </div>

          <el-upload
            v-if="!file"
            ref="uploadRef"
            drag
            accept=".pdf"
            :auto-upload="false"
            :limit="1"
            :show-file-list="false"
            :on-change="handleFileChange"
          >
            <div class="drop-zone">
              <el-icon :size="42" color="#c7d2e8"><UploadFilled /></el-icon>
              <div class="drop-title">拖拽 PDF 文件到此处，或点击选择文件</div>
              <div class="drop-sub">选择文件后，点击提交上传至服务器</div>
            </div>
          </el-upload>

          <div v-else class="file-card">
            <div class="file-icon">PDF</div>
            <div class="file-info">
              <div class="file-name ph-ellipsis">{{ file.name }}</div>
              <div class="file-meta">{{ file.sizeMB }} MB · 页数以服务器解析为准</div>
            </div>
            <div class="file-progress">
              <template v-if="uploading">
                <el-progress :percentage="Math.floor(uploadProgress)" :stroke-width="8" />
                <div class="progress-text">正在校验文件…</div>
              </template>
              <template v-else>
                <span class="ph-status ph-status--success">文件已就绪</span>
              </template>
            </div>
            <el-button text :icon="'Delete'" class="danger-btn" @click="removeFile">移除</el-button>
          </div>
        </section>

        <!-- ② 论文信息区 -->
        <section class="upload-section">
          <div class="section-head">
            <span class="section-index">2</span>
            <div>
              <div class="section-title">论文信息</div>
              <div class="section-desc">带 <em>*</em> 为必填项，用于共享库检索与展示</div>
            </div>
          </div>

          <el-form
            ref="formRef"
            :model="form"
            :rules="rules"
            label-width="92px"
            label-position="left"
            class="info-form"
          >
            <el-form-item label="论文标题" prop="title">
              <el-input
                v-model="form.title"
                placeholder="请输入论文标题"
                maxlength="120"
                show-word-limit
              />
            </el-form-item>
            <el-form-item label="作者" prop="authors">
              <el-input v-model="form.authors" placeholder="多个作者用逗号分隔，如：张三，李四" />
            </el-form-item>
            <el-row :gutter="16">
              <el-col :span="12">
                <el-form-item label="发表年份" prop="year">
                  <el-select v-model="form.year" placeholder="选择年份" style="width: 100%">
                    <el-option v-for="y in yearOptions" :key="y" :value="y" :label="`${y} 年`" />
                  </el-select>
                </el-form-item>
              </el-col>
              <el-col :span="12">
                <el-form-item label="期刊 / 会议">
                  <el-input v-model="form.journal" placeholder="可留空" />
                </el-form-item>
              </el-col>
            </el-row>
            <el-form-item label="DOI">
              <el-input
                v-model="form.doi"
                placeholder="如 10.1109/XXX.2026.00001，共享时将用于查重"
              />
            </el-form-item>
            <el-form-item label="摘要" prop="abstract">
              <el-input
                v-model="form.abstract"
                type="textarea"
                :rows="4"
                placeholder="请输入论文摘要"
                maxlength="600"
                show-word-limit
              />
            </el-form-item>
            <el-form-item label="关键词">
              <el-input v-model="form.keywords" placeholder="多个关键词用逗号分隔" />
            </el-form-item>
            <el-form-item label="原文链接">
              <el-input v-model="form.link" placeholder="出版商页面或预印本链接，可留空" />
            </el-form-item>
          </el-form>
        </section>

        <!-- ③ 分类和标签区 -->
        <section class="upload-section">
          <div class="section-head">
            <span class="section-index">3</span>
            <div>
              <div class="section-title">分类与标签</div>
              <div class="section-desc">选择合适的分类和标签，便于后续检索与统计</div>
            </div>
          </div>

          <el-form label-width="92px" label-position="left" class="info-form" inline-message>
            <el-form-item label="论文分类">
              <el-tree-select
                v-model="form.categoryId"
                :data="categoryTree"
                placeholder="选择分类（支持二级分类）"
                check-strictly
                style="width: 100%"
              />
            </el-form-item>
            <el-form-item label="标签">
              <el-select
                v-model="form.tagIds"
                multiple
                placeholder="选择标签（可多选）"
                style="width: 100%"
              >
                <el-option
                  v-for="t in tags.filter((tag) => form.visibility === 'private' || !tag.custom)"
                  :key="t.id"
                  :value="t.id"
                  :label="t.name"
                />
              </el-select>
            </el-form-item>
          </el-form>
        </section>

        <!-- ④ 资源权限区 -->
        <section class="upload-section">
          <div class="section-head">
            <span class="section-index">4</span>
            <div>
              <div class="section-title">资源权限</div>
              <div class="section-desc">
                私有资源不进入共享库，管理员访问留痕；共享资源需经审核通过后全员可见
              </div>
            </div>
          </div>

          <div class="visibility-cards">
            <div
              class="vis-card"
              :class="{ active: form.visibility === 'private' }"
              @click="form.visibility = 'private'"
            >
              <el-icon :size="22"><Lock /></el-icon>
              <div class="vis-name">私有论文</div>
              <div class="vis-desc">不进入共享库，可编辑和下载；管理员访问留痕</div>
            </div>
            <div
              class="vis-card"
              :class="{ active: form.visibility === 'shared' }"
              @click="form.visibility = 'shared'"
            >
              <el-icon :size="22"><Share /></el-icon>
              <div class="vis-name">共享论文</div>
              <div class="vis-desc">提交后进入审核队列，审核通过后全员可见</div>
            </div>
          </div>

          <el-alert
            v-if="form.visibility === 'shared'"
            class="copyright-alert"
            :closable="false"
            show-icon
          >
            <template #title>
              共享前请确认：你拥有该论文的上传权利，且不侵犯任何第三方版权。
            </template>
            <el-checkbox v-model="copyrightAgreed" class="copyright-check">
              我确认拥有该论文的传播与授权权利，并承诺由此产生的版权问题由本人负责
            </el-checkbox>
          </el-alert>
        </section>
      </div>

      <!-- ⑤ 提交区（侧栏） -->
      <aside class="submit-aside">
        <div class="ph-card aside-card">
          <div class="aside-title">提交上传</div>
          <ul class="check-list">
            <li :class="{ ok: !!file && !uploading }">
              <el-icon
                ><CircleCheckFilled v-if="file && !uploading" /><CircleClose v-else
              /></el-icon>
              PDF 文件{{ uploading ? '上传中' : file ? '已就绪' : '未上传' }}
            </li>
            <li :class="{ ok: !!form.title && !!form.authors && !!form.abstract }">
              <el-icon
                ><CircleCheckFilled
                  v-if="form.title && form.authors && form.abstract" /><CircleClose v-else
              /></el-icon>
              论文基础信息
            </li>
            <li :class="{ ok: !!form.categoryId }">
              <el-icon><CircleCheckFilled v-if="form.categoryId" /><CircleClose v-else /></el-icon>
              论文分类
            </li>
            <li :class="{ ok: form.visibility === 'private' || copyrightAgreed }">
              <el-icon
                ><CircleCheckFilled
                  v-if="form.visibility === 'private' || copyrightAgreed" /><CircleClose v-else
              /></el-icon>
              {{ form.visibility === 'shared' ? '版权授权确认' : '私有资源（无需授权确认）' }}
            </li>
          </ul>

          <el-button
            type="primary"
            size="large"
            class="submit-btn"
            :loading="submitting"
            :disabled="copyrightRule"
            @click="handleSubmit"
          >
            {{ form.visibility === 'shared' ? '提交审核' : '上传到私有库' }}
          </el-button>
          <el-button size="large" class="submit-btn" @click="handleSaveDraft"> 保存草稿 </el-button>
          <el-button size="large" class="submit-btn" text @click="handleCancel">取消</el-button>

          <div class="aside-tip">
            共享提交时系统将自动进行 DOI / 文件 / 标题三级查重， DOI
            与文件重复将被阻止，标题相同可人工判断后继续。
          </div>
        </div>
      </aside>
    </div>

    <!-- 查重结果弹窗 -->
    <el-dialog
      v-model="dupDialogVisible"
      title="查重结果"
      width="560px"
      :close-on-click-modal="dupResult?.level === 'title'"
    >
      <template v-if="dupResult">
        <el-alert
          :type="dupResult.level === 'title' ? 'warning' : 'error'"
          :closable="false"
          show-icon
          class="dup-alert"
        >
          <template #title>{{ dupResult.message }}</template>
        </el-alert>

        <div v-if="dupResult.paper" class="dup-paper">
          <div class="dup-label">已存在的论文</div>
          <div class="dup-title">{{ dupResult.paper.title }}</div>
          <div class="dup-meta">
            {{ dupResult.paper.authors.join('，') }} · {{ dupResult.paper.journal || '未投稿' }} ·
            {{ dupResult.paper.year }} 年
          </div>
          <div v-if="dupResult.paper.doi" class="dup-meta">DOI：{{ dupResult.paper.doi }}</div>
          <div class="dup-actions">
            <el-button type="primary" size="small" @click="goPaper(dupResult.paper!.id)">
              查看论文
            </el-button>
            <el-button size="small" @click="favoriteDup(dupResult.paper!)">收藏</el-button>
          </div>
        </div>
      </template>
      <template #footer>
        <el-button @click="dupDialogVisible = false">返回修改</el-button>
        <el-button v-if="dupResult?.level === 'title'" type="warning" @click="forceSubmit">
          确认不同版本，继续提交
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.upload-layout {
  display: flex;
  gap: 20px;
  align-items: flex-start;
  padding-bottom: 24px;
}
.upload-main {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 16px;
}
.upload-section {
  background: #fff;
  border: 1px solid var(--ph-border);
  border-radius: 14px;
  padding: 20px 24px;
}
.section-head {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 16px;
}
.section-index {
  width: 26px;
  height: 26px;
  border-radius: 8px;
  background: var(--ph-primary-light);
  color: var(--ph-primary);
  font-weight: 700;
  font-size: 13px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}
.section-title {
  font-size: 15px;
  font-weight: 600;
  color: var(--ph-text);
}
.section-desc {
  font-size: 12px;
  color: var(--ph-text-muted);
  margin-top: 2px;
}
.section-desc em {
  color: var(--ph-danger);
  font-style: normal;
}

/* 文件区 */
.drop-zone {
  padding: 34px 20px;
}
.drop-title {
  margin-top: 10px;
  font-size: 14px;
  color: var(--ph-text-secondary);
}
.drop-sub {
  margin-top: 6px;
  font-size: 12px;
  color: var(--ph-text-muted);
}
.upload-section :deep(.el-upload-dragger) {
  border-radius: 12px;
  padding: 0;
}
.file-card {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 14px 16px;
  border: 1px solid var(--ph-border);
  border-radius: 12px;
  background: var(--ph-bg);
}
.file-icon {
  width: 44px;
  height: 44px;
  border-radius: 10px;
  background: #fef2f2;
  color: #dc2626;
  font-size: 12px;
  font-weight: 700;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}
.file-info {
  min-width: 140px;
  flex: 0 1 auto;
}
.file-name {
  font-size: 14px;
  font-weight: 600;
  color: var(--ph-text);
}
.file-meta {
  font-size: 12px;
  color: var(--ph-text-muted);
  margin-top: 3px;
}
.file-progress {
  flex: 1;
  min-width: 160px;
}
.progress-text {
  font-size: 12px;
  color: var(--ph-text-secondary);
  margin-top: 4px;
}
.danger-btn {
  color: var(--ph-danger);
}

/* 表单 */
.info-form {
  padding: 4px 0 0;
}

/* 权限卡片 */
.visibility-cards {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 14px;
}
.vis-card {
  border: 1.5px solid var(--ph-border);
  border-radius: 12px;
  padding: 16px 18px;
  cursor: pointer;
  transition: all 0.18s;
  color: var(--ph-text-secondary);
}
.vis-card:hover {
  border-color: var(--ph-primary);
  color: var(--ph-primary);
}
.vis-card.active {
  border-color: var(--ph-primary);
  background: var(--ph-primary-light);
  color: var(--ph-primary);
}
.vis-name {
  font-size: 14px;
  font-weight: 700;
  margin-top: 8px;
  color: var(--ph-text);
}
.vis-card.active .vis-name {
  color: var(--ph-primary);
}
.vis-desc {
  font-size: 12px;
  color: var(--ph-text-muted);
  margin-top: 4px;
  line-height: 1.6;
}
.copyright-alert {
  margin-top: 14px;
}
.copyright-alert.copyright-alert :deep(.el-alert__content) {
  max-width: none;
}
.copyright-check {
  margin-top: 4px;
  white-space: normal;
  line-height: 1.6;
}

/* 提交侧栏 */
.submit-aside {
  width: 300px;
  flex-shrink: 0;
  position: sticky;
  top: 20px;
}
.aside-card {
  padding: 18px;
}
.aside-title {
  font-size: 15px;
  font-weight: 700;
  color: var(--ph-text);
  margin-bottom: 14px;
}
.check-list {
  list-style: none;
  padding: 0;
  margin: 0 0 16px;
  display: flex;
  flex-direction: column;
  gap: 9px;
}
.check-list li {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  color: var(--ph-text-muted);
}
.check-list li.ok {
  color: var(--ph-text-secondary);
}
.check-list li .el-icon {
  color: #d1d5db;
}
.check-list li.ok .el-icon {
  color: var(--ph-success);
}
.submit-btn {
  width: 100%;
  margin-left: 0 !important;
  margin-bottom: 10px;
}
.aside-tip {
  margin-top: 6px;
  font-size: 12px;
  color: var(--ph-text-muted);
  line-height: 1.7;
  background: var(--ph-bg);
  border-radius: 8px;
  padding: 10px 12px;
}

/* 查重弹窗 */
.dup-alert {
  margin-bottom: 14px;
}
.dup-paper {
  border: 1px solid var(--ph-border);
  border-radius: 12px;
  padding: 14px 16px;
  background: var(--ph-bg);
}
.dup-label {
  font-size: 12px;
  color: var(--ph-text-muted);
  margin-bottom: 6px;
}
.dup-title {
  font-size: 14px;
  font-weight: 600;
  color: var(--ph-text);
}
.dup-meta {
  font-size: 12.5px;
  color: var(--ph-text-secondary);
  margin-top: 5px;
}
.dup-actions {
  margin-top: 12px;
  display: flex;
  gap: 8px;
}

@media (max-width: 1080px) {
  .upload-layout {
    flex-direction: column;
  }
  .submit-aside {
    width: 100%;
    position: static;
  }
}
</style>
