<script setup lang="ts">
import { reportActionError } from '@/utils/errors'
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { TreeInstance } from 'element-plus'
import type { Category, Paper } from '@/types/paper'
import * as knowledge from '@/services/knowledgeService'
import { getTag } from '@/services/knowledgeService'
import TagChip from '@/components/common/TagChip.vue'

const router = useRouter()

/* ---------------- 分类树 ---------------- */
const loading = ref(true)
const categories = ref<Category[]>([])
const counts = ref<Record<number, number>>({})
const keyword = ref('')
const treeRef = ref<TreeInstance>()
const selectedId = ref<number | null>(null)
const categoryPapers = ref<Paper[]>([])
const papersLoading = ref(false)

async function fetchCategories(keepSelection = true) {
  loading.value = true
  try {
    categories.value = await knowledge.getCategories()
    counts.value = Object.fromEntries(
      categories.value.map((category) => [category.id, category.paperCount ?? 0]),
    )

    if (keepSelection && selectedId.value) {
      const still = categories.value.some((c) => c.id === selectedId.value)
      if (still) await loadPapers(selectedId.value)
      else selectedId.value = null
    }
  } finally {
    loading.value = false
  }
}

async function loadPapers(id: number) {
  papersLoading.value = true
  categoryPapers.value = await knowledge.getPapersOfCategory(id)
  papersLoading.value = false
}

function handleNodeClick(data: Category) {
  selectedId.value = data.id
  loadPapers(data.id)
}

onMounted(fetchCategories)

/* 树数据：根 → 子 */
interface TreeItem {
  id: number
  label: string
  parentId: number | null
  children: TreeItem[]
}
const treeData = computed<TreeItem[]>(() => {
  const build = (parentId: number | null): TreeItem[] =>
    categories.value
      .filter((c) => c.parentId === parentId)
      .map((c) => ({
        id: c.id,
        label: c.name,
        parentId: c.parentId,
        children: build(c.id),
      }))
  return build(null)
})

/* 名称模糊搜索（规划书 5.8） */
watch(keyword, (val) => treeRef.value?.filter(val))
function filterNode(value: string, data: TreeItem): boolean {
  if (!value) return true
  return data.label.toLowerCase().includes(value.toLowerCase())
}

const selectedCategory = computed(() => categories.value.find((c) => c.id === selectedId.value))

function goDetail(id: number) {
  router.push(`/user/papers/${id}`)
}

/* ---------------- 新建 / 重命名 / 删除 ---------------- */
const dialogVisible = ref(false)
const dialogMode = ref<'create' | 'createChild' | 'rename'>('create')
const dialogTarget = ref<Category | null>(null)
const form = reactive({
  name: '',
  parentId: null as number | null,
})

const dialogTitle = computed(
  () =>
    ({
      create: '新建一级分类',
      createChild: `新建「${dialogTarget.value?.name}」的子分类`,
      rename: `重命名「${dialogTarget.value?.name}」`,
    })[dialogMode.value],
)

function openCreate() {
  dialogMode.value = 'create'
  dialogTarget.value = null
  form.name = ''
  form.parentId = null
  dialogVisible.value = true
}

function openCreateChild(cat: Category) {
  dialogMode.value = 'createChild'
  dialogTarget.value = cat
  form.name = ''
  form.parentId = cat.id
  dialogVisible.value = true
}

function openRename(cat: Category) {
  dialogMode.value = 'rename'
  dialogTarget.value = cat
  form.name = cat.name
  form.parentId = cat.parentId
  dialogVisible.value = true
}

async function saveDialog() {
  const name = form.name.trim()
  if (!name) {
    ElMessage.warning('请输入分类名称')
    return
  }
  if (dialogMode.value === 'create' || dialogMode.value === 'createChild') {
    await knowledge.createCategory(name, form.parentId)
    ElMessage.success(name + ' 已创建')
  } else if (dialogTarget.value) {
    await knowledge.renameCategory(dialogTarget.value.id, name)
    ElMessage.success('分类已重命名')
  }
  dialogVisible.value = false
  fetchCategories()
}

