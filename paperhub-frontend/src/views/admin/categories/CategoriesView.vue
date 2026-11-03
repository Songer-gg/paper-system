<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import * as admin from '@/services/adminService'
import * as knowledge from '@/services/knowledgeService'
import type { Category, Paper } from '@/types/paper'

/* ---------------- 分类数据 ---------------- */
const loading = ref(true)
const categories = ref<Category[]>([])
const papers = ref<Paper[]>([])
const keyword = ref('')
const selectedId = ref<number | null>(null)

interface TreeNode {
  id: number
  name: string
  count: number
  children: TreeNode[]
}

/** 全平台共享池的论文计数（自身分类 + 全部祖先） */
const counts = computed<Record<number, number>>(() => {
  const map = new Map(categories.value.map((c) => [c.id, c]))
  const count: Record<number, number> = {}
  for (const p of papers.value) {
    let cur = map.get(p.categoryId)
    while (cur) {
      count[cur.id] = (count[cur.id] ?? 0) + 1
      cur = cur.parentId !== null ? map.get(cur.parentId) : undefined
    }
  }
  return count
})

const filteredTree = computed<TreeNode[]>(() => {
  const build = (parentId: number | null): TreeNode[] =>
    categories.value
      .filter((c) => c.parentId === parentId)
      .map((c) => ({
        id: c.id,
        name: c.name,
        count: counts.value[c.id] ?? 0,
        children: build(c.id),
      }))
  const kw = keyword.value.trim().toLowerCase()
  if (!kw) return build(null)
  /* 搜索时保留命中节点及其祖先链 */
  const matchChain = (parentId: number | null): TreeNode[] => {
    const result: TreeNode[] = []
    for (const c of categories.value.filter((x) => x.parentId === parentId)) {
      const children = matchChain(c.id)
      if (c.name.toLowerCase().includes(kw) || children.length) {
        result.push({
          id: c.id,
          name: c.name,
          count: counts.value[c.id] ?? 0,
          children,
        })
      }
    }
    return result
  }
  return matchChain(null)
})

async function fetchAll() {
  loading.value = true
  const [cats, ps] = await Promise.all([
    knowledge.getCategories(),
    admin.getSharedPapers({ title: '', doi: '', uploader: '', categoryId: null, status: 'all' }),
  ])
  categories.value = cats
  papers.value = ps
  loading.value = false
}
onMounted(fetchAll)

const selectedCategory = computed(() =>
  selectedId.value !== null
    ? categories.value.find((c) => c.id === selectedId.value) ?? null
    : null,
)

const selectedPapers = computed(() => {
  if (selectedId.value === null) return []
  /* 选中分类自身及其全部后代 */
  const ids = [selectedId.value]
  let frontier = [selectedId.value]
  while (frontier.length) {
    const next = categories.value
      .filter((c) => c.parentId !== null && frontier.includes(c.parentId))
      .map((c) => c.id)
    ids.push(...next)
    frontier = next
  }
  return papers.value.filter((p) => ids.includes(p.categoryId))
})

function selectNode(node: TreeNode) {
  selectedId.value = node.id
}

/* ---------------- 新建 / 重命名 / 删除 ---------------- */
const dialog = ref(false)
const dialogMode = ref<'create' | 'rename'>('create')
const dialogName = ref('')
const targetId = ref<number | null>(null)
const targetParentId = ref<number | null>(null)

function openCreate(parentId: number | null) {
  dialogMode.value = 'create'
  dialogName.value = ''
  targetParentId.value = parentId
  dialog.value = true
}

function openRename(cat: TreeNode) {
  dialogMode.value = 'rename'
  dialogName.value = cat.name
  targetId.value = cat.id
  dialog.value = true
}

async function submitDialog() {
  const name = dialogName.value.trim()
  if (!name) {
    ElMessage.warning('请输入分类名称')
    return
  }
  if (dialogMode.value === 'create') {
    await knowledge.createCategory(name, targetParentId.value)
    ElMessage.success('分类已创建')
  } else if (targetId.value !== null) {
    await knowledge.renameCategory(targetId.value, name)
    ElMessage.success('分类已重命名')
  }
  dialog.value = false
  fetchAll()
}

