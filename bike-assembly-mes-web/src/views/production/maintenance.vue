<template>
  <PageContainer title="设备保养" description="管理设备保养计划与执行记录。">
    <!-- 搜索栏（在当前已加载数据中筛选） -->
    <div class="search-bar">
      <el-input v-model="searchForm.maintenanceType" placeholder="保养类型（本地筛选）" clearable />
      <el-input v-model="searchForm.responsibleUser" placeholder="负责人（本地筛选）" clearable />
      <div class="search-bar-actions">
        <el-button type="primary" @click="handleSearch">查询</el-button>
        <el-button @click="resetSearch">重置</el-button>
      </div>
    </div>

    <!-- 表格工具条 -->
    <div class="table-toolbar">
      <div class="table-toolbar-actions">
        <el-button type="primary" @click="handleAdd">
          <el-icon><Plus /></el-icon> 新增保养记录
        </el-button>
      </div>
    </div>

    <!-- 表格 -->
    <el-table :data="tableData" border stripe v-loading="loading" style="width: 100%">
      <el-table-column prop="maintenanceId" label="ID" width="60" class-name="mono-num" />
      <el-table-column prop="equipmentId" label="设备 ID" width="80" class-name="mono-num" />
      <el-table-column prop="maintenanceType" label="保养类型" width="100">
        <template #default="{ row }">
          <el-tag
            :type="row.maintenanceType === '日常保养' ? 'success' : row.maintenanceType === '一级保养' ? 'warning' : 'info'"
            size="small"
          >
            {{ row.maintenanceType || '-' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="planTime" label="计划时间" width="150" class-name="mono-num" />
      <el-table-column prop="finishTime" label="完成时间" width="150" class-name="mono-num">
        <template #default="{ row }">
          <span :class="{ 'text-muted': !row.finishTime }">
            {{ row.finishTime || '未完成' }}
          </span>
        </template>
      </el-table-column>
      <el-table-column prop="responsibleUser" label="负责人" width="100" />
      <el-table-column prop="status" label="状态" width="80">
        <template #default="{ row }">
          <el-tag :type="row.status === '0' ? 'success' : 'danger'" size="small">
            {{ row.status === '0' ? '启用' : '禁用' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="remark" label="备注" min-width="120" show-overflow-tooltip>
        <template #default="{ row }">
          <span :class="{ 'text-muted': !row.remark }">{{ row.remark || '-' }}</span>
        </template>
      </el-table-column>
      <el-table-column label="操作" min-width="130" fixed="right">
        <template #default="{ row }">
          <div class="table-actions">
            <el-button type="primary" link size="small" title="编辑保养记录" @click="handleEdit(row)">编辑</el-button>
            <el-popconfirm title="确定删除该保养记录吗？" confirm-button-type="danger" @confirm="handleDelete(row.maintenanceId)">
              <template #reference>
                <el-button type="danger" link size="small" title="删除保养记录">删除</el-button>
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
      :title="isEdit ? '编辑保养记录' : '新增保养记录'"
      width="560px"
      :close-on-click-modal="false"
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="设备 ID" prop="equipmentId">
          <el-input-number
            v-model="form.equipmentId"
            :min="1"
            placeholder="请输入设备 ID"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="保养类型" prop="maintenanceType">
          <el-select v-model="form.maintenanceType" placeholder="请选择保养类型" style="width: 100%">
            <el-option label="日常保养" value="日常保养" />
            <el-option label="一级保养" value="一级保养" />
            <el-option label="二级保养" value="二级保养" />
            <el-option label="年度保养" value="年度保养" />
          </el-select>
        </el-form-item>
        <el-form-item label="计划时间" prop="planTime">
          <el-date-picker
            v-model="form.planTime"
            type="datetime"
            placeholder="请选择计划保养时间"
            value-format="YYYY-MM-DD HH:mm:ss"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="完成时间" prop="finishTime">
          <el-date-picker
            v-model="form.finishTime"
            type="datetime"
            placeholder="请选择完成时间（选填）"
            value-format="YYYY-MM-DD HH:mm:ss"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="负责人" prop="responsibleUser">
          <el-input v-model="form.responsibleUser" placeholder="请输入负责人" />
        </el-form-item>
        <el-form-item label="备注" prop="remark">
          <el-input
            v-model="form.remark"
            type="textarea"
            :rows="3"
            placeholder="请输入备注（选填）"
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
import { getEqmMaintenanceList, createEqmMaintenance, updateEqmMaintenance, deleteEqmMaintenance } from '@/api/eqmMaintenance'
import type { EqmMaintenance } from '@/types/api'

const tableData = ref<EqmMaintenance[]>([])
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
  maintenanceType: '',
  responsibleUser: ''
})

const form = reactive<EqmMaintenance>({
  equipmentId: undefined,
  maintenanceType: '',
  planTime: '',
  finishTime: '',
  responsibleUser: '',
  status: '0',
  remark: ''
})

/** 新增和编辑校验规则分开定义，支持后续差异化 */
function getFormRules(_isEdit: boolean): FormRules {
  return {
    equipmentId: [{ required: true, message: '请输入设备 ID', trigger: 'change' }],
    maintenanceType: [{ required: true, message: '请选择保养类型', trigger: 'change' }],
    planTime: [{ required: true, message: '请选择计划时间', trigger: 'change' }],
    // finishTime: 新增和编辑都选填（DB DATETIME NULL，保养可能尚未完成）
    responsibleUser: [{ required: true, message: '请输入负责人', trigger: 'blur' }],
    // remark: 新增和编辑都选填（DB VARCHAR(255) NULL）
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
    const res = await getEqmMaintenanceList({ page: query.page, size: query.size })
    const allData: EqmMaintenance[] = Array.isArray(res?.records) ? res.records : []
    total.value = res?.total ?? 0
    // 前端本地筛选（仅作用于当前页；服务端筛选后续版本再做）
    const type = searchForm.maintenanceType.trim().toLowerCase()
    const user = searchForm.responsibleUser.trim().toLowerCase()
    tableData.value = allData.filter(item => {
      const matchType = !type || (item.maintenanceType || '').toLowerCase().includes(type)
      const matchUser = !user || (item.responsibleUser || '').toLowerCase().includes(user)
      return matchType && matchUser
    })
  } catch { /* 已在 request 拦截器中处理 */ }
  finally { loading.value = false }
}

function resetSearch() {
  searchForm.maintenanceType = ''
  searchForm.responsibleUser = ''
  query.page = 1
  fetchList()
}

function handleSearch() {
  query.page = 1
  fetchList()
}

function resetForm() {
  form.equipmentId = undefined
  form.maintenanceType = ''
  form.planTime = ''
  form.finishTime = ''
  form.responsibleUser = ''
  form.status = '0'
  form.remark = ''
}

function handleAdd() {
  isEdit.value = false
  editId.value = undefined
  resetForm()
  rules.value = getFormRules(false)
  dialogVisible.value = true
}

function handleEdit(row: EqmMaintenance) {
  isEdit.value = true
  editId.value = row.maintenanceId
  form.equipmentId = row.equipmentId
  form.maintenanceType = row.maintenanceType
  form.planTime = row.planTime || ''
  form.finishTime = row.finishTime || ''
  form.responsibleUser = row.responsibleUser
  form.status = row.status
  form.remark = row.remark || ''
  rules.value = getFormRules(true)
  dialogVisible.value = true
}

async function handleSubmit() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return

  submitting.value = true
  try {
    if (isEdit.value && editId.value) {
      await updateEqmMaintenance(editId.value, { ...form })
      ElMessage.success('编辑成功')
    } else {
      await createEqmMaintenance({ ...form })
      ElMessage.success('新增成功')
    }
    dialogVisible.value = false
    fetchList()
  } catch { /* 已在 request 拦截器中处理 */ }
  finally { submitting.value = false }
}

async function handleDelete(id: number) {
  try {
    await deleteEqmMaintenance(id)
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