async function handleDelete(cat: Category) {
  const childCount = categories.value.filter((c) => c.parentId === cat.id).length
  const paperCount = counts.value[cat.id] ?? 0
  const tips: string[] = []
  if (childCount) tips.push(`其下 ${childCount} 个子分类将一并删除`)
  if (paperCount) tips.push(`${paperCount} 篇关联论文将失去该分类（论文本身不受影响）`)
  try {
    await ElMessageBox.confirm(
      `确定删除分类「${cat.name}」吗？${tips.length ? '：' + tips.join('；') + '。' : ''}`,
      '删除分类',
      { confirmButtonText: '删除', cancelButtonText: '取消', type: 'warning' },
    )
    await knowledge.deleteCategory(cat.id)
    ElMessage.success('分类已删除')
    if (selectedId.value === cat.id) selectedId.value = null
    fetchCategories()
  } catch (error) {
    reportActionError(error)
  }
}
</script>

<template>
  <div class="ph-page">
    <div class="ph-page-header">
      <div>
        <h2 class="ph-page-title">分类管理</h2>
        <p class="ph-page-desc">树形分类体系，支持新建子分类、重命名与删除；点击分类查看关联论文</p>
      </div>
      <div class="header-actions">
        <el-input v-model="keyword" placeholder="模糊搜索分类名称" clearable style="width: 220px">
          <template #prefix
            ><el-icon><Search /></el-icon
          ></template>
        </el-input>
        <el-button type="primary" :icon="'Plus'" @click="openCreate">新建分类</el-button>
      </div>
    </div>

    <div class="categories-layout">
      <!-- 左侧分类树 -->
      <div class="ph-card tree-card">
        <div v-if="loading" v-loading="true" class="tree-loading" />
        <el-tree
          v-else
          ref="treeRef"
          :data="treeData"
          node-key="id"
          :props="{ children: 'children', label: 'label' }"
          :filter-node-method="filterNode"
          default-expand-all
          highlight-current
          :expand-on-click-node="false"
          @node-click="handleNodeClick"
        >
          <template #default="{ data }">
            <div class="tree-node">
              <span class="tree-node-label">{{ data.label }}</span>
              <span class="tree-node-count">{{ counts[data.id] ?? 0 }}</span>
              <span class="tree-node-actions" @click.stop>
                <el-tooltip content="新建子分类" placement="top">
                  <el-icon
                    class="node-action"
                    @click="openCreateChild(categories.find((c) => c.id === data.id)!)"
                  >
                    <CirclePlusFilled />
                  </el-icon>
                </el-tooltip>
                <el-tooltip
                  v-if="categories.find((category) => category.id === data.id)?.custom"
                  content="重命名"
                  placement="top"
                >
                  <el-icon
                    class="node-action"
                    @click="openRename(categories.find((c) => c.id === data.id)!)"
                  >
                    <EditPen />
                  </el-icon>
                </el-tooltip>
                <el-tooltip
                  v-if="categories.find((category) => category.id === data.id)?.custom"
                  content="删除"
                  placement="top"
                >
                  <el-icon
                    class="node-action danger"
                    @click="handleDelete(categories.find((c) => c.id === data.id)!)"
                  >
                    <Delete />
                  </el-icon>
                </el-tooltip>
              </span>
            </div>
          </template>
        </el-tree>
      </div>

      <!-- 右侧关联论文 -->
      <div class="ph-card papers-card">
        <template v-if="selectedCategory">
          <div class="papers-head">
            <div>
              <div class="papers-title">{{ selectedCategory.name }}</div>
              <div class="papers-sub">共 {{ categoryPapers.length }} 篇关联论文</div>
            </div>
          </div>
          <div v-if="papersLoading" v-loading="true" class="papers-loading" />
          <template v-else-if="categoryPapers.length">
            <div v-for="p in categoryPapers" :key="p.id" class="paper-row" @click="goDetail(p.id)">
              <div class="paper-row-main">
                <div class="paper-title ph-ellipsis">{{ p.title }}</div>
                <div class="paper-meta">
                  <span
                    class="ph-status"
                    :class="p.visibility === 'shared' ? 'ph-status--primary' : 'ph-status--info'"
                  >
                    {{ p.visibility === 'shared' ? '共享' : '私有' }}
                  </span>
                  <span>{{ p.authors.join('，') }}</span>
                  <span class="meta-dot">·</span>
                  <span>{{ p.year }} 年</span>
                </div>
                <div class="paper-chips">
                  <TagChip
                    v-for="tid in p.tagIds.slice(0, 3)"
                    :key="tid"
                    :name="getTag(tid)?.name ?? ''"
                    :color="getTag(tid)?.color"
                  />
                </div>
              </div>
              <el-icon class="row-arrow"><ArrowRight /></el-icon>
            </div>
          </template>
          <el-empty v-else description="该分类下暂无关联论文" />
        </template>
        <el-empty v-else description="在左侧点击分类，查看其关联的论文" />
      </div>
    </div>

    <!-- 新建 / 重命名弹窗 -->
    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="460px" destroy-on-close>
      <el-form ref="formRef" :model="form" label-width="80px" label-position="left">
        <el-form-item label="分类名称" required>
          <el-input v-model="form.name" placeholder="请输入分类名称" maxlength="20" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="saveDialog">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.header-actions {
  display: flex;
  gap: 12px;
}