async function removeCategory(cat: TreeNode) {
  try {
    /* 先收集子树名称用于确认提示（自身 + 全部后代） */
    const collect = (parentId: number): Category[] =>
      categories.value
        .filter((c) => c.parentId === parentId)
        .flatMap((c) => [c, ...collect(c.id)])
    const subtree = [
      categories.value.find((c) => c.id === cat.id) ?? { id: cat.id, name: cat.name, parentId: null },
      ...collect(cat.id),
    ]
    await ElMessageBox.confirm(
      `将同时删除子分类：${subtree.map((c) => c.name).join('、')}，且不影响论文本体，确定删除吗？`,
      '删除分类',
      { confirmButtonText: '删除', cancelButtonText: '取消', type: 'warning' },
    )
    await knowledge.deleteCategory(cat.id)
    ElMessage.success('分类已删除')
    /* 若选中分类在删除范围内则清空右侧面板 */
    const all = await knowledge.getCategories()
    if (selectedId.value !== null && !all.some((c) => c.id === selectedId.value)) {
      selectedId.value = null
    }
    fetchAll()
  } catch {
    /* 用户取消 */
  }
}

/* ---------------- 调整层级 ---------------- */
const moveDialog = ref(false)
const moveTargetId = ref<number | null>(null)
const moveParentId = ref<number | null>(null)

/** 当前移动操作的目标分类名（显示在弹窗标题，避免误操作歧义） */
const moveTargetName = computed(() =>
  moveTargetId.value !== null
    ? categories.value.find((c) => c.id === moveTargetId.value)?.name ?? ''
    : '',
)

/** 可选目标：排除自身及其后代 */
const moveOptions = computed<Category[]>(() => {
  if (moveTargetId.value === null) return []
  const forbidden = new Set<number>([moveTargetId.value])
  let frontier = [moveTargetId.value]
  while (frontier.length) {
    const next = categories.value
      .filter((c) => c.parentId !== null && frontier.includes(c.parentId))
      .map((c) => c.id)
    next.forEach((id) => forbidden.add(id))
    frontier = next
  }
  return categories.value.filter((c) => !forbidden.has(c.id))
})

const moveTreeData = computed(() => {
  const build = (parentId: number | null): { value: number; label: string; children?: unknown[] }[] =>
    moveOptions.value
      .filter((c) => c.parentId === parentId)
      .map((c) => {
        const children = build(c.id)
        return { value: c.id, label: c.name, ...(children.length ? { children } : {}) }
      })
  return build(null)
})

function openMove(cat: TreeNode) {
  moveTargetId.value = cat.id
  const cur = categories.value.find((c) => c.id === cat.id)
  moveParentId.value = cur?.parentId ?? null
  moveDialog.value = true
}

async function submitMove() {
  if (moveTargetId.value === null) return
  const ok = await knowledge.moveCategory(moveTargetId.value, moveParentId.value)
  if (!ok) {
    ElMessage.error('不能移动到自身或其子分类下')
    return
  }
  ElMessage.success('分类层级已调整')
  moveDialog.value = false
  fetchAll()
}
</script>

