<template>
  <PageContainer title="工序管理" description="维护装配工序、标准工时与检验要求。">
    <!-- 搜索栏（在当前已加载数据中筛选） -->
    <div class="search-bar">
      <el-input v-model="searchForm.processCode" placeholder="工序编码（本地筛选）" clearable />
      <el-input v-model="searchForm.processName" placeholder="工序名称（本地筛选）" clearable />
      <div class="search-bar-actions">
        <el-button type="primary" @click="handleSearch">查询</el-button>
        <el-button @click="resetSearch">重置</el-button>
      </div>
    </div>

    <!-- 表格工具条 -->
    <div class="table-toolbar">
      <div class="table-toolbar-actions">
        <el-button type="primary" @click="handleAdd">
          <el-icon><Plus /></el-icon> 新增工序
        </el-button>
      </div>
    </div>

    <!-- 表格 -->
    <el-table :data="tableData" border stripe v-loading="loading" style="width: 100%">
      <el-table-column prop="processId" label="ID" width="80" class-name="mono-num" />
      <el-table-column prop="processCode" label="工序编码" width="140" show-overflow-tooltip />
      <el-table-column prop="processName" label="工序名称" width="160" show-overflow-tooltip />
      <el-table-column prop="standardMinutes" label="标准工时(分钟)" width="140" class-name="mono-num" />
      <el-table-column prop="status" label="状态" width="100">
        <template #default="{ row }">
          <el-tag :type="row.status === '0' ? 'success' : 'danger'" size="small">
            {{ row.status === '0' ? '启用' : '禁用' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="createTime" label="创建时间" width="180" class-name="mono-num" />
      <el-table-column label="操作" min-width="150" fixed="right">
        <template #default="{ row }">
          <div class="table-actions">
            <el-button type="primary" link size="small" title="编辑工序" @click="handleEdit(row)">编辑</el-button>
            <el-popconfirm title="确定删除该工序吗？" confirm-button-type="danger" @confirm="handleDelete(row.processId)">
              <template #reference>
                <el-button type="danger" link size="small" title="删除工序">删除</el-button>
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
      :title="isEdit ? '编辑工序' : '新增工序'"
      width="520px"
      :close-on-click-modal="false"
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-width="120px">
        <el-form-item label="工序编码" prop="processCode">
          <el-input v-model="form.processCode" placeholder="请输入工序编码" />
        </el-form-item>
        <el-form-item label="工序名称" prop="processName">
          <el-input v-model="form.processName" placeholder="请输入工序名称" />
        </el-form-item>
        <el-form-item label="标准工时(分钟)" prop="standardMinutes">
          <el-input-number
            v-model="form.standardMinutes"
            :min="0"
            placeholder="请输入标准工时"
            style="width: 100%"
          />
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
import { getBaseProcessList, createBaseProcess, updateBaseProcess, deleteBaseProcess } from '@/api/baseProcess'
import type { BaseProcess } from '@/types/api'

const tableData = ref<BaseProcess[]>([])
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
  processCode: '',
  processName: ''
})

const form = reactive<BaseProcess>({
  processCode: '',
  processName: '',
  standardMinutes: undefined,
  status: '0'
})

/** 新增和编辑校验规则分开定义，支持后续差异化 */
function getFormRules(_isEdit: boolean): FormRules {
  return {
    processCode: [{ required: true, message: '请输入工序编码', trigger: 'blur' }],
    processName: [{ required: true, message: '请输入工序名称', trigger: 'blur' }],
    // standardMinutes: 新增和编辑都选填（DB INT NULL DEFAULT 0）
    status: [{ required: true, message: '请选择状态', trigger: 'change' }]
  }
}

const rules = ref<FormRules>(getFormRules(false))

onMounted(() => {
  fetchList()
})

async function fetchList() {
  loading.value = true
  try {
    const res = await getBaseProcessList({ page: query.page, size: query.size })
    // V2.3: 后端返回 PageResult，request 拦截器已解包 data
    const allData: BaseProcess[] = Array.isArray(res?.records) ? res.records : []
    total.value = res?.total ?? 0
    // 前端本地筛选（仅作用于当前页；服务端筛选后续版本再做）
    const code = searchForm.processCode.trim().toLowerCase()
    const name = searchForm.processName.trim().toLowerCase()
    tableData.value = allData.filter(item => {
      const matchCode = !code || (item.processCode || '').toLowerCase().includes(code)
      const matchName = !name || (item.processName || '').toLowerCase().includes(name)
      return matchCode && matchName
    })
  } catch { /* 已在 request 拦截器中处理 */ }
  finally { loading.value = false }
}

function handleSearch() {
  query.page = 1
  fetchList()
}

function resetSearch() {
  searchForm.processCode = ''
  searchForm.processName = ''
  handleSearch()
}

function resetForm() {
  form.processCode = ''
  form.processName = ''
  form.standardMinutes = undefined
  form.status = '0'
}

function handleAdd() {
  isEdit.value = false
  editId.value = undefined
  resetForm()
  rules.value = getFormRules(false)
  dialogVisible.value = true
}

function handleEdit(row: BaseProcess) {
  isEdit.value = true
  editId.value = row.processId
  form.processCode = row.processCode
  form.processName = row.processName
  form.standardMinutes = row.standardMinutes
  form.status = row.status
  rules.value = getFormRules(true)
  dialogVisible.value = true
}

async function handleSubmit() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return

  submitting.value = true
  try {
    if (isEdit.value && editId.value) {
      await updateBaseProcess(editId.value, { ...form })
      ElMessage.success('编辑成功')
    } else {
      await createBaseProcess({ ...form })
      ElMessage.success('新增成功')
    }
    dialogVisible.value = false
    fetchList()
  } catch { /* 已在 request 拦截器中处理 */ }
  finally { submitting.value = false }
}

async function handleDelete(id: number) {
  try {
    await deleteBaseProcess(id)
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
</style>
