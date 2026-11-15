<template>
  <PageContainer title="库位管理" description="维护仓库库位编码、类型与容量。">
    <!-- 搜索栏 -->
    <div class="search-bar">
      <el-input v-model="searchForm.locationCode" placeholder="位置编码" clearable />
      <el-input v-model="searchForm.locationName" placeholder="位置名称" clearable />
      <div class="search-bar-actions">
        <el-button type="primary" @click="handleSearch">查询</el-button>
        <el-button @click="resetSearch">重置</el-button>
      </div>
    </div>

    <!-- 表格工具条 -->
    <div class="table-toolbar">
      <div class="table-toolbar-actions">
        <el-button type="primary" @click="handleAdd">
          <el-icon><Plus /></el-icon> 新增库位
        </el-button>
      </div>
    </div>

    <!-- 表格 -->
    <el-table :data="filteredData" border stripe v-loading="loading" style="width: 100%">
      <el-table-column prop="locationId" label="ID" width="80" class-name="mono-num" />
      <el-table-column prop="locationCode" label="位置编码" width="140" />
      <el-table-column prop="locationName" label="位置名称" min-width="140" show-overflow-tooltip />
      <el-table-column label="父级库位" width="150" show-overflow-tooltip>
        <template #default="{ row }">
          {{ parentLocationLabel(row.parentId) }}
        </template>
      </el-table-column>
      <el-table-column label="位置类型" width="100">
        <template #default="{ row }">
          <el-tag :type="locationTypeTag(row.locationType)" size="small">
            {{ locationTypeLabel(row.locationType) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="状态" width="80">
        <template #default="{ row }">
          <el-tag :type="row.status === '0' ? 'success' : 'danger'" size="small">
            {{ row.status === '0' ? '启用' : '禁用' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="createTime" label="创建时间" width="180" class-name="mono-num" />
      <el-table-column label="操作" min-width="200" fixed="right">
        <template #default="{ row }">
          <div class="table-actions">
            <el-button type="primary" link size="small" title="查看库位详情" @click="handleView(row)">详情</el-button>
            <el-button type="primary" link size="small" title="编辑库位" @click="handleEdit(row)">编辑</el-button>
            <el-popconfirm title="确定删除该库位吗？" confirm-button-type="danger" @confirm="handleDelete(row.locationId!)">
              <template #reference>
                <el-button type="danger" link size="small" title="删除库位">删除</el-button>
              </template>
            </el-popconfirm>
          </div>
        </template>
      </el-table-column>
      <template #empty>
        <el-empty description="暂无数据" :image-size="72" />
      </template>
    </el-table>

    <!-- 分页 -->
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
      :title="isEdit ? '编辑库位' : '新增库位'"
      width="520px"
      :close-on-click-modal="false"
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="位置编码" prop="locationCode">
          <el-input v-model="form.locationCode" placeholder="请输入位置编码" maxlength="64" />
        </el-form-item>
        <el-form-item label="位置名称" prop="locationName">
          <el-input v-model="form.locationName" placeholder="请输入位置名称" maxlength="128" />
        </el-form-item>
        <el-form-item label="父级库位" prop="parentId">
          <el-select
            v-model="form.parentId"
            placeholder="请选择父级库位（可选）"
            clearable
            filterable
            style="width: 100%"
          >
            <el-option
              v-for="loc in parentOptions"
              :key="loc.locationId"
              :label="`${loc.locationCode} - ${loc.locationName}`"
              :value="loc.locationId!"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="位置类型" prop="locationType">
          <el-select v-model="form.locationType" placeholder="请选择位置类型" style="width: 100%">
            <el-option label="仓库 (W)" value="W" />
            <el-option label="库区 (A)" value="A" />
            <el-option label="库位 (L)" value="L" />
          </el-select>
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

    <!-- 查看详情对话框（只读） -->
    <el-dialog
      v-model="detailVisible"
      title="库位详情"
      width="520px"
      :close-on-click-modal="false"
    >
      <el-descriptions v-if="detailRow" :column="2" border>
        <el-descriptions-item label="ID">{{ detailRow.locationId }}</el-descriptions-item>
        <el-descriptions-item label="位置编码">{{ detailRow.locationCode }}</el-descriptions-item>
        <el-descriptions-item label="位置名称">{{ detailRow.locationName }}</el-descriptions-item>
        <el-descriptions-item label="父级库位">{{ parentLocationLabel(detailRow.parentId) }}</el-descriptions-item>
        <el-descriptions-item label="位置类型">{{ locationTypeLabel(detailRow.locationType) }}</el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag :type="detailRow.status === '0' ? 'success' : 'danger'" size="small">
            {{ detailRow.status === '0' ? '启用' : '禁用' }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="创建时间" :span="2">{{ detailRow.createTime || '-' }}</el-descriptions-item>
      </el-descriptions>
      <template #footer>
        <el-button @click="detailVisible = false">关闭</el-button>
      </template>
    </el-dialog>
  </PageContainer>
</template>

<script setup lang="ts">
import { reactive, ref, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import PageContainer from '@/components/PageContainer.vue'
import {
  getWmsLocationList,
  createWmsLocation,
  updateWmsLocation,
  deleteWmsLocation
} from '@/api/wmsLocation'
import type { WmsLocation } from '@/types/api'

// ==================== 响应式状态 ====================

const tableData = ref<WmsLocation[]>([])
const allLocations = ref<WmsLocation[]>([])
const loading = ref(false)
const total = ref(0)
const dialogVisible = ref(false)
const isEdit = ref(false)
const submitting = ref(false)
const formRef = ref<FormInstance>()
const editId = ref<number>()

// 详情对话框
const detailVisible = ref(false)
const detailRow = ref<WmsLocation | null>(null)

const query = reactive({ page: 1, size: 15 })

const searchForm = reactive({
  locationCode: '',
  locationName: ''
})

const form = reactive<WmsLocation>({
  locationCode: '',
  locationName: '',
  parentId: undefined,
  locationType: '',
  status: '0'
})

// ==================== 表单校验规则 ====================

const rules: FormRules = {
  locationCode: [
    { required: true, message: '请输入位置编码', trigger: 'blur' }
  ],
  locationName: [
    { required: true, message: '请输入位置名称', trigger: 'blur' }
  ],
  locationType: [
    { required: true, message: '请选择位置类型', trigger: 'change' }
  ],
  status: [
    { required: true, message: '请选择状态', trigger: 'change' }
  ]
}

// ==================== 计算属性 ====================

/** 当前页数据中按搜索条件过滤 */
const filteredData = computed(() => {
  const code = searchForm.locationCode.trim().toLowerCase()
  const name = searchForm.locationName.trim().toLowerCase()
  if (!code && !name) return tableData.value
  return tableData.value.filter(item => {
    const matchCode = !code || (item.locationCode || '').toLowerCase().includes(code)
    const matchName = !name || (item.locationName || '').toLowerCase().includes(name)
    return matchCode && matchName
  })
})

/** 父级库位下拉选项（编辑时排除自身，避免循环引用） */
const parentOptions = computed(() => {
  if (isEdit.value && editId.value) {
    return allLocations.value.filter(loc => loc.locationId !== editId.value)
  }
  return allLocations.value
})

// ==================== 标签映射 ====================

function locationTypeLabel(code: string): string {
  const map: Record<string, string> = { W: '仓库', A: '库区', L: '库位' }
  return map[code] || code || '-'
}

function locationTypeTag(code: string): 'primary' | 'success' | 'warning' | 'info' {
  if (code === 'W') return 'primary'
  if (code === 'A') return 'success'
  if (code === 'L') return 'warning'
  return 'info'
}

function parentLocationLabel(parentId: number | undefined): string {
  if (!parentId || parentId === 0) return '-'
  const loc = allLocations.value.find(l => l.locationId === parentId)
  return loc ? `${loc.locationCode} - ${loc.locationName}` : `ID:${parentId}`
}

// ==================== 生命周期 ====================

onMounted(() => {
  fetchList()
  fetchAllLocations()
})

// ==================== 数据获取 ====================

async function fetchList() {
  loading.value = true
  try {
    const res = await getWmsLocationList({ page: query.page, size: query.size })
    tableData.value = Array.isArray(res?.records) ? res.records : []
    total.value = res?.total ?? 0
  } catch {
    // 错误已在 request 拦截器中统一处理
  } finally {
    loading.value = false
  }
}

/** 加载全部库位用于父级下拉选择 */
async function fetchAllLocations() {
  try {
    const res = await getWmsLocationList({ page: 1, size: 9999 })
    allLocations.value = Array.isArray(res?.records) ? res.records : []
  } catch {
    // 加载失败不影响主列表功能
  }
}

// ==================== 详情 ====================

function handleView(row: WmsLocation) {
  detailRow.value = row
  detailVisible.value = true
}

// ==================== 搜索 ====================

function handleSearch() {
  // 当前后端 list 接口不支持搜索参数，使用前端本地筛选
  // 始终回到第 1 页
  query.page = 1
  fetchList()
}

function resetSearch() {
  searchForm.locationCode = ''
  searchForm.locationName = ''
  query.page = 1
  fetchList()
}

// ==================== 表单操作 ====================

function resetForm() {
  form.locationCode = ''
  form.locationName = ''
  form.parentId = undefined
  form.locationType = ''
  form.status = '0'
  formRef.value?.clearValidate()
}

function handleAdd() {
  isEdit.value = false
  editId.value = undefined
  resetForm()
  dialogVisible.value = true
}

function handleEdit(row: WmsLocation) {
  isEdit.value = true
  editId.value = row.locationId
  form.locationCode = row.locationCode
  form.locationName = row.locationName
  form.parentId = row.parentId && row.parentId > 0 ? row.parentId : undefined
  form.locationType = row.locationType
  form.status = row.status
  dialogVisible.value = true
}

async function handleSubmit() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return

  submitting.value = true
  try {
    // parentId 为 undefined 时发送 0（数据库 NOT NULL DEFAULT 0）
    const payload = { ...form, parentId: form.parentId ?? 0 }

    if (isEdit.value && editId.value) {
      await updateWmsLocation(editId.value, payload)
      ElMessage.success('编辑成功')
    } else {
      await createWmsLocation(payload)
      ElMessage.success('新增成功')
    }
    dialogVisible.value = false
    fetchList()
    fetchAllLocations()
  } catch {
    // 错误已在 request 拦截器中统一处理
  } finally {
    submitting.value = false
  }
}

// ==================== 删除 ====================

async function handleDelete(id: number) {
  try {
    await deleteWmsLocation(id)
    ElMessage.success('删除成功')
    fetchList()
    fetchAllLocations()
  } catch {
    // 错误已在 request 拦截器中统一处理
  }
}
</script>

<style scoped>
/* 工具条仅放主操作：右对齐 */
.table-toolbar {
  justify-content: flex-end;
}
</style>