<template>
  <div class="ph-page">
    <div class="ph-page-header">
      <div>
        <h2 class="ph-page-title">平台分类管理</h2>
        <p class="ph-page-desc">维护平台统一分类体系（树形结构，统计范围为全平台共享论文）</p>
      </div>
      <el-button type="primary" @click="openCreate(null)">新建根分类</el-button>
    </div>

    <div v-if="loading" v-loading="true" class="page-loading" />

    <div v-else class="two-col">
      <!-- 左：分类树 -->
      <div class="ph-card tree-card">
        <el-input
          v-model="keyword"
          placeholder="搜索分类名称（模糊匹配）"
          clearable
          class="tree-search"
        >
          <template #prefix><el-icon><Search /></el-icon></template>
        </el-input>
        <el-tree
          :data="filteredTree"
          node-key="id"
          default-expand-all
          :expand-on-click-node="false"
          highlight-current
          @node-click="(data) => selectNode(data as TreeNode)"
        >
          <template #default="{ data }">
            <div class="tree-node">
              <span class="tree-name">{{ data.name }}</span>
              <span class="tree-count">{{ data.count }} 篇</span>
              <span class="tree-actions" @click.stop>
                <el-button text type="primary" size="small" @click="openCreate(data.id)">子分类</el-button>
                <el-button text size="small" @click="openRename(data)">重命名</el-button>
                <el-button text size="small" @click="openMove(data)">移动</el-button>
                <el-button text type="danger" size="small" @click="removeCategory(data)">删除</el-button>
              </span>
            </div>
          </template>
        </el-tree>
      </div>

      <!-- 右：分类论文面板 -->
      <div class="ph-card panel-card">
        <template v-if="selectedCategory">
          <h3 class="ph-section-title">
            {{ selectedCategory.name }}（{{ selectedPapers.length }} 篇）
          </h3>
          <el-table v-if="selectedPapers.length" :data="selectedPapers.slice(0, 30)" style="width: 100%">
            <el-table-column label="论文标题" min-width="240" show-overflow-tooltip>
              <template #default="{ row }">
                <span class="paper-title">{{ row.title }}</span>
              </template>
            </el-table-column>
            <el-table-column prop="journal" label="期刊" min-width="130" show-overflow-tooltip />
            <el-table-column prop="year" label="年份" width="70" align="center" />
          </el-table>
          <div v-if="selectedPapers.length > 30" class="more-hint">
            仅展示前 30 篇，共 {{ selectedPapers.length }} 篇
          </div>
        </template>
        <el-empty
          v-else
          description="点击左侧分类查看关联论文"
          :image-size="100"
        />
      </div>
    </div>

    <!-- 新建/重命名弹窗 -->
    <el-dialog
      v-model="dialog"
      :title="dialogMode === 'create' ? (targetParentId !== null ? '新建子分类' : '新建根分类') : '重命名分类'"
      width="420px"
    >
      <el-input v-model="dialogName" placeholder="分类名称" maxlength="20" show-word-limit />
      <template #footer>
        <el-button @click="dialog = false">取消</el-button>
        <el-button type="primary" @click="submitDialog">保存</el-button>
      </template>
    </el-dialog>

    <!-- 调整层级弹窗 -->
    <el-dialog v-model="moveDialog" :title="`调整分类层级 — ${moveTargetName}`" width="440px">
      <div class="move-body">
        <el-tree-select
          v-model="moveParentId"
          :data="moveTreeData"
          placeholder="选择新的父分类"
          check-strictly
          clearable
          style="width: 100%"
        />
        <div class="move-hint">不选择父分类则移动为根分类；不能移动到自身或其子分类下。</div>
      </div>
      <template #footer>
        <el-button @click="moveDialog = false">取消</el-button>
        <el-button type="primary" @click="submitMove">确认移动</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.page-loading {
  height: 360px;
}
.two-col {
  display: grid;
  grid-template-columns: 1.15fr 1fr;
  gap: 20px;
  align-items: start;
}
.tree-card {
  padding: 18px 20px;
}
.tree-search {
  margin-bottom: 14px;
}
.tree-node {
  flex: 1;
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
  padding-right: 4px;
}
.tree-name {
  font-size: 13.5px;
  color: var(--ph-text);
  font-weight: 500;
}
.tree-count {
  font-size: 12px;
  color: var(--ph-text-muted);
  flex-shrink: 0;
}
.tree-actions {
  margin-left: auto;
  display: none;
  flex-shrink: 0;
  white-space: nowrap;
}
.tree-node:hover .tree-actions {
  display: inline-flex;
}
.tree-actions .el-button + .el-button {
  margin-left: 0;
}

.panel-card {
  padding: 20px 24px;
  min-height: 360px;
}
.paper-title {
  color: var(--ph-text);
  font-weight: 500;
  font-size: 13px;
}
.more-hint {
  margin-top: 10px;
  font-size: 12px;
  color: var(--ph-text-muted);
  text-align: center;
}

.move-body {
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.move-hint {
  font-size: 12px;
  color: var(--ph-text-muted);
}

@media (max-width: 1080px) {
  .two-col {
    grid-template-columns: 1fr;
  }
}
</style>
