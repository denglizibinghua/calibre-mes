<template>
  <PageContainer title="质检项管理" description="配置质检项目、判定标准与抽检规则。">
    <!-- 搜索栏（在当前已加载数据中筛选） -->
    <div class="search-bar">
      <el-input v-model="searchForm.itemName" placeholder="质检项名称（本地筛选）" clearable />
      <el-input v-model="searchForm.checkMethod" placeholder="检验方法（本地筛选）" clearable />
      <div class="search-bar-actions">
        <el-button type="primary" @click="handleSearch">查询</el-button>
        <el-button @click="resetSearch">重置</el-button>
      </div>
    </div>

    <!-- 表格工具条 -->
    <div class="table-toolbar">
      <div class="table-toolbar-actions">
        <el-button type="primary" @click="handleAdd">
          <el-icon><Plus /></el-icon> 新增质检项
        </el-button>
      </div>
    </div>

    <!-- 表格 -->
    <el-table :data="tableData" border stripe v-loading="loading" style="width: 100%">
      <el-table-column prop="inspectionItemId" label="ID" width="60" class-name="mono-num" />
      <el-table-column label="工序" min-width="140" show-overflow-tooltip>
        <template #default="{ row }">
          {{ processNameLabel(row.processId) }}
        </template>
      </el-table-column>
      <el-table-column prop="itemName" label="质检项名称" min-width="130" show-overflow-tooltip />
      <el-table-column prop="standard" label="检验标准" min-width="160">
        <template #default="{ row }">
          <el-tooltip :content="row.standard" placement="top" :show-after="500">
            <span class="text-ellipsis">{{ row.standard }}</span>
          </el-tooltip>
        </template>
      </el-table-column>
      <el-table-column prop="checkMethod" label="检验方法" width="100" />
      <el-table-column prop="status" label="状态" width="90">
        <template #default="{ row }">
          <el-tag :type="row.status === '0' ? 'success' : 'danger'" size="small">
            {{ row.status === '0' ? '启用' : '禁用' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="createTime" label="创建时间" width="160" class-name="mono-num" />
      <el-table-column label="操作" min-width="140" fixed="right">
        <template #default="{ row }">
          <div class="table-actions">
            <el-button type="primary" link size="small" title="编辑质检项" @click="handleEdit(row)">编辑</el-button>
            <el-popconfirm title="确定删除该质检项吗？" confirm-button-type="danger" @confirm="handleDelete(row.inspectionItemId)">
              <template #reference>
                <el-button type="danger" link size="small" title="删除质检项">删除</el-button>
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
      :title="isEdit ? '编辑质检项' : '新增质检项'"
      width="560px"
      :close-on-click-modal="false"
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-width="110px">
        <el-form-item label="工序" prop="processId">
          <el-select
            v-model="form.processId"
            placeholder="请选择关联工序"
            filterable
            clearable
            style="width: 100%"
          >
            <el-option
              v-for="p in processOptions"
              :key="p.processId"
              :label="`${p.processCode} - ${p.processName}`"
              :value="p.processId"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="质检项名称" prop="itemName">
          <el-input v-model="form.itemName" placeholder="请输入质检项名称" />
        </el-form-item>
        <el-form-item label="检验标准" prop="standard">
          <el-input
            v-model="form.standard"
            type="textarea"
            :rows="3"
            placeholder="请输入检验标准"
          />
        </el-form-item>
        <el-form-item label="检验方法" prop="checkMethod">
          <el-select v-model="form.checkMethod" placeholder="请选择检验方法" style="width: 100%">
            <el-option label="目视检查" value="目视检查" />
            <el-option label="量具测量" value="量具测量" />
            <el-option label="仪器检测" value="仪器检测" />
            <el-option label="功能测试" value="功能测试" />
            <el-option label="试装验证" value="试装验证" />
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
  </PageContainer>
</template>

<script setup lang="ts">
import { reactive, ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import PageContainer from '@/components/PageContainer.vue'
import { getQmsInspectionItemList, createQmsInspectionItem, updateQmsInspectionItem, deleteQmsInspectionItem } from '@/api/qmsInspectionItem'
import { getBaseProcessList } from '@/api/baseProcess'
import type { QmsInspectionItem, BaseProcess } from '@/types/api'

const tableData = ref<QmsInspectionItem[]>([])
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
  itemName: '',
  checkMethod: ''
})

const form = reactive<QmsInspectionItem>({
  processId: undefined,
  itemName: '',
  standard: '',
  checkMethod: '',
  status: '0'
})

/** 新增和编辑校验规则分开定义，支持后续差异化 */
function getFormRules(_isEdit: boolean): FormRules {
  return {
    processId: [{ required: true, message: '请选择工序', trigger: 'change' }],
    itemName: [{ required: true, message: '请输入质检项名称', trigger: 'blur' }],
    standard: [{ required: true, message: '请输入检验标准', trigger: 'blur' }],
    checkMethod: [{ required: true, message: '请选择检验方法', trigger: 'change' }],
    status: [{ required: true, message: '请选择状态', trigger: 'change' }]
  }
}

const rules = ref<FormRules>(getFormRules(false))

/** 可选工序列表（仅启用状态） */
const processOptions = ref<BaseProcess[]>([])
/** 工序 ID → 工序信息映射（表格显示用） */
const processMap = ref<Map<number, BaseProcess>>(new Map())

onMounted(() => {
  fetchList()
  fetchProcessOptions()
})

async function fetchList() {
  loading.value = true
  try {
    const res = await getQmsInspectionItemList({ page: query.page, size: query.size })
    // V2.3: 后端返回 PageResult，request 拦截器已解包 data
    const allData: QmsInspectionItem[] = Array.isArray(res?.records) ? res.records : []
    total.value = res?.total ?? 0
    // 前端本地筛选（仅作用于当前页；服务端筛选后续版本再做）
    const name = searchForm.itemName.trim().toLowerCase()
    const method = searchForm.checkMethod.trim().toLowerCase()
    tableData.value = allData.filter(item => {
      const matchName = !name || (item.itemName || '').toLowerCase().includes(name)
      const matchMethod = !method || (item.checkMethod || '').toLowerCase().includes(method)
      return matchName && matchMethod
    })
  } catch { /* 已在 request 拦截器中处理 */ }
  finally { loading.value = false }
}

function resetSearch() {
  searchForm.itemName = ''
  searchForm.checkMethod = ''
  query.page = 1
  fetchList()
}

function handleSearch() {
  query.page = 1
  fetchList()
}

async function fetchProcessOptions() {
  try {
    const res = await getBaseProcessList({ page: 1, size: 9999 })
    const all: BaseProcess[] = Array.isArray(res?.records) ? res.records : []
    processOptions.value = all.filter(p => p.status === '0')
    processMap.value = new Map(all.map(p => [p.processId!, p]))
  } catch { /* ignore */ }
}

function processNameLabel(processId?: number): string {
  if (processId == null) return '-'
  const p = processMap.value.get(processId)
  return p ? `${p.processCode} - ${p.processName}` : `ID: ${processId}`
}

function resetForm() {
  form.processId = undefined
  form.itemName = ''
  form.standard = ''
  form.checkMethod = ''
  form.status = '0'
}

function handleAdd() {
  isEdit.value = false
  editId.value = undefined
  resetForm()
  rules.value = getFormRules(false)
  dialogVisible.value = true
  fetchProcessOptions()
}

function handleEdit(row: QmsInspectionItem) {
  isEdit.value = true
  editId.value = row.inspectionItemId
  form.processId = row.processId
  form.itemName = row.itemName
  form.standard = row.standard
  form.checkMethod = row.checkMethod
  form.status = row.status
  rules.value = getFormRules(true)
  dialogVisible.value = true
  fetchProcessOptions()
}

async function handleSubmit() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return

  submitting.value = true
  try {
    if (isEdit.value && editId.value) {
      await updateQmsInspectionItem(editId.value, { ...form })
      ElMessage.success('编辑成功')
    } else {
      await createQmsInspectionItem({ ...form })
      ElMessage.success('新增成功')
    }
    dialogVisible.value = false
    fetchList()
  } catch { /* 已在 request 拦截器中处理 */ }
  finally { submitting.value = false }
}

async function handleDelete(id: number) {
  try {
    await deleteQmsInspectionItem(id)
    ElMessage.success('删除成功')
    fetchList()
  } catch { /* 已在 request 拦截器中处理 */ }
}
</script>

<style scoped>
/* 工具条仅放主操作：右对齐 */
.table-toolbar {
  justify-content: flex-end;
}
</style>