.categories-layout {
  display: flex;
  gap: 20px;
  align-items: flex-start;
  padding-bottom: 24px;
}
.tree-card {
  width: 360px;
  flex-shrink: 0;
  padding: 14px 10px;
  max-height: calc(100vh - 210px);
  overflow-y: auto;
}
.tree-loading,
.papers-loading {
  height: 240px;
}

.tree-node {
  flex: 1;
  display: flex;
  align-items: center;
  gap: 8px;
  padding-right: 8px;
  min-width: 0;
}
.tree-node-label {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 14px;
}
.tree-node-count {
  font-size: 11.5px;
  color: var(--ph-text-muted);
  background: var(--ph-bg);
  border-radius: 999px;
  padding: 1px 8px;
  flex-shrink: 0;
}
.tree-node-actions {
  display: none;
  align-items: center;
  gap: 8px;
  flex-shrink: 0;
}
.tree-node:hover .tree-node-actions {
  display: inline-flex;
}
.tree-node:hover .tree-node-count {
  display: none;
}
.node-action {
  font-size: 15px;
  color: var(--ph-text-muted);
  cursor: pointer;
  transition: color 0.15s;
}
.node-action:hover {
  color: var(--ph-primary);
}
.node-action.danger:hover {
  color: var(--ph-danger);
}

.papers-card {
  flex: 1;
  min-width: 0;
  padding: 20px 24px;
}
.papers-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding-bottom: 14px;
  border-bottom: 1px solid var(--ph-border);
}
.papers-title {
  font-size: 16px;
  font-weight: 700;
  color: var(--ph-text);
}
.papers-sub {
  font-size: 12.5px;
  color: var(--ph-text-muted);
  margin-top: 3px;
}
.paper-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 14px 4px;
  border-bottom: 1px solid #f3f4f6;
  cursor: pointer;
}
.paper-row:last-child {
  border-bottom: none;
}
.paper-row-main {
  flex: 1;
  min-width: 0;
}
.paper-title {
  font-size: 14.5px;
  font-weight: 600;
  color: var(--ph-text);
}
.paper-row:hover .paper-title {
  color: var(--ph-primary);
}
.paper-meta {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12.5px;
  color: var(--ph-text-secondary);
  margin-top: 5px;
  flex-wrap: wrap;
}
.meta-dot {
  color: #d4d7dc;
}
.paper-chips {
  display: flex;
  gap: 6px;
  margin-top: 7px;
}
.row-arrow {
  color: #c7cdd8;
  flex-shrink: 0;
}

@media (max-width: 1080px) {
  .categories-layout {
    flex-direction: column;
  }
  .tree-card {
    width: 100%;
  }
}
</style>
