<template>
  <PageContainer title="工艺路线" description="配置产品装配的工序顺序与版本。">
    <!-- 搜索栏（在当前已加载数据中筛选） -->
    <div class="search-bar">
      <el-input v-model="searchForm.routeCode" placeholder="路线编码（本地筛选）" clearable />
      <el-input v-model="searchForm.version" placeholder="版本（本地筛选）" clearable />
      <div class="search-bar-actions">
        <el-button type="primary" @click="handleSearch">查询</el-button>
        <el-button @click="resetSearch">重置</el-button>
      </div>
    </div>

    <!-- 表格工具条 -->
    <div class="table-toolbar">
      <div class="table-toolbar-actions">
        <el-button type="primary" @click="handleAdd">
          <el-icon><Plus /></el-icon> 新增工艺路线
        </el-button>
      </div>
    </div>

    <!-- 表格 -->
    <el-table :data="tableData" border stripe v-loading="loading" style="width: 100%">
      <el-table-column prop="routeId" label="ID" width="80" class-name="mono-num" />
      <el-table-column label="物料" min-width="160" show-overflow-tooltip>
        <template #default="{ row }">
          <template v-if="itemMap.has(row.itemId)">
            {{ itemMap.get(row.itemId)?.itemCode }} - {{ itemMap.get(row.itemId)?.itemName }}
          </template>
          <template v-else>ID: {{ row.itemId }}</template>
        </template>
      </el-table-column>
      <el-table-column prop="routeCode" label="路线编码" width="140" show-overflow-tooltip />
      <el-table-column prop="version" label="版本" width="100" />
      <el-table-column prop="stepsJson" label="工序步骤 JSON" min-width="200">
        <template #default="{ row }">
          <el-tooltip :content="row.stepsJson" placement="top" :show-after="500">
            <span class="text-ellipsis">{{ row.stepsJson }}</span>
          </el-tooltip>
        </template>
      </el-table-column>
      <el-table-column prop="status" label="状态" width="100">
        <template #default="{ row }">
          <el-tag :type="row.status === '0' ? 'success' : 'danger'" size="small">
            {{ row.status === '0' ? '启用' : '禁用' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="createTime" label="创建时间" width="180" class-name="mono-num" />
      <el-table-column prop="updateTime" label="更新时间" width="180" class-name="mono-num" />
      <el-table-column label="操作" min-width="150" fixed="right">
        <template #default="{ row }">
          <div class="table-actions">
            <el-button type="primary" link size="small" title="编辑工艺路线" @click="handleEdit(row)">编辑</el-button>
            <el-popconfirm title="确定删除该工艺路线吗？" confirm-button-type="danger" @confirm="handleDelete(row.routeId)">
              <template #reference>
                <el-button type="danger" link size="small" title="删除工艺路线">删除</el-button>
              </template>
            </el-popconfirm>
          </div>
        </template>
      </el-table-column>
      <template #empty>
        <el-empty description="暂无数据" :image-size="72" />
      </template>
    </el-table>

    <!-- V2.3 分页 -->
    <div class="pagination-bar">
      <el-pagination
        v-model:current-page="query.page"
        v-model:page-size="query.size"
        :total="total"
        :page-sizes="[15, 30, 50, 100]"
        layout="total, sizes, prev, pager, next, jumper"
        @current-change="fetchList"
        @size-change="fetchList"
      />
    </div>

    <!-- 新增/编辑对话框 -->
    <el-dialog
      v-model="dialogVisible"
      :title="isEdit ? '编辑工艺路线' : '新增工艺路线'"
      width="560px"
      :close-on-click-modal="false"
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px" class="route-form">
        <el-form-item label="物料类型">
          <el-select v-model="filterItemType" placeholder="全部类型（可选筛选）" clearable style="width: 100%">
            <el-option label="成品" value="P" />
            <el-option label="半成品" value="S" />
            <el-option label="零部件" value="M" />
            <el-option label="原材料" value="R" />
          </el-select>
        </el-form-item>
        <el-form-item label="物料" prop="itemId">
          <el-select
            v-model="form.itemId"
            placeholder="请选择物料"
            filterable
            clearable
            style="width: 100%"
            @change="onItemChange"
          >
            <el-option
              v-for="item in filteredItems"
              :key="item.itemId"
              :label="`${item.itemCode} - ${item.itemName}`"
              :value="item.itemId"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="路线编码" prop="routeCode">
          <el-input v-model="form.routeCode" placeholder="请输入路线编码" style="width: 100%" />
        </el-form-item>
        <el-form-item label="版本" prop="version">
          <el-input v-model="form.version" placeholder="请输入版本，如 V1.0" style="width: 100%" />
        </el-form-item>
        <el-form-item label="工序配置" prop="stepsJson" class="steps-form-item">
          <el-table :data="routeSteps" border size="small" class="steps-table">
            <el-table-column label="顺序" width="55" align="center">
              <template #default="{ $index }">{{ $index + 1 }}</template>
            </el-table-column>
            <el-table-column label="工序">
              <template #default="{ row, $index }">
                <el-select
                  v-model="row.processId"
                  placeholder="请选择工序"
                  filterable
                  clearable
                  style="width: 100%"
                >
                  <el-option
                    v-for="p in availableProcessOptions($index)"
                    :key="p.processId"
                    :label="p.processName"
                    :value="p.processId"
                  />
                </el-select>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="170" align="center">
              <template #default="{ $index }">
                <el-button link size="small" title="上移工序" :disabled="$index === 0" @click="moveStepUp($index)">上移</el-button>
                <el-button link size="small" title="下移工序" :disabled="$index === routeSteps.length - 1" @click="moveStepDown($index)">下移</el-button>
                <el-button link size="small" type="danger" title="删除工序" @click="removeStep($index)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
          <div class="add-step-row">
            <el-button type="primary" link @click="addStep">
              <el-icon><Plus /></el-icon> 添加工序
            </el-button>
          </div>
        </el-form-item>
        <el-form-item label="状态" prop="status">
          <el-radio-group v-model="form.status">
            <el-radio value="0">启用</el-radio>
            <el-radio value="1">禁用</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSubmit" :loading="submitting">确定</el-button>
      </template>
    </el-dialog>
  </PageContainer>
</template>

<script setup lang="ts">
import { reactive, ref, computed, watch, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import PageContainer from '@/components/PageContainer.vue'
import { getBaseRouteList, createBaseRoute, updateBaseRoute, deleteBaseRoute } from '@/api/baseRoute'
import { getBaseProcessList } from '@/api/baseProcess'
import { getBaseItemList } from '@/api/baseItem'
import type { BaseRoute, BaseProcess, BaseItem } from '@/types/api'

const tableData = ref<BaseRoute[]>([])
const loading = ref(false)
const total = ref(0)
const dialogVisible = ref(false)
const isEdit = ref(false)
const submitting = ref(false)
const formRef = ref<FormInstance>()
const editId = ref<number>()

const query = reactive({
  page: 1,
  size: 15
})

const searchForm = reactive({
  routeCode: '',
  version: ''
})

/** 工序步骤表单行 */
interface RouteStepForm {
  processId?: number
}

const form = reactive<BaseRoute>({
  itemId: undefined,
  routeCode: '',
  version: 'V1.0',
  stepsJson: '',
  status: '0'
})

/** 工序配置表格数据 */
const routeSteps = ref<RouteStepForm[]>([{ processId: undefined }])

/** 可选基础工序列表（仅启用状态） */
const processOptions = ref<BaseProcess[]>([])

/** 所有启用状态的物料列表（用于联动下拉） */
const allItems = ref<BaseItem[]>([])

/** 物料类型筛选（非必选，仅用于前端过滤下拉列表） */
const filterItemType = ref('')

/** 根据物料类型筛选后的物料列表 */
const filteredItems = computed(() => {
  if (!filterItemType.value) return allItems.value
  return allItems.value.filter(item => item.itemType === filterItemType.value)
})

/** 物料 ID → 物料信息映射（表格显示用） */
const itemMap = ref<Map<number, BaseItem>>(new Map())

// 物料类型筛选变化时，若当前选中物料不在新筛选列表中则清空
watch(filterItemType, () => {
  if (form.itemId != null) {
    const stillExists = filteredItems.value.some(item => item.itemId === form.itemId)
    if (!stillExists) form.itemId = undefined
  }
})

/** 新增和编辑校验规则分开定义，支持后续差异化 */
function getFormRules(_isEdit: boolean): FormRules {
  return {
    itemId: [{ required: true, message: '请选择物料', trigger: 'change' }],
    routeCode: [{ required: true, message: '请输入路线编码', trigger: 'blur' }],
    version: [{ required: true, message: '请输入版本', trigger: 'blur' }],
    status: [{ required: true, message: '请选择状态', trigger: 'change' }]
  }
}

const rules = ref<FormRules>(getFormRules(false))

onMounted(() => {
  fetchList()
  fetchAllItems()
})

async function fetchList() {
  loading.value = true
  try {
    const res = await getBaseRouteList({ page: query.page, size: query.size })
    // V2.3: 后端返回 PageResult，request 拦截器已解包 data
    const allData: BaseRoute[] = Array.isArray(res?.records) ? res.records : []
    total.value = res?.total ?? 0
    // 前端本地筛选（仅作用于当前页；服务端筛选后续版本再做）
    const code = searchForm.routeCode.trim().toLowerCase()
    const ver = searchForm.version.trim().toLowerCase()
    tableData.value = allData.filter(item => {
      const matchCode = !code || (item.routeCode || '').toLowerCase().includes(code)
      const matchVer = !ver || (item.version || '').toLowerCase().includes(ver)
      return matchCode && matchVer
    })
  } catch { /* 已在 request 拦截器中处理 */ }
  finally { loading.value = false }
}

function handleSearch() {
  query.page = 1
  fetchList()
}

function resetSearch() {
  searchForm.routeCode = ''
  searchForm.version = ''
  handleSearch()
}

function resetForm() {
  form.itemId = undefined
  form.routeCode = ''
  form.version = 'V1.0'
  form.stepsJson = ''
  form.status = '0'
  routeSteps.value = [{ processId: undefined }]
  filterItemType.value = ''
}

async function fetchProcessOptions() {
  try {
    const res = await getBaseProcessList({ page: 1, size: 999 })
    const all: BaseProcess[] = Array.isArray(res?.records) ? res.records : []
    processOptions.value = all.filter(p => p.status === '0')
  } catch { /* 已在 request 拦截器中处理 */ }
}

async function fetchAllItems() {
  try {
    const res = await getBaseItemList({ page: 1, size: 9999 })
    const all: BaseItem[] = Array.isArray(res?.records) ? res.records : []
    allItems.value = all.filter(item => item.status === '0')
    itemMap.value = new Map(all.map(item => [item.itemId!, item]))
  } catch { /* 已在拦截器处理 */ }
}

/** 选择物料后，从全量映射反查类型并回填类型筛选 */
function onItemChange(itemId: number | undefined) {
  if (itemId != null) {
    const matched = itemMap.value.get(itemId)
    if (matched) filterItemType.value = matched.itemType
  }
}

function handleAdd() {
  isEdit.value = false
  editId.value = undefined
  resetForm()
  rules.value = getFormRules(false)
  dialogVisible.value = true
  fetchProcessOptions()
  fetchAllItems()
}

function handleEdit(row: BaseRoute) {
  isEdit.value = true
  editId.value = row.routeId
  form.itemId = row.itemId
  form.routeCode = row.routeCode
  form.version = row.version
  form.stepsJson = row.stepsJson
  form.status = row.status
  parseStepsJson(row.stepsJson)
  rules.value = getFormRules(true)
  dialogVisible.value = true
  fetchProcessOptions()
  fetchAllItems().then(() => {
    // 从全量映射中查找（含禁用物料），自动回填物料类型筛选
    const matched = itemMap.value.get(row.itemId!)
    if (matched) filterItemType.value = matched.itemType
  })
}

// ==================== 工序步骤解析 ====================

/** 将后端返回的 stepsJson 解析为 routeSteps 表格数据 */
function parseStepsJson(stepsJson?: string): void {
  if (!stepsJson) {
    routeSteps.value = [{ processId: undefined }]
    return
  }

  let parsed: unknown
  try {
    parsed = JSON.parse(stepsJson)
  } catch {
    ElMessage.warning('历史工序步骤 JSON 解析失败，请重新配置工序')
    routeSteps.value = [{ processId: undefined }]
    return
  }

  if (!Array.isArray(parsed) || parsed.length === 0) {
    routeSteps.value = [{ processId: undefined }]
    return
  }

  // 解析每步：兼容 stepSeq / seq，按顺序号排序
  const steps: { processId: number; order: number }[] = []
  for (let i = 0; i < parsed.length; i++) {
    const item = parsed[i]
    if (typeof item !== 'object' || item === null) continue
    const pid = item.processId
    if (pid == null) continue
    const order: number = item.stepSeq ?? item.seq ?? (i + 1)
    steps.push({ processId: Number(pid), order: Number(order) })
  }

  if (steps.length === 0) {
    routeSteps.value = [{ processId: undefined }]
    return
  }

  // 按顺序号升序排列
  steps.sort((a, b) => a.order - b.order)
  routeSteps.value = steps.map(s => ({ processId: s.processId }))
}

// ==================== 工序表格操作 ====================

/** 获取当前行可选的工序列表（排除其他行已选的 processId） */
function availableProcessOptions(currentIndex: number): BaseProcess[] {
  const selectedIds = routeSteps.value
    .filter((_, i) => i !== currentIndex)
    .map(s => s.processId)
    .filter((id): id is number => id != null)
  return processOptions.value.filter(p => !selectedIds.includes(p.processId!))
}

function addStep() {
  routeSteps.value.push({ processId: undefined })
}

function removeStep(index: number) {
  if (routeSteps.value.length <= 1) {
    // 只剩一行时清空选择而不是删除
    routeSteps.value[0].processId = undefined
    return
  }
  routeSteps.value.splice(index, 1)
}

function moveStepUp(index: number) {
  if (index <= 0) return
  const temp = routeSteps.value[index]!
  routeSteps.value[index] = routeSteps.value[index - 1]!
  routeSteps.value[index - 1] = temp
}

function moveStepDown(index: number) {
  if (index >= routeSteps.value.length - 1) return
  const temp = routeSteps.value[index]!
  routeSteps.value[index] = routeSteps.value[index + 1]!
  routeSteps.value[index + 1] = temp
}

// ==================== 提交校验与生成 ====================

/** 校验工序配置并返回标准化 stepsJson；校验失败时返回 null 并弹提示 */
function validateAndBuildStepsJson(): string | null {
  // 过滤掉未选择工序的空行
  const validSteps = routeSteps.value.filter(s => s.processId != null)

  if (validSteps.length === 0) {
    ElMessage.warning('工艺路线至少需要一道工序')
    return null
  }

  // 检查是否存在空白行
  const firstEmptyIndex = routeSteps.value.findIndex(s => s.processId == null)
  if (firstEmptyIndex !== -1) {
    ElMessage.warning(`请选择第${firstEmptyIndex + 1}道工序`)
    return null
  }

  // 检查重复 processId
  const ids = validSteps.map(s => Number(s.processId))
  const idSet = new Set<number>()
  for (const id of ids) {
    if (idSet.has(id)) {
      ElMessage.warning('工艺路线中不能重复选择同一道工序')
      return null
    }
    idSet.add(id)
  }

  // 生成标准化格式：仅保留 processId + stepSeq，按当前表格顺序重新编号
  const normalized = routeSteps.value.map((step, index) => ({
    processId: Number(step.processId),
    stepSeq: index + 1,
  }))

  return JSON.stringify(normalized)
}

async function handleSubmit() {
  // 先校验除 stepsJson 以外的表单字段
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return

  // 校验并生成标准化 stepsJson
  const builtStepsJson = validateAndBuildStepsJson()
  if (builtStepsJson === null) return

  form.stepsJson = builtStepsJson

  submitting.value = true
  try {
    if (isEdit.value && editId.value) {
      await updateBaseRoute(editId.value, { ...form })
      ElMessage.success('编辑成功')
    } else {
      await createBaseRoute({ ...form })
      ElMessage.success('新增成功')
    }
    dialogVisible.value = false
    fetchList()
  } catch { /* 已在 request 拦截器中处理 */ }
  finally { submitting.value = false }
}

async function handleDelete(id: number) {
  try {
    await deleteBaseRoute(id)
    ElMessage.success('删除成功')
    fetchList()
  } catch { /* 已在 request 拦截器中处理 */ }
}
</script>

<style scoped>
/* 工具条仅承载主操作：右对齐；窄屏由全局工具条规则自动换行 */
.table-toolbar {
  justify-content: flex-end;
}

/* ======== 弹窗表单统一对齐 ======== */
.route-form .el-form-item__label {
  text-align: left;
  justify-content: flex-start;
}

.route-form .el-form-item {
  align-items: center;
  margin-bottom: 18px;
}

/* ======== 工序配置表格 ======== */
.steps-table {
  width: 100%;
}

/* 表格单元格垂直居中 */
.steps-table :deep(.el-table__cell) {
  vertical-align: middle;
}

.steps-table :deep(.cell) {
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 4px 6px;
}

/* 工序列内容左对齐 */
.steps-table .el-table__column:not(:first-child):not(:last-child) .cell {
  justify-content: flex-start;
}

/* 工序下拉框无额外边距 */
.steps-table .el-select {
  display: block;
}

/* 添加工序按钮行：左对齐，间距统一 */
.add-step-row {
  margin-top: 10px;
  text-align: left;
}

/* 状态单选框行垂直居中 */
.route-form .el-radio-group {
  display: inline-flex;
  align-items: center;
  gap: 16px;
}

/* 表单提示信息左对齐 */
.route-form :deep(.el-form-item__error) {
  text-align: left;
}
</style>
