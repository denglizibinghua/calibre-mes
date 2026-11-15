<template>
  <PageContainer title="设备台账" description="维护生产设备档案与运行状态。">
    <!-- 搜索栏 -->
    <div class="search-bar">
      <el-input v-model="searchForm.equipmentCode" placeholder="设备编码" clearable />
      <el-input v-model="searchForm.equipmentName" placeholder="设备名称" clearable />
      <div class="search-bar-actions">
        <el-button type="primary" @click="handleSearch">查询</el-button>
        <el-button @click="resetSearch">重置</el-button>
      </div>
    </div>

    <!-- 表格工具条 -->
    <div class="table-toolbar">
      <div class="table-toolbar-actions">
        <el-button type="primary" @click="handleAdd">
          <el-icon><Plus /></el-icon> 新增设备
        </el-button>
      </div>
    </div>

    <!-- 表格 -->
    <el-table :data="tableData" border stripe v-loading="loading" style="width: 100%">
      <el-table-column prop="equipmentId" label="ID" width="80" class-name="mono-num" />
      <el-table-column prop="equipmentCode" label="设备编码" width="140" show-overflow-tooltip />
      <el-table-column prop="equipmentName" label="设备名称" width="140" show-overflow-tooltip />
      <el-table-column prop="model" label="型号" width="120" show-overflow-tooltip />
      <el-table-column prop="unitId" label="所属单元ID" width="120" class-name="mono-num" />
      <el-table-column prop="status" label="启用状态" width="100">
        <template #default="{ row }">
          <el-tag :type="row.status === '0' ? 'success' : 'danger'" size="small">
            {{ row.status === '0' ? '启用' : '禁用' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="runningStatus" label="运行状态" width="100">
        <template #default="{ row }">
          <el-tag :type="row.runningStatus === '0' ? 'success' : 'danger'" size="small">
            {{ row.runningStatus === '0' ? '可运行' : '不可运行' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="createTime" label="创建时间" width="180" class-name="mono-num" />
      <el-table-column label="操作" min-width="150" fixed="right">
        <template #default="{ row }">
          <div class="table-actions">
            <el-button type="primary" link size="small" title="编辑设备" @click="handleEdit(row)">编辑</el-button>
            <el-popconfirm title="确定删除该设备吗？" confirm-button-type="danger" @confirm="handleDelete(row.equipmentId)">
              <template #reference>
                <el-button type="danger" link size="small" title="删除设备">删除</el-button>
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
      :title="isEdit ? '编辑设备' : '新增设备'"
      width="560px"
      :close-on-click-modal="false"
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-width="80px">
        <el-form-item label="设备编码" prop="equipmentCode">
          <el-input v-model="form.equipmentCode" placeholder="请输入设备编码" />
        </el-form-item>
        <el-form-item label="设备名称" prop="equipmentName">
          <el-input v-model="form.equipmentName" placeholder="请输入设备名称" />
        </el-form-item>
        <el-form-item label="型号" prop="model">
          <el-input v-model="form.model" placeholder="请输入型号" />
        </el-form-item>
        <el-form-item label="所属单元ID" prop="unitId">
          <el-input v-model.number="form.unitId" placeholder="请输入所属单元ID" />
        </el-form-item>
        <el-form-item label="启用状态" prop="status">
          <el-radio-group v-model="form.status">
            <el-radio value="0">启用</el-radio>
            <el-radio value="1">禁用</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="运行状态" v-if="isEdit">
          <span>{{ form.runningStatus === '1' ? '不可运行' : '可运行' }}</span>
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
import { getBaseEquipmentList, createBaseEquipment, updateBaseEquipment, deleteBaseEquipment } from '@/api/baseEquipment'
import type { BaseEquipment } from '@/types/api'

const tableData = ref<BaseEquipment[]>([])
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
  equipmentCode: '',
  equipmentName: ''
})

const form = reactive<BaseEquipment>({
  equipmentCode: '',
  equipmentName: '',
  model: '',
  unitId: undefined,
  status: '0'
})

const rules: FormRules = {
  equipmentCode: [{ required: true, message: '请输入设备编码', trigger: 'blur' }],
  equipmentName: [{ required: true, message: '请输入设备名称', trigger: 'blur' }],
  status: [{ required: true, message: '请选择状态', trigger: 'change' }]
}

onMounted(() => {
  fetchList()
})

async function fetchList() {
  loading.value = true
  try {
    const res = await getBaseEquipmentList({ page: query.page, size: query.size, ...searchForm })
    // V2.3: 后端返回 PageResult，request 拦截器已解包 data
    tableData.value = Array.isArray(res?.records) ? res.records : []
    total.value = res?.total ?? 0
  } catch { /* 已在 request 拦截器中处理 */ }
  finally { loading.value = false }
}

function resetSearch() {
  searchForm.equipmentCode = ''
  searchForm.equipmentName = ''
  query.page = 1
  fetchList()
}

function handleSearch() {
  query.page = 1
  fetchList()
}

function resetForm() {
  form.equipmentCode = ''
  form.equipmentName = ''
  form.model = ''
  form.unitId = undefined
  form.status = '0'
}

function handleAdd() {
  isEdit.value = false
  editId.value = undefined
  resetForm()
  dialogVisible.value = true
}

function handleEdit(row: BaseEquipment) {
  isEdit.value = true
  editId.value = row.equipmentId
  form.equipmentCode = row.equipmentCode
  form.equipmentName = row.equipmentName
  form.model = row.model
  form.unitId = row.unitId
  form.status = row.status
  dialogVisible.value = true
}

async function handleSubmit() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return

  submitting.value = true
  try {
    if (isEdit.value && editId.value) {
      await updateBaseEquipment(editId.value, { ...form })
      ElMessage.success('编辑成功')
    } else {
      await createBaseEquipment({ ...form })
      ElMessage.success('新增成功')
    }
    dialogVisible.value = false
    fetchList()
  } catch { /* 已在 request 拦截器中处理 */ }
  finally { submitting.value = false }
}

async function handleDelete(id: number) {
  try {
    await deleteBaseEquipment(id)
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
